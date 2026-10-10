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
 * A row hidden by a swipe and brought back by an undo must stay back (#2366). [SwipeToHide]'s swipe
 * state is rememberSaveable, and a lazy list restores saved state under the item's key — so unless
 * the swipe is settled before the hide, the returning row comes back already swiped and hides itself
 * again before it finishes appearing.
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
}
