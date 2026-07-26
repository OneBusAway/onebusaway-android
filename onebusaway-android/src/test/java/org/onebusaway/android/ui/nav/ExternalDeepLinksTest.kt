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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.region.CustomRegionRequest
import org.onebusaway.android.ui.nav.ExternalDeepLinks.Link
import org.onebusaway.android.ui.nav.ExternalDeepLinks.Target
import org.onebusaway.android.ui.nav.ExternalDeepLinks.parse

/**
 * Unit tests for the cross-platform (iOS-parity) deep-link parser (#2027). These exercise the pure
 * vocabulary — which links are recognized, which are deliberately not, and what each maps to. The
 * `Uri` decomposition that feeds it (`ExternalDeepLinks.toLink`) needs a real `android.net.Uri`, so it
 * is covered by the instrumented `ExternalDeepLinksUriTest` instead.
 */
class ExternalDeepLinksTest {

    /** Both schemes the OBA brand answers to; a brand build adds its own (e.g. `kiedybus`). */
    private val schemes = setOf("onebusaway", "kiedybus")

    private fun appLink(
        host: String,
        params: Map<String, String> = emptyMap(),
        scheme: String = "onebusaway"
    ) = Link(scheme = scheme, host = host, pathSegments = emptyList(), params = params)

    private fun webLink(
        host: String = "onebusaway.co",
        pathSegments: List<String> = listOf("regions", "1", "stops", "1_75403", "trips"),
        params: Map<String, String> = mapOf("trip_id" to "1_18196913")
    ) = Link(scheme = "https", host = host, pathSegments = pathSegments, params = params)

    // --- view-stop ---

    @Test
    fun `view-stop opens the stop named by stopID`() {
        val target = parse(
            appLink("view-stop", mapOf("stopID" to "1_75403", "regionID" to "1")),
            schemes
        )
        assertEquals(Target.Stop("1_75403"), target)
    }

    @Test
    fun `view-stop is recognized under the brand's own scheme too`() {
        val target = parse(
            appLink("view-stop", mapOf("stopID" to "1_75403"), scheme = "kiedybus"),
            schemes
        )
        assertEquals(Target.Stop("1_75403"), target)
    }

    @Test
    fun `view-stop under an unknown scheme is not a deep link`() {
        val link = appLink("view-stop", mapOf("stopID" to "1_75403"), scheme = "someotherapp")
        assertNull(parse(link, schemes))
    }

    @Test
    fun `view-stop without a regionID still opens the stop`() {
        // Deliberate divergence from iOS, which requires regionID even though it never uses it: the
        // stop id is all the arrivals screen needs, so we don't drop an otherwise-usable link.
        assertEquals(
            Target.Stop("1_75403"),
            parse(appLink("view-stop", mapOf("stopID" to "1_75403")), schemes)
        )
    }

    @Test
    fun `view-stop without a stopID is not a deep link`() {
        assertNull(parse(appLink("view-stop", mapOf("regionID" to "1")), schemes))
    }

    @Test
    fun `view-stop with a blank stopID is not a deep link`() {
        // A `?stopID=` with no value reads as "" (Uri.getQueryParameter), same as absent.
        assertNull(parse(appLink("view-stop", mapOf("stopID" to "")), schemes))
    }

    // --- add-region: a full custom-region request (#2027), applied only after the rider confirms ---

    @Test
    fun `add-region carries the whole region the link describes`() {
        val target = parse(
            appLink(
                "add-region",
                mapOf(
                    "name" to "Test Deployment",
                    "oba-url" to "https://api.example.com",
                    "otp-url" to "https://otp.example.com",
                    "sidecar-url" to "https://sidecar.example.com",
                    "umami-url" to "https://umami.example.com",
                    "umami-id" to "abc123"
                )
            ),
            schemes
        )
        assertEquals(
            Target.AddRegion(
                CustomRegionRequest(
                    name = "Test Deployment",
                    obaBaseUrl = "https://api.example.com",
                    otpBaseUrl = "https://otp.example.com",
                    sidecarBaseUrl = "https://sidecar.example.com",
                    umamiAnalyticsUrl = "https://umami.example.com",
                    umamiAnalyticsId = "abc123"
                )
            ),
            target
        )
    }

    @Test
    fun `add-region needs only a name and an oba-url`() {
        assertEquals(
            Target.AddRegion(
                CustomRegionRequest(name = "Test", obaBaseUrl = "https://api.example.com")
            ),
            parse(
                appLink("add-region", mapOf("name" to "Test", "oba-url" to "https://api.example.com")),
                schemes
            )
        )
    }

    @Test
    fun `add-region without a name is not a deep link`() {
        // Matches iOS, which requires it. Note this is stricter than pre-#2027 Android, where a bare
        // `add-region?oba-url=...` set a pair of API-URL preferences — see docs/DEEP_LINKING.md.
        assertNull(parse(appLink("add-region", mapOf("oba-url" to "https://api.example.com")), schemes))
    }

    @Test
    fun `add-region without an oba-url is not a deep link`() {
        // A region with no OBA server can't answer anything, so there is nothing to add.
        assertNull(parse(appLink("add-region", mapOf("name" to "Test")), schemes))
    }

    @Test
    fun `add-region treats blank parameters as absent`() {
        assertNull(parse(appLink("add-region", mapOf("name" to "", "oba-url" to "https://api.example.com")), schemes))
        assertEquals(
            Target.AddRegion(
                CustomRegionRequest(name = "Test", obaBaseUrl = "https://api.example.com", otpBaseUrl = null)
            ),
            parse(
                appLink(
                    "add-region",
                    mapOf("name" to "Test", "oba-url" to "https://api.example.com", "otp-url" to "")
                ),
                schemes
            )
        )
    }

