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
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.ui.nav.ExternalDeepLinks.Link
import org.onebusaway.android.ui.nav.ExternalDeepLinks.Target
import org.onebusaway.android.ui.nav.ExternalDeepLinks.parse

/**
 * Unit tests for the cross-platform (iOS-parity) deep-link parser (#2027). The `Uri` decomposition
 * lives in `IntentRouteMapper.read`, so these exercise the pure vocabulary: which links are
 * recognized, which are deliberately not, and what each maps to.
 */
class ExternalDeepLinksTest {

    /** Both schemes the OBA brand answers to; a brand build adds its own (e.g. `kiedybus`). */
    private val schemes = setOf("onebusaway", "kiedybus")

    private fun appLink(host: String, params: Map<String, String> = emptyMap(), scheme: String = "onebusaway") = Link(scheme = scheme, host = host, pathSegments = emptyList(), params = params)

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

    @Test
    fun `add-region is not a route - its URLs apply as a side effect`() {
        assertNull(parse(appLink("add-region", mapOf("oba-url" to "https://api.example.com")), schemes))
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
    fun `a stop-only web path is not a deep link`() {
        // Matches iOS: only the /trips endpoint decodes; a stop page falls through to the browser.
        assertNull(parse(webLink(pathSegments = listOf("regions", "1", "stops", "1_75403")), schemes))
    }

    @Test
    fun `a trip path with a trailing segment is not a deep link`() {
        val path = listOf("regions", "1", "stops", "1_75403", "trips", "extra")
        assertNull(parse(webLink(pathSegments = path), schemes))
    }

    @Test
    fun `a misspelled trip path is not a deep link`() {
        val path = listOf("regions", "1", "stations", "1_75403", "trips")
        assertNull(parse(webLink(pathSegments = path), schemes))
    }

    @Test
    fun `a trip link with a blank stop id in its path is not a deep link`() {
        val path = listOf("regions", "1", "stops", "", "trips")
        assertNull(parse(webLink(pathSegments = path), schemes))
    }

    @Test
    fun `a bare host with no path is not a deep link`() {
        assertNull(parse(webLink(pathSegments = emptyList(), params = emptyMap()), schemes))
    }
}
