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
package org.onebusaway.android.map.render

import kotlin.math.cos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.EARTH_RADIUS_METERS

@OptIn(ExperimentalCoroutinesApi::class)
class RoutePolylineRenderPipelineTest {

    private val pipeline = RoutePolylineRenderPipeline(
        listOf(ViewportClipRoutePolylinePass(), ZoomSimplifyRoutePolylinePass())
    )

    @Test
    fun `unmarked lines pass through with their list identity`() {
        val line = RoutePolyline(0xFF123456.toInt(), listOf(point(0.0, 0.0), point(0.0, 1.0)))
        val lines = listOf(line)

        assertSame(lines, pipeline.apply(lines, camera()))
        assertSame(lines, pipeline.apply(lines, null))
    }

    @Test
    fun `viewport lines wait for a camera while ordinary lines remain`() {
        val ordinary = RoutePolyline(null, listOf(point(0.0, 0.0), point(0.0, 1.0)))
        val clipped = viewportLine(point(0.0, 0.0), point(0.0, 1.0))

        assertEquals(listOf(ordinary), pipeline.apply(listOf(ordinary, clipped), null))
    }

    @Test
    fun `render flow processes a copy while canonical geometry stays complete`() = runTest {
        val raw = viewportLine(point(0.0, -4.0), point(0.0, 4.0))
        val snapshot = MutableStateFlow(MapRenderSnapshot(routePolylines = listOf(raw)))
        val viewport = MutableStateFlow<CameraSnapshot?>(null)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        assertTrue(routePolylineRenderFlow(snapshot, viewport, dispatcher).first().isEmpty())
        viewport.value = camera()
        val rendered = routePolylineRenderFlow(snapshot, viewport, dispatcher).first().single()

        assertEquals(-3.0, rendered.points.first().longitude, 1e-8)
        assertEquals(3.0, rendered.points.last().longitude, 1e-8)
        assertSame(raw, snapshot.value.routePolylines.single())
        assertEquals(listOf(-4.0, 4.0), raw.points.map(GeoPoint::longitude), 0.0)
    }

    @Test
    fun `one-screen margin retains an inside line unchanged`() {
        val line = viewportLine(point(0.0, -2.9), point(0.0, 2.9))

        val result = pipeline.apply(listOf(line), camera())

        assertEquals(listOf(line), result)
        assertSame(line, result.single())
    }

    @Test
    fun `crossing line is clipped to the padded viewport`() {
        val line = viewportLine(point(0.0, -4.0), point(0.0, 4.0))

        val result = pipeline.apply(listOf(line), camera()).single()

        assertEquals(-3.0, result.points.first().longitude, 1e-8)
        assertEquals(3.0, result.points.last().longitude, 1e-8)
        assertEquals(line.color, result.color)
        assertEquals(line.widthDp, result.widthDp)
        assertEquals(line.transforms, result.transforms)
    }

    @Test
    fun `outside excursion produces separate fragments without a connector`() {
        val line = viewportLine(
            point(0.0, 0.0),
            point(0.0, 4.0),
            point(0.0, 5.0),
            point(0.0, 4.0),
            point(0.0, 0.0),
        )

        val result = pipeline.apply(listOf(line), camera())

        assertEquals(2, result.size)
        assertEquals(listOf(0.0, 3.0), result[0].points.map(GeoPoint::longitude), 1e-8)
        assertEquals(listOf(3.0, 0.0), result[1].points.map(GeoPoint::longitude), 1e-8)
    }

    @Test
    fun `fully outside line is omitted`() {
        val line = viewportLine(point(0.0, 10.0), point(0.0, 11.0))

        assertTrue(pipeline.apply(listOf(line), camera()).isEmpty())
    }

    @Test
    fun `segments crossing the antimeridian clip in the camera's unwrapped world`() {
        val line = viewportLine(point(0.0, 175.0), point(0.0, -177.0))
        val camera = camera(
            center = point(0.0, 179.0),
            southWest = point(-1.0, 178.0),
            northEast = point(1.0, -180.0),
        )

        val result = pipeline.apply(listOf(line), camera).single()

        assertEquals(176.0, result.points.first().longitude, 1e-8)
        assertEquals(-178.0, result.points.last().longitude, 1e-8)
    }

    @Test
    fun `zoom simplification keeps a visible bend and removes a sub-pixel bend`() {
        val latitude = 47.6
        val points = listOf(
            localPoint(latitude, eastMeters = 0.0),
            localPoint(latitude, eastMeters = 50.0, northMeters = 10.0),
            localPoint(latitude, eastMeters = 100.0),
        )
        val line = RoutePolyline(
            color = null,
            points = points,
            transforms = setOf(RoutePolylineTransform.ZOOM_SIMPLIFY),
        )

        val overview = pipeline.apply(listOf(line), camera(center = point(latitude, 0.0), zoom = 10.0))
        val close = pipeline.apply(listOf(line), camera(center = point(latitude, 0.0), zoom = 20.0))

        assertEquals(listOf(points.first(), points.last()), overview.single().points)
        assertEquals(points, close.single().points)
    }

    private fun viewportLine(vararg points: GeoPoint) = RoutePolyline(
        color = 0xFF123456.toInt(),
        points = points.toList(),
        widthDp = 10f,
        transforms = setOf(
            RoutePolylineTransform.VIEWPORT_CLIP,
            RoutePolylineTransform.ZOOM_SIMPLIFY,
        ),
    )

    private fun camera(
        center: GeoPoint = point(0.0, 0.0),
        zoom: Double = 20.0,
        southWest: GeoPoint = point(-1.0, -1.0),
        northEast: GeoPoint = point(1.0, 1.0),
    ) = CameraSnapshot(
        center = center,
        zoom = zoom,
        latSpan = northEast.latitude - southWest.latitude,
        lonSpan = 2.0,
        southWest = southWest,
        northEast = northEast,
    )

    private fun point(latitude: Double, longitude: Double) = GeoPoint(latitude, longitude)

    private fun localPoint(
        latitude: Double,
        eastMeters: Double,
        northMeters: Double = 0.0,
    ): GeoPoint {
        val latitudeRadians = Math.toRadians(latitude)
        return GeoPoint(
            latitude = latitude + Math.toDegrees(northMeters / EARTH_RADIUS_METERS),
            longitude = Math.toDegrees(eastMeters / (EARTH_RADIUS_METERS * cos(latitudeRadians))),
        )
    }

    private fun assertEquals(expected: List<Double>, actual: List<Double>, delta: Double) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (left, right) -> assertEquals(left, right, delta) }
    }
}
