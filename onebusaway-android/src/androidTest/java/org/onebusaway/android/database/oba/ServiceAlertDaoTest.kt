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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase

/**
 * Verifies [ServiceAlertDao]'s insert-then-update `@Transaction` helpers and the reactive `hideDecisions`
 * stream against a real Room DB. The tri-state hidden column (NULL = no decision, 0 = shown, 1 = hidden)
 * is #1593-critical: marking read must not fabricate a hide decision, and only explicit decisions stream.
 */
@RunWith(AndroidJUnit4::class)
class ServiceAlertDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ServiceAlertDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        dao = db.serviceAlertDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun markRead_stampsRead_withoutFabricatingAHideDecision() = runBlocking {
        dao.markRead("a1", now = 500)

        assertFalse(dao.isHidden("a1"))
        assertTrue(dao.hideDecisions().first().isEmpty())   // hidden stayed NULL -> not a decision
    }

    @Test
    fun setHidden_recordsDecision_andHideDecisionsEmitsIt() = runBlocking {
        dao.setHidden("a1", true)
        assertTrue(dao.isHidden("a1"))
        assertEquals(listOf("a1"), dao.hideDecisions().first().map { it.id })

        // Flip to shown: still an explicit decision (hidden = 0, not NULL), so it still streams.
        dao.setHidden("a1", false)
        assertFalse(dao.isHidden("a1"))
        assertEquals(1, dao.hideDecisions().first().size)
    }

    @Test
    fun markReadThenHide_updateSameRow_bothStatesCoexist() = runBlocking {
        dao.markRead("a1", now = 100)   // inserts row, read stamped, hidden NULL
        dao.setHidden("a1", true)       // updates the same row's hidden decision

        assertTrue(dao.isHidden("a1"))
        assertEquals(1, dao.hideDecisions().first().size)   // one row, not a duplicate insert
    }
}
