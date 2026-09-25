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
package org.onebusaway.android.api.net

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository

/**
 * Resolves the OBA REST endpoint and per-request identity for [ObaApiProvider] (the base URL) and
 * [ApiParamsInterceptor] (the key + app identifiers), reading the active region from
 * [RegionRepository] (and a user-entered custom API URL from [PreferencesRepository]). This is the
 * single source of truth for "which host + key + app identifiers does a request get".
 */
@Singleton
class ObaEndpointResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val preferences: PreferencesRepository
) {

    /**
     * The base endpoint (scheme + authority + any partial path) for OBA REST requests: a
     * user-entered custom API URL if present, otherwise the active region's base URL, or null if
     * neither is set. A scheme-less custom URL is assumed to be https (#126).
     */
    fun baseUrl(): Uri? {
        val raw = obaEndpoint(
            customApiUrl = preferences.getString(R.string.preference_key_oba_api_url, null),
            region = regionRepository.region.value
        ) ?: return null
        // A scheme-less custom URL is assumed to be https (#126).
        val withScheme = if (raw.toUri().scheme != null) {
            raw
        } else {
            context.getString(R.string.https_prefix) + raw
        }
        return withScheme.toUri()
    }

    /** The OBA API key appended to every request. */
    val apiKey: String get() = ObaApi.API_KEY

    /** The app version code (`app_ver`) — a build constant, so no per-request lookup. */
    val appVersion: Int get() = BuildConfig.VERSION_CODE

    /** The persisted per-install app UID (`app_uid`), seeded eagerly in `Application.onCreate` (it has
     * other direct readers too, e.g. the Open311 report path). Invariant per process, so read once at
     * construction rather than on every request. */
    val appUid: String? = preferences.getString(ObaApi.APP_UID, null)
}

/**
 * The OBA endpoint requests actually go to, as entered: a non-empty user-entered custom API URL ahead
 * of [region]'s base URL, or null when neither is set. [ObaEndpointResolver.baseUrl] builds on it; a
 * per-deployment verdict (such as [org.onebusaway.android.api.data.OnDemandSupport]'s) keys on it so it
 * names the server that answered.
 */
fun obaEndpoint(customApiUrl: String?, region: Region?): String? = customApiUrl?.takeIf { it.isNotEmpty() } ?: region?.obaBaseUrl
