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
package org.onebusaway.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for [ObaSituation.contentKey], which collapses republished-duplicate alerts so they
 * don't stack up as identical rows (see #1593): identical rider-visible content shares a key even
 * when id/creation-time/active-window differ, while any content difference yields a distinct key.
 */
class ObaSituationContentKeyTest {

    @Test
    fun `identical content shares a key despite different ids and windows`() {
        // Mirrors the real Adelaide Metro feed: same alert republished under new ids, creation
        // times, and (potentially) active windows.
        val a = situation(id = "22_A1782622866", summary = "Stop closure - Tea Tree Plaza", from = 100, to = 0)
        val b = situation(id = "22_A1782623766", summary = "Stop closure - Tea Tree Plaza", from = 200, to = 999)

        assertEquals(a.contentKey, b.contentKey)
    }

    @Test
    fun `different summaries yield different keys`() {
        val a = situation(id = "1", summary = "Stop closure - Tea Tree Plaza")
        val b = situation(id = "2", summary = "Detour - O-Bahn Bus Tunnel")

        assertNotEquals(a.contentKey, b.contentKey)
    }

    @Test
    fun `content differing only in description yields different keys`() {
        val a = situation(id = "1", summary = "Stop closure", description = "Until Monday")
        val b = situation(id = "2", summary = "Stop closure", description = "Until Friday")

        assertNotEquals(a.contentKey, b.contentKey)
    }

    @Test
    fun `adjacent field values cannot collide across the separator`() {
        // "ab" + "" must not key-equal "a" + "b"; the NUL separator guards against this.
        val a = situation(id = "1", summary = "ab", description = "")
        val b = situation(id = "2", summary = "a", description = "b")

        assertNotEquals(a.contentKey, b.contentKey)
    }

    @Test
    fun `distinctBy contentKey keeps the first occurrence`() {
        val a = situation(id = "first", summary = "Stop closure")
        val b = situation(id = "second", summary = "Stop closure")

        val deduped = listOf(a, b).distinctBy { it.contentKey }

        assertEquals(1, deduped.size)
        // The kept id must still resolve a real situation for the detail dialog.
        assertEquals("first", deduped[0].id)
    }

    // --- fixture ---

    private fun situation(
        id: String,
        summary: String? = null,
        description: String? = null,
        severity: String? = null,
        from: Long = 0,
        to: Long = 0
    ): ObaSituation = FakeSituation(
        id = id,
        summary = summary,
        description = description,
        severity = severity,
        activeWindows =
            if (from != 0L || to != 0L) arrayOf(FakeActiveWindow(from, to)) else emptyArray()
    )

    private class FakeSituation(
        override val id: String,
        override val summary: String?,
        override val description: String?,
        override val severity: String?,
        override val activeWindows: Array<ObaSituation.ActiveWindow>
    ) : ObaSituation {
        override val advice: String? = null
        override val reason: String? = null
        override val url: String? = null
        override val creationTime: Long = 0L
        override val allAffects: Array<ObaSituation.AllAffects> = emptyArray()
        override val consequences: Array<ObaSituation.Consequence> = emptyArray()
    }

    private class FakeActiveWindow(
        override val from: Long,
        override val to: Long
    ) : ObaSituation.ActiveWindow
}
