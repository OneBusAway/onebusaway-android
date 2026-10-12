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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * [SwipeToHide] hides a row only on a swipe that ends dismissed (#2366): a drag past the threshold
 * that's pulled back before release must leave it alone, and a row hidden by a swipe and brought back
 * by an undo must stay back — a lazy list restores saveable state under the item's key, so a saved
 * swipe state would bring the row back already swiped, and it would hide itself again.
 */
@RunWith(AndroidJUnit4::class)
class SwipeToHideTest {

    @get:Rule
    val compose = createUnconfinedComposeRule()

    @Test
    fun rowBroughtBackAfterSwipeHideStaysShown() {
        val rows = mutableStateListOf("a", "b")
        var hides = 0
        compose.setContent {
            LazyColumn {
                items(rows, key = { it }) { row ->
                    SwipeToHide(onHide = {
                        hides++
                        rows.remove(row)
                    }) {
                        Text(row, Modifier.fillMaxWidth().height(64.dp).testTag(row))
                    }
                }
            }
        }

        compose.onNodeWithTag("a").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(1, hides)
        assertEquals(listOf("b"), rows.toList())

        // The undo: the same key returns to the list.
        compose.runOnIdle { rows.add(0, "a") }
        compose.waitForIdle()

        assertEquals("the returning row must not hide itself again", 1, hides)
        compose.onNodeWithTag("a").assertIsDisplayed()
    }

    @Test
    fun dragPastThresholdThenBackDoesNotHide() {
        val rows = mutableStateListOf("a", "b")
        var hides = 0
        compose.setContent {
            LazyColumn {
                items(rows, key = { it }) { row ->
                    SwipeToHide(onHide = {
                        hides++
                        rows.remove(row)
                    }) {
                        Text(row, Modifier.fillMaxWidth().height(64.dp).testTag(row))
                    }
                }
            }
        }

        // Well past the halfway threshold, then all the way back, then release.
        compose.onNodeWithTag("a").performTouchInput {
            down(centerLeft)
            moveBy(Offset(width * 0.8f, 0f))
            moveBy(Offset(-width * 0.8f, 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals("a swipe pulled back before release must not hide the row", 0, hides)
        compose.onNodeWithTag("a").assertIsDisplayed()
    }
}
