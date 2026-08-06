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
import org.onebusaway.android.region.CustomRegionRequest

/**
 * The one place that knows the **externally reachable** OneBusAway deep-link vocabulary — schemes,
 * hosts, path shape and query-parameter names. It is the same vocabulary OneBusAway for iOS handles, so
 * a link shared from either app opens the same screen (#2027). Two families:
 *
 *  1. **Custom scheme** ([APP_SCHEMES]): `<scheme>://view-stop?stopID=…` and
 *     `<scheme>://add-region?name=…&oba-url=…`.
 *  2. **Web links** (iOS "universal links") on [WEB_HOSTS]:
 *     `https://onebusaway.co/regions/{regionID}/stops/{stopID}/trips?trip_id=…`.
 *
 * Every recognized link becomes a [Target]. [parse] is pure — [Link] is an already-decomposed URI with
 * no Android or app-state dependency — so the whole vocabulary is JVM-unit-testable; the `Uri` overloads
 * are the thin Android-facing entry points its callers use ([IntentRouteMapper] to route, and
 * `HomeActivity.applyIntentSideEffects` to run the domain mutation [Target.AddRegion] implies and to
 * hand [isUnhandledWebLink] URLs back to the browser).
 *
 * The manifest's intent-filters are the gate that decides which of these links reach the app at all, and
 * must stay in step with the scheme/host sets here — see `src/main/AndroidManifest.xml` (custom scheme)
 * and `src/oba/AndroidManifest.xml` (web links). The web filter is necessarily *broader* than [parse]
 * (see [isUnhandledWebLink]); the custom-scheme filter matches it exactly.
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

    /** Custom-scheme host that adds a user-supplied region and switches to it. */
    private const val ADD_REGION_HOST = "add-region"

    // Query-parameter names. The custom-scheme links use iOS's camelCase spelling; the web links use
    // the sidecar's snake_case. Only the ones an Android screen consumes are named here — see the
    // recognized-and-ignored list in docs/DEEP_LINKING.md.
    private const val PARAM_STOP_ID = "stopID"
    private const val PARAM_TRIP_ID = "trip_id"
    private const val PARAM_NAME = "name"
    private const val PARAM_OBA_URL = "oba-url"
    private const val PARAM_OTP_URL = "otp-url"
    private const val PARAM_SIDECAR_URL = "sidecar-url"
    private const val PARAM_UMAMI_URL = "umami-url"
    private const val PARAM_UMAMI_ID = "umami-id"
    private const val PARAM_REGION_ID = "region-id"

    /** App links are `https` only — a plaintext link to one of [WEB_HOSTS] is not a deep link. */
    private const val WEB_SCHEME = "https"

    /**
     * Hosts whose [WEB_SCHEME] links are app links, mirroring the iOS app's associated domains
     * (`applinks:onebusaway.co`, `applinks:www.onebusaway.co`, `applinks:sidecar.onebusaway.org`).
     *
     * These are the OneBusAway deployment's own hosts, and deliberately not a per-brand value: only a
     * brand that owns a host can verify it (App Links verification matches the *installed* app's signing
     * certificate), so the matching intent-filter lives in `src/oba/AndroidManifest.xml` alone. A brand
     * wanting web links of its own needs both a flavor manifest and a host set — that's a real feature,
     * not a config tweak, so it isn't half-wired here. See `docs/DEEP_LINKING.md`.
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
         * `add-region`: add [request] as a custom region and switch to it. Routes nowhere — it's a
         * domain mutation, and one that repoints every API the app talks to, so
         * `HomeActivity.applyIntentSideEffects` hands it to `AddRegionViewModel` to confirm with the
         * rider first (#2030) rather than applying it. Validating the URLs is the region domain's job.
         */
        data class AddRegion(val request: CustomRegionRequest) : Target
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
        link.isWebLink -> parseWebLink(link)
        else -> null
    }

    /** On one of [WEB_HOSTS] over [WEB_SCHEME] — the shared precondition of the two web-link readers. */
    private val Link.isWebLink: Boolean get() = scheme == WEB_SCHEME && host in WEB_HOSTS

    private fun parseAppSchemeLink(link: Link): Target? = when (link.host) {
        VIEW_STOP_HOST -> link.params.nonBlank(PARAM_STOP_ID)?.let { Target.Stop(it) }
        ADD_REGION_HOST -> parseAddRegionLink(link)
        else -> null
    }

    /**
     * `add-region?name=…&oba-url=…` — the rest is optional.
     *
     * Both `name` and `oba-url` are **required**, matching OneBusAway for iOS: the result is a named
     * region the rider will later pick out of a list, so a nameless one has nothing to show, and a
     * region with no OBA server can't answer anything. A link missing either is not a deep link.
     *
     * Note this is stricter than the pre-#2027 Android behaviour, where `add-region?oba-url=…` alone set
     * a pair of API-URL preferences. Such a link no longer resolves — see `docs/DEEP_LINKING.md`.
     *
     * `region-id` (#2165) is optional on both ends — the sidecar only started emitting it — so a link
     * without it, or with one that isn't a number, parses exactly as it did before rather than being
     * rejected: the region is still perfectly usable, it just can't reach the sidecar's region-scoped
     * endpoints.
     */
    private fun parseAddRegionLink(link: Link): Target? {
        val name = link.params.nonBlank(PARAM_NAME) ?: return null
        val obaUrl = link.params.nonBlank(PARAM_OBA_URL) ?: return null
        return Target.AddRegion(
            CustomRegionRequest(
                name = name,
                obaBaseUrl = obaUrl,
                otpBaseUrl = link.params.nonBlank(PARAM_OTP_URL),
                sidecarBaseUrl = link.params.nonBlank(PARAM_SIDECAR_URL),
                umamiAnalyticsUrl = link.params.nonBlank(PARAM_UMAMI_URL),
                umamiAnalyticsId = link.params.nonBlank(PARAM_UMAMI_ID),
                regionId = link.params.nonBlank(PARAM_REGION_ID)?.toLongOrNull()
            )
        )
    }

    /**
     * True when [uri] is a link the web intent-filter claims but [parse] can't turn into a screen.
     *
     * That gap is unavoidable, not a bug to close in the manifest: an intent-filter can't see the query
     * string at all (so it can't require `trip_id`), and `pathPattern` is a `PATTERN_SIMPLE_GLOB` whose
     * `.*` spans `/` (so it can't require exactly one segment per wildcard; `pathAdvancedPattern` would,
     * but it is API 31+ and `minSdk` is 23). The filter is therefore a superset of this parser, and the
     * app can be launched for a URL that routes nowhere. `HomeActivity.applyIntentSideEffects` hands
     * those back to the browser rather than silently dropping the user on the map.
     *
     * Custom-scheme links are deliberately excluded: their filter matches [parse] exactly (scheme × host,
     * no path or query involved), nothing else on the device would handle a `onebusaway://` URL, and
     * they are not web pages — an unrecognized one is a dead link either way, so home/map is the right
     * landing.
     */
    fun isUnhandledWebLink(uri: Uri): Boolean = isUnhandledWebLink(uri.toLink())

    /** @see isUnhandledWebLink */
    fun isUnhandledWebLink(link: Link): Boolean = link.scheme == WEB_SCHEME &&
        link.host in WEB_HOSTS &&
        isClaimedWebPath(link.pathSegments) &&
        parseWebLink(link) == null

    /**
     * Whether the web intent-filter's `pathPattern` would match [pathSegments] — i.e. whether this app
     * actually claims the URL. Must stay in step with `src/oba/AndroidManifest.xml`, like [WEB_HOSTS] does.
     *
     * An exact translation of `/regions/.*` + `/stops/.*` + `/trips` under `PATTERN_SIMPLE_GLOB`, where
     * `.*` spans `/`: first segment `regions`, last segment `trips`, and a `stops` segment in between with
     * at least one segment either side of it (each `.*` stands where a decoded path segment must exist).
     *
     * Without this, *every* `https` URL on an OBA host counted as "claimed", so an explicit intent for a
     * URL the filter never claimed — `https://onebusaway.co/about`, say — would have been bounced out to a
     * browser instead of simply opening the app.
     */
    private fun isClaimedWebPath(pathSegments: List<String>): Boolean {
        if (pathSegments.size < 5) return false
        if (pathSegments.first() != "regions" || pathSegments.last() != "trips") return false
        return (2..pathSegments.size - 3).any { pathSegments[it] == "stops" }
    }

    /** `/regions/{regionID}/stops/{stopID}/trips?trip_id=…` — the only recognized web path shape. */
    private fun parseWebLink(link: Link): Target? {
        // `Uri.getPathSegments()` drops empty segments, so a real URI can't produce a blank stopId; the
        // check guards the directly-constructed [Link]s that [parse]'s pure entry point also accepts.
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
     * Scheme and host are lowercased (locale-independently), because they are case-*insensitive* per
     * RFC 3986 while `Uri` reports them verbatim and this parser compares them by equality. The
     * intent-filter that let the URI in matched case-insensitively too, so without this a
     * `HTTPS://OneBusAway.co/…` link would clear the filter and then fail to parse.
     *
     * `getQueryParameterNames()`/`getQueryParameter()` throw on an opaque URI (one with no hierarchical
     * part, e.g. `onebusaway:view-stop?…` without the `//`), so [Uri.isHierarchical] guards them.
     */
    private fun Uri.toLink(): Link = Link(
        scheme = scheme?.lowercase(),
        host = host?.lowercase(),
        pathSegments = pathSegments,
        params = if (isHierarchical) {
            queryParameterNames.associateWith { getQueryParameter(it).orEmpty() }
        } else {
            emptyMap()
        }
    )
}
