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
import org.onebusaway.android.io.elements.ObaRoute
import org.junit.Test

/** Unit tests for [primaryRouteType] — the stop-icon route-type priority resolution. */
class StopRouteTypeTest {

    @Test
    fun noRoutes_fallsBackToBus() {
        assertEquals(ObaRoute.TYPE_BUS, primaryRouteType(null, emptyMap()))
        assertEquals(ObaRoute.TYPE_BUS, primaryRouteType(arrayOf(), emptyMap()))
    }

    @Test
    fun railBeatsBus() {
        val types = mapOf("r" to ObaRoute.TYPE_RAIL, "b" to ObaRoute.TYPE_BUS)
        assertEquals(ObaRoute.TYPE_RAIL, primaryRouteType(arrayOf("b", "r"), types))
    }

    @Test
    fun busWhenOnlyBus() {
        val types = mapOf("b1" to ObaRoute.TYPE_BUS, "b2" to ObaRoute.TYPE_BUS)
        assertEquals(ObaRoute.TYPE_BUS, primaryRouteType(arrayOf("b1", "b2"), types))
    }

    @Test
    fun unknownRouteIdsIgnored() {
        assertEquals(ObaRoute.TYPE_BUS, primaryRouteType(arrayOf("missing"), emptyMap()))
    }
}
