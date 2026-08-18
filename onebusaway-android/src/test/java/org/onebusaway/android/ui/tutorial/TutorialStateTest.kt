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
package org.onebusaway.android.ui.tutorial

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TutorialState]'s step machine — in particular the distinction between reaching the
 * end of a sequence, which plays the finish flourish, and dismissing it with "X", which doesn't.
 */
class TutorialStateTest {

    private fun step(id: String) = TutorialStep(id = id, title = 0, body = 0)

    @Test
    fun `start with an empty list is a no-op`() {
        val state = TutorialState()
        state.start(emptyList())
        assertFalse(state.active)
        assertNull(state.current)
    }

    @Test
    fun `start begins at the first step`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        assertTrue(state.active)
        assertEquals("a", state.current?.id)
        assertFalse(state.isLast)
    }

    @Test
    fun `advance moves to the next step and flags the last`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        state.advance()
        assertEquals("b", state.current?.id)
        assertTrue(state.isLast)
    }

    @Test
    fun `advancing past a final step enters the finish flourish`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        state.advance() // -> b (last)
        state.advance() // -> finishing
        assertTrue(state.finishing)
        // The overlay keeps showing the final step until it has played the expand-to-fill flourish.
        assertTrue(state.active)
        assertEquals("b", state.current?.id)
    }

    @Test
    fun `onFinishExpanded ends the tutorial after the flourish`() {
        val state = TutorialState()
        state.start(listOf(step("a")))
        state.advance() // -> finishing
        state.onFinishExpanded()
        assertFalse(state.active)
        assertNull(state.current)
        assertFalse(state.finishing)
    }

    @Test
    fun `advance is a no-op while finishing`() {
        val state = TutorialState()
        state.start(listOf(step("a")))
        state.advance() // -> finishing
        state.advance() // ignored
        assertTrue(state.finishing)
        assertEquals("a", state.current?.id)
    }

    @Test
    fun `back returns to the previous step`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b"), step("c")))
        state.advance()
        state.advance()
        assertEquals("c", state.current?.id)

        state.back()

        assertEquals("b", state.current?.id)
        assertFalse(state.isLast)
    }

    @Test
    fun `back is a no-op on the first step`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        assertFalse(state.canGoBack)

        state.back()

        assertEquals("a", state.current?.id)
        assertTrue(state.active)
    }

    @Test
    fun `back is offered from the second step onward`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        assertFalse(state.canGoBack)

        state.advance()

        assertTrue(state.canGoBack)
    }

    @Test
    fun `back is refused once the finish flourish has started`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        state.advance() // -> b (last)
        state.advance() // -> finishing
        assertFalse(state.canGoBack)

        state.back()

        assertTrue(state.finishing)
        assertEquals("b", state.current?.id)
    }

    @Test
    fun `dismiss ends the tutorial without playing the flourish`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        state.dismiss()
        assertFalse(state.active)
        assertFalse(state.finishing)
        assertNull(state.current)
    }

    @Test
    fun `dismiss on the last step also skips the flourish`() {
        val state = TutorialState()
        state.start(listOf(step("a"))) // single step -> already the last
        assertTrue(state.isLast)
        state.dismiss()
        assertFalse(state.active)
        assertFalse(state.finishing)
    }

    @Test
    fun `a step anchors on its own id unless it names another target`() {
        assertEquals("a", step("a").anchorId)
        assertEquals(
            "shared_target",
            TutorialStep(id = "a", title = 0, body = 0, anchorId = "shared_target").anchorId
        )
    }

    @Test
    fun `reportBounds round-trips and unknown ids are null`() {
        val state = TutorialState()
        val rect = Rect(1f, 2f, 3f, 4f)
        state.reportBounds("a", rect)
        assertEquals(rect, state.boundsFor("a"))
        assertNull(state.boundsFor("missing"))
    }
}
