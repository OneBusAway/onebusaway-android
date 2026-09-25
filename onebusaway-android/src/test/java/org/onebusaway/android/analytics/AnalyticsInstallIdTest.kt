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
    fun `generates a valid random UUID on first access`() {
        val id = AnalyticsInstallId(FakePreferencesRepository()).value
        // Throws if not a valid UUID string.
        assertTrue(UUID.fromString(id).toString() == id)
    }

    @Test
    fun `is stable across repeated reads`() {
        val installId = AnalyticsInstallId(FakePreferencesRepository())
        val first = installId.value
        val second = installId.value
        assertEquals(first, second)
    }

    @Test
    fun `is stable across separate instances sharing the same persisted store`() {
        val prefs = FakePreferencesRepository()
        val first = AnalyticsInstallId(prefs).value
        val second = AnalyticsInstallId(prefs).value
        assertEquals(first, second)
    }

    @Test
    fun `reuses a value already persisted by a prior install`() {
        val prefs = FakePreferencesRepository().apply {
            setString("analyticsInstallId", "existing-id")
        }
        assertEquals("existing-id", AnalyticsInstallId(prefs).value)
    }
}
