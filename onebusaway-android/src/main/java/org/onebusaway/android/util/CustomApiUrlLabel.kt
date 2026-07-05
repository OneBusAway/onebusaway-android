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
package org.onebusaway.android.util

import android.content.Context
import java.security.MessageDigest
import org.onebusaway.android.R

/**
 * Builds the analytics region label for a custom API URL: the `analytics_label_custom_url` string plus
 * a SHA-1 hash of the URL (so the custom endpoint is identifiable without logging the raw URL), or just
 * the label if hashing fails.
 *
 * This is only the label *construction*. Both `RegionUtils.getObaRegionName` and
 * `Application.reportAnalytics` build this exact label, but they deliberately differ on *when* to use
 * it: `reportAnalytics` prefers the custom URL over a region, while `getObaRegionName` prefers the
 * region name. So the two are not interchangeable — don't "unify" them by routing one through the other.
 */
object CustomApiUrlLabel {

    @JvmStatic
    fun forUrl(context: Context, customApiUrl: String): String = try {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(customApiUrl.toByteArray())
            .toHexString()
        context.getString(R.string.analytics_label_custom_url) + ": " + hash
    } catch (e: Exception) {
        context.getString(R.string.analytics_label_custom_url)
    }
}
