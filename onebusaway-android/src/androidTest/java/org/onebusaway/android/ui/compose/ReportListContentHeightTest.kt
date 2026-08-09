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
package org.onebusaway.android.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.unit.dp
import java.util.Collections.synchronizedList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * On-device tests for [ReportListContentHeight], the measurement both arrivals sheets use to fit their
 * collapsed peek to short content.
 *
 * The case worth pinning is **replacing the content while reusing the same [LazyListState]**, which is
 * what the drawers do on every poll and every pan. The effect restarts on the new content key before
 * that frame's layout pass has run, so the first `layoutInfo` it can see still describes the previous
 * list. A measurement that reported only that first reading would publish the outgoing list's height
 * and never correct itself — so these tests assert the height the reporter *settles* on, not the first
 * one it emits.
 */
class ReportListContentHeightTest {

    // Unconfined composition — see createUnconfinedComposeRule (issue #1792).
    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val viewportHeight = 200.dp
    private val rowHeight = 20.dp

    @Test
    fun reportsTheHeightOfContentThatFits() {
        val reported = synchronizedList(mutableListOf<Int>())
        composeRule.setContent { Harness(rowCount = FEW_ROWS, reported = reported) }

        assertSettlesAt(heightOf(FEW_ROWS), reported)
    }

    /**
     * The regression this file exists for. Both row counts fit the viewport, so both measurements are
     * exact — which makes this an assertion about the *value*, not about a floor: after the swap the
     * reporter must settle on the incoming list's height, never the outgoing one's.
     */
    @Test
    fun remeasuresWhenContentGrowsOnTheSameListState() {
        val reported = synchronizedList(mutableListOf<Int>())
        var rowCount by mutableStateOf(FEW_ROWS)
        composeRule.setContent { Harness(rowCount = rowCount, reported = reported) }
        assertSettlesAt(heightOf(FEW_ROWS), reported)

        setRowCount { rowCount = MORE_ROWS }

        assertSettlesAt(heightOf(MORE_ROWS), reported)
    }

    /** And the shrinking direction, which a latched first measurement would also have missed. */
    @Test
    fun remeasuresWhenContentShrinksOnTheSameListState() {
        val reported = synchronizedList(mutableListOf<Int>())
        var rowCount by mutableStateOf(MORE_ROWS)
        composeRule.setContent { Harness(rowCount = rowCount, reported = reported) }
        assertSettlesAt(heightOf(MORE_ROWS), reported)

        setRowCount { rowCount = FEW_ROWS }

        assertSettlesAt(heightOf(FEW_ROWS), reported)
    }

    /**
     * Content past the viewport reports a floor rather than a measurement — the bottom edge of the item
     * straddling the viewport's end. The contract is only that it exceeds anything that fits, which is
     * all a caller clamping to a peek cap needs.
     */
    @Test
    fun reportsAFloorForContentTallerThanTheViewport() {
        val reported = synchronizedList(mutableListOf<Int>())
        composeRule.setContent { Harness(rowCount = OVERFLOWING_ROWS, reported = reported) }

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) { reported.isNotEmpty() }
        assertTrue(
            "overflowing content should measure past the fitting rows, got $reported",
            reported.last() > heightOf(MORE_ROWS)
        )
    }

    private fun heightOf(rows: Int) = rows * with(composeRule.density) { rowHeight.roundToPx() }

    /**
     * Waits for [reported] to come to rest at [expected]. `waitForIdle` is not enough: the measurement
     * is published from a `snapshotFlow` collector, so the emission for a freshly laid-out list can
     * land after composition has already gone idle.
     */
    private fun assertSettlesAt(expected: Int, reported: List<Int>) {
        try {
            composeRule.waitUntil(timeoutMillis = TIMEOUT_MS) { reported.lastOrNull() == expected }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("expected to settle at $expected, reported $reported", e)
        }
        assertEquals(expected, reported.last())
    }

    /** Writes happen on the UI thread, so the change reaches composition the way a real one would. */
    private fun setRowCount(set: () -> Unit) = composeRule.runOnIdle(set)

    @Composable
    private fun Harness(
        rowCount: Int,
        reported: MutableList<Int>,
        listState: LazyListState = rememberLazyListState()
    ) {
        val rows = List(rowCount) { "row-$it" }
        Box(Modifier.fillMaxWidth().height(viewportHeight)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(rows, key = { it }) { Box(Modifier.fillMaxWidth().size(rowHeight)) }
            }
        }
        ReportListContentHeight(listState) { px -> reported += px }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** Both of these fit inside viewportHeight (200dp), so their measurements are exact. */
        const val FEW_ROWS = 3
        const val MORE_ROWS = 8

        /** 40 * 20dp = 800dp, well past the viewport. */
        const val OVERFLOWING_ROWS = 40
    }
}
