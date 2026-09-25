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
 * The gesture that replaced the vehicle info window's "more info" chevron (#2194): the first tap on a
 * vehicle selects it, a second tap on that same vehicle opens its trip details. Getting this wrong
 * navigates away from the map on a tap the rider meant as "select", so the rule is pinned here.
 */
class VehicleReTapTest {

    @Test
    fun tappingTheSelectedVehicleAgainIsAReTap() {
        assertTrue(isVehicleReTap(selected = "trip1", tapped = "trip1"))
    }

    @Test
    fun theFirstTapOnAVehicleIsNotAReTap() {
        assertFalse("nothing selected yet", isVehicleReTap(selected = null, tapped = "trip1"))
        assertFalse("a different vehicle was selected", isVehicleReTap(selected = "trip2", tapped = "trip1"))
    }

    /**
     * A blank id names no particular vehicle (OBA sends "" for a block-edge trip, #2003). Two such
     * vehicles compare equal, so treating that as a re-tap would open one vehicle's trip from a tap on
     * another — a wrong destination, not merely a missed shortcut.
     */
    @Test
    fun aBlankTripIdIsNeverAReTap() {
        assertFalse(isVehicleReTap(selected = "", tapped = ""))
        assertFalse(isVehicleReTap(selected = null, tapped = ""))
    }
}