    @Test
    fun `an unknown custom-scheme host is not a deep link`() {
        assertNull(parse(appLink("view-route", mapOf("routeId" to "1_100194")), schemes))
    }

    // --- web (app link) trip links ---

    @Test
    fun `a web trip link opens the trip at the stop in its path`() {
        assertEquals(
            Target.Trip(tripId = "1_18196913", stopId = "1_75403"),
            parse(webLink(), schemes)
        )
    }

    @Test
    fun `every associated host is recognized`() {
        ExternalDeepLinks.WEB_HOSTS.forEach { host ->
            assertEquals(
                "host $host",
                Target.Trip(tripId = "1_18196913", stopId = "1_75403"),
                parse(webLink(host = host), schemes)
            )
        }
    }

    @Test
    fun `a trip link on an unrelated host is not a deep link`() {
        assertNull(parse(webLink(host = "example.com"), schemes))
    }

    @Test
    fun `a plaintext http trip link is not a deep link`() {
        // App links are https-only; the manifest filter grants no http scheme, and the same host over
        // http is not one of our links.
        assertNull(parse(webLink().copy(scheme = "http"), schemes))
    }

    @Test
    fun `a trip link carrying the full iOS parameter set ignores what no screen consumes`() {
        val target = parse(
            webLink(
                params = mapOf(
                    "trip_id" to "1_18196913",
                    "service_date" to "1698307200.0",
                    "stop_sequence" to "5",
                    "title" to "Link Light Rail",
                    "vehicle_id" to "1_1234",
                    "destination_stop_id" to "1_75414"
                )
            ),
            schemes
        )
        assertEquals(Target.Trip(tripId = "1_18196913", stopId = "1_75403"), target)
    }

    @Test
    fun `a trip link without a trip_id is not a deep link`() {
        assertNull(parse(webLink(params = mapOf("service_date" to "1698307200.0")), schemes))
    }

    @Test
    fun `only the exact trips path shape is a deep link`() {
        val rejected = listOf(
            // Matches iOS: only the /trips endpoint decodes; a stop page falls through to the browser.
            listOf("regions", "1", "stops", "1_75403"),
            listOf("regions", "1", "stops", "1_75403", "trips", "extra"),
            listOf("regions", "1", "stations", "1_75403", "trips"),
            // Unreachable from a real Uri (getPathSegments() drops empty segments); guards the pure
            // entry point, which any caller may hand a directly-constructed Link.
            listOf("regions", "1", "stops", "", "trips"),
            emptyList()
        )
        rejected.forEach { path ->
            assertNull("path $path", parse(webLink(pathSegments = path), schemes))
        }
    }

    // --- links the manifest filter claims but the parser can't route (handed back to the browser) ---

    @Test
    fun `a claimed web link the parser can't route is an unhandled web link`() {
        // The intent-filter can't see the query string, so a trip URL stripped of its trip_id still
        // launches the app; it must go back to the browser rather than land on the map.
        assertTrue(ExternalDeepLinks.isUnhandledWebLink(webLink(params = emptyMap())))
        // pathPattern's `.*` spans '/', so a deeper path clears the filter too.
        assertTrue(
            ExternalDeepLinks.isUnhandledWebLink(
                webLink(pathSegments = listOf("regions", "1", "x", "stops", "1_75403", "trips"))
            )
        )
    }

    @Test
    fun `a routable web link is not an unhandled web link`() {
        assertFalse(ExternalDeepLinks.isUnhandledWebLink(webLink()))
    }

    @Test
    fun `links we were never claiming are not unhandled web links`() {
        // Only the web filter is broader than the parser. A foreign host never reached us, an http URL
        // isn't in the filter, and an unrecognized custom-scheme link is a dead link, not a web page —
        // handing any of these to a browser would be wrong.
        assertFalse(ExternalDeepLinks.isUnhandledWebLink(webLink(host = "example.com")))
        assertFalse(ExternalDeepLinks.isUnhandledWebLink(webLink().copy(scheme = "http")))
        assertFalse(ExternalDeepLinks.isUnhandledWebLink(appLink("view-route")))
    }

    // --- the per-brand scheme derived from the deepLinkScheme manifest placeholder ---

    @Test
    fun `this build answers to the shared scheme and to its own`() {
        // Guards the Gradle wiring itself (deepLinkScheme placeholder -> BuildConfig.DEEP_LINK_SCHEME ->
        // APP_SCHEMES), which every other test in this class bypasses by passing an explicit scheme set.
        // A placeholder that failed to resolve would surface here as a blank or literal-"${…}" scheme.
        assertTrue("shared scheme missing", "onebusaway" in ExternalDeepLinks.APP_SCHEMES)
        assertTrue("brand scheme is blank", BuildConfig.DEEP_LINK_SCHEME.isNotBlank())
        assertFalse("placeholder unresolved", BuildConfig.DEEP_LINK_SCHEME.contains("$"))
        assertTrue(BuildConfig.DEEP_LINK_SCHEME in ExternalDeepLinks.APP_SCHEMES)
    }

    @Test
    fun `the default appSchemes overload accepts this build's own scheme`() {
        assertEquals(
            Target.Stop("1_75403"),
            parse(appLink("view-stop", mapOf("stopID" to "1_75403"), scheme = BuildConfig.DEEP_LINK_SCHEME))
        )
    }
}
