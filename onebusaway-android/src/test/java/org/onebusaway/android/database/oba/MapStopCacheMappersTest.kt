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
package org.onebusaway.android.database.oba

import android.location.Location
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.time.WallTime

/** JVM unit tests for the pure map-stop-cache mappers ([MapStopCacheMappers]). */
class MapStopCacheMappersTest {

    // --- route-id join/split ---

    @Test
    fun routeIds_emptyRoundTripsToEmptyArray() {
        assertEquals("", joinRouteIds(emptyArray()))
        assertArrayEquals(emptyArray<String>(), splitRouteIds(""))
    }

    @Test
    fun routeIds_singleAndManyRoundTrip() {
        val one = arrayOf("1_100")
        assertArrayEquals(one, splitRouteIds(joinRouteIds(one)))

        // Ids containing the characters that a comma/space delimiter would have collided with.
        val many = arrayOf("1_100", "40_A-Line", "MTA NYCT_M15", "agency:route")
        assertArrayEquals(many, splitRouteIds(joinRouteIds(many)))
    }

    // --- viewport bounds ---

    @Test
    fun boundsFor_isCenterPlusMinusHalfSpan() {
        val b = boundsFor(centerLat = 47.6, centerLon = -122.3, latSpan = 0.02, lonSpan = 0.04)
        assertEquals(47.59, b.minLat, 1e-9)
        assertEquals(47.61, b.maxLat, 1e-9)
        assertEquals(-122.32, b.minLon, 1e-9)
        assertEquals(-122.28, b.maxLon, 1e-9)
    }

    // --- TTL cutoff (pure; now passed in) ---

    @Test
    fun ttlCutoff_subtractsTtlFromNow() {
        val now = WallTime(1_000_000_000_000L)
        assertEquals(now - STOP_CACHE_TTL, ttlCutoff(now))
        assertEquals(now.epochMs - STOP_CACHE_TTL.inWholeMilliseconds, ttlCutoff(now).epochMs)
    }

    // --- record <-> ObaStop round trip ---

    @Test
    fun toCachedRecord_thenToObaStop_preservesFields() {
        val stop = fakeStop(
            id = "1_75403", code = "75403", name = "Pine St & 5th Ave",
            direction = "NW", locationType = ObaStop.LOCATION_STOP,
            lat = 47.61, lon = -122.34, routeIds = arrayOf("1_100", "1_101"),
        )

        val record = stop.toCachedRecord(regionId = 3L, now = 500L)
        assertEquals(3L, record.regionId)
        assertEquals(500L, record.lastSeen)

        val back = record.toObaStop()
        assertEquals("1_75403", back.id)
        assertEquals("75403", back.stopCode)
        assertEquals("Pine St & 5th Ave", back.name)
        assertEquals("NW", back.direction)
        assertEquals(ObaStop.LOCATION_STOP, back.locationType)
        assertEquals(47.61, back.latitude, 1e-9)
        assertEquals(-122.34, back.longitude, 1e-9)
        assertArrayEquals(arrayOf("1_100", "1_101"), back.routeIds)
    }

    @Test
    fun roundTrip_preservesNullDirectionSentinel_nullCodeName_andZeroRoutes() {
        // The wire "no direction" sentinel is the literal string "null"; must survive verbatim.
        val sentinel = fakeStop(
            id = "s1", code = null, name = null, direction = "null",
            locationType = ObaStop.LOCATION_STATION, lat = 1.0, lon = 2.0, routeIds = emptyArray(),
        )
        val back = sentinel.toCachedRecord(regionId = 1L, now = 1L).toObaStop()

        assertEquals("null", back.direction)
        assertEquals(null, back.stopCode)
        assertEquals(null, back.name)
        assertEquals(ObaStop.LOCATION_STATION, back.locationType)
        assertArrayEquals(emptyArray<String>(), back.routeIds)
    }

    private fun fakeStop(
        id: String,
        code: String?,
        name: String?,
        direction: String?,
        locationType: Int,
        lat: Double,
        lon: Double,
        routeIds: Array<String>,
    ): ObaStop = object : ObaStop {
        override val id = id
        override val stopCode = code
        override val name = name
        override val direction = direction
        override val locationType = locationType
        override val latitude = lat
        override val longitude = lon
        override val routeIds = routeIds

        // Never touched by the mappers (would need Android); guard so a regression is loud.
        override val location: Location get() = throw UnsupportedOperationException()
    }
}
