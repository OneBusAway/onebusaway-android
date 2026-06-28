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
package org.onebusaway.android.ui.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.RegionState
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository

/**
 * Unit tests for [applyExperimentalRegionsToggle] — the Context-free wiring of
 * [AdvancedSettingsViewModel.setExperimentalRegions] (clear the region, persist the toggle, then re-resolve
 * and apply the OTP-version reset). Guards the end-to-end behavior the deleted HomeViewModel cases used to
 * cover: that the region is cleared only when an experimental region is turned off, the toggle is persisted,
 * and `refresh()`'s actual result drives [resetOtpVersionOnRegionChange]. (The precedence of the reset rule
 * itself is covered by [RegionOtpResetTest]; the analytics report needs a Context and is left to
 * instrumented coverage.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedSettingsViewModelTest {

    private val EXPERIMENTAL = R.string.preference_key_experimental_regions
    private val OTP_VERSION = R.string.preference_key_otp_api_url_version

    @Test
    fun `turning off an experimental region clears it, persists the toggle, and resets the OTP version`() =
        runTest {
            val repo = FakeRegionRepository(region(1)).apply {
                refreshResult = RegionStatus.Changed(region(2)) // the re-resolve lands on a new region
            }
            val prefs = FakePreferencesRepository().apply { setBoolean(OTP_VERSION, true) }

            applyExperimentalRegionsToggle(
                enabled = false, regionWasExperimental = true, prefs = prefs, regionRepository = repo,
            )

            assertNull("the now-invalid region is cleared", repo.region.value)
            assertFalse("the toggle is persisted", prefs.getBoolean(EXPERIMENTAL, true))
            assertEquals("the region is re-resolved", 1, repo.refreshCount)
            assertFalse("a real change resets the OTP version", prefs.getBoolean(OTP_VERSION, true))
        }

    @Test
    fun `enabling experimental regions persists the toggle without clearing the region`() = runTest {
        val repo = FakeRegionRepository(region(1)).apply { refreshResult = RegionStatus.Unchanged }
        val prefs = FakePreferencesRepository().apply { setBoolean(OTP_VERSION, true) }

        applyExperimentalRegionsToggle(
            enabled = true, regionWasExperimental = false, prefs = prefs, regionRepository = repo,
        )

        assertNotNull("enabling never clears the region", repo.region.value)
        assertTrue("the toggle is persisted", prefs.getBoolean(EXPERIMENTAL, false))
        assertEquals("the region is still re-resolved", 1, repo.refreshCount)
        assertTrue("an unchanged region leaves the OTP version alone", prefs.getBoolean(OTP_VERSION, true))
    }

    @Test
    fun `turning off a non-experimental region persists the toggle but does not clear the region`() =
        runTest {
            val repo = FakeRegionRepository(region(1)).apply { refreshResult = RegionStatus.Unchanged }
            val prefs = FakePreferencesRepository()

            applyExperimentalRegionsToggle(
                enabled = false, regionWasExperimental = false, prefs = prefs, regionRepository = repo,
            )

            assertNotNull("nothing to clear when the region wasn't experimental", repo.region.value)
            assertFalse("the toggle is persisted", prefs.getBoolean(EXPERIMENTAL, true))
        }

    @Test
    fun `a re-resolve that needs a manual pick defers the OTP reset until the user chooses`() = runTest {
        val regions = listOf(region(1), region(2))
        // regionWasExperimental = false so the clear() (which would itself flip state to Active) doesn't run
        // and the pending NeedsManualChoice state survives until the reset rule awaits it.
        val repo = FakeRegionRepository(region(1)).apply {
            refreshResult = RegionStatus.NeedsManualSelection(regions)
            emitState(RegionState.NeedsManualChoice(regions))
        }
        val prefs = FakePreferencesRepository().apply { setBoolean(OTP_VERSION, true) }

        val job = launch {
            applyExperimentalRegionsToggle(
                enabled = false, regionWasExperimental = false, prefs = prefs, regionRepository = repo,
            )
        }
        advanceUntilIdle()
        assertTrue("must not reset before the pick resolves", prefs.getBoolean(OTP_VERSION, true))

        repo.emit(regions[1]) // the user's choice resolves the region
        advanceUntilIdle()
        assertFalse("resets once the region becomes Active", prefs.getBoolean(OTP_VERSION, true))
        job.cancel()
    }
}
