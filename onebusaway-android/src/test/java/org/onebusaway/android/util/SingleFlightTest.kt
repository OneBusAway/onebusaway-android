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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `an inline dispatcher with a non-suspending block clears the completed entry`() = runTest {
        // Dispatchers.Unconfined runs continuations inline on the current thread. Were the async
        // start eager rather than LAZY, the block would run inside async() — which is called inside
        // computeIfAbsent's mapping function — so the finally/remove would fire re-entrantly there.
        // The LAZY start defers execution to await() (below), which runs after computeIfAbsent has
        // returned and stored the Deferred. A stale entry would make the second call join the
        // first's completed Deferred instead of running fresh.
        val singleFlight = SingleFlight<String, Int>(CoroutineScope(Dispatchers.Unconfined))
        var executions = 0

        assertEquals(1, singleFlight.run("k") { ++executions })
        assertEquals(2, singleFlight.run("k") { ++executions })
        assertEquals(2, executions)
    }

    @Test
    fun `a cancelled block propagates instead of resolving to null`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)

        // CancellationException is rethrown ahead of the catch-to-null, so a stopped flight surfaces
        // as cancellation to the caller rather than masquerading as a null (no-result) success.
        val thrown = runCatching {
            singleFlight.run("k") { throw CancellationException("stop") }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `the entry clears after a cancelled flight so a later call runs fresh`() = runTest {
        val singleFlight = SingleFlight<String, Int>(backgroundScope)
        var executions = 0

        // The finally/remove runs even when the block is cancelled, so the entry does not linger.
        runCatching { singleFlight.run("k") { executions++; throw CancellationException("stop") } }

        assertEquals(7, singleFlight.run("k") { executions++; 7 })
        assertEquals(2, executions) // the second call ran fresh rather than joining a dead flight
    }

    // One failure branch stays uncovered: a *non-cancellation* exception resolves to null for every
    // joined caller. That path logs via android.util.Log.e, which is unmocked in plain JVM tests
    // (this module avoids Robolectric/mocking for unit tests), so it is currently untested — a
    // candidate for an instrumented test. The cancellation-propagation and entry-cleared-on-
    // cancellation branches above never reach Log, so they are exercised here.
}
