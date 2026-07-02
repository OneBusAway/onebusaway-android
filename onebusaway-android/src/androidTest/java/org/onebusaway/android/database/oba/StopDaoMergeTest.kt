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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies [StopDao]'s merge-on-write and the region-scoped/`UI_NAME` list queries against a real Room
 * DB. The load-bearing invariant: re-recording a stop (arrivals load) must never clobber the user's
 * favorite flag or custom name — that's why the row is recorded before a favorite toggle.
 */
@RunWith(AndroidJUnit4::class)
class StopDaoMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: StopDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        dao = db.stopDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun markStopUsed_preservesFavoriteAndUserName() = runBlocking {
        dao.markStopUsed("s1", "100", "Main St", "N", 47.0, -122.0, regionId = 1L, now = 100)
        dao.setFavorite("s1", 1)
        dao.upsert(dao.getStop("s1")!!.copy(userName = "Home"))

        // Record again (e.g. a later arrivals load) — favorite + custom name must survive.
        dao.markStopUsed("s1", "100", "Main Street", "N", 47.0, -122.0, regionId = 1L, now = 200)

        val s = dao.getStop("s1")!!
        assertEquals(1, s.favorite)
        assertEquals("Home", s.userName)
        assertEquals(2, s.useCount)
        assertEquals(200L, s.accessTime)
        assertEquals("Main Street", s.name)     // identity/coords still refreshed
    }

    @Test
    fun starredByName_scopesToRegion_andProjectsUiName() = runBlocking {
        // region 1, with a custom name (UI_NAME should be the user name)
        dao.markStopUsed("s1", "1", "Alpha", "N", 1.0, 1.0, regionId = 1L, now = 1)
        dao.setFavorite("s1", 1)
        dao.upsert(dao.getStop("s1")!!.copy(userName = "Zulu"))
        // region 2 (should be excluded when scoping to region 1)
        dao.markStopUsed("s2", "2", "Beta", "N", 2.0, 2.0, regionId = 2L, now = 2)
        dao.setFavorite("s2", 1)
        // no region (always visible)
        dao.markStopUsed("s3", "3", "Gamma", "N", 3.0, 3.0, regionId = null, now = 3)
        dao.setFavorite("s3", 1)

        val rows = dao.starredByName(regionId = 1L).first()

        assertEquals(setOf("s1", "s3"), rows.map { it.id }.toSet())   // region 2 excluded
        assertEquals("Zulu", rows.first { it.id == "s1" }.uiName)     // user_name wins over name
        assertEquals("Gamma", rows.first { it.id == "s3" }.uiName)    // falls back to name
    }

    @Test
    fun recents_excludesUnusedAndHonorsRegionScope() = runBlocking {
        dao.markStopUsed("s1", "1", "Alpha", "N", 1.0, 1.0, regionId = 1L, now = 10)
        dao.markStopUsed("s2", "2", "Beta", "N", 2.0, 2.0, regionId = 2L, now = 20)
        dao.markUnused("s1")   // use_count -> 0, access_time -> null

        val rows = dao.recents(cutoff = 0, regionId = 2L).first()

        assertEquals(listOf("s2"), rows.map { it.id })   // s1 unused; s2 in region
    }
}
