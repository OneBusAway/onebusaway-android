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
 * the local Room region cache ([RegionMapper.toRegion]), and tests (MockRegion).
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
    // OTP 2.x GraphQL endpoint (#1780). A non-blank value is the explicit protocol signal — trip
    // planning routes through the OTP2 `planConnection` path against this URL; null/blank (the
    // default, and every region but the OTP2-enabled ones) stays on OTP1 REST `/plan`. Distinct
    // from [otpBaseUrl] because the GraphQL endpoint is a different host than the OTP1 REST server.
    // Never sniffed/inferred from the URL shape or a failed request.
    val otpBaseGraphqlUrl: String? = null,
    // Whether the region's OTP **2.x GraphQL** server has bike-rental data — the sibling of
    // [supportsOtpBikeshare], which describes the OTP1 REST server only. The directory publishes them
    // separately because they are independent facts about two different hosts, and they genuinely
    // differ: Puget Sound is false + true (its OTP1 `/bike_rental` really is empty, while its OTP2
    // `planConnection` returns rented-bike + Link itineraries); Tampa Bay is the exact mirror.
    // Collapsing them onto one flag is what hid bikeshare trip planning in Seattle.
    val supportsOtpGraphqlBikeshare: Boolean = false,
    val supportsEmbeddedSocial: Boolean = false,
    val paymentAndroidAppId: String? = null,
    val paymentWarningTitle: String? = null,
    val paymentWarningBody: String? = null,
    val sidecarBaseUrl: String? = "",
    val plausibleAnalyticsServerUrl: String? = "",
    val umamiAnalytics: UmamiAnalyticsConfig? = null,
    // A user-added region (the `add-region` deep link, #2027) rather than one from the OBA regions
    // directory. Three things key off it: the cache preserves these rows across a directory refresh,
    // their ids are negative so they can't collide with directory ids, and [resolveRegionStatus] never
    // auto-replaces one — the rider chose this server deliberately, so a closer directory region
    // silently taking over would undo that. Custom regions carry no [bounds], so they are also never
    // auto-*selected* (getClosestRegion can't measure a distance to them).
    val custom: Boolean = false
) {
    val umamiAnalyticsUrl: String? get() = umamiAnalytics?.url
    val umamiAnalyticsId: String? get() = umamiAnalytics?.id

    /**
     * Whether this region's trip planning speaks OTP 2.x GraphQL — true exactly when it publishes an
     * [otpBaseGraphqlUrl]. The single definition of that protocol signal, shared by the request
     * builder's endpoint resolution and the bikeshare capability gate so the two can't disagree about
     * which server a plan will hit.
     */
    val usesOtp2: Boolean get() = !otpBaseGraphqlUrl.isNullOrBlank()

    // A region is an entity identified by [id]: the selection logic and tests compare by id, and
    // structural equality over all fields (incl. the cached bounds/servers) would be wrong here.
    override fun equals(other: Any?): Boolean = other is Region && id == other.id

    override fun hashCode(): Int = id.hashCode()

    /** A single bounding rectangle within the region. */
    data class Bounds(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val latSpan: Double = 0.0,
        val lonSpan: Double = 0.0
    )

    data class Open311Server(
        val jurisdictionId: String? = "",
        val apiKey: String? = "",
        val baseUrl: String? = ""
    ) {
        /** Legacy (misspelled) accessor kept for existing callers. */
        val juridisctionId: String? get() = jurisdictionId
    }

    data class UmamiAnalyticsConfig(val url: String? = null, val id: String? = null)
}
