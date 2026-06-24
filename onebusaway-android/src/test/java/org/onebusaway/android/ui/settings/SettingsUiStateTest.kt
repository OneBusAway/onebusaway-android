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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the settings category-visibility and summary derivation — the branchy
 * `removePreference` / `setSummary` decisions the old PreferenceFragments made imperatively.
 */
class SettingsUiStateTest {

    private val prefs = SettingsPrefSnapshot(
        autoSelectRegion = true,
        showNegativeArrivals = true,
        hideAlerts = false,
        showZoomControls = false,
        displayWeatherView = true,
        showAvailableStudies = true,
        showTutorialScreens = true,
        leftHandMode = false,
        showHeaderArrivals = false,
        vibrateAllowed = true,
        tripPlanNotifications = true,
        analyticsEnabled = true,
        shareDestinationLogs = true,
        mapMode = "normal",
        preferredUnits = "Automatic",
        preferredTempUnits = "Automatic",
        appTheme = "System default",
    )

    private fun env(useFixedRegion: Boolean = false, sdkInt: Int = 30, isObaFlavor: Boolean = true) =
        SettingsEnvironment(useFixedRegion, sdkInt, isObaFlavor)

    private fun build(
        region: RegionSummaryInfo? = RegionSummaryInfo("Puget Sound", hasOtp = true),
        env: SettingsEnvironment = env(),
    ) = buildSettingsUiState(prefs, region, env, customApiRegionSummary = "Custom API")

    // --- region category / summary ---

    @Test
    fun `region category hidden when using a fixed region`() {
        assertFalse(build(env = env(useFixedRegion = true)).showRegionCategory)
        assertTrue(build(env = env(useFixedRegion = false)).showRegionCategory)
    }

    @Test
    fun `region summary is the region name, or the custom-api string when there is no region`() {
        assertEquals("Puget Sound", build().regionSummary)
        assertEquals("Custom API", build(region = null).regionSummary)
    }

    // --- notifications / trip plan ---

    @Test
    fun `notifications category hidden on Android 8 and up`() {
        assertTrue(build(env = env(sdkInt = 25)).showNotificationsCategory)
        assertFalse(build(env = env(sdkInt = 26)).showNotificationsCategory)
        assertFalse(build(env = env(sdkInt = 33)).showNotificationsCategory)
    }

    @Test
    fun `trip-plan notifications hidden only when a region has no OTP endpoint`() {
        assertTrue(build(region = RegionSummaryInfo("R", hasOtp = true)).showTripPlanNotifications)
        assertFalse(build(region = RegionSummaryInfo("R", hasOtp = false)).showTripPlanNotifications)
        // No region (custom API): the row stays.
        assertTrue(build(region = null).showTripPlanNotifications)
    }

    // --- user logs ---

    @Test
    fun `user logs category only on Android 7 and 8 (24 through 27)`() {
        assertFalse(build(env = env(sdkInt = 23)).showUserLogsCategory)
        assertTrue(build(env = env(sdkInt = 24)).showUserLogsCategory)
        assertTrue(build(env = env(sdkInt = 27)).showUserLogsCategory)
        assertFalse(build(env = env(sdkInt = 28)).showUserLogsCategory)
    }

    // --- flavor: donate vs powered-by ---

    @Test
    fun `OBA flavor shows donate and hides powered-by-oba`() {
        val s = build(env = env(isObaFlavor = true))
        assertTrue(s.showDonate)
        assertFalse(s.showPoweredByOba)
    }

    @Test
    fun `white-label flavor hides donate and shows powered-by-oba`() {
        val s = build(env = env(isObaFlavor = false))
        assertFalse(s.showDonate)
        assertTrue(s.showPoweredByOba)
    }

    // --- values pass through ---

    @Test
    fun `toggle and list values are copied through to the state`() {
        val s = build()
        assertTrue(s.autoSelectRegion)
        assertEquals("normal", s.mapMode)
        assertEquals("System default", s.appTheme)
    }

    // --- advanced settings ---

    private val advPrefs = AdvancedPrefSnapshot(
        experimentalRegionsEnabled = false,
        displayTestAlerts = false,
        customObaApiUrl = null,
        customOtpApiUrl = null,
    )

    private fun buildAdv(
        prefs: AdvancedPrefSnapshot = advPrefs,
        region: AdvancedRegionInfo? = AdvancedRegionInfo(isExperimental = false),
        useFixedRegion: Boolean = false,
    ) = buildAdvancedSettingsUiState(
        prefs, region, useFixedRegion,
        obaBrandedSummary = "OBA server", otpDefaultSummary = "OTP default",
    )

    @Test
    fun `experimental regions row hidden when using a fixed region`() {
        assertFalse(buildAdv(useFixedRegion = true).showExperimentalRegions)
        assertTrue(buildAdv(useFixedRegion = false).showExperimentalRegions)
    }

    @Test
    fun `OBA url summary is the branded text with a region, and the custom url without one`() {
        assertEquals("OBA server", buildAdv(region = AdvancedRegionInfo(false)).customObaApiUrlSummary)
        assertEquals(
            "https://custom.example.org",
            buildAdv(
                prefs = advPrefs.copy(customObaApiUrl = "https://custom.example.org"),
                region = null,
            ).customObaApiUrlSummary,
        )
    }

    @Test
    fun `OTP url summary shows the custom url only when set with a region, else the default`() {
        assertEquals("OTP default", buildAdv(region = AdvancedRegionInfo(false)).customOtpApiUrlSummary)
        assertEquals(
            "https://otp.example.org",
            buildAdv(
                prefs = advPrefs.copy(customOtpApiUrl = "https://otp.example.org"),
                region = AdvancedRegionInfo(false),
            ).customOtpApiUrlSummary,
        )
        // Custom OTP set but no region: legacy showed the default summary.
        assertEquals(
            "OTP default",
            buildAdv(
                prefs = advPrefs.copy(customOtpApiUrl = "https://otp.example.org"),
                region = null,
            ).customOtpApiUrlSummary,
        )
    }

    @Test
    fun `currentRegionIsExperimental reflects the active region`() {
        assertTrue(buildAdv(region = AdvancedRegionInfo(isExperimental = true)).currentRegionIsExperimental)
        assertFalse(buildAdv(region = AdvancedRegionInfo(isExperimental = false)).currentRegionIsExperimental)
        assertFalse(buildAdv(region = null).currentRegionIsExperimental)
    }
}
