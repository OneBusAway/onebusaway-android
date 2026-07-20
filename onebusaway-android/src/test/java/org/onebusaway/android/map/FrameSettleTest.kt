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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.map.render.MapPadding

/**
 * The frame-settle race ([MapHost.frameRoute]/[frameItinerary], #1954): the map's obstruction insets —
 * the route header / directions form (top) and the arrivals / directions results sheet (bottom) — are
 * Compose-measured and land *after* the frame first fits, so the fit under-reserves them. [insetGrowthReframes]
 * emits a re-fit tick each time an inset grows past its value at frame time, and stops the instant the user
 * takes the camera. A gesture is emitted at the end of each case to complete the (otherwise open) flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrameSettleTest {

    // Baseline the frame fit against; a tick fires only when an inset grows past this.
    private val baseline = MapPadding(topPx = 100, bottomPx = 200)

    /**
     * Collect [insetGrowthReframes] over an inset script and return how many re-fit ticks it produced.
     * The flow only completes on a gesture, so every [script] must end by raising [interacting] (or start
     * with it raised) to unblock the join. UnconfinedTestDispatcher subscribes eagerly, so each mutation
     * inside [script] is observed before the next.
     */
    private suspend fun TestScope.settleTicks(
        startInteracting: Boolean = false,
        script: (padding: MutableStateFlow<MapPadding>, interacting: MutableStateFlow<Boolean>) -> Unit
    ): Int {
        val padding = MutableStateFlow(baseline)
        val interacting = MutableStateFlow(startInteracting)
        val ticks = mutableListOf<Unit>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            insetGrowthReframes(padding, interacting, baseline).toList(ticks)
        }
        script(padding, interacting)
        job.join()
        return ticks.size
    }

    @Test
    fun `re-fits when the top inset lands (route header or directions form)`() = runTest {
        val ticks = settleTicks { padding, interacting ->
            padding.value = baseline.copy(topPx = 260) // header/form measured after the fit
            interacting.value = true // completes the flow
        }
        assertEquals(1, ticks)
    }

    @Test
    fun `re-fits again as a second inset lands (directions form then results sheet)`() = runTest {
        val ticks = settleTicks { padding, interacting ->
            padding.value = baseline.copy(topPx = 260) // top inset lands
            padding.value = MapPadding(topPx = 260, bottomPx = 700) // bottom inset lands in a later tick
            interacting.value = true
        }
        assertEquals(2, ticks)
    }

    @Test
    fun `does not re-fit when the user takes the camera first`() = runTest {
        val ticks = settleTicks { _, interacting -> interacting.value = true }
        assertEquals(0, ticks)
    }

    @Test
    fun `does not re-fit when a gesture is already in flight`() = runTest {
        val ticks = settleTicks(startInteracting = true) { _, _ -> }
        assertEquals(0, ticks)
    }

    @Test
    fun `a shrinking inset (a sheet collapsing) does not trigger a re-fit`() = runTest {
        val ticks = settleTicks { padding, interacting ->
            padding.value = baseline.copy(bottomPx = 120) // sheet collapsed: bottom shrank below baseline
            interacting.value = true
        }
        assertEquals(0, ticks)
    }
}
