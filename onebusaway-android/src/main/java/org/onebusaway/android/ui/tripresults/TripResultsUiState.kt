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
package org.onebusaway.android.ui.tripresults

import org.onebusaway.android.map.RiddenSegment
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint

/**
 * One of the (up to three) itinerary option cards shown above the directions. Carries structured data
 * (not pre-formatted strings) so the card can render route badges / a walk glyph, the ETA-pill duration,
 * and a device-localized time range:
 *  - [mode] — what the card's first line shows for the trip (route badges, a walk glyph, or a label).
 *  - [durationMinutes] — whole-minute trip length, formatted like the arrivals ETA pill.
 *  - [startTime]/[endTime] — the server-clock trip endpoints, unwrapped only at the time formatter.
 *  - [walkDistanceMeters] — total walking across the trip's legs, in meters; the card formats it to the
 *    user's units (miles/km, or feet/meters for short walks). 0 when the trip has no walking.
 */
data class ItineraryOption(
    val mode: ModeSummary,
    val durationMinutes: Long,
    val startTime: ServerTime,
    val endTime: ServerTime,
    val walkDistanceMeters: Double = 0.0
)

/** What an option card's first line shows for the trip's modes (mutually exclusive by construction). */
sealed interface ModeSummary {
    /** A transit trip: its legs' route roundels, in order. */
    data class Routes(val badges: List<RouteBadge>) : ModeSummary

    /** A walk-only trip — shown as a walk glyph. */
    data object Walk : ModeSummary

    /** Any other non-transit trip (bike/car), as the legacy mode-label title. */
    data class Label(val text: String) : ModeSummary
}

/** A transit leg's route roundel data: its short name and (nullable) GTFS color as an ARGB int. */
data class RouteBadge(val shortName: String, val routeColor: Int?)

/**
 * One entry in the trip **log** — the directions rendered as a single transit timeline the user reads
 * top-to-bottom, each event on a shared vertical spine next to its clock time (see [TripResultsList]).
 * The list is the trip in order: a [Terminal] Start, then one [Walk] or [Transit] per leg, then a
 * [Terminal] Arrive. The spine's per-node connector colours (route colour for a ride, a dashed neutral
 * for a walk) are derived by the renderer from the entry *sequence*, so each entry carries only its own
 * identity — never its neighbours' or the spine geometry.
 *
 * Times are [ServerTime] (the OTP server clock), unwrapped only at the time formatter; distances are
 * metres and durations whole minutes, formatted to the user's units by the renderer. The route colour
 * rides as its raw wire hex ([Transit.routeColorHex]) rather than a parsed ARGB int, so this model — and
 * the builder that makes it — stay Android-free and JVM-unit-testable (colour parsing needs
 * `android.graphics`); the renderer parses it once for the badge and the spine.
 */
sealed interface TripLogEntry {

    /**
     * A trip endpoint — the origin ([TerminalKind.START]) or destination ([TerminalKind.ARRIVE]). A
     * single node with its [time] and [place] name; [point] recentres the map when tapped (null when the
     * endpoint carried no coordinates). Not expandable.
     */
    data class Terminal(
        val kind: TerminalKind,
        val time: ServerTime,
        val place: String,
        val point: GeoPoint? = null
    ) : TripLogEntry

    /**
     * A walk (or bike/car) leg — one node on the spine, its dashed-neutral segment running to the next
     * node. Expands to its turn-by-turn [steps]. [isTransfer] is true for a walk *between* two transit
     * legs (vs. the first/last-mile walk), letting the renderer label it accordingly. Tapping the leg
     * frames [legPoints] (or, with no geometry, recentres on [focusPoint]).
     */
    data class Walk(
        val durationMinutes: Long,
        val distanceMeters: Double,
        val isTransfer: Boolean,
        val steps: List<LogStep>,
        val legPoints: List<GeoPoint> = emptyList(),
        val focusPoint: GeoPoint? = null
    ) : TripLogEntry

