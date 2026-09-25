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
package org.onebusaway.android.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOnDemandZonesTest {

    private fun snapshot(showOnDemandZones: Boolean) = SettingsPrefSnapshot(
        autoSelectRegion = true, showNegativeArrivals = true, hideAlerts = false, showZoomControls = false,
        compactStopIcons = false, showRentalButton = true, showOnDemandZones = showOnDemandZones,
        displayWeatherView = true, showAvailableStudies = true, leftHandMode = false, vibrateAllowed = true,
        tripPlanNotifications = true, analyticsEnabled = true, preferredUnits = null, preferredTempUnits = null, appTheme = null
    )

    private val env = SettingsEnvironment(useFixedRegion = false, sdkInt = 33, isObaFlavor = true, isGoogleMaps = true)

    @Test
    fun `the zones toggle flows from the snapshot to the ui state`() {
        assertTrue(buildSettingsUiState(snapshot(true), null, env, "custom").showOnDemandZones)
        assertFalse(buildSettingsUiState(snapshot(false), null, env, "custom").showOnDemandZones)
    }
}
