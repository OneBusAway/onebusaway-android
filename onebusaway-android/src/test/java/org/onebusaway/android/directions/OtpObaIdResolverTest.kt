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
package org.onebusaway.android.directions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.data.AgenciesDataSource
import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.models.AgencyContact
import org.onebusaway.android.models.RouteMapData
import org.onebusaway.android.models.RouteStopGroup

/**
 * JVM tests for [OtpObaIdResolver]'s derive → verify → name-fallback route resolution and its
 * resolve-against-the-route's-own-stops stop resolution (#2170), over Puget-Sound-shaped data (verified
 * against the live OTP/OBA deployments).
 */
class OtpObaIdResolverTest {

    // The region's covered OBA agencies (id + name), as agencies-with-coverage would report them.
    private val coverage = listOf(
        agency("1", "Metro Transit"),
        agency("40", "Sound Transit"),
        agency("19", "Intercity Transit"),
        agency("97", "Skagit Transit")
    )

    /** Stops-for-route stub: the OBA stop ids each route serves, or a failure for an unstubbed route. */
    private class RouteStops(private val stops: Map<String, List<String>>) : StopsForRouteRepository {
        val calls = mutableListOf<String>()
        override suspend fun routeStopGroups(routeId: String): Result<List<RouteStopGroup>> = Result.success(emptyList())
        override suspend fun routeMap(routeId: String): Result<RouteMapData?> = Result.success(null)
        override suspend fun routeStopIds(routeId: String): Result<List<String>> {
            calls += routeId
            return stops[routeId]?.let { Result.success(it) } ?: Result.failure(IllegalStateException("offline"))
        }
    }

    private fun resolver(
        agencies: Result<List<AgencyContact>> = Result.success(coverage),
        routeStops: StopsForRouteRepository = RouteStops(emptyMap())
    ) = OtpObaIdResolver(
        object : AgenciesDataSource {
            override suspend fun getAgencies() = agencies
        },
        routeStops
    )

    private fun agency(id: String, name: String) = AgencyContact(id = id, name = name, email = null, url = null, phone = null)

    @Test
    fun derivedAgencySuffix_whenCovered() = runTest {
        // kcm:1 → suffix "1" is a covered agency → OBA route 1_102574.
        assertEquals(
            "1_102574",
            resolver().obaRouteId("kcm:102574", agencyGtfsId = "kcm:1", agencyName = "Metro Transit")
        )
    }

    @Test
    fun stopIsNamedByTheRouteItIsServedOn() = runTest {
        val routeStops = RouteStops(mapOf("1_102574" to listOf("1_13580", "1_13585")))
        assertEquals("1_13585", resolver(routeStops = routeStops).obaStopId("kcm:13585", "1_102574"))
    }

    @Test
    fun stopDoesNotTakeItsRouteAgencyPrefix() = runTest {
        // #2170: ST 522 is agency 40 in both OTP (kcm:40) and OBA (40_100232), but every stop it calls
        // at is a Metro stop — 40_23561 does not exist. The route's own stop list is what says so.
        val routeStops = RouteStops(mapOf("40_100232" to listOf("1_23561", "1_38567")))
        assertEquals("1_23561", resolver(routeStops = routeStops).obaStopId("kcm:23561", "40_100232"))
    }

    @Test
    fun stopIsFoundWhenARouteSpansTwoAgenciesStops() = runTest {
        // KCM 931 calls at both Metro (1_*) and Community Transit (29_*) stops, so no single prefix
        // could have been guessed for the leg at all.
        val routeStops = RouteStops(mapOf("1_102558" to listOf("1_75995", "29_2229")))
        val resolver = resolver(routeStops = routeStops)
        assertEquals("29_2229", resolver.obaStopId("CommTrans:2229", "1_102558"))
        assertEquals("1_75995", resolver.obaStopId("kcm:75995", "1_102558"))
    }

