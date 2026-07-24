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

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.SearchCenter
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.TimeProvider

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
    private val regionRepository: RegionRepository,
    private val searchCenter: SearchCenter,
    timeProvider: TimeProvider,
    settingsRepository: AdvancedSettingsRepository
) : ViewModel() {

    /**
     * Fallback center for the map-pick screen when the chosen endpoint has no coordinates yet:
     * the device's last known location, else the region center, else null.
     */
    fun mapPickerCenter(): Location? = searchCenter.current()

    private val initialDateTimeMillis = timeProvider.now()
    private val initialSettings = settingsRepository.load()

    /**
     * The active region's OTP "report a problem" contact email, or null when none is configured. The
     * VM owns the [RegionRepository] dependency so the host destination never reaches into the data
     * layer itself; it's a one-shot read at report time (the host builds the feedback email) rather
     * than surfaced form state, mirroring [RegionRepository.currentRegion].
     */
    val otpContactEmail: String?
        get() = regionRepository.region.value?.otpContactEmail

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

    // Reverse-geocoded names, keyed by the coordinate that produced them. Re-planning re-submits the same
    // points — a date or arrive-by change, an advanced-settings apply, or editing *the other* endpoint all
    // re-plan — and what a point is called doesn't change between them, so the lookup is worth exactly
    // once. Only successes are remembered, so a failed lookup is retried rather than cached as "unnamed".
    // Read and written only from the plan collector, which runs on the main dispatcher.
    private val reverseNames = mutableMapOf<Pair<Double, Double>, String>()

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
                    coroutineScope {
                        // Naming the endpoints is an independent network call, so it runs alongside the
                        // plan rather than before it: it costs no wall clock beyond the plan itself
                        // unless the geocoder is slower, and [REVERSE_GEOCODE_TIMEOUT] caps even that.
                        val origin = async { placeNameOf(params.from) }
                        val destination = async { placeNameOf(params.to) }
                        planRepository.plan(params).fold(
                            // Carry the request that produced the results so the host can arm the
                            // trip-plan-change monitor to re-plan it (see PlanResult.Success.params).
                            onSuccess = { itineraries ->
                                _planState.value = PlanResult.Success(
                                    itineraries.withTerminalPlaceNames(origin.await(), destination.await()),
                                    params
                                )
                            },
                            onFailure = {
                                // Nothing left to name — don't hold the error behind a lookup whose answer
                                // is about to be discarded ("outside the transit network" is a routine
                                // failure for exactly the map-picked endpoints that need one).
                                origin.cancel()
                                destination.cancel()
                                _planState.value = PlanResult.Error(it.toTripPlanError())
                            }
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun suggestionsFor(query: String): List<TripEndpoint.Geocoded> = if (query.isBlank()) emptyList() else geocode.suggest(query).getOrDefault(emptyList())

    /**
     * What to call a plan endpoint: an endpoint the user picked by name already answers for itself, but
     * "my location" or a point on the map is only a coordinate — and OTP, sent bare coordinates, echoes
     * them back as its own "Origin" placeholder, which is what the directions used to be left labelling
     * their first node with (#2006). Reverse-geocodes that case.
     *
     * Best-effort: a failed, timed-out, or empty lookup returns null and the timeline keeps OTP's name,
     * so a plan is never blocked or failed by geocoding.
     */
    private suspend fun placeNameOf(endpoint: TripEndpoint): String? {
        endpoint.displayText?.takeIf { it.isNotBlank() }?.let { return it }
        val lat = endpoint.lat ?: return null
        val lon = endpoint.lon ?: return null
        reverseNames[lat to lon]?.let { return it }
        val name = withTimeoutOrNull(REVERSE_GEOCODE_TIMEOUT) { geocode.reverse(lat, lon).getOrNull() }
        return name?.also { reverseNames[lat to lon] = it }
    }

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
                from = it.to,
                to = it.from,
                fromSuggestions = emptyList(),
                toSuggestions = emptyList()
            )
        }
        replanOrClearResult()
    }

    /** Clears a surfaced error after the host shows it. */
    fun clearPlanResult() {
        _planState.value = PlanResult.Idle
    }

    /**
     * Seeds the form and results from a re-entry (e.g. a trip-plan monitor trip-update notification)
     * without re-planning, so the user lands back on the trip they were watching.
     */
    fun restoreFrom(
        from: TripEndpoint?,
        to: TripEndpoint?,
        dateTimeMillis: Long,
        arriving: Boolean,
        itineraries: List<TripItinerary>
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

    private fun formatDate(millis: Long): String = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(millis))

    private fun formatTime(millis: Long): String = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(millis))

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 350L

        /**
         * How long a plan will wait on the endpoint-naming lookups it runs alongside the OTP call.
         * Naming is a cosmetic upgrade of the directions' first/last node, so a slow geocoder must not
         * hold the route back — past this the plan publishes with OTP's own names.
         */
        val REVERSE_GEOCODE_TIMEOUT = 4.seconds

        // Mirror OTPConstants.TRIP_PLAN_DATE/TIME_STRING_FORMAT (inlined to keep this JVM-pure).
        const val DATE_PATTERN = "MMMM dd"
        const val TIME_PATTERN = "hh:mm a"
    }
}

/**
 * Names the trip's two ends on the itineraries themselves — the first leg's origin and the last leg's
 * destination — leaving OTP's own name wherever we have nothing better (#2006).
 *
 * OTP is sent bare coordinates for *every* endpoint (`TripRequestBuilder.getAddressString`), so what it
 * echoes back for the two terminals is its "Origin"/"Destination" placeholder, never a real place. The
 * requester is the only party that knows what the user actually picked, so it stamps that in here, once,
 * where the request and its results meet — rather than handing every downstream reader a second channel
 * to consult. Everything that reads an itinerary afterwards (the trip log, the map focus, the trip-update
 * notification, which serializes these very objects and restores them on re-entry) then sees one
 * self-describing trip.
 *
 * Top-level and `internal` so this stays JVM-unit-testable, like the OTP-side [otpPlanUrl].
 */
internal fun List<TripItinerary>.withTerminalPlaceNames(origin: String?, destination: String?): List<TripItinerary> {
    val originName = origin?.takeIf { it.isNotBlank() }
    val destinationName = destination?.takeIf { it.isNotBlank() }
    if (originName == null && destinationName == null) return this
    return map { itinerary ->
        if (itinerary.legs.isEmpty()) {
            itinerary
        } else {
            val legs = itinerary.legs.toMutableList()
            // A single-leg trip is both terminals, so these apply in order to the same leg.
            originName?.let { legs[0] = legs[0].let { leg -> leg.copy(from = leg.from.copy(name = it)) } }
            destinationName?.let {
                val last = legs.lastIndex
                legs[last] = legs[last].let { leg -> leg.copy(to = leg.to.copy(name = it)) }
            }
            itinerary.copy(legs = legs)
        }
    }
}
