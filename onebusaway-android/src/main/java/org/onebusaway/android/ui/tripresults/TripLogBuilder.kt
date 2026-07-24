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

import kotlin.math.roundToLong
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.directions.model.routeDisplayShortName
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.geoPointOrNull

/**
 * Builds the trip **log** — the flat [TripLogEntry] timeline the results drawer renders — from an
 * itinerary's [legs][TripLeg], the legacy [DirectionsGenerator][org.onebusaway.android.directions.util
 * .DirectionsGenerator]'s flat [Direction] list (which supplies the *localized* step / intermediate-stop
 * text), and the pre-resolved (OBA-id) [RouteLegRef] per transit leg.
 *
 * The output is the trip in order: a Start [TripLogEntry.Terminal] synthesized from the first leg's
 * origin, one [TripLogEntry.Walk] or [TripLogEntry.Transit] per leg, then an Arrive terminal from the
 * last leg's destination. Structured facts (per-event [ServerTime][org.onebusaway.android.time.ServerTime]s,
 * distances, the real-time delay, the raw route colour, geo points) come straight off the [TripLeg]; only
 * the human-readable step / stop *strings* are reused from the generator (which needed a `Context` for
 * resources and has already run).
 *
 * The generator emits a predictable count per leg — 1 [Direction] for a non-transit leg, 2 for a transit
 * leg (a board direction then an alight one) — so this walks the flat list with a cursor, mirroring that
 * split with the same walk-vs-transit predicate ([TripMode.isOnStreetNonTransit][org.onebusaway.android
 * .directions.model.TripMode]) the generator used, so the cursor never drifts.
 *
 * Pure (no `Context`, no `android.graphics` — the route colour rides as its raw hex for the renderer to
 * parse) and so JVM-unit-testable.
 */
object TripLogBuilder {

    /** [routeLegRefs] is aligned to [legs]: the resolved route/stop identity per transit leg, else null. */
    fun build(
        legs: List<TripLeg>,
        flatDirections: List<Direction>,
        routeLegRefs: List<RouteLegRef?>
    ): List<TripLogEntry> {
        if (legs.isEmpty()) return emptyList()
        val entries = ArrayList<TripLogEntry>(legs.size + 2)

        val first = legs.first()
        entries += TripLogEntry.Terminal(
            kind = TerminalKind.START,
            time = first.startTime,
            place = first.from.name.orEmpty(),
            point = geoPointOrNull(first.from.lat, first.from.lon)
        )

        // Continuation legs of a stay-aboard interline (#2000) — every transit leg after a chain leader.
        // Derived from the same Interlines.chains() the repository's route-leg resolution uses, so the two
        // agree on exactly which legs fold, and it defensively drops a leading/malformed continuation flag.
        val foldedLegIndices = Interlines.chains(legs)
            .flatMap { (it.leaderIndex + 1)..it.alightIndex }
            .toSet()
        // The seam each continuation leg is boarded across. Already keyed by leg index by the resolver
        // (which had the index in hand), so there is nothing to re-derive here; chains are disjoint, so
        // merging every leg's map is unambiguous.
        val transitionByLeg = routeLegRefs.filterNotNull().flatMap { it.interlineTransitions.entries }
            .associate { it.key to it.value }

        var cursor = 0
        legs.forEachIndexed { legIndex, leg ->
            val legPoints = leg.legGeometry?.decodedPoints().orEmpty()
            if (leg.mode?.isOnStreetNonTransit == true) {
                val walk = flatDirections.getOrNull(cursor++) ?: return@forEachIndexed
                entries += walkEntry(leg, walk, legPoints, isTransfer = legs.isTransferAt(legIndex))
            } else {
                val board = flatDirections.getOrNull(cursor++) ?: return@forEachIndexed
                // Consume the generator's "get off" direction to keep the cursor aligned; the Board/Exit
                // stops ride on the pre-resolved routeLeg and are rendered as the leg's endpoint nodes.
                flatDirections.getOrNull(cursor++)
                if (legIndex in foldedLegIndices) {
                    // A stay-aboard interline continuation: the rider never leaves the vehicle. Extend the
                    // chain leader's ride to this leg's destination/time and geometry rather than starting a
                    // new entry; the leader's routeLeg already carries the chain alight (built span-aware
                    // in TripResultsRepository).
                    foldIntoLeader(entries, leg, board, legPoints, transitionByLeg[legIndex])
                } else {
                    entries += transitEntry(leg, board, legPoints, routeLegRefs.getOrNull(legIndex))
                }
            }
        }

        val last = legs.last()
        entries += TripLogEntry.Terminal(
            kind = TerminalKind.ARRIVE,
            time = last.endTime,
            place = last.to.name.orEmpty(),
            point = geoPointOrNull(last.to.lat, last.to.lon)
        )
        return entries
    }

    private fun walkEntry(
        leg: TripLeg,
        walk: Direction,
        legPoints: List<GeoPoint>,
        isTransfer: Boolean
    ) = TripLogEntry.Walk(
        mode = leg.mode.streetMode(),
        durationMinutes = leg.duration.inWholeMinutes,
        distanceMeters = leg.distance,
        isTransfer = isTransfer,
        // The generator emits one sub-direction per leg.step (in order), so the localized instruction and
        // the structured step distance line up by index.
        steps = walk.subDirections.orEmpty().mapIndexed { i, sub ->
            LogStep(sub.directionText.str(), leg.steps.getOrNull(i)?.distance ?: 0.0, sub.focusPoint())
        },
        legPoints = legPoints,
        focusPoint = walk.focusPoint()
    )