    @Test
    fun stopEntityIdKeepsItsOwnUnderscores() = runTest {
        // OBA splits an id at its *first* underscore (AgencyAndId.convertFromString), so an entity id
        // containing one still matches.
        val routeStops = RouteStops(mapOf("40_TLINE" to listOf("40_T05_T1")))
        assertEquals("40_T05_T1", resolver(routeStops = routeStops).obaStopId("40:T05_T1", "40_TLINE"))
    }

    @Test
    fun stopUnresolvable_whenTheRouteDoesNotServeIt() = runTest {
        val routeStops = RouteStops(mapOf("40_100232" to listOf("1_23561")))
        assertNull(resolver(routeStops = routeStops).obaStopId("kcm:99999", "40_100232"))
    }

    @Test
    fun stopUnresolvable_whenTheRouteStopsCannotBeFetched() = runTest {
        // Nothing to fall back on: guessing the prefix is exactly the bug. Callers degrade instead.
        assertNull(resolver().obaStopId("kcm:23561", "40_100232"))
    }

    @Test
    fun stopUnresolvable_whenTheRouteItselfIsUnresolvable() = runTest {
        val routeStops = RouteStops(mapOf("40_100232" to listOf("1_23561")))
        assertNull(resolver(routeStops = routeStops).obaStopId("kcm:23561", null))
        // No point asking for a route we can't name.
        assertEquals(emptyList<String>(), routeStops.calls)
    }

    @Test
    fun prefetchWarmsEachRouteOnce() = runTest {
        val routeStops = RouteStops(mapOf("40_100232" to listOf("1_23561"), "1_102558" to listOf("29_2229")))
        val resolver = resolver(routeStops = routeStops)

        resolver.prefetchRouteStops(listOf("40_100232", "1_102558", "40_100232"))

        assertEquals(setOf("40_100232", "1_102558"), routeStops.calls.toSet())
        assertEquals(2, routeStops.calls.size)
    }

    @Test
    fun numericFeedPrefix_resolvesToItself() = runTest {
        // Sound Transit's own feed is numeric: 40:40 → "40", covered → 40_2LINE.
        assertEquals(
            "40_2LINE",
            resolver().obaRouteId("40:2LINE", agencyGtfsId = "40:40", agencyName = "Sound Transit")
        )
    }

    @Test
    fun nameFallback_whenDerivedAgencyNotCovered() = runTest {
        // Intercity is 19:0 in OTP (suffix "0" isn't covered) but agency "19" in OBA — matched by name.
        assertEquals(
            "19_600",
            resolver().obaRouteId("19:600", agencyGtfsId = "19:0", agencyName = "Intercity Transit")
        )
    }

    @Test
    fun nameFallback_forUuidAgencyId() = runTest {
        // Skagit uses a UUID agency id in OTP; only the name resolves it to OBA agency "97".
        assertEquals(
            "97_42",
            resolver().obaRouteId(
                "Skagit:42",
                agencyGtfsId = "Skagit:e0e4541a-2714-487b-b30c-f5c6cb4a310f",
                agencyName = "Skagit Transit"
            )
        )
    }

    @Test
    fun unresolvable_whenNeitherSuffixNorNameMatches() = runTest {
        assertNull(
            resolver().obaRouteId("foo:9", agencyGtfsId = "foo:9", agencyName = "Nowhere Transit")
        )
    }

    @Test
    fun offline_trustsDerivedSuffix() = runTest {
        // No coverage data (fetch failed): fall back to the derived suffix (correct for the common case).
        assertEquals(
            "1_102574",
            resolver(Result.failure(RuntimeException("offline")))
                .obaRouteId("kcm:102574", agencyGtfsId = "kcm:1", agencyName = "Metro Transit")
        )
    }

    @Test
    fun nullRoute_returnsNull() = runTest {
        assertNull(resolver().obaRouteId(null, agencyGtfsId = "kcm:1", agencyName = "Metro Transit"))
    }
}
