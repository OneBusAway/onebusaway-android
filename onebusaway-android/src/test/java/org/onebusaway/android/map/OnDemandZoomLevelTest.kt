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

class OnDemandZoomLevelTest {

    private val metersPerDegree = 111_133.0

    @Test
    fun `street below 4 km, region to 65 km, hidden above`() {
        assertEquals(OnDemandZoomLevel.STREET, onDemandZoomLevel(0.0))
        assertEquals(OnDemandZoomLevel.STREET, onDemandZoomLevel(ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS / metersPerDegree))
        assertEquals(OnDemandZoomLevel.REGION, onDemandZoomLevel(ONDEMAND_STREET_LEVEL_MAX_HEIGHT_METERS * 1.01 / metersPerDegree))
        assertEquals(OnDemandZoomLevel.REGION, onDemandZoomLevel(ONDEMAND_MAX_VISIBLE_HEIGHT_METERS / metersPerDegree))
        assertEquals(OnDemandZoomLevel.HIDDEN, onDemandZoomLevel(ONDEMAND_MAX_VISIBLE_HEIGHT_METERS * 1.01 / metersPerDegree))
    }
}
