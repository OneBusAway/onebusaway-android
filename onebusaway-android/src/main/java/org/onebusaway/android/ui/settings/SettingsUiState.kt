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

/*
 * Pure (Android-free) state + derivation for the Compose settings screens. The ViewModels read the
 * raw preference values + the current region into the snapshot/info holders below and call the
 * `build*UiState` functions; keeping the branchy visibility/summary logic here makes it
 * JVM-unit-testable. These mirror the imperative `removePreference` / `setSummary` decisions the old
 * PreferenceFragments made.
 */

// SDK gates (kept as plain ints so this file has no android.* dependency).
private const val SDK_N = 24 // Android 7.0
private const val SDK_O = 26 // Android 8.0
private const val SDK_P = 28 // Android 9.0

// ---------------------------------------------------------------------------------------------
// Root settings
// ---------------------------------------------------------------------------------------------

/** The raw current values of every preference the root settings screen renders. */
data class SettingsPrefSnapshot(
    val autoSelectRegion: Boolean,
    val showNegativeArrivals: Boolean,
    val hideAlerts: Boolean,
    val showZoomControls: Boolean,
    val displayWeatherView: Boolean,
    val showAvailableStudies: Boolean,
    val showTutorialScreens: Boolean,
    val leftHandMode: Boolean,
    val showHeaderArrivals: Boolean,
    val vibrateAllowed: Boolean,
    val tripPlanNotifications: Boolean,
    val analyticsEnabled: Boolean,
    val shareDestinationLogs: Boolean,
    val mapMode: String?,
    val preferredUnits: String?,
    val preferredTempUnits: String?,
    val appTheme: String?,
)

/** What the screen needs to know about the current region; null means no region (custom API). */
data class RegionSummaryInfo(val name: String, val hasOtp: Boolean)

/** Build-time/device inputs that gate category visibility (don't change at runtime). */
data class SettingsEnvironment(
    val useFixedRegion: Boolean,
    val sdkInt: Int,
    val isObaFlavor: Boolean,
)

data class SettingsUiState(
    val showRegionCategory: Boolean,
    val showNotificationsCategory: Boolean,
    val showTripPlanNotifications: Boolean,
    val showUserLogsCategory: Boolean,
    val showDonate: Boolean,
    val showPoweredByOba: Boolean,
    val regionSummary: String,
    val autoSelectRegion: Boolean,
    val showNegativeArrivals: Boolean,
    val hideAlerts: Boolean,
    val showZoomControls: Boolean,
    val displayWeatherView: Boolean,
    val showAvailableStudies: Boolean,
    val showTutorialScreens: Boolean,
    val leftHandMode: Boolean,
    val showHeaderArrivals: Boolean,
    val vibrateAllowed: Boolean,
    val tripPlanNotifications: Boolean,
    val analyticsEnabled: Boolean,
    val shareDestinationLogs: Boolean,
    val mapMode: String?,
    val preferredUnits: String?,
    val preferredTempUnits: String?,
    val appTheme: String?,
)

/**
 * @param customApiRegionSummary the "Custom API" region summary string, shown when [region] is null.
 */
fun buildSettingsUiState(
    prefs: SettingsPrefSnapshot,
    region: RegionSummaryInfo?,
    env: SettingsEnvironment,
    customApiRegionSummary: String,
): SettingsUiState = SettingsUiState(
    showRegionCategory = !env.useFixedRegion,
    // Android 8+ manages notification channels itself, so the legacy category is dropped there.
    showNotificationsCategory = env.sdkInt < SDK_O,
    // Trip-plan notifications are dropped only when a region is set but has no OTP endpoint.
    showTripPlanNotifications = region == null || region.hasOtp,
    showUserLogsCategory = env.sdkInt in SDK_N until SDK_P,
    // OBA-branded builds solicit donations; white-label builds show "powered by OneBusAway" instead.
    showDonate = env.isObaFlavor,
    showPoweredByOba = !env.isObaFlavor,
    regionSummary = region?.name ?: customApiRegionSummary,
    autoSelectRegion = prefs.autoSelectRegion,
    showNegativeArrivals = prefs.showNegativeArrivals,
    hideAlerts = prefs.hideAlerts,
    showZoomControls = prefs.showZoomControls,
    displayWeatherView = prefs.displayWeatherView,
    showAvailableStudies = prefs.showAvailableStudies,
    showTutorialScreens = prefs.showTutorialScreens,
    leftHandMode = prefs.leftHandMode,
    showHeaderArrivals = prefs.showHeaderArrivals,
    vibrateAllowed = prefs.vibrateAllowed,
    tripPlanNotifications = prefs.tripPlanNotifications,
    analyticsEnabled = prefs.analyticsEnabled,
    shareDestinationLogs = prefs.shareDestinationLogs,
    mapMode = prefs.mapMode,
    preferredUnits = prefs.preferredUnits,
    preferredTempUnits = prefs.preferredTempUnits,
    appTheme = prefs.appTheme,
)

// ---------------------------------------------------------------------------------------------
// Advanced settings
// ---------------------------------------------------------------------------------------------

data class AdvancedPrefSnapshot(
    val experimentalRegionsEnabled: Boolean,
    val displayTestAlerts: Boolean,
    val customObaApiUrl: String?,
    val customOtpApiUrl: String?,
)

/** Region info the advanced screen needs; null means no region (custom API in use). */
data class AdvancedRegionInfo(val isExperimental: Boolean)

data class AdvancedSettingsUiState(
    val showExperimentalRegions: Boolean,
    val experimentalRegionsEnabled: Boolean,
    val displayTestAlerts: Boolean,
    val customObaApiUrl: String?,
    val customOtpApiUrl: String?,
    val customObaApiUrlSummary: String,
    val customOtpApiUrlSummary: String,
    val currentRegionIsExperimental: Boolean,
)

/**
 * @param obaBrandedSummary the branded "<app> API server" summary, shown for the OBA URL when a
 * region is active.
 * @param otpDefaultSummary the default OTP-URL summary, shown unless a custom OTP URL is set.
 */
fun buildAdvancedSettingsUiState(
    prefs: AdvancedPrefSnapshot,
    region: AdvancedRegionInfo?,
    useFixedRegion: Boolean,
    obaBrandedSummary: String,
    otpDefaultSummary: String,
): AdvancedSettingsUiState {
    val hasRegion = region != null
    // With a region: the OBA row describes the server generically; without one, it shows the custom URL.
    val obaSummary = if (hasRegion) obaBrandedSummary else prefs.customObaApiUrl.orEmpty()
    // The OTP row shows the custom URL only when one is set (and a region is active); else the default.
    val otpSummary =
        if (hasRegion && !prefs.customOtpApiUrl.isNullOrEmpty()) prefs.customOtpApiUrl else otpDefaultSummary
    return AdvancedSettingsUiState(
        showExperimentalRegions = !useFixedRegion,
        experimentalRegionsEnabled = prefs.experimentalRegionsEnabled,
        displayTestAlerts = prefs.displayTestAlerts,
        customObaApiUrl = prefs.customObaApiUrl,
        customOtpApiUrl = prefs.customOtpApiUrl,
        customObaApiUrlSummary = obaSummary,
        customOtpApiUrlSummary = otpSummary,
        currentRegionIsExperimental = region?.isExperimental == true,
    )
}

/** Result of an advanced custom-URL edit; the screen maps this to keep its dialog open on invalid input. */
enum class UrlChangeResult { Accepted, InvalidObaUrl, InvalidOtpUrl, ClearedGoHome }
