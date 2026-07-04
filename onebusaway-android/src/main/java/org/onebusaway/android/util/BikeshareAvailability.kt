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
 * Whether bikeshare is an available trip/layer option — the predicate lifted off the former
 * `Application.isBikeshareEnabled()` static so it reads the injected region / preferences seams instead
 * of app-global `Application` state. Bikeshare is available when the current region supports OTP
 * bikeshare, or when a custom OTP API URL is set (which eases testing against a bikeshare-capable OTP).
 */
object BikeshareAvailability {

    /**
     * Resolves the region + custom OTP URL from [context] (via the DI EntryPoints) for callers that
     * aren't themselves injectable — static Java utilities, the [TripRequestBuilder], composables.
     */
    @JvmStatic
    fun isEnabled(context: Context): Boolean = isEnabled(
        RegionEntryPoint.get(context).currentRegion(),
        PreferencesEntryPoint.get(context)
            .getString(context.getString(R.string.preference_key_otp_api_url), null),
    )

    /** Pure predicate for injected consumers that already hold the [region] + [customOtpApiUrl]. */
    @JvmStatic
    fun isEnabled(region: Region?, customOtpApiUrl: String?): Boolean =
        (region != null && region.supportsOtpBikeshare) || !customOtpApiUrl.isNullOrEmpty()
}
