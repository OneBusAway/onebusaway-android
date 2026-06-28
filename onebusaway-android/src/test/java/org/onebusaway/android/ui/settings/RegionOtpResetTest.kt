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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.region.RegionState
import org.onebusaway.android.region.RegionStatus
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.MainDispatcherRule

/**
 * Unit tests for [resetOtpVersionOnRegionChange] — the experimental-regions-toggle OTP-version reset rule
 * extracted from [AdvancedSettingsViewModel]: reset on a real change (incl. an `otpBaseUrl == null` region
 * the repository's own reset would miss), leave it alone when unchanged, and defer until the user picks on a
 * forced manual selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegionOtpResetTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anyState = MutableStateFlow<RegionState>(RegionState.Active(null))

    @Test
    fun `a changed region resets the OTP version`() = runTest {
        var reset = false
        resetOtpVersionOnRegionChange(RegionStatus.Changed(region(1)), anyState) { reset = true }
        assertTrue(reset)
    }

    @Test
    fun `unchanged and terminal statuses leave the OTP version untouched`() = runTest {
        for (status in listOf(
            RegionStatus.Unchanged, RegionStatus.Skipped, RegionStatus.Fixed(region(1)), RegionStatus.Failed,
        )) {
            var reset = false
            resetOtpVersionOnRegionChange(status, anyState) { reset = true }
            assertFalse("$status should not reset the OTP version", reset)
        }
    }

    @Test
    fun `a forced manual selection defers the reset until the user picks`() = runTest {
        val regions = listOf(region(1), region(2))
        val state = MutableStateFlow<RegionState>(RegionState.NeedsManualChoice(regions))
        var reset = false

        val job = launch {
            resetOtpVersionOnRegionChange(RegionStatus.NeedsManualSelection(regions), state) { reset = true }
        }
        advanceUntilIdle()
        assertFalse("must not reset before the pick resolves", reset)

        state.value = RegionState.Active(regions[1]) // the user's choice resolved the region
        advanceUntilIdle()
        assertTrue("resets once the region becomes Active", reset)
        job.cancel()
    }
}
