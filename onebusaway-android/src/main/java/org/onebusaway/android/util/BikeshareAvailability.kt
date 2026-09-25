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
 *  - [isStationLayerEnabled] — can we *draw rentals*? Either server will do, since the rental map
 *    layer reads whichever one publishes them (#2168).
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
    fun isTripPlanningEnabled(context: Context): Boolean = isTripPlanningEnabled(region(context), customOtpApiUrl(context))

    /** Pure predicate for injected consumers that already hold the [region] + [customOtpApiUrl]. */
    fun isTripPlanningEnabled(region: Region?, customOtpApiUrl: String?): Boolean = enabled(
        // The flag for whichever OTP server the plan will actually be sent to. This selection is app
        // routing policy, not a fact about the region, so it lives here rather than on [Region] —
        // which can't see the custom-URL half of the same decision either way.
        region?.let { if (it.usesOtp2) it.supportsOtpGraphqlBikeshare else it.supportsOtpBikeshare } ?: false,
        customOtpApiUrl
    )

    /** [isStationLayerEnabled] for callers that only hold a [Context]. */
    fun isStationLayerEnabled(context: Context): Boolean = isStationLayerEnabled(region(context), customOtpApiUrl(context))

    /**
     * Whether the map's rental overlay has anything to show — **either** flag, because since #2168 the
     * layer reads either server: OTP2's `vehicleRentalsByBbox` where the region publishes a
     * bikeshare-capable GraphQL endpoint, else OTP1 REST `/bike_rental`. `RentalPlacesRepository`
     * makes the same choice with the same two flags, and this predicate answers "is there any server
     * to ask" for the layer toggle and the loader's own gate.
     *
     * Before #2168 this was deliberately the OTP1 flag alone, since the overlay's only data source was
     * OTP1 REST — which meant an OTP2-only bikeshare region could plan bike trips but drew no rentals.
     */
    fun isStationLayerEnabled(region: Region?, customOtpApiUrl: String?): Boolean = enabled(
        region?.let { it.supportsOtpBikeshare || (it.supportsOtpGraphqlBikeshare && it.usesOtp2) } ?: false,
        customOtpApiUrl
    )

    /**
     * The shape both predicates share: the region's own per-server flag, or the custom-OTP-URL hatch.
     * Written once so the hatch rule can't drift between the two — only [regionSupports] differs.
     */
    private fun enabled(regionSupports: Boolean, customOtpApiUrl: String?): Boolean = regionSupports || !customOtpApiUrl.isNullOrEmpty()

    private fun region(context: Context): Region? = RegionEntryPoint.get(context).currentRegion()

    private fun customOtpApiUrl(context: Context): String? = PreferencesEntryPoint.get(context)
        .getString(context.getString(R.string.preference_key_otp_api_url), null)
}
