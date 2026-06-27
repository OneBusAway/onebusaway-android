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
package org.onebusaway.android.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LaunchIntentChannel]'s queue contract — the no-drop / exactly-once / in-order delivery
 * the home launch-intent flow relies on (#1582). Exercised over String "intents" so the contract is
 * checked on the JVM without an Android `Intent`; a regression to a conflating latch (the #1582 bug)
 * fails the burst case below. The intent → route translation half is covered by `IntentRouteMapperTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchIntentChannelTest {

    @Test
    fun `rapid distinct items are each delivered exactly once, in order`() = runTest {
        val channel = LaunchIntentChannel<String>()
        val received = mutableListOf<String>()
        val job = launch { channel.items.collect { received.add(it) } }

        // Burst three distinct items before the collector drains any (e.g. FCM A -> B -> A). A conflating
        // latch would drop "a" and "b"; the UNLIMITED queue delivers all three, in order.
        channel.submit("a")
        channel.submit("b")
        channel.submit("a")
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "a"), received)
        job.cancel()
    }

    @Test
    fun `an item submitted before the collector subscribes is not lost`() = runTest {
        val channel = LaunchIntentChannel<String>()
        channel.submit("home") // staged in onCreate, before the NavHost (collector) composes

        val received = mutableListOf<String>()
        val job = launch { channel.items.collect { received.add(it) } }
        advanceUntilIdle()

        assertEquals(listOf("home"), received)
        job.cancel()
    }
}
