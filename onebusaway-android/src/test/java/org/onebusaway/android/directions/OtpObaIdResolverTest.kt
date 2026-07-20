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
import org.onebusaway.android.models.AgencyContact

/**
 * JVM tests for [OtpObaIdResolver]'s derive → verify → name-fallback resolution, over Puget-Sound-shaped
 * agency data (verified against the live OTP/OBA deployments).
 */
class OtpObaIdResolverTest {

    // The region's covered OBA agencies (id + name), as agencies-with-coverage would report them.
    private val coverage = listOf(
        agency("1", "Metro Transit"),
        agency("40", "Sound Transit"),
        agency("19", "Intercity Transit"),
        agency("97", "Skagit Transit")
    )

    private fun resolver(agencies: Result<List<AgencyContact>> = Result.success(coverage)) = OtpObaIdResolver(object : AgenciesDataSource {
        override suspend fun getAgencies() = agencies
    })

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
    fun stopTakesTheSameAgencyPrefix() = runTest {
        assertEquals(
            "1_13585",
            resolver().obaStopId("kcm:13585", agencyGtfsId = "kcm:1", agencyName = "Metro Transit")
        )
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
