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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies [MapStopCacheDao] against a real Room DB: the bounding-box viewport query (region + TTL
 * scoped), the route-type lookup, and the TTL + size-cap eviction.
 */
@RunWith(AndroidJUnit4::class)
class MapStopCacheDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MapStopCacheDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        dao = db.mapStopCacheDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun stopsInBounds_filtersByBoundingBox() = runBlocking {
        dao.upsertStops(
            listOf(
                stop("in", lat = 47.60, lon = -122.30, regionId = 1L, lastSeen = 100),
                stop("outLat", lat = 47.90, lon = -122.30, regionId = 1L, lastSeen = 100),
                stop("outLon", lat = 47.60, lon = -121.90, regionId = 1L, lastSeen = 100),
            )
        )
        val rows = dao.stopsInBounds(
            regionId = 1L, minLat = 47.55, maxLat = 47.65, minLon = -122.35, maxLon = -122.25,
            centerLat = 47.60, centerLon = -122.30, ttlCutoff = 0, limit = 100,
        )
        assertEquals(setOf("in"), rows.map { it.id }.toSet())
    }

    @Test
    fun stopsInBounds_excludesOtherRegionsAndStaleRows() = runBlocking {
        dao.upsertStops(
            listOf(
                stop("fresh", lat = 47.60, lon = -122.30, regionId = 1L, lastSeen = 1_000),
                stop("otherRegion", lat = 47.60, lon = -122.30, regionId = 2L, lastSeen = 1_000),
                stop("stale", lat = 47.60, lon = -122.30, regionId = 1L, lastSeen = 100),
            )
        )
        val rows = dao.stopsInBounds(
            regionId = 1L, minLat = 47.5, maxLat = 47.7, minLon = -122.4, maxLon = -122.2,
            centerLat = 47.60, centerLon = -122.30, ttlCutoff = 500, limit = 100,
        )
        assertEquals(setOf("fresh"), rows.map { it.id }.toSet())
    }

    @Test
    fun stopsInBounds_whenOverLimit_returnsNearestToCenterFirst() = runBlocking {
        // Three stops north of a shared center at increasing distance; a limit of 2 must drop the
        // farthest, so the render is centred on the viewport rather than an arbitrary edge.
        dao.upsertStops(
            listOf(
                stop("near", lat = 47.61, lon = -122.30, regionId = 1L, lastSeen = 100),
                stop("mid", lat = 47.64, lon = -122.30, regionId = 1L, lastSeen = 100),
                stop("far", lat = 47.69, lon = -122.30, regionId = 1L, lastSeen = 100),
            )
        )
        val rows = dao.stopsInBounds(
            regionId = 1L, minLat = 47.5, maxLat = 47.7, minLon = -122.4, maxLon = -122.2,
            centerLat = 47.60, centerLon = -122.30, ttlCutoff = 0, limit = 2,
        )
        assertEquals(listOf("near", "mid"), rows.map { it.id })   // nearest first, farthest dropped
    }

    @Test
    fun routeTypes_returnsRequestedSubset() = runBlocking {
        dao.upsertRouteTypes(
            listOf(
                CachedRouteTypeRecord("r1", type = 3, regionId = 1L, lastSeen = 1),
                CachedRouteTypeRecord("r2", type = 2, regionId = 1L, lastSeen = 1),
                CachedRouteTypeRecord("r3", type = 4, regionId = 1L, lastSeen = 1),
            )
        )
        val types = dao.routeTypes(listOf("r1", "r3")).associate { it.id to it.type }
        assertEquals(mapOf("r1" to 3, "r3" to 4), types)
    }

    @Test
    fun evictStaleStops_dropsOnlyRowsBeforeCutoff() = runBlocking {
        dao.upsertStops(
            listOf(
                stop("old", lat = 1.0, lon = 1.0, regionId = 1L, lastSeen = 100),
                stop("new", lat = 1.0, lon = 1.0, regionId = 1L, lastSeen = 900),
            )
        )
        dao.evictStaleStops(regionId = 1L, ttlCutoff = 500)

        val remaining = dao.stopsInBounds(1L, 0.0, 2.0, 0.0, 2.0, centerLat = 1.0, centerLon = 1.0, ttlCutoff = 0, limit = 100)
        assertEquals(setOf("new"), remaining.map { it.id }.toSet())
    }

    @Test
    fun evictBeyondCap_keepsTheMostRecentlySeen() = runBlocking {
        dao.upsertStops(
            (1..5).map { i ->
                stop("s$i", lat = 1.0, lon = 1.0, regionId = 1L, lastSeen = i.toLong())
            }
        )
        dao.evictBeyondCap(regionId = 1L, cap = 2)

        val remaining = dao.stopsInBounds(1L, 0.0, 2.0, 0.0, 2.0, centerLat = 1.0, centerLon = 1.0, ttlCutoff = 0, limit = 100)
        assertEquals(setOf("s4", "s5"), remaining.map { it.id }.toSet())   // newest two by last_seen
        assertEquals(2, dao.countStops(1L))
    }

    @Test
    fun evictBeyondCap_isScopedToRegion() = runBlocking {
        dao.upsertStops(
            listOf(
                stop("a1", lat = 1.0, lon = 1.0, regionId = 1L, lastSeen = 1),
                stop("a2", lat = 1.0, lon = 1.0, regionId = 1L, lastSeen = 2),
                stop("b1", lat = 1.0, lon = 1.0, regionId = 2L, lastSeen = 1),
            )
        )
        dao.evictBeyondCap(regionId = 1L, cap = 1)

        assertEquals(1, dao.countStops(1L))   // region 1 trimmed to 1
        assertEquals(1, dao.countStops(2L))   // region 2 untouched
    }

    @Test
    fun saveAndEvict_upsertsBothTablesAndTrims() = runBlocking {
        // Pre-seed a stale row that saveAndEvict should drop.
        dao.upsertStops(listOf(stop("stale", 1.0, 1.0, regionId = 1L, lastSeen = 1)))

        dao.saveAndEvict(
            stops = (1..3).map { stop("s$it", 1.0, 1.0, regionId = 1L, lastSeen = 1_000) },
            types = listOf(CachedRouteTypeRecord("r1", 3, regionId = 1L, lastSeen = 1_000)),
            regionId = 1L, ttlCutoff = 500, cap = 100,
        )

        val remaining = dao.stopsInBounds(1L, 0.0, 2.0, 0.0, 2.0, centerLat = 1.0, centerLon = 1.0, ttlCutoff = 0, limit = 100)
        assertEquals(setOf("s1", "s2", "s3"), remaining.map { it.id }.toSet())   // stale gone
        assertEquals(listOf(3), dao.routeTypes(listOf("r1")).map { it.type })
    }

    @Test
    fun zeroRouteStop_roundTrips() = runBlocking {
        dao.upsertStops(listOf(stop("s1", 1.0, 1.0, regionId = 1L, lastSeen = 1, routeIds = "")))
        val row = dao.stopsInBounds(1L, 0.0, 2.0, 0.0, 2.0, centerLat = 1.0, centerLon = 1.0, ttlCutoff = 0, limit = 100).single()
        assertEquals("", row.routeIds)
        assertEquals(emptyList<String>(), splitRouteIds(row.routeIds).toList())
    }

    private fun stop(
        id: String,
        lat: Double,
        lon: Double,
        regionId: Long,
        lastSeen: Long,
        routeIds: String = "r1",
    ) = CachedStopRecord(
        id = id, code = "code", name = "name", direction = "N",
        latitude = lat, longitude = lon, locationType = 0,
        routeIds = routeIds, regionId = regionId, lastSeen = lastSeen,
    )
}
