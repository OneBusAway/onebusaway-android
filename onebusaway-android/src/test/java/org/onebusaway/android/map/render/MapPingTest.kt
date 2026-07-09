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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the ping ripple's timing + easing (the bitmap/circle draw is exercised on device). */
class MapPingTest {

    @Test
    fun `progress runs 0 to 1 and clamps past the duration`() {
        assertEquals(0f, MapPing.progress(0L), 1e-6f)
        assertEquals(0.5f, MapPing.progress(MapPing.DURATION_MS / 2), 1e-3f)
        assertEquals(1f, MapPing.progress(MapPing.DURATION_MS), 1e-6f)
        assertEquals(1f, MapPing.progress(MapPing.DURATION_MS * 5), 1e-6f)
    }

    @Test
    fun `isDone flips at the duration`() {
        assertFalse(MapPing.isDone(0L))
        assertFalse(MapPing.isDone(MapPing.DURATION_MS - 1))
        assertTrue(MapPing.isDone(MapPing.DURATION_MS))
        assertTrue(MapPing.isDone(MapPing.DURATION_MS + 100))
    }

    @Test
    fun `radius eases out - starts at 0, ends at 1, past the midpoint by half-time`() {
        assertEquals(0f, MapPing.radiusFraction(0f), 1e-6f)
        assertEquals(1f, MapPing.radiusFraction(1f), 1e-6f)
        // Decelerating: at half progress the ring is already more than halfway out.
        assertTrue("eases out (fraction > progress mid-way)", MapPing.radiusFraction(0.5f) > 0.5f)
        // Monotonic increasing.
        assertTrue(MapPing.radiusFraction(0.25f) < MapPing.radiusFraction(0.75f))
    }

    @Test
    fun `alpha fades from full to zero`() {
        assertEquals(1f, MapPing.alpha(0f), 1e-6f)
        assertEquals(0.5f, MapPing.alpha(0.5f), 1e-6f)
        assertEquals(0f, MapPing.alpha(1f), 1e-6f)
    }

    @Test
    fun `withAlpha stamps the alpha byte and keeps the rgb`() {
        val rgb = 0x123456
        assertEquals(0xFF123456.toInt(), MapPing.withAlpha(0xFF000000.toInt() or rgb, 1f))
        assertEquals(0x00123456, MapPing.withAlpha(0xFF000000.toInt() or rgb, 0f))
        // Half alpha -> 0x7F, rgb preserved regardless of the input's alpha byte.
        assertEquals((0x7F shl 24) or rgb, MapPing.withAlpha(rgb, 0.5f))
    }
}
