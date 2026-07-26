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

/**
 * The **cross-platform** OneBusAway deep-link vocabulary — the same links OneBusAway for iOS handles,
 * so a link shared from either app opens the same screen (#2027). Two families:
 *
 *  1. **Custom scheme** (`onebusaway://`, plus the brand's own scheme — see `BuildConfig.DEEP_LINK_SCHEME`):
 *     - `onebusaway://view-stop?stopID=1_75403&regionID=1` → that stop's arrivals.
 *     - `onebusaway://add-region?...` → applies custom API URLs. Handled separately (it's a side effect,
 *       not a route): see [IntentRouteMapper.ADD_REGION_HOST] and `HomeActivity.applyIntentSideEffects`.
 *  2. **Web links** (iOS "universal links") on [WEB_HOSTS]:
 *     - `https://onebusaway.co/regions/{regionID}/stops/{stopID}/trips?trip_id=…` → that trip's details.
 *
 * This object is the *pure* parser: it maps an already-decomposed URI ([Link]) to the [Target] screen,
 * with no Android or app-state dependency, so the whole vocabulary is JVM-unit-testable.
 * [IntentRouteMapper.read] does the `Uri` decomposition and feeds the [Target] into the route decision.
 *
 * Distinct from [DeepLinkUris], which is the app's *internal* `content://` stop/route vocabulary
 * (pinned launcher shortcuts and in-app launches).
 *
 * **Divergences from iOS, all deliberate:**
 *  - Parameters iOS carries that no Android screen consumes are recognized and ignored rather than
 *    required: `regionID` (iOS parses it but also only ever searches the current region), and the trip
 *    link's `service_date` / `stop_sequence` / `title` / `vehicle_id` / `destination_stop_id`. A link
 *    is accepted whenever it carries the ids the target screen actually needs, so every link iOS
 *    accepts is accepted here too.
 *  - Like iOS, a *stop-only* web path (`/regions/{id}/stops/{id}` with no `/trips`) is **not** a deep
 *    link; it falls through to the browser.
 */
object ExternalDeepLinks {

    /** Custom-scheme host that opens a stop's arrivals: `<app-scheme>://view-stop`. */
    const val VIEW_STOP_HOST = "view-stop"

    /** `view-stop` query parameters (iOS spelling — camelCase, unlike the web links' snake_case). */
    const val PARAM_STOP_ID = "stopID"

    /** Recognized for cross-platform link compatibility; unused (see the class doc's divergences). */
    const val PARAM_REGION_ID = "regionID"

    /**
     * Hosts whose `https://` links are app links. Mirrors the iOS app's associated domains
     * (`applinks:onebusaway.co`, `applinks:www.onebusaway.co`, `applinks:sidecar.onebusaway.org`); the
     * manifest intent-filter must list the same set.
     */
    val WEB_HOSTS = setOf("onebusaway.co", "www.onebusaway.co", "sidecar.onebusaway.org")

    // Web trip-link path shape: /regions/{regionID}/stops/{stopID}/trips
    private const val REGIONS_SEGMENT = "regions"
    private const val STOPS_SEGMENT = "stops"
    private const val TRIPS_SEGMENT = "trips"
    private const val TRIP_PATH_LENGTH = 5
    private const val STOP_ID_INDEX = 3

    /** The web trip link's trip id — the only one of its parameters an Android screen consumes. */
    const val PARAM_TRIP_ID = "trip_id"

    /** An incoming URI, decomposed into the parts [parse] reads. Query values are already decoded. */
    data class Link(
        val scheme: String?,
        val host: String?,
        val pathSegments: List<String>,
        val params: Map<String, String>
    )

    /** The screen a recognized deep link targets. */
    sealed interface Target {
        /** `view-stop`: open [stopId]'s arrivals. */
        data class Stop(val stopId: String) : Target

        /** A web trip link: open [tripId]'s details, scrolled to [stopId] (the stop in the path). */
        data class Trip(val tripId: String, val stopId: String) : Target
    }

    /**
     * Maps [link] to the screen it targets, or null if it isn't a recognized deep link (including the
     * `add-region` link, which routes nowhere).
     *
     * [appSchemes] is the set of custom schemes this build answers to — `onebusaway` plus the brand's
     * own scheme. Passed in rather than read from `BuildConfig` to keep this parser pure.
     */
    fun parse(link: Link, appSchemes: Set<String>): Target? = when {
        link.scheme in appSchemes -> parseAppSchemeLink(link)
        link.host in WEB_HOSTS -> parseWebLink(link)
        else -> null
    }

    private fun parseAppSchemeLink(link: Link): Target? = when (link.host) {
        VIEW_STOP_HOST -> link.params[PARAM_STOP_ID]?.takeIf { it.isNotBlank() }?.let { Target.Stop(it) }
        else -> null
    }

    /** `/regions/{regionID}/stops/{stopID}/trips?trip_id=…` — the only recognized web path shape. */
    private fun parseWebLink(link: Link): Target? {
        val segments = link.pathSegments
        if (segments.size != TRIP_PATH_LENGTH) return null
        if (segments[0] != REGIONS_SEGMENT ||
            segments[2] != STOPS_SEGMENT ||
            segments[4] != TRIPS_SEGMENT
        ) {
            return null
        }
        val stopId = segments[STOP_ID_INDEX].takeIf { it.isNotBlank() } ?: return null
        val tripId = link.params[PARAM_TRIP_ID]?.takeIf { it.isNotBlank() } ?: return null
        return Target.Trip(tripId = tripId, stopId = stopId)
    }
}
