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
package org.onebusaway.android.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyedMutexTest {

    @Test
    fun `actions on the same key run one at a time, in the order they acquired`() = runTest(UnconfinedTestDispatcher()) {
        val mutex = KeyedMutex<String>()
        val order = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        // First action acquires the key and parks mid-flight.
        launch {
            mutex.withLock("k") {
                order.add("a-start")
                gate.await()
                order.add("a-end")
            }
        }
        // Second action for the same key must wait behind the first — it can't start yet.
        launch { mutex.withLock("k") { order.add("b") } }
        advanceUntilIdle()
        assertEquals(listOf("a-start"), order)

        // Releasing the first lets the second run — and only then.
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("a-start", "a-end", "b"), order)
    }

    @Test
    fun `actions on different keys do not block each other`() = runTest(UnconfinedTestDispatcher()) {
        val mutex = KeyedMutex<String>()
        val order = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()

        // k1 parks holding its own lock...
        launch {
            mutex.withLock("k1") {
                gate.await()
                order.add("k1")
            }
        }
        // ...but k2 is a different key, so it runs immediately regardless.
        launch { mutex.withLock("k2") { order.add("k2") } }
        advanceUntilIdle()
        assertEquals(listOf("k2"), order)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("k2", "k1"), order)
    }

    @Test
    fun `withLock returns the action's result`() = runTest(UnconfinedTestDispatcher()) {
        val mutex = KeyedMutex<String>()

        val result = mutex.withLock("k") { 42 }

        assertEquals(42, result)
    }
}
