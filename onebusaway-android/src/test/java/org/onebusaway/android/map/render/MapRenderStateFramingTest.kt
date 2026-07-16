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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The behavioral contract the framing/gesture split (#1648) depends on: retained framing replays to a
 * late subscriber (so a fit requested before the adapter subscribes — the directions map behind the
 * results sheet, a re-created map — isn't dropped), re-fires on an identical value (unlike a StateFlow),
 * and can be cleared; transient gestures are dropped when nothing is listening.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapRenderStateFramingTest {

    @Test
    fun `framing replays the current intent to a late subscriber`() = runTest {
        val state = MapRenderState()
        // Frame before anyone subscribes — the old replay=0 flow would drop this.
        state.frame(FramingIntent.Route)

        assertEquals(FramingIntent.Route, state.framingIntent.first())
    }

    @Test
    fun `re-emitting the same framing re-fires (a StateFlow would swallow it)`() = runTest {
        val state = MapRenderState()
        val seen = mutableListOf<FramingIntent?>()
        // UnconfinedTestDispatcher subscribes eagerly, so the collector is active before we emit.
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            state.framingIntent.toList(seen)
        }

        state.frame(FramingIntent.Route)
        state.frame(FramingIntent.Route) // identical — must still be delivered

        assertEquals(listOf(FramingIntent.Route, FramingIntent.Route), seen)
        job.cancel()
    }

    @Test
    fun `clearFraming replays null so a stale fit is not re-applied`() = runTest {
        val state = MapRenderState()
        state.frame(FramingIntent.Region)
        state.clearFraming()

        assertNull(state.framingIntent.first())
    }

    @Test
    fun `route framing geometry stays separate from contextual displayed routes`() {
        val selected = RoutePolyline(
            color = 1,
            points = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)),
        )
        val sibling = RoutePolyline(
            color = 2,
            points = listOf(GeoPoint(10.0, 10.0), GeoPoint(11.0, 11.0)),
        )
        val state = MapRenderState()

        state.setRoutePolylines(
            polylines = listOf(sibling, selected),
            framingPolylines = listOf(selected),
        )

        assertEquals(listOf(sibling, selected), state.snapshot.value.routePolylines)
        assertEquals(listOf(selected), state.routeFramingPolylines)
    }

    @Test
    fun `a gesture dispatched with no subscriber is dropped`() = runTest {
        val state = MapRenderState()
        // No collector yet: this gesture has nowhere to go and must not be replayed.
        state.dispatchGesture(CameraCommand.ZoomIn)

        val seen = mutableListOf<CameraCommand>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            state.cameraGestures.toList(seen)
        }
        state.dispatchGesture(CameraCommand.ZoomOut)

        assertEquals(listOf(CameraCommand.ZoomOut), seen)
        job.cancel()
    }
}
