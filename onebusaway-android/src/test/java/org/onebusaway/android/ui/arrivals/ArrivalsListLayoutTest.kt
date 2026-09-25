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
import org.junit.Test

/** The index of the first route row, which the promoted-row scroll targets — one per optional head item. */
class ArrivalsListLayoutTest {
    @Test
    fun `every combination of head items pushes the first route row down by one each`() {
        // The contract: route rows start at 0, and each optional head item adds exactly one.
        for (combination in 0 until 16) {
            val flags = List(4) { bit -> (combination and (1 shl bit)) != 0 }
            assertEquals(
                "flags $flags",
                flags.count { it },
                firstRouteIndex(
                    hasModeSwitch = flags[0],
                    alertsBeforeRoutes = flags[1],
                    onDemandBeforeRoutes = flags[2],
                    directionBeforeRoutes = flags[3]
                )
            )
        }
    }

    @Test
    fun `counts every optional item ahead of the route rows`() {
        assertEquals(0, firstRouteIndex(hasModeSwitch = false, alertsBeforeRoutes = false, onDemandBeforeRoutes = false, directionBeforeRoutes = false))
        assertEquals(1, firstRouteIndex(hasModeSwitch = false, alertsBeforeRoutes = false, onDemandBeforeRoutes = true, directionBeforeRoutes = false))
        assertEquals(4, firstRouteIndex(hasModeSwitch = true, alertsBeforeRoutes = true, onDemandBeforeRoutes = true, directionBeforeRoutes = true))
    }
}
