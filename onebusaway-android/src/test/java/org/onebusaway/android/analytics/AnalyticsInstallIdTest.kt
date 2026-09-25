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
package org.onebusaway.android.analytics

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.testing.FakePreferencesRepository

/**
 * Unit tests for [AnalyticsInstallId], the persisted anonymous per-install id sent as Umami's
 * `payload.id` (see [UmamiAnalytics]). Exercised through [FakePreferencesRepository] so no Android
 * context is needed.
 */
class AnalyticsInstallIdTest {

    @Test
    fun `generates a valid random UUID on first call`() {
        val id = AnalyticsInstallId(FakePreferencesRepository()).get()
        // Throws if not a valid UUID string.
        assertTrue(UUID.fromString(id).toString() == id)
    }

    @Test
    fun `is stable across repeated calls`() {
        val installId = AnalyticsInstallId(FakePreferencesRepository())
        val first = installId.get()
        val second = installId.get()
        assertEquals(first, second)
    }

    @Test
    fun `is stable across separate instances sharing the same persisted store`() {
        val prefs = FakePreferencesRepository()
        val first = AnalyticsInstallId(prefs).get()
        val second = AnalyticsInstallId(prefs).get()
        assertEquals(first, second)
    }

    @Test
    fun `reuses a value already persisted by a prior install`() {
        val prefs = FakePreferencesRepository().apply {
            setString("analyticsInstallId", "existing-id")
        }
        assertEquals("existing-id", AnalyticsInstallId(prefs).get())
    }

    @Test
    fun `concurrent first calls converge on a single id`() {
        val installId = AnalyticsInstallId(FakePreferencesRepository())
        val results = CopyOnWriteArrayList<String>()
        val threadCount = 16
        val ready = CountDownLatch(threadCount)
        val go = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        repeat(threadCount) {
            executor.execute {
                ready.countDown()
                go.await()
                results += installId.get()
                done.countDown()
            }
        }
        ready.await()
        go.countDown()
        done.await()
        executor.shutdown()

        assertEquals(threadCount, results.size)
        assertEquals(1, results.toSet().size)
    }
}
