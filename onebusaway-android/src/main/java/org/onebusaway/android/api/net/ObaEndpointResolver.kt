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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * Resolves the OBA REST endpoint and per-request identity for [ObaApiProvider] (the base URL) and
 * [ApiParamsInterceptor] (the key + app identifiers), reading the active region from
 * [RegionRepository] (and a user-entered custom API URL from [PreferencesRepository]). This is the
 * single source of truth for "which host + key + app identifiers does a request get".
 */
@Singleton
class ObaEndpointResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val preferences: PreferencesRepository,
) {

    /**
     * The base endpoint (scheme + authority + any partial path) for OBA REST requests: a
     * user-entered custom API URL if present, otherwise the active region's base URL, or null if
     * neither is set. A scheme-less custom URL is assumed to be https (#126).
     */
    fun baseUrl(): Uri? {
        val custom = preferences.getString(R.string.preference_key_oba_api_url, null)
        val raw = custom?.takeIf { it.isNotEmpty() } ?: regionRepository.region.value?.obaBaseUrl
        ?: return null
        // A scheme-less custom URL is assumed to be https (#126).
        val withScheme = if (Uri.parse(raw).scheme != null) raw
        else context.getString(R.string.https_prefix) + raw
        return Uri.parse(withScheme)
    }

    /** The OBA API key appended to every request. */
    val apiKey: String get() = ObaApi.API_KEY

    /** The app version code (`app_ver`) — a build constant, so no per-request lookup. */
    val appVersion: Int get() = BuildConfig.VERSION_CODE

    /** The persisted per-install app UID (`app_uid`), generated once at app startup. Invariant per
     * process, so read once at construction rather than on every request. */
    val appUid: String? = preferences.getString(ObaApi.APP_UID, null)
}
