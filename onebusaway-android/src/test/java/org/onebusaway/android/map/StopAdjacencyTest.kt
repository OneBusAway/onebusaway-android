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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure direction-specific membership coverage for adjacency stop minimization. */
class StopAdjacencyTest {

    @Test
    fun `inactive adjacency never dims a stop`() {
        assertFalse(isStopDimmed("stop", arrayOf("route"), null))
    }

    @Test
    fun `a stop on an upcoming trip direction stays full size`() {
        val filter = AdjacencyStopFilter(setOf("focus", "same-side"))

        assertFalse(isStopDimmed("same-side", arrayOf("route"), filter))
    }

    @Test
    fun `the opposite direction of the same route is dimmed`() {
        val filter = AdjacencyStopFilter(setOf("focus", "same-side"))

        assertTrue(isStopDimmed("opposite-side", arrayOf("route"), filter))
    }

    @Test
    fun `an unresolved route conservatively keeps all of its stops`() {
        val filter = AdjacencyStopFilter(
            stopIds = setOf("focus"),
            fallbackRouteIds = setOf("unresolved"),
        )

        assertFalse(isStopDimmed("unknown", arrayOf("unresolved"), filter))
        assertTrue(isStopDimmed("other", arrayOf("different"), filter))
    }
}
