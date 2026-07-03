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
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies [RouteDao]'s `@Transaction` merge-on-write helpers against a real (in-memory) Room DB — the
 * classic silent-data-loss shape where re-recording a route must not clobber columns another path set.
 */
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
