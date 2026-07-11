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

/**
 * [isRouteContinuation] — the interlining continuation test (#1691): a plain routeId compare, not
 * "does the block continue" (true at nearly every trip boundary) and not a headsign/directionId
 * change (also true at an ordinary same-route direction reversal). Route ids are King County Metro's
 * real ones for routes 45/75, captured live against the production API while investigating #1691;
 * `route45Id`'s trip `1_664701340`'s `nextTripId` (still route 45, direction 0→1) is the
 * false-positive case this test exists to rule out, and its `previousTripId` (route 75) is the
 * genuine positive.
 */
class RouteContinuationTest {

    private val route45Id = "1_100225"
    private val route75Id = "1_100269"

    @Test
    fun same_route_direction_reversal_is_not_interlining() {
        assertFalse(isRouteContinuation(route45Id, route45Id))
    }

    @Test
    fun a_genuine_cross_route_block_continuation_is_interlining() {
        assertTrue(isRouteContinuation(route45Id, route75Id))
    }

    @Test
    fun an_unresolved_neighbor_route_id_is_never_a_continuation() {
        assertFalse(isRouteContinuation(route45Id, ""))
    }
}
