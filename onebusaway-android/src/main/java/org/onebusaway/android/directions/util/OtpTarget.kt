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
import android.util.Log
import androidx.core.net.toUri
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.Region

/**
 * The OTP server a trip-plan request targets: its [baseUrl] and whether it speaks OTP 2.x GraphQL.
 *
 * Lifted out of [TripRequestBuilder] so the callers that need the protocol answer share one
 * definition: the builder itself (which endpoint to hit, and which request shape to build), the
 * advanced-settings UI (which street preferences are even expressible — OTP2 has no
 * `maxWalkDistance`, OTP1 has no cycling-optimization knob that doesn't collide with `optimize`), and
 * the gates that decide whether to offer trip planning at all ([isAvailable]). Duplicating the
 * resolution would let the dialog, the offer and the request disagree about which server the plan is
 * going to.
 */
data class OtpTarget(
    val baseUrl: String?,
    val usesOtp2: Boolean,
    /**
     * Whether a region was selected at all when this target was resolved. Only consulted when no
     * [baseUrl] resolved, to tell the two very different "can't plan" situations apart — see
     * [unavailable].
     */
    val regionSelected: Boolean
) {

    /**
     * Whether a trip plan can be attempted at all: some OTP server — the region's or a custom one — is
     * configured for this device. The single gate every trip-planning affordance asks, so the drawer
     * row, the map's "navigate here" offer and the request itself can't disagree about whether this
     * rider has a planner (#2264).
     */
    val isAvailable: Boolean get() = usableBaseUrl != null

    /**
     * [baseUrl] if there is actually a server in it, else null — blank is "no server" exactly as null
     * is, and a region cached with an empty `otpBaseUrl` is the way blank arrives. The one place that
     * rule lives: the request path reads this rather than re-deriving it, so it cannot drift from
     * [isAvailable].
     */
    val usableBaseUrl: String? get() = baseUrl?.takeUnless { it.isBlank() }

    /** Why no plan can be attempted, or null exactly when [isAvailable]. */
    val unavailable: Unavailable? get() = when {
        isAvailable -> null
        regionSelected -> Unavailable.REGION_HAS_NO_PLANNER
        else -> Unavailable.NO_REGION
    }

    /**
     * The two reasons a device has no planner to ask, which read as completely different things to a
     * rider: [NO_REGION] means the app doesn't yet know which transit system they are in, while
     * [REGION_HAS_NO_PLANNER] means it does and that system simply publishes no trip planner — the
     * case that made Washington, D.C. report "No region selected" with D.C. plainly selected (#2264).
     */
    enum class Unavailable { NO_REGION, REGION_HAS_NO_PLANNER }

    companion object {

        private const val TAG = "OtpTarget"

        /**
         * Resolves the custom-URL-or-region branch once so a caller's base URL and its protocol
         * can't disagree (#1780), reading both from the app-global seams.
         */
        fun resolve(context: Context): OtpTarget {
            val appContext = context.applicationContext
            val prefs = PreferencesEntryPoint.get(appContext)
            val customUrl = customOtpApiUrl(prefs)
            if (customUrl != null) {
                // Host only, never the whole URL: a hand-entered one can carry credentials in its
                // userinfo or a token in its query, and Log.d is not stripped from release builds.
                // The host is the part that actually answers "which server am I talking to?".
                Log.d(TAG, "Using custom OTP API URL set by user (host: ${customUrl.toUri().host ?: "unparsed"}).")
            }
            return resolve(
                customUrl = customUrl,
                // No [Region] to carry the setting for a custom server, so the user sets it.
                customUrlUsesOtp2 = prefs.getBoolean(R.string.preference_key_otp_api_url_is_graphql, false),
                region = RegionEntryPoint.get(appContext).currentRegion()
            )
        }

        /**
         * The resolution itself, over values a caller already holds — `Context`-free so it is
         * JVM-unit-testable and so the nav drawer's "offer trip planning?" gate can reach the same
         * answer from its injected region/preference seams instead of restating the branch.
         *
         * Protocol selection is explicit — a custom server's manual `..._is_graphql` preference, or a
         * region publishing an `otpBaseGraphqlUrl` — never sniffed from the URL shape or a failed
         * request. [baseUrl] is null when neither a custom URL nor a planner-carrying region is
         * available.
         */
        fun resolve(customUrl: String?, customUrlUsesOtp2: Boolean, region: Region?): OtpTarget {
            if (customUrl != null) {
                return OtpTarget(customUrl, usesOtp2 = customUrlUsesOtp2, regionSelected = region != null)
            }
            // No custom URL and no selected region: baseUrl stays null so the caller
            // (TripPlanRepository) surfaces a "no server selected" error instead of crashing.
            region ?: return OtpTarget(null, usesOtp2 = false, regionSelected = false)
            // An OTP2 region publishes its GraphQL endpoint separately (a different host than the
            // OTP1 REST server); route to it when present, else the OTP1 REST base URL. Reads
            // [org.onebusaway.android.region.Region.usesOtp2] rather than re-deriving it, so a
            // region's endpoint and its per-protocol capability flags are resolved from one
            // definition. A region that publishes neither leaves baseUrl null with [regionSelected]
            // true — that is [Unavailable.REGION_HAS_NO_PLANNER]. Which regions those are is
            // directory data that changes without this repo touching anything; CLAUDE.md carries the
            // current list, deliberately in one place.
            return OtpTarget(
                baseUrl = if (region.usesOtp2) region.otpBaseGraphqlUrl else region.otpBaseUrl,
                usesOtp2 = region.usesOtp2,
                regionSelected = true
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
        fun customOtpApiUrl(prefs: PreferencesRepository): String? = prefs
            .getString(R.string.preference_key_otp_api_url, null as String?)
            ?.trim()
            ?.takeUnless { it.isEmpty() }
    }
}
