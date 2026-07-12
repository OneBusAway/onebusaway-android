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
package org.onebusaway.android.ui.compose.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Truth tables for the SlideBox's pure pull-past-the-end arithmetic (see SlideBox.kt). */
class SlideBoxPullMathTest {

    private val resistance = 0.5f
    private val maxPx = 100f

    // --- buildPull ---------------------------------------------------------------------------------

    @Test
    fun `build grows the pull at the resistance ratio and consumes the whole finger delta`() {
        val delta = buildPull(pullPx = 10f, availableX = -20f, resistance = resistance, maxPx = maxPx)
        // 20px of finger travel × 0.5 resistance = 10px more pull; all 20px consumed so the
        // overscroll never leaks to a parent scrollable.
        assertEquals(PullDelta(pullPx = 20f, consumedX = -20f), delta)
    }

    @Test
    fun `build clamps the pull at maxPx but still consumes the finger delta`() {
        val delta = buildPull(pullPx = 95f, availableX = -40f, resistance = resistance, maxPx = maxPx)
        assertEquals(PullDelta(pullPx = 100f, consumedX = -40f), delta)
    }

    @Test
    fun `build ignores rightward travel`() {
        val delta = buildPull(pullPx = 10f, availableX = 5f, resistance = resistance, maxPx = maxPx)
        assertEquals(PullDelta(pullPx = 10f, consumedX = 0f), delta)
    }

    // --- unwindPull --------------------------------------------------------------------------------

    @Test
    fun `unwind pays off the pull at inverse resistance and consumes only what it needs`() {
        val delta = unwindPull(pullPx = 10f, availableX = 30f, resistance = resistance)
        // Fully unwinding a 10px pull needs 10 / 0.5 = 20px of finger travel; the remaining 10px of
        // the finger's 30px is left for the box to scroll as usual.
        assertEquals(PullDelta(pullPx = 0f, consumedX = 20f), delta)
    }

    @Test
    fun `a short drag unwinds the pull partially and is consumed whole`() {
        val delta = unwindPull(pullPx = 10f, availableX = 10f, resistance = resistance)
        assertEquals(PullDelta(pullPx = 5f, consumedX = 10f), delta)
    }

    @Test
    fun `unwind is a no-op without an active pull`() {
        val delta = unwindPull(pullPx = 0f, availableX = 10f, resistance = resistance)
        assertEquals(PullDelta(pullPx = 0f, consumedX = 0f), delta)
    }

    @Test
    fun `unwind is a no-op for leftward travel - that is the build direction`() {
        val delta = unwindPull(pullPx = 10f, availableX = -10f, resistance = resistance)
        assertEquals(PullDelta(pullPx = 10f, consumedX = 0f), delta)
    }

    // --- crossesArmThreshold -----------------------------------------------------------------------

    @Test
    fun `arms exactly on the rising edge`() {
        assertTrue(crossesArmThreshold(before = 47f, after = 48f, triggerPx = 48f))
    }

    @Test
    fun `already-armed deltas do not re-arm`() {
        assertFalse(crossesArmThreshold(before = 48f, after = 60f, triggerPx = 48f))
    }

    @Test
    fun `deltas short of the threshold do not arm`() {
        assertFalse(crossesArmThreshold(before = 10f, after = 20f, triggerPx = 48f))
    }
}
