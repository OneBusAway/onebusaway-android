/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation,
 * 2026 Aaron Brethorst
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
package org.onebusaway.android.io.elements

data class ObaRegion(
    val id: Long = 0,
    @get:JvmName("getName") val regionName: String = "",
    val active: Boolean = false,
    val obaBaseUrl: String? = null,
    val sidecarBaseUrl: String? = null,
    val plausibleAnalyticsServerUrl: String? = null,
    val siriBaseUrl: String? = null,
    val bounds: Array<Bounds>? = null,
    val open311Servers: Array<Open311Server>? = null,
    val language: String? = null,
    val contactEmail: String? = null,
    val supportsObaDiscoveryApis: Boolean = false,
    val supportsObaRealtimeApis: Boolean = false,
    val supportsSiriRealtimeApis: Boolean = false,
    val twitterUrl: String? = null,
    val experimental: Boolean = true,
    val stopInfoUrl: String? = null,
    val otpBaseUrl: String? = null,
    val otpContactEmail: String? = null,
    val supportsOtpBikeshare: Boolean = false,
    @Deprecated("Embedded Social is no longer supported")
    val supportsEmbeddedSocial: Boolean = false,
    val paymentAndroidAppId: String? = null,
    val paymentWarningTitle: String? = null,
    val paymentWarningBody: String? = null,
    @get:JvmName("isTravelBehaviorDataCollectionEnabled")
    val travelBehaviorDataCollectionEnabled: Boolean = false,
    @get:JvmName("isEnrollParticipantsInStudy")
    val enrollParticipantsInStudy: Boolean = false,
) {
    data class Bounds(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val latSpan: Double = 0.0,
        val lonSpan: Double = 0.0,
    ) {
        companion object {
            @JvmField val EMPTY_ARRAY = emptyArray<Bounds>()
        }
    }

    data class Open311Server(
        val jurisdictionId: String? = null,
        val apiKey: String? = null,
        val baseUrl: String? = null,
    ) {
        fun getJuridisctionId(): String? = jurisdictionId

        companion object {
            @JvmField val EMPTY_ARRAY = emptyArray<Open311Server>()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObaRegion) return false
        return id == other.id
    }

    override fun hashCode(): Int = 31 + if (id == 0L) 0 else id.hashCode()

    companion object {
        @JvmField val EMPTY_ARRAY = emptyArray<ObaRegion>()
    }
}
