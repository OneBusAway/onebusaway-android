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

import org.onebusaway.android.map.render.MapPadding
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The route-load framing race (#1954): the route header (a Compose banner) reports its height into the
 * map's top padding only after it lays out, so a fast route load can fit against stale padding and land
 * the route under the header. [awaitHeaderInsetCorrection] decides whether the retained route frame
 * should be re-applied once the header inset lands — while yielding to a user who takes the camera first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteFrameSettleTest {

    // Every case frames against the same baseline; UnconfinedTestDispatcher subscribes eagerly so the
    // collector is active before we mutate the flows below.
    private fun TestScope.settle(
        padding: MutableStateFlow<MapPadding>,
        interacting: MutableStateFlow<Boolean>,
    ): Deferred<Boolean> = async(UnconfinedTestDispatcher(testScheduler)) {
        awaitHeaderInsetCorrection(padding, interacting, baselineTopPx = 100)
    }

    @Test
    fun `re-fits when the header inset lands (top padding grows) before any gesture`() = runTest {
        val padding = MutableStateFlow(MapPadding(topPx = 100))
        val interacting = MutableStateFlow(false)
        val result = settle(padding, interacting)

        // The route header measured after the frame fired, pushing the top inset down.
        padding.value = MapPadding(topPx = 260)

        assertTrue(result.await())
    }

    @Test
    fun `does not re-fit when the user takes the camera first`() = runTest {
        val padding = MutableStateFlow(MapPadding(topPx = 100))
        val interacting = MutableStateFlow(false)
        val result = settle(padding, interacting)

        interacting.value = true

        assertFalse(result.await())
    }

    @Test
    fun `does not re-fit when a gesture is already in flight`() = runTest {
        val padding = MutableStateFlow(MapPadding(topPx = 100))
        val interacting = MutableStateFlow(true)
        val result = settle(padding, interacting)

        assertFalse(result.await())
    }

    @Test
    fun `a bottom-only padding change does not trigger a re-fit`() = runTest {
        val padding = MutableStateFlow(MapPadding(topPx = 100))
        val interacting = MutableStateFlow(false)
        val result = settle(padding, interacting)

        // Arrivals sheet resizes: bottom grows, top inset unchanged — must not be read as the header.
        padding.value = MapPadding(topPx = 100, bottomPx = 400)
        // Only a genuine gesture settles it, and it yields to the user.
        interacting.value = true

        assertFalse(result.await())
    }
}