    /**
     * Extend the most recent transit ride (the chain leader) over a folded interline continuation leg.
     *
     * The duration is re-derived from the ride's own board→exit clock times rather than summing the legs'
     * durations, so the ledger's elapsed delta always agrees with the two times printed beside it (the
     * rider is aboard for that whole span, seam dwell included). The continuation's own events append in
     * travel order — its [transition] seam (when it changes route) first, then the stops it passes after
     * that seam — so the merged ride reads `stops… → "stay aboard for route X" → stops…` and the leg's
     * "N stops" summary counts the whole chain.
     */
    private fun foldIntoLeader(
        entries: MutableList<TripLogEntry>,
        leg: TripLeg,
        board: Direction,
        legPoints: List<GeoPoint>,
        transition: InterlineTransition?
    ) {
        val idx = entries.indexOfLast { it is TripLogEntry.Transit }
        if (idx < 0) return
        val leader = entries[idx] as TripLogEntry.Transit
        entries[idx] = leader.copy(
            exitTime = leg.endTime,
            durationMinutes = (leg.endTime - leader.boardTime).inWholeMinutes,
            rideEvents = leader.rideEvents +
                listOfNotNull(transition?.let { RideEvent.Transition(it) }) +
                stopEvents(board),
            legPoints = leader.legPoints + legPoints
        )
    }

    private fun transitEntry(
        leg: TripLeg,
        board: Direction,
        legPoints: List<GeoPoint>,
        ref: RouteLegRef?
    ): TripLogEntry.Transit {
        val routeLeg = ref ?: fallbackRouteLeg(leg)
        return TripLogEntry.Transit(
            routeShortName = leg.routeDisplayShortName().orEmpty(),
            routeDisplayName = leg.routeDisplayName(),
            routeColorHex = leg.routeColor,
            headsign = leg.headsign ?: routeLeg.headsign,
            boardTime = leg.startTime,
            exitTime = leg.endTime,
            durationMinutes = leg.duration.inWholeMinutes,
            realtime = leg.realtimeState(),
            // Only this leg's own stops; a folded chain's later legs append theirs after their seam.
            rideEvents = stopEvents(board),
            routeLeg = routeLeg,
            legPoints = legPoints
        )
    }

    /**
     * A transit leg's intermediate stops: the board direction's sub-directions, in travel order.
     *
     * A stop the generator could label neither by name nor by code is dropped rather than carried as an
     * empty string — the timeline would otherwise draw a node and a blank line for it. Dropping it here
     * rather than at the renderer also keeps [TripLogEntry.Transit.stopCount] honest: the "N stops"
     * summary counts exactly the stops the leg can actually list.
     */
    private fun stopEvents(board: Direction): List<RideEvent> = board.subDirections.orEmpty()
        .map { LogStop(it.directionText.str(), it.focusPoint()) }
        .filter { it.name.isNotBlank() }
        .map { RideEvent.Stop(it) }

    /** A minimal route identity when the repository couldn't resolve one (unknown agency / OTP1 path). */
    private fun fallbackRouteLeg(leg: TripLeg) = RouteLegRef(
        routeId = null,
        headsign = leg.headsign,
        board = RouteStopRef(null, leg.from.stopCode, leg.from.name, geoPointOrNull(leg.from.lat, leg.from.lon)),
        alight = RouteStopRef(null, leg.to.stopCode, leg.to.name, geoPointOrNull(leg.to.lat, leg.to.lon))
    )

    /**
     * How this on-street leg is travelled. Mirrors the generator's own action pick in
     * [DirectionsGenerator.generateNonTransitDirections][org.onebusaway.android.directions.util
     * .DirectionsGenerator] — bicycle and car each get their own verb, everything else walks — so the
     * timeline's header can't disagree with the step text the same leg produced.
     */
    private fun TripMode?.streetMode(): StreetMode = when (this) {
        TripMode.BICYCLE -> StreetMode.BIKE
        TripMode.CAR -> StreetMode.CAR
        else -> StreetMode.WALK
    }

    /** True for a walk leg flanked by transit on both sides — a transfer, vs. a first/last-mile walk. */
    private fun List<TripLeg>.isTransferAt(i: Int): Boolean = getOrNull(i - 1)?.mode?.isTransit == true && getOrNull(i + 1)?.mode?.isTransit == true

    /** The route's fuller display name for the board title (long name, else the shared short-name fallback). */
    private fun TripLeg.routeDisplayName(): String = routeLongName?.takeIf { it.isNotBlank() } ?: routeDisplayShortName().orEmpty()

    /** Real-time board state from the leg's [TripLeg.realTime] flag + [TripLeg.departureDelay]. */
    private fun TripLeg.realtimeState(): RealtimeState {
        if (!realTime) return RealtimeState.Unknown
        val minutes = (departureDelay.inWholeSeconds / 60.0).roundToLong()
        return when {
            minutes == 0L -> RealtimeState.OnTime
            minutes > 0L -> RealtimeState.Late(minutes)
            else -> RealtimeState.Early(-minutes)
        }
    }

    /** The point this direction refers to, or null when the underlying place had no coordinates. */
    private fun Direction.focusPoint(): GeoPoint? = geoPointOrNull(focusLat, focusLon)

    private fun CharSequence?.str(): String = this?.toString().orEmpty()
}
