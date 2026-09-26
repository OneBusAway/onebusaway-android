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
package org.onebusaway.android.ui.home.directions

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionsSheetHeightTest {

    private val peek = 72.dp

    // The fraction is a Float, so a share of a window can land a few ulps off the exact dp.
    private fun assertDp(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.001f)

    @Test
    fun `fills half the window when the form leaves room`() {
        assertDp(400.dp, directionsSheetHeight(windowHeight = 800.dp, formBottom = 240.dp, peekHeight = peek))
    }

    @Test
    fun `stops short of a form that reaches past half the window`() {
        // 640 - 360 - 8dp gap: the half-window sheet (320dp) would slide 48dp over the form.
        assertDp(272.dp, directionsSheetHeight(windowHeight = 640.dp, formBottom = 360.dp, peekHeight = peek))
    }

    @Test
    fun `an unmeasured form caps nothing`() {
        assertDp(400.dp, directionsSheetHeight(windowHeight = 800.dp, formBottom = 0.dp, peekHeight = peek))
    }

    @Test
    fun `never shorter than its own peek`() {
        assertDp(peek, directionsSheetHeight(windowHeight = 400.dp, formBottom = 380.dp, peekHeight = peek))
        assertDp(peek, directionsSheetHeight(windowHeight = 100.dp, formBottom = 0.dp, peekHeight = peek))
    }
}
