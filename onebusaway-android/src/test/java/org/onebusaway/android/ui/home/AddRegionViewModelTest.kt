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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.region.CustomRegionRequest
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.testing.MainDispatcherRule

/**
 * Unit tests for the consent gate on the `add-region` deep link (#2027, #2030).
 *
 * The property that matters is the *negative* one: staging a request must write nothing at all, because
 * any web page can fire one of these links. Everything else follows from that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddRegionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repo: FakeRegionRepository
    private lateinit var viewModel: AddRegionViewModel

    private val request = CustomRegionRequest(
        name = "Test Deployment",
        obaBaseUrl = "https://api.example.com"
    )

    @Before
    fun setUp() {
        repo = FakeRegionRepository()
        viewModel = AddRegionViewModel(repo)
    }

    @Test
    fun `staging a request writes nothing`() = runTest {
        viewModel.request(request)

        assertEquals(request, viewModel.pending.value)
        assertTrue("no region may be added before the rider confirms", repo.addedCustomRegions.isEmpty())
    }

    @Test
    fun `declining writes nothing and clears the request`() = runTest {
        viewModel.request(request)
        viewModel.decline()

        assertNull(viewModel.pending.value)
        assertTrue(repo.addedCustomRegions.isEmpty())
    }

    @Test
    fun `confirming adds the region`() = runTest {
        viewModel.request(request)
        viewModel.confirm()
        advanceUntilIdle()

        assertEquals(listOf(request), repo.addedCustomRegions)
        assertNull("the dialog closes once confirmed", viewModel.pending.value)
        assertFalse(viewModel.invalid.value)
    }

    @Test
    fun `confirming with nothing staged is a no-op`() = runTest {
        viewModel.confirm()
        advanceUntilIdle()

        assertTrue(repo.addedCustomRegions.isEmpty())
    }

    @Test
    fun `a rejected request surfaces the invalid alert`() = runTest {
        repo.addCustomRegionSucceeds = false
        viewModel.request(request)
        viewModel.confirm()
        advanceUntilIdle()

        assertTrue("an unusable server address must be reported, not swallowed", viewModel.invalid.value)
        assertTrue("a rejected request must not have been saved", repo.addedCustomRegions.isEmpty())

        viewModel.dismissInvalid()
        assertFalse(viewModel.invalid.value)
    }

    @Test
    fun `a second link replaces the one still awaiting an answer`() = runTest {
        val other = request.copy(name = "Other", obaBaseUrl = "https://other.example.com")
        viewModel.request(request)
        viewModel.request(other)

        // Stacked consent dialogs would invite answering the wrong one; the newest link is the one the
        // rider just followed.
        assertEquals(other, viewModel.pending.value)

        viewModel.confirm()
        advanceUntilIdle()
        assertEquals(listOf(other), repo.addedCustomRegions)
    }
}
