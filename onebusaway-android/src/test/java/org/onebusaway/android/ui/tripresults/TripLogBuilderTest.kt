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

import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripStep
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint

/**
 * JVM tests for [TripLogBuilder]: the generator's flat, leg-ordered [Direction] list plus the legs'
 * structured times/colours become the trip-log timeline — a Start terminal, one entry per leg (walk =
 * 1 direction, transit = board + alight = 2), then an Arrive terminal — with per-event times, the raw
 * route colour, the intermediate-stop list, and the transfer flag all filled from the [TripLeg].
 */
class TripLogBuilderTest {

    // Google's canonical example polyline (decodes to 3 points); any valid encoding works here.
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    private val origin = GeoPoint(47.6100, -122.3300)
    private val boardFrom = GeoPoint(47.6150, -122.3350)
    private val alightTo = GeoPoint(47.6200, -122.3400)
    private val stopMid = GeoPoint(47.6175, -122.3375)

    private val walkLeg = TripLeg(
        mode = TripMode.WALK,
        distance = 320.0,
        duration = 4.minutes,
        startTime = ServerTime(0L),
        endTime = ServerTime(4 * 60_000L),
        from = TripPlace(name = "Origin", lat = origin.latitude, lon = origin.longitude),
        to = TripPlace(name = "Pine St & 3rd Ave", lat = boardFrom.latitude, lon = boardFrom.longitude),
        // Structured steps, aligned by index to the generator's sub-directions below.
        steps = listOf(
            TripStep(distance = 61.0, streetName = "Pike St"),
            TripStep(distance = 145.0, streetName = "3rd Ave")
        ),
        legGeometry = TripLegGeometry(points = encoded, length = 3)
    )

    private val transitLeg = TripLeg(
        mode = TripMode.BUS,
        routeId = "1_100",
        routeShortName = "8",
        routeLongName = "Route 8",
        routeColor = "1B6EF3",
        headsign = "Rainier Beach",
        realTime = true,
        duration = 16.minutes,
        departureDelay = 2.minutes,
        startTime = ServerTime(4 * 60_000L),
        endTime = ServerTime(20 * 60_000L),
        from = TripPlace(name = "Pine St & 3rd Ave", stopId = "1_500", stopCode = "500", lat = boardFrom.latitude, lon = boardFrom.longitude),
        to = TripPlace(name = "Rainier & Alaska", stopId = "1_600", stopCode = "600", lat = alightTo.latitude, lon = alightTo.longitude),
        legGeometry = TripLegGeometry(points = encoded, length = 3)
    )

    private val walkDir = Direction().apply {
        directionText = "Walk to Pine St & 3rd Ave"
        focusLat = origin.latitude
        focusLon = origin.longitude
        subDirections = arrayListOf(
            Direction().apply {
                directionText = "Turn left onto Pike St"
                focusLat = stopMid.latitude
                focusLon = stopMid.longitude
            },
            Direction().apply {
                directionText = "Turn right onto 3rd Ave"
                focusLat = boardFrom.latitude
                focusLon = boardFrom.longitude
            }
        )
    }

    private val boardDir = Direction().apply {
        isTransit = true
        service = "Get on BUS Route 8"
        subDirections = arrayListOf(
            Direction().apply {
                directionText = "Capitol Hill Station"
                focusLat = stopMid.latitude
                focusLon = stopMid.longitude
            }
        )
    }

    // The continuation leg's own board direction — a distinct stop name, so a folded chain's event
    // ordering is observable rather than two identical rows.
    private val continuationBoardDir = Direction().apply {
        isTransit = true
        service = "Get on BUS Route 49"
        subDirections = arrayListOf(
            Direction().apply {
                directionText = "Rainier Beach Station"
                focusLat = alightTo.latitude
                focusLon = alightTo.longitude
            }
        )
    }

    private val alightDir = Direction().apply { isTransit = true }

    /** The names of the intermediate stops in [TripLogEntry.Transit.rideEvents], in travel order. */
    private fun TripLogEntry.Transit.stopNames(): List<String> = rideEvents.filterIsInstance<RideEvent.Stop>().map { it.stop.name }

    private val transitRef = RouteLegRef(
        routeId = "1_100",
        headsign = "Rainier Beach",
        board = RouteStopRef("1_500", "500", "Pine St & 3rd Ave", boardFrom),
        alight = RouteStopRef("1_600", "600", "Rainier & Alaska", alightTo)
    )

