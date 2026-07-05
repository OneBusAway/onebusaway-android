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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [activeAlertFor], the pure mapping that picks the representative active alert for an
 * arrival's per-row indicator (issue #1687 Bug 2). The row lights up only for an alert that is also
 * active in the banner set, so a row can't show a cue for an expired/unreferenced situation.
 */
class ActiveAlertForTest {

    @Test
    fun `an arrival with no situations has no alert`() {
        assertNull(activeAlertFor(emptyList(), activeSituationIds = setOf("1")))
    }

    @Test
    fun `a referenced active situation is the row's alert`() {
        assertEquals("1", activeAlertFor(listOf("1"), activeSituationIds = setOf("1", "2")))
    }

    @Test
    fun `a referenced but inactive situation does not light the row`() {
        // The closure alert has expired out of the active set — the row must show no cue for it.
        assertNull(activeAlertFor(listOf("1"), activeSituationIds = emptySet()))
    }

    @Test
    fun `the first active referenced situation wins when several apply`() {
        // Arrival references 3 alerts; only 2 are active. The first active one in the arrival's order
        // represents the row (and is what the tap opens).
        assertEquals(
            "b",
            activeAlertFor(listOf("a", "b", "c"), activeSituationIds = setOf("b", "c"))
        )
    }
}
