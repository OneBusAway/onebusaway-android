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
package org.onebusaway.android.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for [pillColumnsThatFit]. Text measures 10px a character; pills pad 20px, are at least 50px
 * wide and 5px apart — so "3:42pm" is an 80px pill.
 */
class PillColumnsThatFitTest {

    private fun fit(rowWidthPx: Float, vararg rows: List<String>) = pillColumnsThatFit(
        rowWidthPx = rowWidthPx,
        rows = rows.toList(),
        measurePx = { it.length * 10f },
        pillPaddingPx = 20f,
        pillMinWidthPx = 50f,
        gapPx = 5f
    )

    private val twelveHour = listOf("3:42pm", "3:57pm", "4:12pm") // 80px pills: 80, 165, 250 cumulative
    private val twentyFourHour = listOf("15:42", "15:57", "16:12") // 70px pills: 70, 145, 220 cumulative

    @Test
    fun `columns follow the width the clock times actually need`() {
        assertEquals(3, fit(250f, twelveHour))
        assertEquals(2, fit(249f, twelveHour))
        assertEquals(1, fit(164f, twelveHour))
        // The same widget width holds a column more in 24-hour time, which is narrower.
        assertEquals(3, fit(220f, twentyFourHour))
        assertEquals(2, fit(220f, twelveHour))
    }

    @Test
    fun `a pill is never narrower than its minimum width`() {
        // "a" measures 10px + 20px padding, but still takes the 50px minimum: 50, 105, 160.
        assertEquals(2, fit(159f, listOf("a", "b", "c")))
        assertEquals(3, fit(160f, listOf("a", "b", "c")))
    }

    @Test
    fun `the tightest row decides for every row, and short rows don't hold the others back`() {
        val short = listOf("3:42pm")
        assertEquals(2, fit(200f, short, twelveHour))
        assertEquals(3, fit(300f, short, twelveHour))
    }

    @Test
    fun `at least one column is always shown, even when nothing fits`() {
        assertEquals(1, fit(10f, twelveHour))
        assertEquals(1, fit(300f))
        assertEquals(1, fit(300f, emptyList()))
    }
}
