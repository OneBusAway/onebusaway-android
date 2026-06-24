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
 * Unit tests for [TutorialState]'s step machine — in particular the distinction that drives the
 * welcome→arrivals hand-off: advancing past the last step signals completion ([completedStepId]) while
 * dismissing ("X") does not.
 */
class TutorialStateTest {

    private fun step(id: String, continuesAfter: Boolean = false) =
        TutorialStep(id = id, title = 0, body = 0, continuesAfter = continuesAfter)

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
    fun `advancing past a final step signals completion and enters the finish flourish`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        state.advance() // -> b (last)
        state.advance() // -> finishing (b doesn't continue into a follow-on)
        assertEquals("b", state.completedStepId)
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
    fun `a final step that continues hands off at once without a flourish`() {
        val state = TutorialState()
        state.start(listOf(step("a", continuesAfter = true)))
        state.advance() // completes + clears so the follow-on sequence can start
        assertEquals("a", state.completedStepId)
        assertFalse(state.finishing)
        assertFalse(state.active)
        assertNull(state.current)
    }

    @Test
    fun `dismiss ends the tutorial WITHOUT signalling completion`() {
        val state = TutorialState()
        state.start(listOf(step("a"), step("b")))
        state.dismiss()
        assertFalse(state.active)
        assertNull(state.completedStepId)
    }

    @Test
    fun `dismiss on the last step also does not signal completion`() {
        val state = TutorialState()
        state.start(listOf(step("a"))) // single step -> already the last
        assertTrue(state.isLast)
        state.dismiss()
        assertNull(state.completedStepId)
    }

    @Test
    fun `consumeCompletion clears the completed step id`() {
        val state = TutorialState()
        state.start(listOf(step("a")))
        state.advance() // completes
        assertEquals("a", state.completedStepId)
        state.consumeCompletion()
        assertNull(state.completedStepId)
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
