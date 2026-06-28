/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [SingleFlight]'s coalescing contract: concurrent callers for one key share a single
 * execution, while an entry clears on completion so a later call runs fresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {

    @Test
    fun `concurrent calls for the same key share one execution`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)
        val firstEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executions = 0

        val block: suspend () -> Int = {
            executions++
            firstEntered.complete(Unit)
            release.await() // hold the in-flight execution open so the second caller has to join it
            42
        }

        val a = async { singleFlight.run("k", block) }
        firstEntered.await() // a is now running block and parked on release
        val b = async { singleFlight.run("k", block) }
        runCurrent()
        release.complete(Unit)

        assertEquals(42, a.await())
        assertEquals(42, b.await())
        assertEquals(1, executions) // b joined a's flight rather than running the block again
    }

    @Test
    fun `a later call after completion runs the block again`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)
        var executions = 0

        // Entries clear on completion, so the second call is a fresh execution, not a joined one.
        assertEquals(1, singleFlight.run("k") { executions++; 1 })
        assertEquals(2, singleFlight.run("k") { executions++; 2 })
        assertEquals(2, executions)
    }

    @Test
    fun `an eager dispatcher with a non-suspending block clears the completed entry`() = runTest {
        // Dispatchers.Unconfined runs the block synchronously to completion before async() returns.
        // The block never suspends, so this exercises the path where the finally/remove could fire
        // re-entrantly inside computeIfAbsent — which the LAZY start guards against. A stale entry
        // would make the second call join the first's completed Deferred instead of running fresh.
        val singleFlight = SingleFlight<String, Int>(CoroutineScope(Dispatchers.Unconfined))
        var executions = 0

        assertEquals(1, singleFlight.run("k") { ++executions })
        assertEquals(2, singleFlight.run("k") { ++executions })
        assertEquals(2, executions)
    }

    // The failure path (a throwing block resolves to null and clears its entry) is left to
    // instrumented coverage: SingleFlight logs via android.util.Log.e, which is unmocked in plain
    // JVM tests here, and this module deliberately avoids Robolectric/mocking for unit tests.
}
