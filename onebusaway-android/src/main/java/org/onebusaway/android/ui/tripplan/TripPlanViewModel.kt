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
import java.time.Instant
import java.time.ZoneId
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.LocationRepository
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
    private val locationRepository: LocationRepository,
    private val timeProvider: TimeProvider,
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
            modes = initialSettings.modes,
            maxWalkMeters = initialSettings.maxWalkMeters,
            optimizeTransfers = initialSettings.optimizeTransfers,
            wheelchair = initialSettings.wheelchair,
            walkPreference = initialSettings.walkPreference,
            cyclingPreference = initialSettings.cyclingPreference,
            bikePreference = initialSettings.bikePreference
        ).withWhen(initialDateTimeMillis, departNow = true)
    )
    val formState: StateFlow<TripPlanFormState> = _formState.asStateFlow()

    private val _planState = MutableStateFlow<PlanResult>(PlanResult.Idle)
    val planState: StateFlow<PlanResult> = _planState.asStateFlow()

    // The in-progress autocomplete query per endpoint, each feeding its own debounced suggestion
    // pipeline below. Keyed by slot rather than held as a from/to pair so the pipeline is written once.
    private val queries = TripEndpointSlot.entries.associateWith { MutableStateFlow("") }

    // Reverse-geocoded names, keyed by the coordinate that produced them. Re-planning mostly re-submits
    // the same points — a date or arrive-by change, an advanced-settings apply, or editing *the other*
    // endpoint all re-plan — and what a point is called doesn't change between them, so the lookup is
    // worth exactly once. (A "my location" end that has moved since is a genuinely different point, and
    // is looked up again; see replanOrClearResult.) Only successes are remembered, so a failed lookup is
    // retried rather than cached as "unnamed".
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
        queries.forEach { (slot, typed) ->
            typed
                .debounce(SUGGEST_DEBOUNCE_MS)
                .mapLatest(::suggestionsFor)
                .onEach { suggestions -> _formState.update { it.withSuggestions(slot, suggestions) } }
                .launchIn(viewModelScope)
        }
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

    /** Types into [slot]'s field, which starts a debounced autocomplete lookup for it. */
    fun onQueryChange(slot: TripEndpointSlot, query: String) {
        _formState.update { it.withTypedQuery(slot, query) }
        queries.getValue(slot).value = query
        // Editing over a resolved endpoint makes the form non-submittable — drop any stale route too.
        replanOrClearResult()
    }

    /** Sets one endpoint (from a suggestion, current location, contacts, or map) and re-plans if ready. */
    fun setEndpoint(slot: TripEndpointSlot, endpoint: TripEndpoint) {
        _formState.update { it.withEndpoint(slot, endpoint) }
        replanOrClearResult()
    }

    /**
     * Sets [slot] to the device's current location, returning false when there's no fix to set it to.
     * Saying *why* there's none is left to the caller, whose business it is: the field's own button
     * explains itself with a toast, while the long-press path below pairs silently.
     */
    fun setEndpointToCurrentLocation(slot: TripEndpointSlot): Boolean {
        val here = currentLocation() ?: return false
        setEndpoint(slot, here)
        return true
    }

    /**
     * Sets the endpoint a map long-press named ("directions from/to here"), pairing the trip's other
     * end with the device's current location — [TripPlanFormState.withEndpointPaired] owns the rule
     * (#2092). Both ends land in one form update, so the pair submits as a single plan.
     *
     * Distinct from [setEndpoint], which the crosshair pick-on-map path uses: that one is an edit
     * within a form the rider is already filling, so it pairs nothing.
     */
    fun setEndpointFromLongPress(slot: TripEndpointSlot, endpoint: TripEndpoint) {
        val here = currentLocation()
        _formState.update { it.withEndpointPaired(slot, endpoint, here) }
        replanOrClearResult()
    }

    /** The device's last known fix as a plan endpoint, or null when there is none (or no permission). */
    private fun currentLocation(): TripEndpoint.CurrentLocation? = locationRepository.lastKnownLocation()
        ?.let { TripEndpoint.CurrentLocation(lat = it.latitude, lon = it.longitude) }

    /** Clears an endpoint back to an empty editable field (the pill's ✕), dropping any stale result. */
    fun clearEndpoint(slot: TripEndpointSlot) {
        queries.getValue(slot).value = ""
        setEndpoint(slot, TripEndpoint.FreeText())
    }

    /**
     * The instant the date/time picker should open on: the pinned one when the trip is already pinned,
     * and the live clock while it is anchored to "now". Under that anchor
     * [TripPlanFormState.dateTimeMillis] still holds the instant this ViewModel was built, so a form
     * left open would otherwise offer the rider a clock reading twenty minutes ago as their starting
     * point — the same moving-anchor distinction [TripPlanFormState.departNow] exists for.
     */
    fun pickerStartMillis(): Long = _formState.value.let {
        if (it.departNow) timeProvider.now() else it.dateTimeMillis
    }

    /**
     * Pins the trip to an explicit instant, leaving the "now" anchor. The picker settles the date and
     * the time together, so this is one write and one re-plan for both halves.
     */
    fun setDateTime(millis: Long) {
        _formState.update { it.withWhen(millis, departNow = false) }
        replanOrClearResult()
    }

    /**
     * Returns the trip to the "now" anchor. The pinned [TripPlanFormState.dateTimeMillis] is refreshed
     * to the current clock as well, so re-opening the picker starts from now rather than from the
     * instant that was abandoned.
     */
    fun setDepartNow() {
        val now = timeProvider.now()
        _formState.update { it.withWhen(now, departNow = true) }
        replanOrClearResult()
    }

    /**
     * Leaving vs arriving. Choosing *arriving* also leaves the "now" anchor, pinning the trip to the
     * clock as it does so: "arrive by now" is not a request that can be answered — it asks for a trip
     * that has already finished — whereas "depart now" is the ordinary case. Pinning puts a concrete
     * instant in the form's time callout, which is the thing the rider then moves.
     */
    fun setArriving(arriving: Boolean) {
        _formState.update { state ->
            if (!arriving || !state.departNow) {
                state.copy(arriving = arriving)
            } else {
                // Pin to the clock now, not to dateTimeMillis — under the "now" anchor that field
                // still holds the instant the ViewModel was built, which may be long past.
                state.copy(arriving = true).withWhen(timeProvider.now(), departNow = false)
            }
        }
        replanOrClearResult()
    }

    fun applyAdvancedSettings(settings: AdvancedSettings) {
        _formState.update {
            it.copy(
                modes = settings.modes,
                maxWalkMeters = settings.maxWalkMeters,
                optimizeTransfers = settings.optimizeTransfers,
                wheelchair = settings.wheelchair,
                walkPreference = settings.walkPreference,
                cyclingPreference = settings.cyclingPreference,
                bikePreference = settings.bikePreference
            )
        }
        replanOrClearResult()
    }

    /**
     * Changes either half of the trip mode from the form's action-bar pickers.
     *
     * Re-picking the mode that is already checked is a no-op, not a replan: the menu reports every
     * tap, including one on the current selection, and a replan there would re-issue an identical
     * request (and, on a form that can't submit yet, needlessly clear a result the rider is reading).
     */
    fun setModeSelection(modes: TripModeSelection) {
        if (_formState.value.modes == modes) return
        _formState.update { it.copy(modes = modes) }
        replanOrClearResult()
    }

    fun reverseTrip() {
        // Both reads are of the pre-swap `it`, so this is a swap and not a double-assignment.
        _formState.update {
            it.withEndpoint(TripEndpointSlot.FROM, it.to).withEndpoint(TripEndpointSlot.TO, it.from)
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
            // A restored trip carries the instant it was planned for, so it is pinned rather than
            // anchored to "now" — re-anchoring would silently re-time the very trip being restored.
            it.copy(
                from = from ?: it.from,
                to = to ?: it.to,
                arriving = arriving
            ).withWhen(dateTimeMillis, departNow = false)
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
        // Re-read the device fix here, at submit, so a "my location" end plans from where the rider is
        // now rather than from where they were when they set it (#2134) — the same moving-anchor
        // argument as the clock below. The form moves with it, so what it holds and what was sent stay
        // the same trip. Only read when an end actually is the device's position: while no fix has been
        // established lastKnownLocation() polls the providers, and a place-to-place trip has no reason
        // to pay for that on every mode or date change.
        val here = if (_formState.value.hasDeviceLocationEndpoint) currentLocation() else null
        val form = _formState.updateAndGet { it.withDeviceLocationAt(here) }
        // Read the clock here, at submit, so a "depart now" trip left sitting in an open form plans
        // from the moment it is sent rather than the moment the ViewModel was built.
        planInputs.trySend(if (form.canSubmit) form.toParams(timeProvider.now()) else null)
    }

    /**
     * This form with its "when" set to [millis], and every label describing it re-derived in the same
     * write. The three are one fact about one instant — the date, the time, and which day that is —
     * so they move together and can never be left describing the instant before.
     */
    private fun TripPlanFormState.withWhen(millis: Long, departNow: Boolean): TripPlanFormState = copy(
        dateTimeMillis = millis,
        departNow = departNow,
        dateLabel = formatDate(millis),
        timeLabel = formatTime(millis),
        dayRelation = dayRelationOf(millis)
    )

    private fun formatDate(millis: Long): String = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(millis))

    private fun formatTime(millis: Long): String = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(millis))

    /**
     * Which day [millis] falls on, in the device's own zone — the same wall clock [formatDate] renders
     * in, and the one the picker composes its instants in ([TripDateTimeDialog]).
     *
     * Read against the clock at the moment the label is written, which is what makes this a label and
     * not live state: a form left open across midnight keeps saying what it said when the rider pinned
     * the trip, exactly as the picker's own day dropdown holds its "Today" for as long as it is open.
     * The instant itself never moves, so the trip that gets planned is unaffected either way.
     */
    private fun dayRelationOf(millis: Long): TripDay {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(timeProvider.now()).atZone(zone).toLocalDate()
        return when (day) {
            today -> TripDay.TODAY
            today.plusDays(1) -> TripDay.TOMORROW
            else -> TripDay.OTHER
        }
    }

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
