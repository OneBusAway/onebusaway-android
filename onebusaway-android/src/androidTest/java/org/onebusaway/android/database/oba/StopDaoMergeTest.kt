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
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
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
        assertEquals("Main Street", s.name) // identity/coords still refreshed
    }

    @Test
    fun setFavoriteEnsuringRow_insertsIdentityRowWhenAbsent() = runBlocking {
        // A stop focused on the map (only in cached_stops) has no user-state row yet — starring it
        // must create the row with the flag already set (#684).
        assertEquals(null, dao.getStop("s1"))

        dao.setFavoriteEnsuringRow(
            StopRecord(
                id = "s1",
                code = "100",
                name = "Main St",
                direction = "",
                useCount = 0,
                latitude = 47.0,
                longitude = -122.0,
                regionId = 1L
            ),
            favorite = 1
        )

        val s = dao.getStop("s1")!!
        assertEquals(1, s.favorite)
        assertEquals("Main St", s.name)
        assertEquals("100", s.code)
        assertEquals(1L, s.regionId)
    }

    @Test
    fun setFavoriteEnsuringRow_onlyFlipsFlagWhenRowExists() = runBlocking {
        // Existing row carries user state that a re-star must not clobber.
        dao.markStopUsed("s1", "100", "Main St", "N", 47.0, -122.0, regionId = 1L, now = 100)
        dao.upsert(dao.getStop("s1")!!.copy(userName = "Home"))

        // Ensure-row is handed the focused-stop identity (blank direction, no user state), but the row
        // exists, so only the flag flips — user_name / use_count / access_time survive.
        dao.setFavoriteEnsuringRow(
            StopRecord(
                id = "s1",
                code = "100",
                name = "Main St",
                direction = "",
                useCount = 0,
                latitude = 47.0,
                longitude = -122.0,
                regionId = 1L
            ),
            favorite = 1
        )

        val s = dao.getStop("s1")!!
        assertEquals(1, s.favorite)
        assertEquals("Home", s.userName)
        assertEquals("N", s.direction)
        assertEquals(1, s.useCount)
        assertEquals(100L, s.accessTime)
    }

    @Test
    fun setFavoriteEnsuringRow_earlyStarSurvivesLaterArrivalsLoad() = runBlocking {
        // Star before arrivals load (row inserted), then the arrivals load merges onto the row — the
        // early star must stick and the identity/coords fill in (#684).
        dao.setFavoriteEnsuringRow(
            StopRecord(
                id = "s1",
                code = "",
                name = "",
                direction = "",
                useCount = 0,
                latitude = 47.0,
                longitude = -122.0,
                regionId = 1L
            ),
            favorite = 1
        )

        dao.markStopUsed("s1", "100", "Main Street", "N", 47.0, -122.0, regionId = 1L, now = 200)

        val s = dao.getStop("s1")!!
        assertEquals(1, s.favorite) // early star preserved by the merge
        assertEquals("Main Street", s.name) // identity backfilled by the arrivals load
        assertEquals("100", s.code)
        assertEquals(1, s.useCount)
        assertEquals(200L, s.accessTime)
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

        assertEquals(setOf("s1", "s3"), rows.map { it.id }.toSet()) // region 2 excluded
        assertEquals("Zulu", rows.first { it.id == "s1" }.uiName) // user_name wins over name
        assertEquals("Gamma", rows.first { it.id == "s3" }.uiName) // falls back to name
    }

    @Test
    fun recents_excludesUnusedAndHonorsRegionScope() = runBlocking {
        dao.markStopUsed("s1", "1", "Alpha", "N", 1.0, 1.0, regionId = 1L, now = 10)
        dao.markStopUsed("s2", "2", "Beta", "N", 2.0, 2.0, regionId = 2L, now = 20)
        dao.markUnused("s1") // use_count -> 0, access_time -> null

        val rows = dao.recents(cutoff = 0, regionId = 2L).first()

        assertEquals(listOf("s2"), rows.map { it.id }) // s1 unused; s2 in region
    }
}
