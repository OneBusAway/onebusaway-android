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
package org.onebusaway.android.api.adapters

import org.onebusaway.android.api.contract.RegionDto

import org.onebusaway.android.region.Region

/**
 * Maps a wire [RegionDto] to the legacy [Region] (the persistence/domain type the rest of
 * the app — `RegionUtils.saveToProvider`, `RegionRepository`, the picker — consumes as [Region]).
 * Pure and JVM-unit-tested; constructing the existing element keeps the region migration confined to
 * the fetch/decode boundary without touching any [Region] consumer.
 */
fun RegionDto.toObaRegion(): Region = Region(
    id,
    regionName,
    active,
    obaBaseUrl,
    siriBaseUrl,
    bounds.map { Region.Bounds(it.lat, it.lon, it.latSpan, it.lonSpan) }
        .toTypedArray(),
    open311Servers.map { Region.Open311Server(it.jurisdictionId, it.apiKey, it.baseUrl) }
        .toTypedArray(),
    language,
    contactEmail,
    supportsObaDiscoveryApis,
    supportsObaRealtimeApis,
    supportsSiriRealtimeApis,
    twitterUrl,
    experimental,
    stopInfoUrl,
    otpBaseUrl,
    otpContactEmail,
    supportsOtpBikeshare,
    supportsEmbeddedSocial,
    paymentAndroidAppId,
    paymentWarningTitle,
    paymentWarningBody,
    sidecarBaseUrl,
    plausibleAnalyticsServerUrl,
    // Match getRegionsFromProvider: only build a config when something is actually set.
    umamiAnalytics?.takeIf { it.url != null || it.id != null }
        ?.let { Region.UmamiAnalyticsConfig(it.url, it.id) },
)
