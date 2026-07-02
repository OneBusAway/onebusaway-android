/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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

/**
 * A region in the OneBusAway multi-region system: the OBA/SIRI/OTP/sidecar endpoints and
 * capabilities for one deployment. Built from the regions-directory wire (RegionDto.toObaRegion),
 * the local ContentProvider cache ([RegionCursor.fromCursor]), and tests (MockRegion).
 *
 * Was the io/elements `ObaRegion` interface + its sole `ObaRegionElement` implementation; collapsed
 * into one data class since there was never a second implementation.
 */
data class Region(
    val id: Long = 0,
    val name: String = "",
    val active: Boolean = false,
    val obaBaseUrl: String? = null,
    val siriBaseUrl: String? = null,
    val bounds: Array<Bounds> = emptyArray(),
    val open311Servers: Array<Open311Server> = emptyArray(),
    val language: String? = "",
    val contactEmail: String? = "",
    val supportsObaDiscoveryApis: Boolean = false,
    val supportsObaRealtimeApis: Boolean = false,
    val supportsSiriRealtimeApis: Boolean = false,
    val twitterUrl: String? = "",
    val experimental: Boolean = true,
    val stopInfoUrl: String? = "",
    val otpBaseUrl: String? = "",
    val otpContactEmail: String? = "",
    val supportsOtpBikeshare: Boolean = false,
    val supportsEmbeddedSocial: Boolean = false,
    val paymentAndroidAppId: String? = null,
    val paymentWarningTitle: String? = null,
    val paymentWarningBody: String? = null,
    val sidecarBaseUrl: String? = "",
    val plausibleAnalyticsServerUrl: String? = "",
    val umamiAnalytics: UmamiAnalyticsConfig? = null,
) {
    val umamiAnalyticsUrl: String? get() = umamiAnalytics?.url
    val umamiAnalyticsId: String? get() = umamiAnalytics?.id

    // A region is an entity identified by [id]: the selection logic and tests compare by id, and
    // structural equality over all fields (incl. the cached bounds/servers) would be wrong here.
    override fun equals(other: Any?): Boolean = other is Region && id == other.id

    override fun hashCode(): Int = id.hashCode()

    /** A single bounding rectangle within the region. */
    data class Bounds(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val latSpan: Double = 0.0,
        val lonSpan: Double = 0.0,
    )

    data class Open311Server(
        val jurisdictionId: String? = "",
        val apiKey: String? = "",
        val baseUrl: String? = "",
    ) {
        /** Legacy (misspelled) accessor kept for existing callers. */
        val juridisctionId: String? get() = jurisdictionId
    }

    data class UmamiAnalyticsConfig(val url: String? = null, val id: String? = null)
}
