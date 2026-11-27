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
package org.onebusaway.android.database.oba

import org.junit.Assert.assertEquals
import org.junit.Test

/** Converting a carried-over allow-list route filter into the routes it hides (#2366). */
class LegacyRouteFilterConversionTest {

    @Test
    fun hidesEveryServedRouteTheFilterDidntShow() {
        assertEquals(setOf("2", "3"), hiddenRoutesFromLegacyFilter(setOf("1"), setOf("1", "2", "3")))
    }

    @Test
    fun aShownRouteTheStopNoLongerServesIsIgnored() {
        assertEquals(setOf("3"), hiddenRoutesFromLegacyFilter(setOf("1", "2"), setOf("1", "3")))
    }

    @Test
    fun aFilterThatWouldHideAllServiceIsDropped() {
        // Every route the rider chose to show is gone from the stop; carrying that over would blank the board.
        assertEquals(emptySet<String>(), hiddenRoutesFromLegacyFilter(setOf("old"), setOf("1", "2")))
    }

    @Test
    fun aFilterShowingEveryServedRouteHidesNothing() {
        assertEquals(emptySet<String>(), hiddenRoutesFromLegacyFilter(setOf("1", "2"), setOf("1", "2")))
    }
}
