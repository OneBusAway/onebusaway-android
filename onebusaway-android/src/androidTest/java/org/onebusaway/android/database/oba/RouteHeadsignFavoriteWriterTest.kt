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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies the route/headsign favorite write-side reconciliation ([applyRouteHeadsignFavorite]) against
 * a real Room DB — the branchiest storage-write logic, whose read counterpart ([computeRouteHeadsignFavorite])
 * is separately unit-tested. Exercises the favorite/exclude/all-stops/route-star matrix end to end.
 */
@RunWith(AndroidJUnit4::class)
class RouteHeadsignFavoriteWriterTest {

    private lateinit var db: AppDatabase
    private lateinit var headsignDao: RouteHeadsignFavoriteDao
    private lateinit var routeDao: RouteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        headsignDao = db.routeHeadsignFavoriteDao()
        routeDao = db.routeDao()
        // The route row must exist for the star-flag reconciliation to update it.
        runBlocking { routeDao.upsert(RouteRecord(id = "r1", shortName = "10", useCount = 0)) }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun apply(headsign: String?, stopId: String?, favorite: Boolean) =
        applyRouteHeadsignFavorite(headsignDao, routeDao, "r1", headsign, stopId, favorite)

    @Test
    fun favoriteSingleStop_insertsExcludeZeroRow_andStarsRoute() = runBlocking {
        apply(headsign = "Downtown", stopId = "s1", favorite = true)

        val rows = headsignDao.favoritesForStopOrAll("s1")
        assertEquals(1, rows.size)
        assertEquals(0, rows[0].exclude)
        assertEquals(1, routeDao.getRoute("r1")!!.favorite)
        assertTrue(headsignDao.routeHasFavorite("r1"))
    }

    @Test
    fun favoritesForStopOrAll_matchesStopAndWildcard_butNotOtherStop() = runBlocking {
        apply("D", "s1", favorite = true)
        apply("D", stopId = null, favorite = true)   // all stops
        apply("D", "s2", favorite = true)

        val forS1 = headsignDao.favoritesForStopOrAll("s1").map { it.stopId }.toSet()
        assertEquals(setOf("s1", "all"), forS1)       // s2 not visible from s1
    }

    @Test
    fun unfavoriteLastStop_clearsRouteStar() = runBlocking {
        apply("D", "s1", favorite = true)
        assertEquals(1, routeDao.getRoute("r1")!!.favorite)

        apply("D", "s1", favorite = false)

        assertFalse(headsignDao.routeHasFavorite("r1"))
        assertEquals(0, routeDao.getRoute("r1")!!.favorite)
    }

    @Test
    fun unstarSingleStop_whileAllStarred_recordsExclusion_keepsRouteStar() = runBlocking {
        apply("D", stopId = null, favorite = true)    // star the whole route
        assertTrue(computeRouteHeadsignFavorite(headsignDao.favoritesForStopOrAll("s1"), "r1", "D", "s1"))

        apply("D", "s1", favorite = false)            // unstar just s1

        val rows = headsignDao.favoritesForStopOrAll("s1")
        assertTrue(rows.any { it.stopId == "s1" && it.exclude == 1 })            // exclusion recorded
        assertFalse(computeRouteHeadsignFavorite(rows, "r1", "D", "s1"))         // s1 no longer favorite
        assertEquals(1, routeDao.getRoute("r1")!!.favorite)                      // route still starred
    }

    @Test
    fun unfavoriteAllStops_removesEveryHeadsignRow() = runBlocking {
        apply("D", "s1", favorite = true)
        apply("D", stopId = null, favorite = true)

        apply("D", stopId = null, favorite = false)   // unstar all stops

        assertTrue(headsignDao.favoritesForStopOrAll("s1").isEmpty())
        assertFalse(headsignDao.routeHasFavorite("r1"))
        assertEquals(0, routeDao.getRoute("r1")!!.favorite)
    }
}
