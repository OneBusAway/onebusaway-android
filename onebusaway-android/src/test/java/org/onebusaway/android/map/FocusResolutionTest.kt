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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveVehicleFocus] — the pure decision behind RouteMapController's "fit the tapped arrival's
 * vehicle together with its stop" launch. The point of the test is the WAIT rule: a pending focus must
 * not be decided (and so never wrongly dropped) before there's an actual vehicle poll to check against.
 */
class FocusResolutionTest {

    @Test
    fun waits_until_direction_resolved() {
        // A direction-anchored launch is still Pending: hold, even if a poll landed and a marker exists.
        assertEquals(
            FocusResolution.WAIT,
            resolveVehicleFocus(directionResolved = false, pollLanded = true, markerPresent = true)
        )
    }

    @Test
    fun waits_until_a_poll_lands() {
        // Direction resolved but no poll yet — the set is empty because none arrived, not because the
        // vehicle is absent. Must not drop the focus here.
        assertEquals(
            FocusResolution.WAIT,
            resolveVehicleFocus(directionResolved = true, pollLanded = false, markerPresent = false)
        )
    }

    @Test
    fun fits_when_the_vehicle_is_on_the_map() {
        assertEquals(
            FocusResolution.FIT,
            resolveVehicleFocus(directionResolved = true, pollLanded = true, markerPresent = true)
        )
    }

    @Test
    fun drops_when_no_vehicle_runs_the_trip() {
        // A real poll landed and the trip has no live vehicle (e.g. it's a future block trip): drop the
        // focus so the caller leaves the camera put (no reframe, no toast — #1992).
        assertEquals(
            FocusResolution.DROP,
            resolveVehicleFocus(directionResolved = true, pollLanded = true, markerPresent = false)
        )
    }
}
