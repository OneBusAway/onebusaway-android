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
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.InstrumentationRegistry.getTargetContext
import androidx.test.runner.AndroidJUnit4
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
 */
@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryReadYourWritesTest {

    private val key = "test_oba_api_url"

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun synchronousReadReflectsLatestWrite_despiteAStaleDataStoreEmission() = runTest {
        val dataStore = FakeDataStore()
        val repo = DefaultPreferencesRepository(getTargetContext(), dataStore, backgroundScope)
        runCurrent() // let the cache machinery subscribe + process the initial (empty) state

        repo.setString(key, "first")   // optimistic cache = "first"
        repo.setString(key, "second")  // optimistic cache = "second" — the value a reader must see
        runCurrent()                   // run the async persists

        // The earlier write commits late: DataStore emits the stale "first" state *after* "second" was
        // set. A collector that blindly mirrored this into the cache would clobber "second".
        dataStore.emit(preferencesOf(stringPreferencesKey(key) to "first"))
        runCurrent()

        assertEquals("second", repo.getString(key, null))
    }

    /**
     * A [DataStore] test double whose committed-state emissions are driven by the test (via [emit])
     * rather than emitted automatically on [updateData] — so a test can reproduce DataStore's real commit
     * latency, in particular a stale emission of an earlier write arriving after a newer one.
     */
    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val emissions = MutableSharedFlow<Preferences>(replay = 1, extraBufferCapacity = 64)
        @Volatile
        private var committed = initial

        init {
            emissions.tryEmit(initial)
        }

        override val data: Flow<Preferences> = emissions

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            committed = transform(committed)
            return committed // deliberately does not emit; the test drives [emit] to model commit latency
        }

        /** Push a committed-state emission to collectors (models DataStore's async data flow). */
        fun emit(prefs: Preferences) = check(emissions.tryEmit(prefs))
    }
}
