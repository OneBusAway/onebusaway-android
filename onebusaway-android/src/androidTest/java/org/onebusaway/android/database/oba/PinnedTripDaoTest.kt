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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies [PinnedTripDao] against a real (in-memory) Room DB. Two things the JVM store test cannot
 * see: that `replace` really does leave a single row (it is a `@Transaction` over SQL, not Kotlin),
 * and that a full-size itinerary payload survives the round trip through SQLite.
 */
@RunWith(AndroidJUnit4::class)
class PinnedTripDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PinnedTripDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        dao = db.pinnedTripDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replace_twice_leavesExactlyOnePin() = runBlocking {
        dao.replace(record(selectedIndex = 0))
        dao.replace(record(selectedIndex = 2))

        assertEquals(2, dao.pinned()!!.selectedIndex)
        assertEquals(2, dao.observePinned().first()!!.selectedIndex)
    }

    @Test
    fun observePinned_emitsNullAfterClear() = runBlocking {
        dao.replace(record())

        dao.clear()

        assertNull(dao.pinned())
        assertNull(dao.observePinned().first())
    }

    @Test
    fun aFullSizeItineraryPayloadSurvivesTheRoundTrip() = runBlocking {
        // A multi-leg urban trip carries every leg's encoded geometry and turn-by-turn steps, so the
        // stored plan runs to tens of kilobytes. Room reads rows through a CursorWindow, so pin the
        // size here: if a future change stores far more, this is what says so rather than a
        // SQLiteBlobTooBigException in the field.
        val payload = "x".repeat(200_000)

        dao.replace(record().copy(itinerariesJson = payload))

        assertEquals(payload, dao.pinned()!!.itinerariesJson)
    }

    private fun record(selectedIndex: Int = 0) = PinnedTripRecord(
        pinId = SINGLE_PIN_ID,
        formatVersion = 1,
        queryJson = """{"from":{"kind":"GEOCODED"}}""",
        itinerariesJson = "[]",
        selectedIndex = selectedIndex,
        pinnedAtMs = 1_700_000_000_000
    )
}
