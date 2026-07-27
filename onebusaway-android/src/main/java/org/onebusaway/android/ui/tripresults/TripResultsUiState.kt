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

import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.map.RiddenSegment
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.util.GeoPoint

/**
 * One of the (up to three) itinerary option cards shown above the directions. Carries structured data
 * (not pre-formatted strings) so the card can render its mode symbols, the ETA-pill duration, and a
 * device-localized time range:
 *  - [symbols] — the trip as a left-to-right sequence of mode symbols (see [ModeSymbol]).
 *  - [durationMinutes] — whole-minute trip length, formatted like the arrivals ETA pill.
 *  - [startTime]/[endTime] — the server-clock trip endpoints, unwrapped only at the time formatter.
 *  - [walkDistanceMeters] — total walking across the trip's legs, in meters; the card formats it to the
 *    user's units (miles/km, or feet/meters for short walks). 0 when the trip has no walking.
 */
data class ItineraryOption(
    val symbols: List<ModeSymbol>,
    val durationMinutes: Long,
    val startTime: ServerTime,
    val endTime: ServerTime,
    val walkDistanceMeters: Double = 0.0
)

/**
 * One symbol on an option card's first line. A card reads as the whole trip in travel order —
 * `[walk] [4] [walk] [40] [walk]` — rather than as transit routes alone, so a transit option and a
 * walk/bike one are told in the same symbolic language instead of one being badges and the other prose
 * (#2047). Built by [ModeSymbols].
 */
sealed interface ModeSymbol {

    /**
     * An on-street leg, drawn as its mode's glyph alone — how the rider covers the ground between (or
     * instead of) rides. Legs shorter than [ModeSymbols.NEGLIGIBLE_STREET_METERS] carry no symbol at
     * all; see there for why.
     */
    data class Street(val mode: StreetMode) : ModeSymbol

    /** A ride, drawn as one route roundel — which names every route it can be taken on, or becomes
     *  along the way (see [LegBadge]), and falls back to the mode glyph for a ride that names no
     *  route. One symbol per *boarding*, so a stay-aboard interline is one roundel, not two. */
    data class Transit(val badge: LegBadge) : ModeSymbol
}

/**
 * One ride's roundel: every route the rider is on for it, as a single joined chip with each route in its
 * own color. A ride names more than one route two ways, and [join] says which — the routes are
 * interchangeable and any will do ("1 Line/2 Line" for the Lynnwood–downtown pair, #2010), or one vehicle
 * runs as each in turn and the rider never gets off ("5 > 12", a stay-aboard interline, #2000/#2049). An
 * ordinary ride holds exactly one route and draws as the plain one-color chip it always did.
 *
 * [routes] is in the order the badge reads, which [join] decides: natural route-name order for
 * interchangeable routes, so a corridor reads the same way whichever of its lines the planner picked, and
 * **ride order** for an interline, where the order is the information ("5 > 12" is not "12 > 5").
 *
 * [routes] is **empty** for a ride whose route publishes no short name — there is no roundel to draw, so
 * the option card falls back to the ride's [mode] glyph rather than an empty chip or a silently missing
 * leg (a ferry ride still has to appear on the card).
 */
