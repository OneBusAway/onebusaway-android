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

import org.onebusaway.android.database.oba.Open311ServerRecord
import org.onebusaway.android.database.oba.RegionBoundRecord
import org.onebusaway.android.database.oba.RegionRecord
import org.onebusaway.android.database.oba.RegionWithChildren

/**
 * Pure mapping between the region-cache Room rows ([RegionWithChildren]) and the domain [Region].
 * Replaces the legacy cursor-positional region mapping and the `RegionUtils` write projections;
 * kept free of Room/Android types so it is JVM-unit-testable. Nullable integer flags read as false
 * when absent, matching the legacy `Cursor.getInt` (NULL → 0) semantics.
 */
object RegionMapper {

    /** Builds a domain [Region] from a cached region row. A cached region is always [active]. */
    fun toRegion(row: RegionWithChildren): Region {
        val r = row.region
        val umami = if (r.umamiAnalyticsUrl != null || r.umamiAnalyticsId != null) {
            Region.UmamiAnalyticsConfig(r.umamiAnalyticsUrl, r.umamiAnalyticsId)
        } else {
            null
        }
        return Region(
            id = r.id,
            name = r.name,
            active = true,
            obaBaseUrl = r.obaBaseUrl,
            siriBaseUrl = r.siriBaseUrl,
            bounds = row.bounds.map {
                Region.Bounds(it.latitude, it.longitude, it.latSpan, it.lonSpan)
            }.toTypedArray(),
            open311Servers = row.open311Servers.map {
                Region.Open311Server(it.jurisdiction, it.apiKey, it.baseUrl)
            }.toTypedArray(),
            language = r.language,
            contactEmail = r.contactEmail,
            supportsObaDiscoveryApis = r.supportsObaDiscovery > 0,
            supportsObaRealtimeApis = r.supportsObaRealtime > 0,
            supportsSiriRealtimeApis = r.supportsSiriRealtime > 0,
            twitterUrl = r.twitterUrl,
            experimental = (r.experimental ?: 0) > 0,
            stopInfoUrl = r.stopInfoUrl,
            otpBaseUrl = r.otpBaseUrl,
            otpContactEmail = r.otpContactEmail,
            supportsOtpBikeshare = (r.supportsOtpBikeshare ?: 0) > 0,
            supportsEmbeddedSocial = (r.supportsEmbeddedSocial ?: 0) > 0,
            paymentAndroidAppId = r.paymentAndroidAppId,
            paymentWarningTitle = r.paymentWarningTitle,
            paymentWarningBody = r.paymentWarningBody,
            sidecarBaseUrl = r.sidecarBaseUrl,
            plausibleAnalyticsServerUrl = r.plausibleAnalyticsServerUrl,
            umamiAnalytics = umami,
        )
    }

    /** Projects a domain [Region] to its cache rows (the inverse of [toRegion]; the legacy write). */
    fun toEntities(region: Region): RegionWithChildren = RegionWithChildren(
        region = RegionRecord(
            id = region.id,
            name = region.name,
            obaBaseUrl = region.obaBaseUrl.orEmpty(),
            siriBaseUrl = region.siriBaseUrl.orEmpty(),
            language = region.language.orEmpty(),
            contactEmail = region.contactEmail.orEmpty(),
            supportsObaDiscovery = region.supportsObaDiscoveryApis.toInt(),
            supportsObaRealtime = region.supportsObaRealtimeApis.toInt(),
            supportsSiriRealtime = region.supportsSiriRealtimeApis.toInt(),
            twitterUrl = region.twitterUrl,
            experimental = region.experimental.toInt(),
            stopInfoUrl = region.stopInfoUrl,
            otpBaseUrl = region.otpBaseUrl,
            otpContactEmail = region.otpContactEmail,
            supportsOtpBikeshare = region.supportsOtpBikeshare.toInt(),
            supportsEmbeddedSocial = region.supportsEmbeddedSocial.toInt(),
            paymentAndroidAppId = region.paymentAndroidAppId,
            paymentWarningTitle = region.paymentWarningTitle,
            paymentWarningBody = region.paymentWarningBody,
            sidecarBaseUrl = region.sidecarBaseUrl,
            plausibleAnalyticsServerUrl = region.plausibleAnalyticsServerUrl,
            umamiAnalyticsUrl = region.umamiAnalyticsUrl,
            umamiAnalyticsId = region.umamiAnalyticsId,
        ),
        bounds = region.bounds.map {
            RegionBoundRecord(
                regionId = region.id,
                latitude = it.lat,
                longitude = it.lon,
                latSpan = it.latSpan,
                lonSpan = it.lonSpan,
            )
        },
        open311Servers = region.open311Servers.map {
            Open311ServerRecord(
                regionId = region.id,
                jurisdiction = it.jurisdictionId,
                apiKey = it.apiKey.orEmpty(),
                baseUrl = it.baseUrl.orEmpty(),
            )
        },
    )

    private fun Boolean.toInt(): Int = if (this) 1 else 0
}
