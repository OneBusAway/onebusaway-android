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
package org.onebusaway.android.util

import android.content.Context
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.region.Region

/**
 * Whether bikeshare is available — the predicate lifted off the former `Application.isBikeshareEnabled()`
 * static so it reads the injected region / preferences seams instead of app-global `Application` state.
 *
 * There are **two** questions here, not one, because a region has two independent OTP servers with
 * separately-published bike-rental support (see [Region.supportsOtpGraphqlBikeshare]):
 *  - [isTripPlanningEnabled] — can a *plan* use bikeshare? Answered by whichever server the plan will
 *    hit ([Region.usesOtp2]).
 *  - [isStationLayerEnabled] — can we *draw bike stations*? Always the OTP1 flag, since the station
 *    overlay reads OTP1 REST `/bike_rental` regardless of the planning protocol.
 *
 * A custom OTP API URL enables both, unchanged: it's the advanced-setting hatch for testing against a
 * bikeshare-capable OTP, and there's no [Region] to carry a capability flag for a hand-entered server.
 * The hatch answers **true unconditionally** — the custom server's own protocol setting (the
 * `preference_key_otp_api_url_is_graphql` preference that `TripRequestBuilder.otpTarget` reads) is not
 * consulted, so "whichever server the plan will hit" above describes the *region* case only.
 */
object BikeshareAvailability {

    /**
     * Resolves the region + custom OTP URL from [context] (via the DI EntryPoints) for callers that
     * aren't themselves injectable — static Java utilities, the [TripRequestBuilder], composables.
     */
    @JvmStatic
    fun isTripPlanningEnabled(context: Context): Boolean = isTripPlanningEnabled(region(context), customOtpApiUrl(context))

    /** Pure predicate for injected consumers that already hold the [region] + [customOtpApiUrl]. */
    @JvmStatic
    fun isTripPlanningEnabled(region: Region?, customOtpApiUrl: String?): Boolean = enabled(
        // The flag for whichever OTP server the plan will actually be sent to. This selection is app
        // routing policy, not a fact about the region, so it lives here rather than on [Region] —
        // which can't see the custom-URL half of the same decision either way.
        region?.let { if (it.usesOtp2) it.supportsOtpGraphqlBikeshare else it.supportsOtpBikeshare } ?: false,
        customOtpApiUrl
    )

    /** [isStationLayerEnabled] for callers that only hold a [Context]. */
    @JvmStatic
    fun isStationLayerEnabled(context: Context): Boolean = isStationLayerEnabled(region(context), customOtpApiUrl(context))

    /**
     * Whether the map's bike-station overlay has anything to show. Deliberately still the OTP1 flag:
     * the overlay's only data source is OTP1 REST `/bike_rental` (see `BikeStationsRepository`), so an
     * OTP2-only bikeshare region has no stations to draw even though it can plan bike trips.
     */
    @JvmStatic
    fun isStationLayerEnabled(region: Region?, customOtpApiUrl: String?): Boolean = enabled(region?.supportsOtpBikeshare ?: false, customOtpApiUrl)

    /**
     * The shape both predicates share: the region's own per-server flag, or the custom-OTP-URL hatch.
     * Written once so the hatch rule can't drift between the two — only [regionSupports] differs.
     */
    private fun enabled(regionSupports: Boolean, customOtpApiUrl: String?): Boolean = regionSupports || !customOtpApiUrl.isNullOrEmpty()

    private fun region(context: Context): Region? = RegionEntryPoint.get(context).currentRegion()

    private fun customOtpApiUrl(context: Context): String? = PreferencesEntryPoint.get(context)
        .getString(context.getString(R.string.preference_key_otp_api_url), null)
}
