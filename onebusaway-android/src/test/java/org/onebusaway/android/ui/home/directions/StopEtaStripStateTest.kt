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
package org.onebusaway.android.ui.home.directions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * JVM tests for [stopEtaStripState]: which of the directions ETA strip's three states a poll's
 * departures and the rider's reach rule make, and — the part worth pinning — which wins when a poll is
 * both empty and past the rule.
 */
class StopEtaStripStateTest {

    private fun minutes(vararg values: Long) = values.map { ServerTime(it * 60_000L) }

    private fun stateOf(departures: List<ServerTime>, ruleAt: ServerTime?) = stopEtaStripState(departures, ruleAt) { it }

    @Test
    fun `a departure the rider can still get to leaves the pills standing`() {
        assertEquals(StopEtaStripState.PILLS, stateOf(minutes(4, 20), ServerTime(10 * 60_000L)))
    }

    @Test
    fun `a departure exactly at the rule is one the rider can board`() {
        // The strip's own boundary: a plan whose walk is timed to the vehicle must not rule out the very
        // departure it boards, so an at-the-moment pill keeps the strip up.
        assertEquals(StopEtaStripState.PILLS, stateOf(minutes(10), ServerTime(10 * 60_000L)))
    }

    @Test
    fun `every departure before the rule collapses the strip`() {
        assertEquals(StopEtaStripState.NOTHING_BOARDABLE, stateOf(minutes(4, 6), ServerTime(10 * 60_000L)))
    }

    @Test
    fun `a poll with no departures says so rather than offering to show them`() {
        // Both empty and (vacuously) past the rule. The empty state wins: a "Show" here would promise the
        // rider a strip and hand them back the same sentence.
        assertEquals(StopEtaStripState.NO_ARRIVALS, stateOf(emptyList(), ServerTime(10 * 60_000L)))
    }

    @Test
    fun `a rider already at the stop is never ruled out of a departure`() {
        // No rule at all (the plan puts nothing before this ride), so nothing is out of reach — including
        // departures already in the past, which the strip shows as the arrivals drawer does.
        assertEquals(StopEtaStripState.PILLS, stateOf(minutes(-2, 4), null))
    }

    @Test
    fun `an empty poll without a rule is still the empty state`() {
        assertEquals(StopEtaStripState.NO_ARRIVALS, stateOf(emptyList(), null))
    }
}
