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
package org.onebusaway.android.ui.tripplan.pinned

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.fixedLabelRes
import org.onebusaway.android.ui.tripresults.ModeSymbol
import org.onebusaway.android.ui.tripresults.TripResultsRepository
import org.onebusaway.android.util.TimeProvider

/** A name for a pinned trip's destination, resolved by whoever can reach resources. */
sealed interface PinnedLabel {
    data class Text(val value: String) : PinnedLabel
    data class Resource(@StringRes val id: Int) : PinnedLabel
}

/** Everything the resume card draws: where the trip goes, what it looks like, and how long it takes. */
data class PinnedTripCardState(
    val destination: PinnedLabel,
    val symbols: List<ModeSymbol>,
    val durationMinutes: Long
)

/**
 * What to call a pinned trip's destination.
 *
 * In order: what the rider named it; then the name the plan stamped on the trip's last leg (the
 * reverse-geocoded place `withTerminalPlaceNames` resolves for a "my location" or map-point end, which
 * says far more than the fixed label would); and only then the fixed label itself, through the same
 * [fixedLabelRes] rule the form's own endpoint pills use.
 */
internal fun pinnedDestinationLabel(
    params: TripPlanParams,
    itinerary: TripItinerary
): PinnedLabel {
    params.to.displayText?.takeIf { it.isNotBlank() }?.let { return PinnedLabel.Text(it) }
    itinerary.legs.lastOrNull()?.to?.name?.takeIf { it.isNotBlank() }?.let { return PinnedLabel.Text(it) }
    return PinnedLabel.Resource(params.to.fixedLabelRes())
}

/**
 * Owns the one parked trip plan (#2053): the pin/unpin actions, the state every pin control reads, and
 * the summary the resume card draws.
 *
 * Activity-scoped (constructed in `HomeActivity` via `by viewModels()`, not `hiltViewModel()` inside a
 * composable) because two very different places read it — the pin control inside the results sheet and
 * the resume card floating over the map. A NavBackStackEntry-scoped instance would make those two
 * separate objects with two collectors over the same row, disagreeing for a frame at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PinnedTripViewModel @Inject constructor(
    private val store: PinnedTripStore,
    private val results: TripResultsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    /** The pinned trip itself: what a pin control compares against, and what a resume replays. */
    val pinned: StateFlow<PinnedTrip?> =
        store.pinned.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The pinned trip as the card draws it.
     *
     * Summarized here, once per change of pin, rather than in the card: summarizing decodes every leg's
     * geometry and is emphatically not a per-recomposition cost.
     */
    val card: StateFlow<PinnedTripCardState?> = pinned
        .mapLatest { pin -> pin?.let { cardStateFor(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _pendingResumeIndex = MutableStateFlow<Int?>(null)

    /**
     * The option a resume asked the results sheet to land on, held until it has been consumed.
     *
     * A value rather than an event: the sheet seeds itself from a `LaunchedEffect` keyed on the
     * itineraries, and an event emitted before that effect runs would simply be dropped — leaving the
     * rider on option 0 of the trip they pinned option 2 of.
     */
    val pendingResumeIndex: StateFlow<Int?> = _pendingResumeIndex.asStateFlow()

    /** The rider parked [itineraries], having chosen [selectedIndex]. Replaces any previous pin. */
    fun pin(
        params: TripPlanParams,
        departNow: Boolean,
        itineraries: List<TripItinerary>,
        selectedIndex: Int
    ) {
        viewModelScope.launch {
            store.pin(params, departNow, itineraries, selectedIndex, WallTime(timeProvider.now()))
        }
    }

    /**
     * A fresh plan answered the question the pin already asked, so the pin adopts it — keeping the
     * snapshot a live bookmark of one trip instead of a photograph of the first answer to it. Callers
     * establish that it *is* the same request with [describesSameTripAs]; this only rewrites the
     * payload, reusing the pin's own query and anchor.
     *
     * The pinned **option** is re-found by [ItineraryDescription] — the same ordered-trip-id identity the
     * change monitor re-matches a re-plan with — not by its old position. A re-plan can return the
     * options in a different order or drop one entirely, and carrying the index across would quietly
     * re-pin whichever trip happened to land in that slot.
     *
     * The match has to be **unique** to be acted on. That identity is the trips ridden, so every option
     * of an all-on-street plan (walk-only, bike-only) describes the same empty list and would all match
     * each other; adopting the first would be a coin flip dressed up as a lookup. When the option is no
     * longer offered, or several answer to it, there is nothing honest to adopt — the pin keeps what it
     * has, and the rider still has the trip they parked.
     */
    fun resnapshot(itineraries: List<TripItinerary>) {
        val current = pinned.value ?: return
        if (current.itineraries == itineraries) return
        val pinnedDescription = ItineraryDescription(current.selectedItinerary)
        val matches = itineraries.withIndex().filter { (_, itinerary) ->
            ItineraryDescription(itinerary).itineraryMatches(pinnedDescription)
        }
        val match = matches.singleOrNull() ?: return
        pin(current.params, current.departNow, itineraries, match.index)
    }

    fun unpin() {
        viewModelScope.launch { store.unpin() }
    }

    /** The rider tapped the resume card: replay [pin], landing on the option they chose. */
    fun beginResume(pin: PinnedTrip) {
        _pendingResumeIndex.value = pin.selectedIndex
    }

    /** The results sheet has taken the resume index, so a later fresh plan starts at option 0 again. */
    fun onResumeConsumed() {
        _pendingResumeIndex.value = null
    }

    private suspend fun cardStateFor(pin: PinnedTrip): PinnedTripCardState? {
        val option = results.summarize(pin.itineraries).getOrNull()?.getOrNull(pin.selectedIndex) ?: return null
        return PinnedTripCardState(
            destination = pinnedDestinationLabel(pin.params, pin.selectedItinerary),
            symbols = option.symbols,
            durationMinutes = option.durationMinutes
        )
    }

    private companion object {
        /**
         * How long the pin flows stay warm with no collector. Long enough to ride out the recomposition
         * gap as the rider moves between directions and the map — the two places that read them — so
         * the card doesn't have to re-summarize a trip nothing changed.
         */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
