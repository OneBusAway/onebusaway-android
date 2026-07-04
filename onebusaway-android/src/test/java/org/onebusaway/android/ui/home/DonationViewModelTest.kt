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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.donations.DonationsManager
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.ui.home.donation.DonationEffect
import org.onebusaway.android.ui.home.donation.DonationViewModel

/**
 * Unit tests for [DonationViewModel]'s dialog/effect logic — the parts that don't touch the
 * `DonationsManager` (Application) singleton. The manager-backed paths (refresh / donate /
 * dismissForever / remindLater) are exercised by the on-device verification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DonationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // These tests cover only the dialog/effect logic, which never calls the DonationsManager, so a
    // bare instance (its Android collaborators are never touched) is enough to satisfy the constructor.
    private fun donationViewModel() = DonationViewModel(DonationsManager(null, null, 0))

    @Test
    fun `close requests the dismiss confirmation, cancel clears it`() = runTest {
        val vm = donationViewModel()
        assertFalse(vm.state.value.showDismissDialog)

        vm.requestDismiss()
        assertTrue(vm.state.value.showDismissDialog)

        vm.cancelDismiss()
        assertFalse(vm.state.value.showDismissDialog)
    }

    @Test
    fun `learn more emits the open-learn-more effect`() = runTest {
        val vm = donationViewModel()
        val effects = mutableListOf<DonationEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }
        advanceUntilIdle()

        vm.learnMore()
        advanceUntilIdle()

        assertEquals(listOf<DonationEffect>(DonationEffect.OpenLearnMore), effects)
        job.cancel()
    }
}