    @Test
    fun buildsStartLegsArrive_walkThenTransit() {
        val entries = TripLogBuilder.build(
            legs = listOf(walkLeg, transitLeg),
            flatDirections = listOf(walkDir, boardDir, alightDir),
            routeLegRefs = listOf(null, transitRef)
        )

        assertEquals(4, entries.size)
        val start = entries[0] as TripLogEntry.Terminal
        assertEquals(TerminalKind.START, start.kind)
        assertEquals("Origin", start.place)
        assertEquals(origin, start.point)

        assertTrue(entries[1] is TripLogEntry.Walk)
        assertTrue(entries[2] is TripLogEntry.Transit)

        val arrive = entries[3] as TripLogEntry.Terminal
        assertEquals(TerminalKind.ARRIVE, arrive.kind)
        assertEquals("Rainier & Alaska", arrive.place)
        assertEquals(ServerTime(20 * 60_000L), arrive.time)
    }

    @Test
    fun walkEntry_carriesStepsPolylineAndIsNotTransfer() {
        val walk = TripLogBuilder
            .build(listOf(walkLeg), listOf(walkDir), listOf(null))
            .filterIsInstance<TripLogEntry.Walk>()
            .single()

        assertEquals(4L, walk.durationMinutes)
        assertEquals(320.0, walk.distanceMeters, 0.0)
        assertFalse(walk.isTransfer)
        // Each localized instruction pairs with the structured step of the SAME index — the generator
        // emits one sub-direction per leg.step, in order, and nothing else may shift that alignment.
        assertEquals(2, walk.steps.size)
        assertEquals("Turn left onto Pike St", walk.steps[0].text) // no distance baked into the text
        assertEquals(61.0, walk.steps[0].distanceMeters, 0.0) // distance comes from the structured step
        assertEquals(stopMid, walk.steps[0].point)
        assertEquals("Turn right onto 3rd Ave", walk.steps[1].text)
        assertEquals(145.0, walk.steps[1].distanceMeters, 0.0)
        assertEquals(boardFrom, walk.steps[1].point)
        assertEquals(3, walk.legPoints.size) // decoded from the leg geometry, for body-tap framing
        assertEquals(StreetMode.WALK, walk.mode)
    }

    @Test
    fun onStreetLegCarriesItsTravelMode_soABikeLegIsNotLabelledAsAWalk() {
        // The renderer picks the header verb ("Walk"/"Bike"/"Drive") and node glyph from this, so
        // dropping it silently relabels a bikeshare leg as walking.
        fun modeOf(mode: TripMode) = TripLogBuilder
            .build(listOf(walkLeg.copy(mode = mode)), listOf(walkDir), listOf(null))
            .filterIsInstance<TripLogEntry.Walk>()
            .single()
            .mode

        assertEquals(StreetMode.BIKE, modeOf(TripMode.BICYCLE))
        assertEquals(StreetMode.CAR, modeOf(TripMode.CAR))
        assertEquals(StreetMode.WALK, modeOf(TripMode.WALK))
    }

    @Test
    fun transitEntry_carriesTimesColorStopsAndRealtime() {
        val transit = TripLogBuilder
            .build(listOf(transitLeg), listOf(boardDir, alightDir), listOf(transitRef))
            .filterIsInstance<TripLogEntry.Transit>()
            .single()

        assertEquals("8", transit.routeShortName)
        assertEquals("Route 8", transit.routeDisplayName)
        assertEquals("1B6EF3", transit.routeColorHex)
        assertEquals(ServerTime(4 * 60_000L), transit.boardTime)
        assertEquals(ServerTime(20 * 60_000L), transit.exitTime)
        assertEquals(16L, transit.durationMinutes)
        assertEquals(1, transit.stopCount)
        assertEquals("Capitol Hill Station", transit.stopNames().single())
        assertEquals(RealtimeState.Late(2), transit.realtime)
        assertEquals(transitRef, transit.routeLeg)
    }

    @Test
    fun anUnlabelledIntermediateStop_isDroppedRatherThanDrawnBlank() {
        // A stop the generator could name neither by name nor code yields empty text; carrying it would
        // put a node and an empty line on the spine, and inflate the "N stops" summary past what the
        // expanded leg can actually show.
        val withNameless = Direction().apply {
            isTransit = true
            subDirections = arrayListOf(
                Direction().apply { directionText = "Capitol Hill Station" },
                Direction().apply { directionText = "" }
            )
        }
        val transit = TripLogBuilder
            .build(listOf(transitLeg), listOf(withNameless, alightDir), listOf(transitRef))
            .filterIsInstance<TripLogEntry.Transit>()
            .single()

        assertEquals(listOf("Capitol Hill Station"), transit.stopNames())
        assertEquals(1, transit.stopCount)
    }

