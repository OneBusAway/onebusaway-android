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
package org.onebusaway.android.region

import android.database.Cursor

/**
 * Builds a [Region] from a ContentProvider row. The single source for the cursor→Region mapping
 * shared by `RegionUtils.getRegionsFromProvider` and `ObaContract.Regions.get` (which previously
 * each duplicated the 25-column positional construction). The column order matches the
 * `ObaContract.Regions` projection those queries use. Bounds and Open311 servers are fetched
 * separately by each caller (from a bulk map vs. a per-id sub-query) and passed in.
 */
object RegionCursor {

    @JvmStatic
    fun fromCursor(
        c: Cursor,
        bounds: Array<Region.Bounds>?,
        open311Servers: Array<Region.Open311Server>?,
    ): Region {
        val umamiUrl = c.getString(23)
        val umamiId = c.getString(24)
        val umami = if (umamiUrl != null || umamiId != null) {
            Region.UmamiAnalyticsConfig(umamiUrl, umamiId)
        } else {
            null
        }
        return Region(
            id = c.getLong(0),
            name = c.getString(1) ?: "",
            active = true,
            obaBaseUrl = c.getString(2),
            siriBaseUrl = c.getString(3),
            bounds = bounds ?: emptyArray(),
            open311Servers = open311Servers ?: emptyArray(),
            language = c.getString(4),
            contactEmail = c.getString(5),
            supportsObaDiscoveryApis = c.getInt(6) > 0,
            supportsObaRealtimeApis = c.getInt(7) > 0,
            supportsSiriRealtimeApis = c.getInt(8) > 0,
            twitterUrl = c.getString(9),
            experimental = c.getInt(10) > 0,
            stopInfoUrl = c.getString(11),
            otpBaseUrl = c.getString(12),
            otpContactEmail = c.getString(13),
            supportsOtpBikeshare = c.getInt(14) > 0,
            supportsEmbeddedSocial = c.getInt(15) > 0,
            paymentAndroidAppId = c.getString(16),
            paymentWarningTitle = c.getString(17),
            paymentWarningBody = c.getString(18),
            isTravelBehaviorDataCollectionEnabled = c.getInt(19) > 0,
            isEnrollParticipantsInStudy = c.getInt(20) > 0,
            sidecarBaseUrl = c.getString(21),
            plausibleAnalyticsServerUrl = c.getString(22),
            umamiAnalytics = umami,
        )
    }
}
