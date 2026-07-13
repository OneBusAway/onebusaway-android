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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.SmokeTest
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies [RouteDao]'s `@Transaction` merge-on-write helpers against a real (in-memory) Room DB — the
 * classic silent-data-loss shape where re-recording a route must not clobber columns another path set.
 */
@SmokeTest // API-23 floor smoke subset (#1818): exercises Room runtime CRUD/@Transaction on the floor
@RunWith(AndroidJUnit4::class)
class RouteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RouteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        dao = db.routeDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun markRouteUsed_freshRoute_startsAtUseCountOne() = runBlocking {
        dao.markRouteUsed("r2", "20", "Route 20", regionId = 2L, now = 50)

        val r = dao.getRoute("r2")!!
        assertEquals(1, r.useCount)
        assertEquals(50L, r.accessTime)
        assertEquals(2L, r.regionId)
        assertNull(r.url)
    }

    @Test
    fun markRouteUsed_incrementsAndPreservesUrlAndRegion() = runBlocking {
        // storeRouteDetails sets the URL; a later markRouteUsed must not wipe it.
        dao.storeRouteDetails("r1", "10", "Route 10", "http://u", regionId = 1L, now = 100)
        assertEquals("http://u", dao.getRoute("r1")!!.url)

        dao.markRouteUsed("r1", "10", "Route 10 renamed", regionId = null, now = 200)

        val r = dao.getRoute("r1")!!
        assertEquals(2, r.useCount)
        assertEquals(200L, r.accessTime)
        assertEquals("http://u", r.url)          // NOT clobbered by markRouteUsed
        assertEquals(1L, r.regionId)             // regionId = null keeps the existing region
        assertEquals("10", r.shortName)          // short/long names refreshed
        assertEquals("Route 10 renamed", r.longName)
    }

    @Test
    fun favoriteRouteIds_reflectsTheFavoriteBit() = runBlocking {
        // No row yet -> not in the favorites set.
        assertTrue(dao.favoriteRouteIds().first().isEmpty())

        dao.storeRouteDetails("r4", "40", "Route 40", "http://u", regionId = 4L, now = 100)
        assertTrue(dao.favoriteRouteIds().first().isEmpty())   // row exists, favorite still unset

        dao.setFavorite("r4", 1)
        assertEquals(listOf("r4"), dao.favoriteRouteIds().first())

        dao.setFavorite("r4", 0)
        assertTrue(dao.favoriteRouteIds().first().isEmpty())
    }

    @Test
    fun ensureRouteDetails_doesNotCountAsAUse() = runBlocking {
        // A route the user has actually viewed twice.
        dao.storeRouteDetails("r5", "50", "Route 50", "http://u", regionId = 5L, now = 100)
        dao.markRouteUsed("r5", "50", "Route 50", regionId = 5L, now = 200)
        assertEquals(2, dao.getRoute("r5")!!.useCount)

        // Favoriting ensures the row but must not bump use_count / access_time (#1727 review).
        dao.ensureRouteDetails("r5", "50", "Route 50", url = null, regionId = null)

        val r = dao.getRoute("r5")!!
        assertEquals(2, r.useCount)               // unchanged — a favorite toggle is not a view
        assertEquals(200L, r.accessTime)          // unchanged
        assertEquals("http://u", r.url)           // null url preserves the existing one
        assertEquals(5L, r.regionId)

        // A brand-new (never-viewed) route starts at use_count 0, so it sorts last by frequency.
        dao.ensureRouteDetails("r6", "60", "Route 60", url = "http://v", regionId = 6L)
        val fresh = dao.getRoute("r6")!!
        assertEquals(0, fresh.useCount)
        assertNull(fresh.accessTime)
        assertEquals("http://v", fresh.url)
    }

    @Test
    fun refreshRouteShortName_onlyTouchesShortName() = runBlocking {
        dao.storeRouteDetails("r3", "30", "Route 30", "http://u", regionId = 3L, now = 100)

        dao.refreshRouteShortName("r3", "30-express")

        val r = dao.getRoute("r3")!!
        assertEquals("30-express", r.shortName)
        assertEquals("http://u", r.url)          // untouched
        assertEquals("Route 30", r.longName)     // untouched
        assertEquals(1, r.useCount)              // did not mark used
    }
}