data class LegBadge(
    val routes: List<RouteBadge>,
    val mode: TransitMode,
    // Moot for the single-route badge that most rides get, hence a default: with nothing to divide,
    // there is no relation to name. It matters only once a second route joins the chip.
    val join: RouteBadgeJoin = RouteBadgeJoin.ANY_OF
) {
    /** Whether this ride has more than one route on its badge, i.e. the chip is a joined/multicolor one. */
    val isJoined: Boolean get() = routes.size > 1

    /** Whether the badge offers a *choice* of routes — as opposed to naming a ride that becomes another
     *  route under the rider ([RouteBadgeJoin.THEN]), which is one route to board, not several. */
    val isInterchangeable: Boolean get() = isJoined && join == RouteBadgeJoin.ANY_OF

    /** True when the ride names no route at all, and can only be shown as its mode. */
    val isUnnamed: Boolean get() = routes.isEmpty()
}

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
     * An on-street leg — one node on the spine, its dashed-neutral segment running to the next node.
     * Expands to its turn-by-turn [steps]. [mode] is how the rider covers it, and picks the header's
     * verb and node glyph — a bike leg must not read "Walk". [isTransfer] is true for a leg *between*
     * two transit legs (vs. the first/last-mile one), letting the renderer label it accordingly.
     * Tapping the leg frames [legPoints] (or, with no geometry, recentres on [focusPoint]).
     *
     * Named `Walk` because walking is overwhelmingly the case; [mode] carries the rest.
     */
    data class Walk(
        val mode: StreetMode,
        val durationMinutes: Long,
        val distanceMeters: Double,
        val isTransfer: Boolean,
        val steps: List<LogStep>,
        val legPoints: List<GeoPoint> = emptyList(),
        val focusPoint: GeoPoint? = null
    ) : TripLogEntry

    /**
     * A transit leg — a Board node and an Exit node uniting the ride, its solid route-coloured segment
     * running between them. Everything that happens *aboard* — the stops passed and any stay-aboard
     * route change — is [rideEvents], **in travel order**, so a folded interline chain (#2000) keeps its
     * post-seam stops after the seam they follow. Board/Exit stop identity + the route id ride on
     * [routeLeg]: tapping the leg highlights the route ([routeLeg] + [legPoints]) and the Board node
     * shows that stop's live ETA strip. [routeColorHex] is the raw GTFS colour (nullable); the renderer
     * re-tones it for the badge and the spine, falling back to a neutral transit colour.
     *
     * [routeShortName] is the badge name and is **null when the route publishes none** — the renderer
     * then draws no roundel and leads with [routeDisplayName]; see
     * [routeDisplayShortName][org.onebusaway.android.directions.model.routeDisplayShortName]. [mode] is
     * what the rider boards, and picks the Board node's glyph — a ferry leg must not read as a bus.
     */
    data class Transit(
        val routeShortName: String?,
        val routeDisplayName: String?,
        val mode: TransitMode,
        val routeColorHex: String?,
        val headsign: String?,
        val boardTime: ServerTime,
        val exitTime: ServerTime,
        val durationMinutes: Long,
        val realtime: RealtimeState,
        val rideEvents: List<RideEvent>,
        val routeLeg: RouteLegRef,
        val legPoints: List<GeoPoint> = emptyList()
    ) : TripLogEntry {
        /**
         * How many intermediate stops the ride passes — derived from [rideEvents] rather than stored, so
         * the "N stops" summary can't disagree with the list the leg expands to (it did when a folded
         * interline continuation's stops were merged but its count wasn't).
         */
        val stopCount: Int get() = rideEvents.count { it is RideEvent.Stop }
    }
}

/**
 * Something that happens between boarding and exiting a ride, in travel order: an intermediate [Stop]
 * passed, or a stay-aboard [Transition] onto another route mid-vehicle (#2000). They interleave — a
 * folded interline chain is `stops… → transition → stops… → transition → stops…` — which is why they
 * share one ordered list rather than sitting in two parallel ones.
 */
sealed interface RideEvent {
    data class Stop(val stop: LogStop) : RideEvent
    data class Transition(val transition: InterlineTransition) : RideEvent
}

/** Which trip endpoint a [TripLogEntry.Terminal] marks. */
enum class TerminalKind { START, ARRIVE }

/**
 * How a rider covers ground off a vehicle — a [TripLogEntry.Walk] leg, or a [ModeSymbol.Street] on an
 * option card. The three modes [TripMode.isOnStreetNonTransit][
 * org.onebusaway.android.directions.model.TripMode] admits, narrowed to just those so the renderer's
 * verb/glyph choice is total and a transit mode can't reach it — plus [BIKESHARE], which is a bicycle
 * leg the rider *rents*, split out because the two are different acts to the rider (find a dock vs.
 * take the bike you brought) and the app draws them with different symbols.
 *
 * [BIKESHARE] is not a wire mode: OTP calls such a leg `BICYCLE` like any other and says it's a rental
 * by giving the leg a vehicle-rental endpoint ([TripVertexType.BIKESHARE][
 * org.onebusaway.android.directions.model.TripVertexType]) — a structural fact off the wire, not a
 * guess. See `ModeSymbols.streetMode`.
 */
enum class StreetMode { WALK, BIKE, BIKESHARE, CAR }

