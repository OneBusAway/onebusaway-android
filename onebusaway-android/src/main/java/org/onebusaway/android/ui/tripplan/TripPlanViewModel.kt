/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.tripplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.onebusaway.android.util.TimeProvider
import org.opentripplanner.api.model.Itinerary

/**
 * Owns the trip-plan form ([formState]) and plan submission ([planState]). Address autocomplete runs
 * through two debounced query pipelines (one per endpoint), mirroring the SearchViewModel pattern.
 * Like the legacy form, completing a field auto-submits when both endpoints have coordinates. The
 * initial date/time and advanced options come from injected collaborators (a [TimeProvider] for "now"
 * and an [AdvancedSettingsRepository] for the saved preferences) so the ViewModel stays JVM-testable.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripPlanViewModel @Inject constructor(
    private val geocode: GeocodeRepository,
    private val planRepository: TripPlanRepository,
    timeProvider: TimeProvider,
    settingsRepository: AdvancedSettingsRepository,
) : ViewModel() {

    private val initialDateTimeMillis = timeProvider.now()
    private val initialSettings = settingsRepository.load()

    private val _formState = MutableStateFlow(
        TripPlanFormState(
            dateTimeMillis = initialDateTimeMillis,
            dateLabel = formatDate(initialDateTimeMillis),
            timeLabel = formatTime(initialDateTimeMillis),
            modeId = initialSettings.modeId,
            maxWalkMeters = initialSettings.maxWalkMeters,
            optimizeTransfers = initialSettings.optimizeTransfers,
            wheelchair = initialSettings.wheelchair
        )
    )
    val formState: StateFlow<TripPlanFormState> = _formState.asStateFlow()

    private val _planState = MutableStateFlow<PlanResult>(PlanResult.Idle)
    val planState: StateFlow<PlanResult> = _planState.asStateFlow()

    private val fromQueries = MutableStateFlow("")
    private val toQueries = MutableStateFlow("")

    // Plan submissions, driven reactively. Each user action that can change the plan sends its params
    // (or null when the form isn't submittable) here; the collector below [mapLatest]-cancels the plan
    // in flight as soon as the next input arrives, so a slow plan that outlives its edit can't publish a
    // stale result — the same latest-wins pattern as the suggestion pipelines, no request bookkeeping.
    // A CONFLATED channel (not a SharedFlow) keeps only the latest input and retains it until the
    // collector is ready, so an input sent before collection starts isn't lost. Deliberately NOT derived
    // from [_formState]: restoreFrom injects a result without re-planning.
    private val planInputs = Channel<TripPlanParams?>(Channel.CONFLATED)

    init {
        fromQueries
            .debounce(SUGGEST_DEBOUNCE_MS)
            .mapLatest(::suggestionsFor)
            .onEach { suggestions -> _formState.update { it.copy(fromSuggestions = suggestions) } }
            .launchIn(viewModelScope)
        toQueries
            .debounce(SUGGEST_DEBOUNCE_MS)
            .mapLatest(::suggestionsFor)
            .onEach { suggestions -> _formState.update { it.copy(toSuggestions = suggestions) } }
            .launchIn(viewModelScope)
        planInputs.receiveAsFlow()
            .mapLatest { params ->
                if (params == null) {
                    // Form no longer submittable: drop a surfaced result (Success/Loading) back to Idle.
                    if (_planState.value !is PlanResult.Idle) _planState.value = PlanResult.Idle
                } else {
                    _planState.value = PlanResult.Loading
                    planRepository.plan(params).fold(
                        onSuccess = { _planState.value = PlanResult.Success(it) },
                        onFailure = { _planState.value = PlanResult.Error(it.message.orEmpty()) }
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun suggestionsFor(query: String): List<TripEndpoint.Geocoded> =
        if (query.isBlank()) emptyList() else geocode.suggest(query).getOrDefault(emptyList())

    fun onFromQueryChange(query: String) {
        _formState.update { it.copy(from = TripEndpoint.FreeText(query)) }
        fromQueries.value = query
        // Editing over a resolved endpoint makes the form non-submittable — drop any stale route too.
        replanOrClearResult()
    }

    fun onToQueryChange(query: String) {
        _formState.update { it.copy(to = TripEndpoint.FreeText(query)) }
        toQueries.value = query
        replanOrClearResult()
    }

    /** Sets the origin (from a suggestion, current location, contacts, or map) and re-plans if ready. */
    fun setFrom(endpoint: TripEndpoint) {
        _formState.update { it.copy(from = endpoint, fromSuggestions = emptyList()) }
        replanOrClearResult()
    }

    fun setTo(endpoint: TripEndpoint) {
        _formState.update { it.copy(to = endpoint, toSuggestions = emptyList()) }
        replanOrClearResult()
    }

    /** Clears the origin back to an empty editable field (the pill's ✕), dropping any stale result. */
    fun clearFrom() {
        _formState.update { it.copy(from = TripEndpoint.FreeText(), fromSuggestions = emptyList()) }
        fromQueries.value = ""
        replanOrClearResult()
    }

    fun clearTo() {
        _formState.update { it.copy(to = TripEndpoint.FreeText(), toSuggestions = emptyList()) }
        toQueries.value = ""
        replanOrClearResult()
    }

    fun setDateTime(millis: Long) {
        _formState.update {
            it.copy(dateTimeMillis = millis, dateLabel = formatDate(millis), timeLabel = formatTime(millis))
        }
        replanOrClearResult()
    }

    fun setArriving(arriving: Boolean) {
        _formState.update { it.copy(arriving = arriving) }
        replanOrClearResult()
    }

    fun applyAdvancedSettings(settings: AdvancedSettings) {
        _formState.update {
            it.copy(
                modeId = settings.modeId,
                maxWalkMeters = settings.maxWalkMeters,
                optimizeTransfers = settings.optimizeTransfers,
                wheelchair = settings.wheelchair
            )
        }
        replanOrClearResult()
    }

    fun reverseTrip() {
        _formState.update {
            it.copy(
                from = it.to, to = it.from,
                fromSuggestions = emptyList(), toSuggestions = emptyList()
            )
        }
        replanOrClearResult()
    }

    /** Clears a surfaced error after the host shows it. */
    fun clearPlanResult() {
        _planState.value = PlanResult.Idle
    }

    /**
     * Seeds the form and results from a re-entry (e.g. a RealtimeService trip-update notification)
     * without re-planning, so the user lands back on the trip they were watching.
     */
    fun restoreFrom(
        from: TripEndpoint?,
        to: TripEndpoint?,
        dateTimeMillis: Long,
        arriving: Boolean,
        itineraries: List<Itinerary>
    ) {
        _formState.update {
            it.copy(
                from = from ?: it.from,
                to = to ?: it.to,
                dateTimeMillis = dateTimeMillis,
                dateLabel = formatDate(dateTimeMillis),
                timeLabel = formatTime(dateTimeMillis),
                arriving = arriving
            )
        }
        if (itineraries.isNotEmpty()) {
            _planState.value = PlanResult.Success(itineraries)
        }
    }

    /**
     * Called after any form change: hand the latest plan inputs to the [planInputs] pipeline, which
     * re-plans when both endpoints resolve and otherwise drops a stale result back to [PlanResult.Idle]
     * so a changed or cleared endpoint can't leave an old route on screen (the results sheet keys off
     * [PlanResult.Success]). Emitting supersedes any in-flight plan via the collector's [mapLatest].
     */
    private fun replanOrClearResult() {
        val form = _formState.value
        planInputs.trySend(if (form.canSubmit) form.toParams() else null)
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(millis))

    private fun formatTime(millis: Long): String =
        SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(millis))

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 350L

        // Mirror OTPConstants.TRIP_PLAN_DATE/TIME_STRING_FORMAT (inlined to keep this JVM-pure).
        const val DATE_PATTERN = "MMMM dd"
        const val TIME_PATTERN = "hh:mm a"
    }
}
