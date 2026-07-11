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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.ui.arrivals.components.LoadMoreOutcome
import org.onebusaway.android.ui.arrivals.components.NO_LOAD_REQUEST
import org.onebusaway.android.ui.arrivals.components.loadMoreOutcome
import org.onebusaway.android.ui.arrivals.components.spinnerVisible

/** Truth tables for the ETA strip's pure load-more interpretation (see EtaStripLoadMore.kt). */
class EtaStripLoadMoreTest {

    private val ours = 7
    private val theirs = 8

    // --- loadMoreOutcome -------------------------------------------------------------------------

    @Test
    fun `Idle reads as superseded - the shared slot no longer tracks our request`() {
        assertEquals(LoadMoreOutcome.Superseded, loadMoreOutcome(LoadMoreState.Idle, ours))
    }

    @Test
    fun `Loading with our token is pending`() {
        assertEquals(LoadMoreOutcome.Pending, loadMoreOutcome(LoadMoreState.Loading(ours), ours))
    }

    @Test
    fun `Loading with another strip's token is superseded`() {
        assertEquals(LoadMoreOutcome.Superseded, loadMoreOutcome(LoadMoreState.Loading(theirs), ours))
    }

    @Test
    fun `Finished with our token lands with its dataVersion`() {
        val landed = loadMoreOutcome(LoadMoreState.Finished(ours, success = true, dataVersion = 42L), ours)
        assertEquals(LoadMoreOutcome.Landed(dataVersion = 42L), landed)
    }

    @Test
    fun `NO_LOAD_REQUEST reads as superseded - it can never match a live token`() {
        assertEquals(
            LoadMoreOutcome.Superseded,
            loadMoreOutcome(LoadMoreState.Loading(ours), NO_LOAD_REQUEST)
        )
    }

    @Test
    fun `Finished with another token is superseded`() {
        assertEquals(
            LoadMoreOutcome.Superseded,
            loadMoreOutcome(LoadMoreState.Finished(theirs, success = true, dataVersion = 42L), ours)
        )
    }

    // --- spinnerVisible ----------------------------------------------------------------------------

    @Test
    fun `a pending request shows the spinner`() {
        assertTrue(spinnerVisible(LoadMoreOutcome.Pending, renderedDataVersion = 5L))
    }

    @Test
    fun `a superseded request drops the spinner immediately`() {
        assertFalse(spinnerVisible(LoadMoreOutcome.Superseded, renderedDataVersion = 5L))
    }

    @Test
    fun `a landed request keeps the spinner until the completing data's composition arrives`() {
        val landed = LoadMoreOutcome.Landed(dataVersion = 6L)
        // Still rendering the pre-load data: spinner stays, so it swaps with the new pills atomically.
        assertTrue(spinnerVisible(landed, renderedDataVersion = 5L))
        // The completing data's composition (or a later one) has arrived: spinner gone.
        assertFalse(spinnerVisible(landed, renderedDataVersion = 6L))
        assertFalse(spinnerVisible(landed, renderedDataVersion = 7L))
    }
}
