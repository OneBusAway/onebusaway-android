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
package org.onebusaway.android.ui.nav

import android.net.Uri
import org.onebusaway.android.BuildConfig

/**
 * The one place that knows the **externally reachable** OneBusAway deep-link vocabulary — schemes,
 * hosts, path shape and query-parameter names. It is the same vocabulary OneBusAway for iOS handles, so
 * a link shared from either app opens the same screen (#2027). Two families:
 *
 *  1. **Custom scheme** ([APP_SCHEMES]): `<scheme>://view-stop?stopID=…` and `<scheme>://add-region?…`.
 *  2. **Web links** (iOS "universal links") on [WEB_HOSTS]:
 *     `https://onebusaway.co/regions/{regionID}/stops/{stopID}/trips?trip_id=…`.
 *
 * Every recognized link becomes a [Target]. [parse] is pure — [Link] is an already-decomposed URI with
 * no Android or app-state dependency — so the whole vocabulary is JVM-unit-testable; the `Uri` overload
 * is the thin Android-facing entry point its two callers use ([IntentRouteMapper] to route, and
 * `HomeActivity.applyIntentSideEffects` to run the domain mutation [Target.AddRegion] implies).
 *
 * The manifest's intent-filters are the gate that decides which of these links reach the app at all, and
 * must stay in step with the scheme/host sets here — see `src/main/AndroidManifest.xml` (custom scheme)
 * and `src/oba/AndroidManifest.xml` (web links).
 *
 * Distinct from [DeepLinkUris], the app's *internal* `content://` stop/route vocabulary (pinned launcher
 * shortcuts and in-app launches). `docs/DEEP_LINKING.md` documents this vocabulary for link authors,
 * including which iOS parameters are deliberately recognized-and-ignored here.
 */
object ExternalDeepLinks {

    /** The cross-platform scheme every brand answers to, shared with OneBusAway for iOS. */
    private const val SHARED_SCHEME = "onebusaway"

    /**
     * The custom schemes this build answers to: [SHARED_SCHEME] plus the brand's own
     * (`BuildConfig.DEEP_LINK_SCHEME` — `kiedybus` for KiedyBus, the same string for the OBA brand,
     * derived from the `deepLinkScheme` manifest placeholder so the two can't disagree).
     */
    val APP_SCHEMES: Set<String> = setOf(SHARED_SCHEME, BuildConfig.DEEP_LINK_SCHEME)

    /** Custom-scheme host that opens a stop's arrivals. */
    private const val VIEW_STOP_HOST = "view-stop"

    /** Custom-scheme host that applies custom API URLs. */
    private const val ADD_REGION_HOST = "add-region"

    // Query-parameter names. The custom-scheme links use iOS's camelCase spelling; the web links use
    // the sidecar's snake_case. Only the ones an Android screen consumes are named here — see the
    // recognized-and-ignored list in docs/DEEP_LINKING.md.
    private const val PARAM_STOP_ID = "stopID"
    private const val PARAM_TRIP_ID = "trip_id"
    private const val PARAM_OBA_URL = "oba-url"
    private const val PARAM_OTP_URL = "otp-url"

    /**
     * Hosts whose `https://` links are app links, mirroring the iOS app's associated domains
     * (`applinks:onebusaway.co`, `applinks:www.onebusaway.co`, `applinks:sidecar.onebusaway.org`).
     */
    val WEB_HOSTS = setOf("onebusaway.co", "www.onebusaway.co", "sidecar.onebusaway.org")

    /** An incoming URI, decomposed into the parts [parse] reads. Query values are already decoded. */
    data class Link(
        val scheme: String?,
        val host: String?,
        val pathSegments: List<String>,
        val params: Map<String, String>
    )

    /** What a recognized deep link means. */
    sealed interface Target {
        /** `view-stop`: open [stopId]'s arrivals. */
        data class Stop(val stopId: String) : Target

        /** A web trip link: open [tripId]'s details, scrolled to [stopId] (the stop in the path). */
        data class Trip(val tripId: String, val stopId: String) : Target

        /**
         * `add-region`: apply these custom API URLs. Routes nowhere — it's a domain mutation, run by
         * `HomeActivity.applyIntentSideEffects`; validating the URLs is the region domain's job.
         */
        data class AddRegion(val obaUrl: String?, val otpUrl: String?) : Target
    }

    /** Reads [uri] as a deep link, or null if it isn't one. */
    fun parse(uri: Uri): Target? = parse(uri.toLink())

    /**
     * Maps [link] to what it means, or null if it isn't a recognized deep link.
     *
     * [appSchemes] defaults to this build's [APP_SCHEMES]; tests pass an explicit set to exercise a
     * brand scheme other than the one they were built for.
     */
    fun parse(link: Link, appSchemes: Set<String> = APP_SCHEMES): Target? = when {
        link.scheme in appSchemes -> parseAppSchemeLink(link)
        link.host in WEB_HOSTS -> parseWebLink(link)
        else -> null
    }

    private fun parseAppSchemeLink(link: Link): Target? = when (link.host) {
        VIEW_STOP_HOST -> link.params.nonBlank(PARAM_STOP_ID)?.let { Target.Stop(it) }
        ADD_REGION_HOST -> Target.AddRegion(
            obaUrl = link.params.nonBlank(PARAM_OBA_URL),
            otpUrl = link.params.nonBlank(PARAM_OTP_URL)
        )
        else -> null
    }

    /** `/regions/{regionID}/stops/{stopID}/trips?trip_id=…` — the only recognized web path shape. */
    private fun parseWebLink(link: Link): Target? {
        val (regions, _, stops, stopId, trips) = link.pathSegments.takeIf { it.size == 5 } ?: return null
        if (regions != "regions" || stops != "stops" || trips != "trips" || stopId.isBlank()) return null
        val tripId = link.params.nonBlank(PARAM_TRIP_ID) ?: return null
        return Target.Trip(tripId = tripId, stopId = stopId)
    }

    /** A parameter present with no value reads as `""` (`Uri.getQueryParameter`) — treat it as absent. */
    private fun Map<String, String>.nonBlank(name: String): String? = this[name]?.takeIf { it.isNotBlank() }

    /**
     * Decomposes a data URI into the plain parts [parse] reads.
     *
     * `getQueryParameterNames()`/`getQueryParameter()` throw on an opaque URI (one with no hierarchical
     * part, e.g. `onebusaway:view-stop?…` without the `//`), so [Uri.isHierarchical] guards them.
     */
    private fun Uri.toLink(): Link = Link(
        scheme = scheme,
        host = host,
        pathSegments = pathSegments,
        params = if (isHierarchical) {
            queryParameterNames.associateWith { getQueryParameter(it).orEmpty() }
        } else {
            emptyMap()
        }
    )
}
