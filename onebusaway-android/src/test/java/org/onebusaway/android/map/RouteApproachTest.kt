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
package org.onebusaway.android.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline

/** JVM tests for the symbolic upstream approach (#2124) — see [tripApproach]. */
class RouteApproachTest {

    private fun schedule(vararg stops: Pair<String, Double>): ObaTripSchedule = TripScheduleData(
        stops.map { (stopId, distance) ->
            StopTimeData(stopId = stopId, distanceAlongTrip = distance)
        }.toTypedArray<ObaTripSchedule.StopTime>()
    )

    // A straight due-north line from the equator-ish origin; ~111.32 km per degree of latitude, so
    // these points are ~1113 m apart. Distances below are chosen well inside it.
    private val northLine = Polyline(
        listOf(
            GeoPoint(47.60, -122.33),
            GeoPoint(47.61, -122.33),
            GeoPoint(47.62, -122.33),
            GeoPoint(47.63, -122.33)
        )
    )

    private val trip = RideTrip(
        tripId = "trip-1",
        routeId = "route-1",
        shapeId = "shape-1",
        schedule = schedule("origin" to 0.0, "board" to 1113.0, "alight" to 3339.0),
        shape = northLine,
        distanceAlongTrip = null
    )

    // -- tripApproach: one trip's clipped shape --

    @Test
    fun approachRunsFromTheShapeStartToTheBoardingStopsOwnOffset() {
        val approach = tripApproach(trip, "board")!!
        assertEquals(GeoPoint(47.60, -122.33), approach.first())
        // Clipped at the boarding offset, not carried on to the trip's end.
        assertTrue("approach should stop short of the trip end", approach.last().latitude < 47.63)
        assertTrue("approach should reach roughly the second point", approach.last().latitude > 47.605)
    }

    @Test
    fun approachIsNullWhenTheTripDoesNotServeTheBoardingStop() {
        // A trip running the other direction, or a short-turn: no approach, and no guess. This is
        // what lets the caller skip a direction prefilter entirely.
        assertNull(tripApproach(trip, "somewhere-else"))
    }

    @Test
    fun approachIsNullWhenTheTripServesTheBoardingStopTwice() {
        // A loop has no single boarding to clip at — the same refusal soleOffsetOf makes, rather
        // than picking the first visit.
        val loop = trip.copy(
            schedule = schedule("board" to 500.0, "far" to 1500.0, "board" to 2500.0)
        )
        assertNull(tripApproach(loop, "board"))
    }

    @Test
    fun approachIsNullBeforeTheScheduleOrShapeBackfillLands() {
        assertNull(tripApproach(trip.copy(schedule = null), "board"))
        assertNull(tripApproach(trip.copy(shape = null), "board"))
    }

    @Test
    fun tripStartingAtTheBoardingStopHasNoApproach() {
        // Nothing upstream to draw — the rider boards where the trip begins. Must be dropped rather
        // than emitted as a degenerate stub.
        val fromBoard = trip.copy(schedule = schedule("board" to 0.0, "alight" to 3339.0))
        assertNull(tripApproach(fromBoard, "board"))
    }

    // -- approachPolylines: the active trips' distinct approaches --

    @Test
    fun tripsSharingAShapeContributeOneApproach() {
        // A dozen buses on one shape must not stack a dozen identical lines.
        val approaches = approachPolylines(listOf(trip, trip.copy(), trip.copy()), "board")
        assertEquals(1, approaches.size)
    }

    @Test
    fun distinctShapeVariantsEachKeepTheirOwnApproach() {
        val branch = trip.copy(
            shapeId = "shape-2",
            shape = Polyline(
                listOf(
                    GeoPoint(47.60, -122.35),
                    GeoPoint(47.61, -122.35),
                    GeoPoint(47.62, -122.35)
                )
            )
        )
        val approaches = approachPolylines(listOf(trip, branch), "board")
        assertEquals(2, approaches.size)
        assertEquals(-122.33, approaches[0].first().longitude, 1e-9)
        assertEquals(-122.35, approaches[1].first().longitude, 1e-9)
    }

    @Test
    fun undecidableTripsAreDroppedWithoutSinkingTheDecidableOnes() {
        val reverse = trip.copy(shapeId = "shape-rev", schedule = schedule("other" to 100.0))
        val approaches = approachPolylines(listOf(reverse, trip), "board")
        assertEquals(1, approaches.size)
    }

    @Test
    fun noApproachWithoutABoardingStopId() {
        // An OTP→OBA resolution failure flows through as "no symbolic answer", leaving the caller on
        // its geometric fallback rather than drawing something arbitrary.
        assertEquals(emptyList<List<GeoPoint>>(), approachPolylines(listOf(trip), null))
    }

    @Test
    fun noActiveTripsMeansNoSymbolicApproach() {
        assertEquals(emptyList<List<GeoPoint>>(), approachPolylines(emptyList(), "board"))
    }
}
