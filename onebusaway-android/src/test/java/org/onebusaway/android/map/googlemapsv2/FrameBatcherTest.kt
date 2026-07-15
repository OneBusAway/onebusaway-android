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
package org.onebusaway.android.map.googlemapsv2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameBatcherTest {
    @Test
    fun `applies at most one batch per scheduled frame`() {
        val frames = ArrayDeque<() -> Unit>()
        val applied = mutableListOf<Int>()
        val batcher = FrameBatcher<Int>(batchSize = 8, scheduleNextFrame = frames::addLast)

        batcher.submit((1..9).toList(), applied::add)
        assertEquals(emptyList<Int>(), applied)

        frames.removeFirst().invoke()
        assertEquals((1..8).toList(), applied)
        assertEquals(1, frames.size)

        frames.removeFirst().invoke()
        assertEquals((1..9).toList(), applied)
        assertFalse(batcher.cancel())
    }

    @Test
    fun `replacement invalidates an already scheduled callback`() {
        val frames = ArrayDeque<() -> Unit>()
        val applied = mutableListOf<Int>()
        val batcher = FrameBatcher<Int>(batchSize = 1, scheduleNextFrame = frames::addLast)

        batcher.submit(listOf(1), applied::add)
        batcher.submit(listOf(2), applied::add)

        frames.removeFirst().invoke()
        assertEquals(emptyList<Int>(), applied)
        frames.removeFirst().invoke()
        assertEquals(listOf(2), applied)
    }

    @Test
    fun `cancel invalidates pending work`() {
        val frames = ArrayDeque<() -> Unit>()
        val applied = mutableListOf<Int>()
        val batcher = FrameBatcher<Int>(batchSize = 8, scheduleNextFrame = frames::addLast)

        batcher.submit(listOf(1), applied::add)
        assertTrue(batcher.cancel())
        frames.removeFirst().invoke()

        assertEquals(emptyList<Int>(), applied)
    }
}
