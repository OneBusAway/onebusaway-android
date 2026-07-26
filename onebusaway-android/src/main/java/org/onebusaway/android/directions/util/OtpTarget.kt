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
package org.onebusaway.android.directions.util

import android.content.Context
import android.net.Uri
import android.util.Log
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint

/**
 * The OTP server a trip-plan request targets: its [baseUrl] and whether it speaks OTP 2.x GraphQL.
 *
 * Lifted out of [TripRequestBuilder] so the two callers that need the protocol answer share one
 * definition: the builder itself (which endpoint to hit, and which request shape to build) and the
 * advanced-settings UI (which street preferences are even expressible — OTP2 has no
 * `maxWalkDistance`, OTP1 has no cycling-optimization knob that doesn't collide with `optimize`).
 * Duplicating the resolution in the UI would let the dialog and the request disagree about which
 * server the plan is going to.
 */
data class OtpTarget(val baseUrl: String?, val usesOtp2: Boolean) {

    companion object {

        private const val TAG = "OtpTarget"

        /**
         * Resolves the custom-URL-or-region branch once so a caller's base URL and its protocol
         * can't disagree (#1780). Protocol selection is explicit — a custom server's manual
         * `..._is_graphql` preference, or a region publishing an `otpBaseGraphqlUrl` — never sniffed
         * from the URL shape or a failed request. [baseUrl] is null when neither a custom URL nor a
         * region is available.
         */
        fun resolve(context: Context): OtpTarget {
            val appContext = context.applicationContext
            val customUrl = customOtpApiUrl(appContext)
            if (customUrl != null) {
                // Host only, never the whole URL: a hand-entered one can carry credentials in its
                // userinfo or a token in its query, and Log.d is not stripped from release builds.
                // The host is the part that actually answers "which server am I talking to?".
                Log.d(TAG, "Using custom OTP API URL set by user (host: ${Uri.parse(customUrl).host ?: "unparsed"}).")
                // No [Region] to carry the setting for a custom server, so the user sets it.
                return OtpTarget(
                    baseUrl = customUrl,
                    usesOtp2 = PreferencesEntryPoint.get(appContext)
                        .getBoolean(R.string.preference_key_otp_api_url_is_graphql, false)
                )
            }
            // No custom URL and no selected region: baseUrl stays null so the caller
            // (TripPlanRepository) surfaces a "no server selected" error instead of crashing.
            val region = RegionEntryPoint.get(appContext).currentRegion() ?: return OtpTarget(null, false)
            // An OTP2 region publishes its GraphQL endpoint separately (a different host than the
            // OTP1 REST server); route to it when present, else the OTP1 REST base URL. Reads
            // [org.onebusaway.android.region.Region.usesOtp2] rather than re-deriving it, so a
            // region's endpoint and its per-protocol capability flags are resolved from one
            // definition.
            return OtpTarget(
                baseUrl = if (region.usesOtp2) region.otpBaseGraphqlUrl else region.otpBaseUrl,
                usesOtp2 = region.usesOtp2
            )
        }

        /**
         * The user's custom OTP API URL preference, or null if unset/blank — the "is a custom server
         * configured" signal [resolve] branches on.
         *
         * Trimmed so the code matches that description: a whitespace-only value would otherwise read
         * as a configured server, suppressing region-based selection entirely and then failing every
         * request. Defence in depth rather than a live bug — the settings screen already trims before
         * storing (`AdvancedSettingsViewModel.onCustomOtpApiUrlChanged`) — but this is the reader that
         * decides which server the app talks to, so it should not depend on every writer behaving.
         */
        private fun customOtpApiUrl(context: Context): String? = PreferencesEntryPoint.get(context)
            .getString(context.getString(R.string.preference_key_otp_api_url), null as String?)
            ?.trim()
            ?.takeUnless { it.isEmpty() }
    }
}
