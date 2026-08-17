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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The scripted tour's navigation rule (#2164): which action establishes the state a given step
 * describes. This is what lets the rider step *backwards* through a tour that drives the app forwards.
 */
class ScriptedTutorialTest {

    private fun step(id: String, action: TutorialAction? = null) = TutorialStep(id = id, title = 0, body = 0, action = action)

    @Test
    fun `a step with its own action is governed by it`() {
        val steps = listOf(step("a", TutorialAction.FOCUS_DEMO_STOP), step("b", TutorialAction.SHOW_DEMO_ROUTE))
        assertEquals(1, governingActionIndex(steps, 1))
    }

    @Test
    fun `a step without one inherits the most recent action before it`() {
        val steps = listOf(
            step("a", TutorialAction.FOCUS_DEMO_STOP),
            step("b"),
            step("c")
        )
        // "b" and "c" both narrate the stop that "a" focused.
        assertEquals(0, governingActionIndex(steps, 1))
        assertEquals(0, governingActionIndex(steps, 2))
    }

    @Test
    fun `nothing governs a step before the first action`() {
        val steps = listOf(step("a"), step("b", TutorialAction.FOCUS_DEMO_STOP))
        assertNull(governingActionIndex(steps, 0))
    }

    @Test
    fun `an index past the end is clamped rather than thrown`() {
        val steps = listOf(step("a", TutorialAction.FOCUS_DEMO_STOP), step("b"))
        assertEquals(0, governingActionIndex(steps, 99))
    }

    @Test
    fun `an empty sequence governs nothing`() {
        assertNull(governingActionIndex(emptyList(), 0))
    }

    /**
     * Stepping back across an action returns to the previous one, so the app is put back where that
     * step's caption describes — rather than left on whatever the step ahead had opened.
     */
    @Test
    fun `stepping back across an action returns to the previous one`() {
        val steps = listOf(
            step("map", TutorialAction.RESET_MAP),
            step("stop", TutorialAction.FOCUS_DEMO_STOP),
            step("arrivals"),
            step("route", TutorialAction.SHOW_DEMO_ROUTE)
        )
        // Back from the route step to the arrivals step re-establishes the focused stop…
        assertEquals(1, governingActionIndex(steps, 2))
        // …and back again to the stop step asks for the same thing, so nothing has to be re-run.
        assertEquals(governingActionIndex(steps, 2), governingActionIndex(steps, 1))
    }

    /**
     * Every step of the real tour has somewhere to return to. The first step carries an action for
     * exactly this reason: without one, stepping back to "this is the map" would leave the arrivals
     * drawer from the step ahead of it still open.
     */
    @Test
    fun `every step of the shipped tour is governed by some action`() {
        ScriptedTutorial.steps.indices.forEach { index ->
            assertNotNull(
                "step $index (${ScriptedTutorial.steps[index].id}) has no state to return to",
                governingActionIndex(ScriptedTutorial.steps, index)
            )
        }
    }

    @Test
    fun `the shipped tour's steps all have distinct ids`() {
        val ids = ScriptedTutorial.steps.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `the shipped tour opens on the map and ends on a finishable step`() {
        assertEquals(ScriptedTutorial.KEY_MAP, ScriptedTutorial.steps.first().anchorId)
        // The fixed length is the point of a *scripted* tour (#2164) — it walks the issue's script, so
        // a step appearing or disappearing is a change to what the tour teaches, not an incidental one.
        assertEquals(17, ScriptedTutorial.steps.size)
    }
}
