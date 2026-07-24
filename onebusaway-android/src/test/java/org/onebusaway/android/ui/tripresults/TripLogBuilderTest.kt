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
        // One structured step, aligned to the generator's single sub-direction below.
        steps = listOf(TripStep(distance = 61.0, streetName = "Pike St")),
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
            }
        )
    }

    private val boardDir = Direction().apply {
        isTransit = true
        service = "Get on BUS Route 8"
        subDirections = arrayListOf(
            Direction().apply {
                directionText = "1. Capitol Hill Station"
                focusLat = stopMid.latitude
                focusLon = stopMid.longitude
            }
        )
    }

    private val alightDir = Direction().apply { isTransit = true }

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
        assertEquals(1, walk.steps.size)
        assertEquals("Turn left onto Pike St", walk.steps.single().text) // no distance baked into the text
        assertEquals(61.0, walk.steps.single().distanceMeters, 0.0) // distance comes from the structured step
        assertEquals(stopMid, walk.steps.single().point)
        assertEquals(3, walk.legPoints.size) // decoded from the leg geometry, for body-tap framing
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
        assertEquals("1. Capitol Hill Station", transit.intermediateStops.single().name)
        assertEquals(RealtimeState.Late(2), transit.realtime)
        assertEquals(transitRef, transit.routeLeg)
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
            interlineTransitions = listOf(
                InterlineTransition("49", "Downtown", RouteStopRef("1_600", "600", "Seam Stop", alightTo))
            )
        )

        val entries = TripLogBuilder.build(
            legs = listOf(transitLeg, continuation),
            flatDirections = listOf(boardDir, alightDir, boardDir, alightDir),
            routeLegRefs = listOf(chainRef, null)
        )

        val transit = entries.filterIsInstance<TripLogEntry.Transit>().single()
        assertEquals("one ride for the whole chain", ServerTime(28 * 60_000L), transit.exitTime)
        assertEquals(chainRef, transit.routeLeg)
        assertEquals(1, transit.routeLeg.interlineTransitions.size)
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
