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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.render.GeoPoint

/**
 * Instrumented coverage for the route-map stop projection geometry ([projectStopsOntoPolylines] and
 * [projectFocusedStops]) — the pure nearest-point-across-polylines kernel and its fall-backs. Runs on a
 * device because [org.onebusaway.android.util.Polyline] snaps against `android.location.Location`, which
 * this project's plain-JVM unit tests can't construct (the presentation-assembly logic that consumes the
 * result is JVM-tested in [RouteMapPresentationPlanTest]).
 */
@RunWith(AndroidJUnit4::class)
class RouteStopProjectionTest {

    // A meridian segment: latitude 0, longitude 0..10.
    private val meridian = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 10.0))

    @Test
    fun projectStopsOntoPolylines_snapsEachStopOntoTheLine() {
        val stops = listOf(stop("a", lat = 1.0, lon = 5.0), stop("b", lat = -2.0, lon = 8.0))

        val projected = projectStopsOntoPolylines(stops, listOf(meridian))

        // Each stop drops perpendicularly onto the meridian: same longitude, latitude pulled to 0.
        assertClose(GeoPoint(0.0, 5.0), projected.getValue("a"))
        assertClose(GeoPoint(0.0, 8.0), projected.getValue("b"))
    }

    @Test
    fun projectStopsOntoPolylines_picksTheNearerCandidateShape() {
        val far = listOf(GeoPoint(10.0, 0.0), GeoPoint(10.0, 10.0))
        val stops = listOf(stop("a", lat = 1.0, lon = 5.0))

        val projected = projectStopsOntoPolylines(stops, listOf(far, meridian))

        // The stop at latitude 1 is nearer the meridian (lat 0) than the far line (lat 10).
        assertClose(GeoPoint(0.0, 5.0), projected.getValue("a"))
    }

    @Test
    fun projectStopsOntoPolylines_withNoDrawableShapeReturnsEmpty() {
        val stops = listOf(stop("a", lat = 1.0, lon = 5.0))

        // A single-point "line" is not drawable (< 2 points), so nothing projects.
        assertTrue(projectStopsOntoPolylines(stops, listOf(listOf(GeoPoint(0.0, 0.0)))).isEmpty())
        assertTrue(projectStopsOntoPolylines(stops, emptyList()).isEmpty())
    }

    @Test
    fun projectFocusedStops_snapsScheduledStopOntoItsTripShape() {
        val trips = setOf(focusedTrip("t1", "r1", shapeId = "s1"))
        val geometry = FocusedTripGeometry(listOf(shape("s1", "r1", meridian)))
        val stops = FocusedTripStops(
            stopIdsByTripId = mapOf("t1" to listOf("a")),
            stopsById = mapOf("a" to stop("a", lat = 1.0, lon = 5.0)),
        )

        val projected = projectFocusedStops(trips, geometry, stops)

        assertClose(GeoPoint(0.0, 5.0), projected.getValue("a"))
    }

    @Test
    fun projectFocusedStops_picksNearestAcrossEveryTripThatServesTheStop() {
        val trips = setOf(
            focusedTrip("t1", "r1", shapeId = "s1"),
            focusedTrip("t2", "r2", shapeId = "s2"),
        )
        val geometry = FocusedTripGeometry(
            listOf(
                shape("s1", "r1", listOf(GeoPoint(10.0, 0.0), GeoPoint(10.0, 10.0))),
                shape("s2", "r2", meridian),
            )
        )
        // The stop is served by both trips; it must snap to whichever shape is nearer (the meridian).
        val stops = FocusedTripStops(
            stopIdsByTripId = mapOf("t1" to listOf("a"), "t2" to listOf("a")),
            stopsById = mapOf("a" to stop("a", lat = 1.0, lon = 5.0)),
        )

        val projected = projectFocusedStops(trips, geometry, stops)

        assertClose(GeoPoint(0.0, 5.0), projected.getValue("a"))
    }

    @Test
    fun projectFocusedStops_omitsStopsWithNoDrawableCandidate() {
        val trips = setOf(
            focusedTrip("t-missing", "r1", shapeId = "s-absent"),
            focusedTrip("t-degenerate", "r2", shapeId = "s-degenerate"),
            focusedTrip("t-noshape", "r3", shapeId = null),
        )
        val geometry = FocusedTripGeometry(
            // s-absent isn't present at all; s-degenerate has < 2 points.
            listOf(shape("s-degenerate", "r2", listOf(GeoPoint(0.0, 0.0))))
        )
        val stops = FocusedTripStops(
            stopIdsByTripId = mapOf(
                "t-missing" to listOf("a"),
                "t-degenerate" to listOf("b"),
                "t-noshape" to listOf("c"),
            ),
            stopsById = mapOf(
                "a" to stop("a", lat = 1.0, lon = 5.0),
                "b" to stop("b", lat = 1.0, lon = 5.0),
                "c" to stop("c", lat = 1.0, lon = 5.0),
            ),
        )

        val projected = projectFocusedStops(trips, geometry, stops)

        assertTrue(projected.isEmpty())
        assertNull(projected["a"])
        assertFalse(projected.containsKey("b"))
    }

    private fun assertClose(expected: GeoPoint, actual: GeoPoint) {
        assertEquals(expected.latitude, actual.latitude, 1e-6)
        assertEquals(expected.longitude, actual.longitude, 1e-6)
    }

    private fun stop(id: String, lat: Double, lon: Double) = ObaStopElement(id = id, lat = lat, lon = lon)

    private fun shape(shapeId: String, routeId: String, points: List<GeoPoint>) =
        FocusedTripShape(shapeId, routeId, routeColor = null, points = points)

    private fun focusedTrip(tripId: String, routeId: String, shapeId: String?) =
        org.onebusaway.android.models.FocusedTrip(tripId, routeId, shapeId, routeColor = null)
}