/**
 * What a [TripLogEntry.Transit] leg is ridden on, narrowed from [TripMode] to the vehicle families the
 * app ships a glyph for — so the renderer's glyph choice is total and an on-street mode can't reach it.
 *
 * [BUS] doubles as the generic: OTP's own umbrella modes (`TRANSIT`/`BUSISH`) and anything unrecognized
 * land there, which is the status quo for every transit leg and right far more often than not. The
 * named cases exist so the modes a rider would notice being wrong — a boat drawn as a bus — are right.
 */
enum class TransitMode { BUS, RAIL, SUBWAY, TRAM, FERRY }

/**
 * The vehicle family a transit leg is ridden on, narrowed from the wire mode to the art the app
 * actually ships.
 *
 * Collapsing a cable car onto the tram glyph matches how the map already normalizes route types
 * (`VehicleBitmaps.normalizeVehicleType`). Aerial lifts and funiculars aren't named here for the same
 * reason they aren't there: the app ships no art for them, so they take the generic rather than a glyph
 * that asserts the wrong vehicle. OTP's umbrella modes and a leg with no mode land there too.
 */
fun TripMode?.transitMode(): TransitMode = when (this) {
    TripMode.FERRY -> TransitMode.FERRY
    TripMode.RAIL, TripMode.TRAINISH -> TransitMode.RAIL
    TripMode.SUBWAY -> TransitMode.SUBWAY
    TripMode.TRAM, TripMode.CABLE_CAR -> TransitMode.TRAM
    else -> TransitMode.BUS
}

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
 * data) renders no chip; [OnTime] when the delay rounds to zero whole minutes (i.e. within 30s of
 * schedule either way); [Late]/[Early] carry whole minutes.
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
 *
 * [alternatives] are the interchangeable routes for this leg (#2010) — the board stop shows each
 * one's live ETA strip under the planned route's, so the rider can see which of them comes first.
 * [badge] is the leg's finished roundel (planned route joined by those alternatives), built once by
 * the repository rather than re-derived per row while composing; null where no badge was built at all
 * (fixtures), which is not the same as a badge that found no route to name.
 */
data class RouteLegRef(
    val routeId: String?,
    val headsign: String?,
    val board: RouteStopRef?,
    val alight: RouteStopRef?,
    // Mid-ride route changes on one continuous vehicle (stay-aboard interlines onto a *different*
    // route, #2000), between [board] and [alight], **keyed by the itinerary leg index the change
    // happens at**. Keyed rather than positional because the consumer has to place each seam among
    // that leg's stops; the resolver knows the index, so nothing downstream has to recover it. Empty
    // for an ordinary transit leg and for a self-interline (a route reversing onto itself) — whose
    // seam is folded away silently.
    val interlineTransitions: Map<Int, InterlineTransition> = emptyMap(),
    // The *additional* ridden legs beyond the leader ([routeId] + [board]) when this card folds a
    // stay-aboard interline (#2000): each names the route continued onto and the seam stop boarded
    // there. The map focus draws each segment's shape + stops and the shared vehicle across them.
    // Empty for an ordinary leg. Carried straight onto [org.onebusaway.android.map.ShowRouteRequest].
    val extraSegments: List<RiddenSegment> = emptyList(),
    val alternatives: List<AlternativeRouteRef> = emptyList(),
    val badge: LegBadge? = null
)

/**
 * A stay-aboard interline onto a **different** route within one continuous vehicle ride (#2000): at
 * [stop] the vehicle changes to route [routeLabel] (heading to [headsign]) and the passenger stays
 * seated. Rendered as a distinct row between Board and Alight so the directions never tell the rider to
 * get off and reboard. Self-interlines (same route reversing onto itself) produce no transition — the
 * seam vanishes entirely.
 *
 * [routeLabel] is the prose label ([Interlines.transitionRouteLabel]), not the badge name: this row is a
 * sentence, so it names a short-name-less route by its long name rather than saying nothing.
 */
data class InterlineTransition(
    val routeLabel: String?,
    val headsign: String?,
    val stop: RouteStopRef
)

/**
 * An interchangeable route on a transit leg: its display name and color for the badge beside its ETA
 * strip, plus the same OBA-id/headsign pair [RouteLegRef] carries for the planned route, used to pick
 * that route's direction group out of the board stop's arrivals. [routeId] is null when the OTP route
 * couldn't be resolved onto an OBA id — the route still names itself on the leg's badge, but has no
 * ETA strip to show.
 */
data class AlternativeRouteRef(
    val routeId: String?,
    val headsign: String?,
    val shortName: String,
    val routeColor: Int?
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
