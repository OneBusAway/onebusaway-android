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
import org.junit.Test
import org.onebusaway.android.models.ObaRoute

/** Pure-logic guards for [VehicleBitmaps]'s route-type normalization (the cablecar→tram promise). */
class VehicleBitmapsTest {

    /**
     * `iconKey` and `vehicleBitmap` both route through [VehicleBitmaps.normalizeVehicleType], so a cablecar
     * route must collapse onto tram — otherwise the two paths could disagree (or mint a second icon for a
     * vehicle that should share tram's).
     */
    @Test
    fun cablecarNormalizesToTram() {
        assertEquals(
            ObaRoute.TYPE_TRAM,
            VehicleBitmaps.normalizeVehicleType(ObaRoute.TYPE_CABLECAR),
        )
        assertEquals(
            "a cablecar route resolves to the same type as a tram route",
            VehicleBitmaps.normalizeVehicleType(ObaRoute.TYPE_TRAM),
            VehicleBitmaps.normalizeVehicleType(ObaRoute.TYPE_CABLECAR),
        )
    }

    /** Every other route type passes through untouched. */
    @Test
    fun otherTypesPassThrough() {
        for (type in intArrayOf(
            ObaRoute.TYPE_TRAM,
            ObaRoute.TYPE_SUBWAY,
            ObaRoute.TYPE_RAIL,
            ObaRoute.TYPE_BUS,
            ObaRoute.TYPE_FERRY,
        )) {
            assertEquals(type.toLong(), VehicleBitmaps.normalizeVehicleType(type).toLong())
        }
    }
}