    @Test
    fun walkBetweenTwoTransitLegs_isFlaggedAsTransfer() {
        val entries = TripLogBuilder.build(
            legs = listOf(transitLeg, walkLeg, transitLeg),
            flatDirections = listOf(boardDir, alightDir, walkDir, boardDir, alightDir),
            routeLegRefs = listOf(transitRef, null, transitRef)
        )
        val walk = entries.filterIsInstance<TripLogEntry.Walk>().single()
        assertTrue("a walk flanked by transit is a transfer", walk.isTransfer)
    }

    @Test
    fun stayAboardInterline_foldsContinuationIntoOneRide() {
        // leg 2 continues on the same vehicle as a different route (a cross-route interline).
        val continuation = transitLeg.copy(
            routeId = "1_200",
            routeShortName = "49",
            interlineWithPreviousLeg = true,
            startTime = ServerTime(20 * 60_000L),
            endTime = ServerTime(28 * 60_000L),
            from = TripPlace(name = "Seam Stop", stopId = "1_600", lat = alightTo.latitude, lon = alightTo.longitude),
            to = TripPlace(name = "Final Dest", stopId = "1_700", lat = alightTo.latitude, lon = alightTo.longitude)
        )
        // The repository resolves one ref for the chain leader (span-aware: alight = the last leg's
        // destination, plus the cross-route transition); the continuation leg's ref is null.
        val chainRef = transitRef.copy(
            alight = RouteStopRef("1_700", "700", "Final Dest", alightTo),
            // Keyed by the leg the change happens at — leg 1, the continuation.
            interlineTransitions = mapOf(
                1 to InterlineTransition("49", "Downtown", RouteStopRef("1_600", "600", "Seam Stop", alightTo))
            )
        )

        val entries = TripLogBuilder.build(
            legs = listOf(transitLeg, continuation),
            flatDirections = listOf(boardDir, alightDir, continuationBoardDir, alightDir),
            routeLegRefs = listOf(chainRef, null)
        )

        val transit = entries.filterIsInstance<TripLogEntry.Transit>().single()
        assertEquals("one ride for the whole chain", ServerTime(28 * 60_000L), transit.exitTime)
        // The ledger delta must span board → exit (4 → 28 min), not just the leader leg.
        assertEquals(24L, transit.durationMinutes)
        assertEquals(chainRef, transit.routeLeg)

        // The whole chain's events, in travel order: the leader's stop, then the seam it is boarded
        // across, then the stops passed *after* that seam — never the post-seam stops before it.
        assertEquals(
            listOf("stop:Capitol Hill Station", "transition:49", "stop:Rainier Beach Station"),
            transit.rideEvents.map {
                when (it) {
                    is RideEvent.Stop -> "stop:${it.stop.name}"
                    is RideEvent.Transition -> "transition:${it.transition.routeShortName}"
                }
            }
        )
        // …and the "N stops" summary counts the whole ride, not just the leader leg's share.
        assertEquals(2, transit.stopCount)
    }

    @Test
    fun selfInterline_foldsWithNoSeamRow() {
        // Same route continuing on the same vehicle: the seam is real but invisible to the rider, so
        // Interlines contributes no transition and the merged ride is a plain run of stops.
        val continuation = transitLeg.copy(
            interlineWithPreviousLeg = true,
            startTime = ServerTime(20 * 60_000L),
            endTime = ServerTime(28 * 60_000L)
        )
        val transit = TripLogBuilder.build(
            legs = listOf(transitLeg, continuation),
            flatDirections = listOf(boardDir, alightDir, continuationBoardDir, alightDir),
            routeLegRefs = listOf(transitRef, null)
        ).filterIsInstance<TripLogEntry.Transit>().single()

        assertTrue("a self-interline shows no seam row", transit.rideEvents.none { it is RideEvent.Transition })
        assertEquals(2, transit.stopCount)
    }

    @Test
    fun noRealtime_isUnknown() {
        val scheduled = transitLeg.copy(realTime = false)
        val transit = TripLogBuilder
            .build(listOf(scheduled), listOf(boardDir, alightDir), listOf(transitRef))
            .filterIsInstance<TripLogEntry.Transit>()
            .single()
        assertEquals(RealtimeState.Unknown, transit.realtime)
    }
}