    /**
     * A transit leg — a Board node and an Exit node uniting the ride, its solid route-coloured segment
     * running between them. Expands to the [intermediateStops] passed on the way (empty on the OTP2 path,
     * which doesn't fetch them). Board/Exit stop identity + the route id ride on [routeLeg]: tapping the
     * leg highlights the route ([routeLeg] + [legPoints]) and the Board node shows that stop's live ETA
     * strip. [routeColorHex] is the raw GTFS colour (nullable); the renderer parses it for the badge and
     * the spine, falling back to a neutral transit colour.
     */
    data class Transit(
        val routeShortName: String,
        val routeDisplayName: String,
        val routeColorHex: String?,
        val headsign: String?,
        val boardTime: ServerTime,
        val exitTime: ServerTime,
        val stopCount: Int,
        val durationMinutes: Long,
        val realtime: RealtimeState,
        val intermediateStops: List<LogStop>,
        val routeLeg: RouteLegRef,
        val legPoints: List<GeoPoint> = emptyList()
    ) : TripLogEntry
}

/** Which trip endpoint a [TripLogEntry.Terminal] marks. */
enum class TerminalKind { START, ARRIVE }

/**
 * One turn-by-turn step of a walk leg: its localized instruction [text] (the maneuver only — the
 * distance is *not* baked in), the step's [distanceMeters] (rendered as a per-step delta in the time
 * column, in the user's units), and the map [point] it refers to (null when the step had no coordinates).
 */
data class LogStep(val text: String, val distanceMeters: Double = 0.0, val point: GeoPoint? = null)

/**
 * One intermediate transit stop passed on a leg — its display [name] and map [point]. Carries no time:
 * the trip-plan model has no per-intermediate-stop timestamp (only the board/exit times are known).
 */
data class LogStop(val name: String, val point: GeoPoint? = null)

/**
 * The real-time state of a transit board, shown as an on-time / delayed chip. [Unknown] (no real-time
 * data) renders no chip; [OnTime] within a minute of schedule; [Late]/[Early] carry whole minutes.
 */
sealed interface RealtimeState {
    data object Unknown : RealtimeState
    data object OnTime : RealtimeState
    data class Late(val minutes: Long) : RealtimeState
    data class Early(val minutes: Long) : RealtimeState
}

/**
 * The route/stop identity of a transit leg, carried on its leg card so tapping the leg highlights the
 * route on the map and its Board/Alight sub-items can show each stop's live ETA strip. Ids are already
 * **OBA-format** — resolved from OTP's GTFS ids at build time (see [org.onebusaway.android.directions
 * .OtpObaIdResolver]); [routeId]/[RouteStopRef.stopId] are null when they couldn't be resolved (an
 * unknown agency, or the OTP1 path). [headsign] disambiguates which direction group's ETAs to show.
 */
data class RouteLegRef(
    val routeId: String?,
    val headsign: String?,
    val board: RouteStopRef?,
    val alight: RouteStopRef?,
    // Mid-ride route changes on one continuous vehicle (stay-aboard interlines onto a *different*
    // route, #2000), in order between [board] and [alight]. Empty for an ordinary transit leg and for
    // a self-interline (a route reversing onto itself) — whose seam is folded away silently.
    val interlineTransitions: List<InterlineTransition> = emptyList(),
    // The *additional* ridden legs beyond the leader ([routeId] + [board]) when this card folds a
    // stay-aboard interline (#2000): each names the route continued onto and the seam stop boarded
    // there. The map focus draws each segment's shape + stops and the shared vehicle across them.
    // Empty for an ordinary leg. Carried straight onto [org.onebusaway.android.map.ShowRouteRequest].
    val extraSegments: List<RiddenSegment> = emptyList()
)

/**
 * A stay-aboard interline onto a **different** route within one continuous vehicle ride (#2000): at
 * [stop] the vehicle changes to route [routeShortName] (heading to [headsign]) and the passenger stays
 * seated. Rendered as a distinct row between Board and Alight so the directions never tell the rider to
 * get off and reboard. Self-interlines (same route reversing onto itself) produce no transition — the
 * seam vanishes entirely.
 */
data class InterlineTransition(
    val routeShortName: String?,
    val headsign: String?,
    val stop: RouteStopRef
)

/** A transit stop reached on a leg — its OBA id (for arrivals), display name, code, and location. */
data class RouteStopRef(
    val stopId: String?,
    val stopCode: String?,
    val name: String?,
    val point: GeoPoint?
)

/** UI state for the trip-planning results screen. */
sealed interface TripResultsUiState {

    data object Loading : TripResultsUiState

    /**
     * @param options the itinerary option cards (1–3)
     * @param selectedIndex the currently-selected option
     * @param directions the directions for the selected option
     */
    data class Success(
        val options: List<ItineraryOption>,
        val selectedIndex: Int,
        val directions: List<TripLogEntry>
    ) : TripResultsUiState

    data class Error(val message: String) : TripResultsUiState
}
