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
import org.junit.Assert.assertNull
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
        showRentalButton = true,
        displayWeatherView = true,
        showAvailableStudies = true,
        leftHandMode = false,
        vibrateAllowed = true,
        tripPlanNotifications = true,
        analyticsEnabled = true,
        preferredUnits = "Automatic",
        preferredTempUnits = "Automatic",
        appTheme = "System default"
    )

    private fun env(useFixedRegion: Boolean = false, sdkInt: Int = 30, isObaFlavor: Boolean = true) = SettingsEnvironment(useFixedRegion, sdkInt, isObaFlavor)

    private fun build(
        region: RegionSummaryInfo? = RegionSummaryInfo("Puget Sound", hasOtp = true),
        env: SettingsEnvironment = env()
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
    fun `trip notification toggle remains accessible on every Android version`() {
        for (sdk in listOf(23, 25, 26, 33, 36)) {
            val state = build(env = env(sdkInt = sdk))
            assertTrue(state.showNotificationsCategory)
            assertTrue(state.showTripPlanNotifications)
            assertEquals(sdk < 26, state.showLegacyNotificationControls)
        }
    }

    @Test
    fun `region without trip planning only shows legacy notification controls`() {
        val region = RegionSummaryInfo("R", hasOtp = false)
        val legacy = build(region = region, env = env(sdkInt = 25))
        assertTrue(legacy.showNotificationsCategory)
        assertTrue(legacy.showLegacyNotificationControls)
        assertFalse(legacy.showTripPlanNotifications)

        val modern = build(region = region, env = env(sdkInt = 26))
        assertFalse(modern.showNotificationsCategory)
        assertFalse(modern.showLegacyNotificationControls)
        assertFalse(modern.showTripPlanNotifications)
    }

    @Test
    fun `disabled trip notifications can be re-enabled on modern Android`() {
        val state = buildSettingsUiState(
            prefs.copy(tripPlanNotifications = false),
            region = null,
            env = env(sdkInt = 33),
            customApiRegionSummary = "Custom API"
        )
        assertTrue(state.showNotificationsCategory)
        assertTrue(state.showTripPlanNotifications)
        assertFalse(state.tripPlanNotifications)
    }

    @Test
    fun `trip-plan notifications hidden only when a region has no OTP endpoint`() {
        assertTrue(build(region = RegionSummaryInfo("R", hasOtp = true)).showTripPlanNotifications)
        assertFalse(build(region = RegionSummaryInfo("R", hasOtp = false)).showTripPlanNotifications)
        // No region (custom API): the row stays.
        assertTrue(build(region = null).showTripPlanNotifications)
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
        assertEquals("System default", s.appTheme)
    }

    // --- advanced settings ---

    private val advPrefs = AdvancedPrefSnapshot(
        experimentalRegionsEnabled = false,
        displayTestAlerts = false,
        pushTestDevice = false,
        pushTestDeviceName = null,
        customObaApiUrl = null,
        customOtpApiUrl = null,
        customOtpApiUrlUsesGraphQl = false,
        mapStopCacheSize = 200
    )

    private fun buildAdv(
        prefs: AdvancedPrefSnapshot = advPrefs,
        region: AdvancedRegionInfo? = AdvancedRegionInfo(isExperimental = false),
        useFixedRegion: Boolean = false
    ) = buildAdvancedSettingsUiState(
        prefs,
        region,
        useFixedRegion,
        obaBrandedSummary = "OBA server",
        otpDefaultSummary = "OTP default"
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
                region = null
            ).customObaApiUrlSummary
        )
    }

    @Test
    fun `OTP url summary shows the custom url only when set with a region, else the default`() {
        assertEquals("OTP default", buildAdv(region = AdvancedRegionInfo(false)).customOtpApiUrlSummary)
        assertEquals(
            "https://otp.example.org",
            buildAdv(
                prefs = advPrefs.copy(customOtpApiUrl = "https://otp.example.org"),
                region = AdvancedRegionInfo(false)
            ).customOtpApiUrlSummary
        )
        // Custom OTP set but no region: legacy showed the default summary.
        assertEquals(
            "OTP default",
            buildAdv(
                prefs = advPrefs.copy(customOtpApiUrl = "https://otp.example.org"),
                region = null
            ).customOtpApiUrlSummary
        )
    }

    @Test
    fun `currentRegionIsExperimental reflects the active region`() {
        assertTrue(buildAdv(region = AdvancedRegionInfo(isExperimental = true)).currentRegionIsExperimental)
        assertFalse(buildAdv(region = AdvancedRegionInfo(isExperimental = false)).currentRegionIsExperimental)
        assertFalse(buildAdv(region = null).currentRegionIsExperimental)
    }

    @Test
    fun `map stop cache size carries through to the ui state`() {
        assertEquals(500, buildAdv(prefs = advPrefs.copy(mapStopCacheSize = 500)).mapStopCacheSize)
    }

    // --- parseStopCacheSize ---

    @Test
    fun `parseStopCacheSize accepts an in-range number`() {
        assertEquals(200, parseStopCacheSize("200"))
        assertEquals(MAP_STOP_CACHE_SIZE_MIN, parseStopCacheSize("  $MAP_STOP_CACHE_SIZE_MIN  "))
        assertEquals(MAP_STOP_CACHE_SIZE_MAX, parseStopCacheSize(MAP_STOP_CACHE_SIZE_MAX.toString()))
    }

    @Test
    fun `parseStopCacheSize rejects non-numbers and out-of-range values`() {
        assertNull(parseStopCacheSize(""))
        assertNull(parseStopCacheSize("abc"))
        assertNull(parseStopCacheSize("12.5"))
        assertNull(parseStopCacheSize((MAP_STOP_CACHE_SIZE_MIN - 1).toString()))
        assertNull(parseStopCacheSize((MAP_STOP_CACHE_SIZE_MAX + 1).toString()))
    }
}
