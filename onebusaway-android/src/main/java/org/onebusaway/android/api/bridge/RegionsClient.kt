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
package org.onebusaway.android.api.bridge

import org.onebusaway.android.api.adapters.toObaRegion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.onebusaway.android.R
import org.onebusaway.android.app.di.NetworkEntryPoint
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.RegionDto
import org.onebusaway.android.api.requireData
import org.onebusaway.android.region.Region

/**
 * Java-callable bridge over the modernized [RegionsWebService] for `RegionUtils` (a static util that
 * can't be injected). Both helpers run the fetch/decode and map each [RegionDto] to the legacy
 * [Region] the rest of the app consumes, returning an empty list on any failure so
 * `RegionUtils.getRegions`' existing source-fallback chain (server -> provider -> bundled) is
 * preserved unchanged. Callers are already on a background thread, so blocking here is expected.
 */
object RegionsClient {

    private const val TAG = "RegionsClient"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Fetches the live regions directory from the fixed regions host; empty list on failure. */
    @JvmStatic
    fun fetchRegionsFromServer(context: Context): List<Region> = runCatching {
        val url = context.getString(R.string.regions_api_url)
        val service = NetworkEntryPoint.getRegions(context)
        runBlocking { service.getRegions(url) }.requireData().list.map { it.toObaRegion() }
    }.getOrElse {
        Log.e(TAG, "Failed to fetch regions from server: $it")
        emptyList()
    }

    /** Parses the bundled offline fail-safe regions file (R.raw.regions_v3); empty list on failure. */
    @JvmStatic
    fun parseBundledRegions(context: Context): List<Region> = runCatching {
        val body = context.resources.openRawResource(R.raw.regions_v3)
            .bufferedReader().use { it.readText() }
        val envelope = json.decodeFromString<ObaEnvelope<ListWithReferences<RegionDto>>>(body)
        envelope.requireData().list.map { it.toObaRegion() }
    }.getOrElse {
        Log.e(TAG, "Failed to parse bundled regions: $it")
        emptyList()
    }
}
