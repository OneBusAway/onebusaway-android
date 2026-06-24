/*
 * Copyright (C) 2010-2017 Brian Ferris (bdferris@onebusaway.org),
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

import android.util.Patterns
import java.net.MalformedURLException
import java.net.URL

/**
 * Stateless validator for a custom OBA/OTP API URL — the rule for "is this a usable region API URL".
 * Used by the region domain ([RegionRepository.applyCustomApiUrls], applying the `add-region` deep
 * link) and by the advanced settings screen
 * ([org.onebusaway.android.ui.settings.AdvancedSettingsViewModel]) for live input validation. It lives
 * in the neutral `region` package so both can depend on it without a `region` → `ui` back-dependency.
 */
object ApiUrlValidator {

    fun validateUrl(apiUrl: String): Boolean {
        val url = if (!apiUrl.startsWith("http")) "https://$apiUrl" else apiUrl
        return try {
            if (URL(url).host == "localhost") {
                true
            } else {
                Patterns.WEB_URL.matcher(url).matches()
            }
        } catch (e: MalformedURLException) {
            false
        }
    }
}
