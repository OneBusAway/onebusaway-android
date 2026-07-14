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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.RoutePolylineTransform
import org.onebusaway.android.models.TripPatternGeometry

@OptIn(ExperimentalCoroutinesApi::class)
class AdjacencyMapControllerTest {

    private data class Request(
        val tripPatterns: Set<TripPatternGeometry>,
        val result: CompletableDeferred<AdjacencyShapes> = CompletableDeferred(),
    )

    private class FakeRepository : AdjacencyRouteShapeRepository {
        val requests = mutableListOf<Request>()

        override suspend fun getShapes(
            tripPatterns: Set<TripPatternGeometry>
        ): AdjacencyShapes {
            val request = Request(LinkedHashSet(tripPatterns))
            requests += request
            return request.result.await()
        }
    }

    @Test
    fun `resolved shapes publish colored downstream lines and ignore invalid paths`() = runTest {
        val state = MapRenderState()
        val repository = FakeRepository()
        val controller = AdjacencyMapController(state, repository, backgroundScope)
        val first = listOf(GeoPoint(47.60, -122.30), GeoPoint(47.61, -122.31))
        val second = listOf(GeoPoint(47.62, -122.32), GeoPoint(47.63, -122.33))

        controller.start(
            "stop",
            first.first(),
            patterns("red", "invalid", "fallback", "failed"),
        )
        runCurrent()
        repository.requests.single().result.complete(
            AdjacencyShapes(
                shapes = linkedMapOf(
                    "red" to shape("red", 0xFFCC0000.toInt(), first),
                    "invalid" to shape("invalid", null, listOf(first.first())),
                    "fallback" to shape("fallback", null, second),
                ),
                failedShapeIds = setOf("failed"),
            )
        )
        runCurrent()

        val lines = state.snapshot.value.routePolylines
        assertEquals(listOf(first, second), lines.map { it.points })
        assertEquals(listOf(0xFFCC0000.toInt(), null), lines.map { it.color })
        assertTrue(
            lines.all {
                it.widthDp == ADJACENCY_DOWNSTREAM_LINE_WIDTH_DP && !it.directional && !it.dashed
            }
        )
        assertTrue(
            lines.all {
                it.transforms == setOf(
                    RoutePolylineTransform.VIEWPORT_CLIP,
                    RoutePolylineTransform.ZOOM_SIMPLIFY,
                )
            }
        )
    }

    @Test
    fun `same stop and route set does not refetch while a changed set replaces the lines`() = runTest {
        val state = MapRenderState()
        val repository = FakeRepository()
        val controller = AdjacencyMapController(state, repository, backgroundScope)
        val oldLine = line(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0))

        controller.start("stop", oldLine.points.first(), patterns("a"))
        runCurrent()
        repository.requests.single().result.complete(shapes("a", oldLine.points))
        runCurrent()
        controller.start("stop", oldLine.points.first(), patterns("a"))
        runCurrent()
        assertEquals(1, repository.requests.size)

        controller.start("stop", oldLine.points.first(), patterns("a", "b"))
        runCurrent()
        assertTrue(state.snapshot.value.routePolylines.isEmpty())
        assertEquals(setOf("a", "b"), repository.requests.last().tripPatterns.map { it.shapeId }.toSet())
    }

    @Test
    fun `a replaced session cannot publish after the new focus`() = runTest {
        val state = MapRenderState()
        val repository = FakeRepository()
        val controller = AdjacencyMapController(state, repository, backgroundScope)
        val oldPoints = listOf(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0))
        val newPoints = listOf(GeoPoint(3.0, 3.0), GeoPoint(4.0, 4.0))

        controller.start("old", oldPoints.first(), patterns("old-shape"))
        runCurrent()
        val oldRequest = repository.requests.single()
        controller.start("new", newPoints.first(), patterns("new-shape"))
        runCurrent()
        val newRequest = repository.requests.last()

        oldRequest.result.complete(shapes("old-shape", oldPoints))
        newRequest.result.complete(shapes("new-shape", newPoints))
        runCurrent()

        assertEquals(listOf(newPoints), state.snapshot.value.routePolylines.map { it.points })
    }

    @Test
    fun `empty set clears an active session but inactive stop preserves foreign route lines`() = runTest {
        val state = MapRenderState()
        val repository = FakeRepository()
        val controller = AdjacencyMapController(state, repository, backgroundScope)
        val foreignLine = line(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0))
        state.setRoutePolylines(listOf(foreignLine))

        controller.stop()
        assertEquals(listOf(foreignLine), state.snapshot.value.routePolylines)

        controller.start("stop", foreignLine.points.first(), patterns("a"))
        runCurrent()
        controller.start("stop", foreignLine.points.first(), emptySet())

        assertTrue(state.snapshot.value.routePolylines.isEmpty())
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun `shape is split at projected stop with thin upstream and wide downstream`() {
        val start = GeoPoint(0.0, 0.0)
        val middle = GeoPoint(0.0, 10.0)
        val end = GeoPoint(0.0, 20.0)
        val projectedStop = GeoPoint(0.0, 4.0)
        val result = shapes("shape", listOf(start, middle, end))

        val lines = result.toRoutePolylines(GeoPoint(1.0, 4.0))

        assertEquals(
            listOf(
                listOf(start, projectedStop),
                listOf(projectedStop, middle, end),
            ),
            lines.map { it.points },
        )
        assertEquals(
            listOf(ADJACENCY_UPSTREAM_LINE_WIDTH_DP, ADJACENCY_DOWNSTREAM_LINE_WIDTH_DP),
            lines.map { it.widthDp },
        )
        assertTrue(ADJACENCY_DOWNSTREAM_LINE_WIDTH_DP > ADJACENCY_UPSTREAM_LINE_WIDTH_DP)
    }

    private fun shapes(shapeId: String, points: List<GeoPoint>) = AdjacencyShapes(
        shapes = mapOf(shapeId to shape(shapeId, null, points)),
        failedShapeIds = emptySet(),
    )

    private fun shape(shapeId: String, color: Int?, points: List<GeoPoint>) =
        AdjacencyRouteShape(shapeId, "route-$shapeId", color, points)

    private fun patterns(vararg shapeIds: String): Set<TripPatternGeometry> =
        shapeIds.mapTo(LinkedHashSet()) { TripPatternGeometry(it, "route-$it", null) }

    private fun line(vararg points: GeoPoint) = RoutePolyline(null, points.toList())

}
