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
package org.onebusaway.android.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the [DefaultPreferencesRepository] read-your-writes clobber bug.
 *
 * The repository serves synchronous reads from an in-memory cache that writes update optimistically and
 * then persist to DataStore asynchronously. A collector that mirrored every DataStore emission back into
 * the cache could revert a newer optimistic write when a *stale* emission of an earlier write arrived
 * after it — so `setX(B)` immediately followed by `getX()` could return an earlier value `A`. This is
 * what made the region/custom-URL instrumented tests flake (`expected api.tampa… but was api.pugetsound…`).
 *
 * Also pins the [PreferencesRepository.edit] batch contract (#1978): all staged keys land in a single
 * DataStore commit — the atomicity that multi-slot records (e.g. `PushRegistrationStore`) rely on to
 * never tear across a process death.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryReadYourWritesTest {

    private val key = "test_oba_api_url"

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun synchronousReadReflectsLatestWrite_despiteAStaleDataStoreEmission() = runTest {
        val dataStore = FakeDataStore()
        val repo = DefaultPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext, dataStore, backgroundScope)
        runCurrent() // let the cache machinery subscribe + process the initial (empty) state

        repo.setString(key, "first") // optimistic cache = "first"
        repo.setString(key, "second") // optimistic cache = "second" — the value a reader must see
        runCurrent() // run the async persists

        // The earlier write commits late: DataStore emits the stale "first" state *after* "second" was
        // set. A collector that blindly mirrored this into the cache would clobber "second".
        dataStore.emit(preferencesOf(stringPreferencesKey(key) to "first"))
        runCurrent()

        assertEquals("second", repo.getString(key, null))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun edit_commitsAllStagedKeysInOneDataStoreUpdate() = runTest {
        val dataStore = FakeDataStore()
        val repo = DefaultPreferencesRepository(InstrumentationRegistry.getInstrumentation().targetContext, dataStore, backgroundScope)
        runCurrent()
        repo.setString("preexisting", "value")
        runCurrent()
        val updatesBefore = dataStore.updateCount

        repo.edit {
            setString("record_a", "a")
            setLong("record_b", 7L)
            setString("preexisting", null) // null removes, same as setString
        }

        // Read-your-writes holds across the whole batch, synchronously.
        assertEquals("a", repo.getString("record_a", null))
        assertEquals(7L, repo.getLong("record_b", 0L))
        assertEquals(null, repo.getString("preexisting", null))

        // The batch persists as ONE commit — the atomicity multi-slot records depend on — and the
        // committed state carries every staged key.
        runCurrent()
        assertEquals(1, dataStore.updateCount - updatesBefore)
        assertEquals("a", dataStore.committed[stringPreferencesKey("record_a")])
        assertEquals(7L, dataStore.committed[longPreferencesKey("record_b")])
        assertEquals(null, dataStore.committed[stringPreferencesKey("preexisting")])
    }

    /**
     * A [DataStore] test double whose committed-state emissions are driven by the test (via [emit])
     * rather than emitted automatically on [updateData] — so a test can reproduce DataStore's real commit
     * latency, in particular a stale emission of an earlier write arriving after a newer one.
     */
    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val emissions = MutableSharedFlow<Preferences>(replay = 1, extraBufferCapacity = 64)

        /** The state the last [updateData] committed. */
        @Volatile
        var committed: Preferences = initial
            private set

        /** Number of [updateData] commits so far — one per DataStore edit, however many keys it carries. */
        @Volatile
        var updateCount = 0
            private set

        init {
            emissions.tryEmit(initial)
        }

        override val data: Flow<Preferences> = emissions

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            committed = transform(committed)
            updateCount++
            return committed // deliberately does not emit; the test drives [emit] to model commit latency
        }

        /** Push a committed-state emission to collectors (models DataStore's async data flow). */
        fun emit(prefs: Preferences) = check(emissions.tryEmit(prefs))
    }
}
