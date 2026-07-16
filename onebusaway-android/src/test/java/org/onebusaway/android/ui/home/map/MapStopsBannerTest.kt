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
package org.onebusaway.android.ui.home.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.map.StopsBanner
import org.onebusaway.android.ui.home.CurrentFocus
import org.onebusaway.android.ui.home.FocusedStop

class MapStopsBannerTest {

    private val stopFocus = CurrentFocus.Stop(FocusedStop("stop", null, null, 0.0, 0.0))

    @Test
    fun `stop focus hides the zoom-in banner`() {
        assertEquals(StopsBanner.None, StopsBanner.MoreStopsAvailable.forFocus(stopFocus))
    }

    @Test
    fun `stop focus preserves the cached-stops banner`() {
        assertEquals(StopsBanner.ShowingSavedStops, StopsBanner.ShowingSavedStops.forFocus(stopFocus))
    }

    @Test
    fun `nearby mode preserves the zoom-in banner`() {
        assertEquals(
            StopsBanner.MoreStopsAvailable,
            StopsBanner.MoreStopsAvailable.forFocus(CurrentFocus.None),
        )
    }
}
