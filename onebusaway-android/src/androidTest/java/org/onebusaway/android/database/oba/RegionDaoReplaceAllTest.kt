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
 * Verifies [RegionDao.replaceAll] against a real Room DB: it clears all three region tables and
 * reinserts each region with its `@Relation` children, `open311_servers` is cleared explicitly (no FK)
 * while `region_bounds` cascades, and region id 0 (Tampa) survives as a real, non-reassigned key.
 */
@RunWith(AndroidJUnit4::class)
class RegionDaoReplaceAllTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RegionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        dao = db.regionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replaceAll_insertsRegionWithChildren_andPreservesRegionZero() = runBlocking {
        dao.replaceAll(listOf(regionWithChildren(0)))

        val hydrated = dao.getRegion(0)!!
        assertEquals(0L, hydrated.region.id)          // Tampa id 0 not reassigned
        assertEquals(1, hydrated.bounds.size)
        assertEquals(1, hydrated.open311Servers.size)
    }

    @Test
    fun replaceAll_clearsPreviousRegionsAndChildren() = runBlocking {
        dao.replaceAll(listOf(regionWithChildren(0), regionWithChildren(1)))

        // Replace with a different set: region 5, and a region with no open311 servers.
        dao.replaceAll(listOf(regionWithChildren(5, servers = 0)))

        assertNull(dao.getRegion(0))                  // old regions gone
        assertNull(dao.getRegion(1))
        assertEquals(1, dao.getAllRegions().size)
        val r5 = dao.getRegion(5)!!
        assertEquals(1, r5.bounds.size)               // region_bounds reinserted
        assertEquals(0, r5.open311Servers.size)       // open311 cleared, none reinserted
    }

    private fun regionWithChildren(id: Long, servers: Int = 1) = RegionWithChildren(
        region = RegionRecord(
            id = id,
            name = "R$id",
            obaBaseUrl = "http://oba/$id",
            siriBaseUrl = "http://siri/$id",
            language = "en",
            contactEmail = "e@x",
            supportsObaDiscovery = 1,
            supportsObaRealtime = 1,
            supportsSiriRealtime = 0,
        ),
        bounds = listOf(
            RegionBoundRecord(regionId = id, latitude = 1.0, longitude = 2.0, latSpan = 0.1, lonSpan = 0.1)
        ),
        open311Servers = if (servers == 0) emptyList() else listOf(
            Open311ServerRecord(regionId = id, apiKey = "k$id", baseUrl = "http://311/$id")
        ),
    )
}
