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
package org.onebusaway.android.ui.arrivals.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a clock time shows the timetable time it corrects, struck through (#2167).
 *
 * The whole rule is "do the two *formatted* times differ", which is why it is worth pinning: the
 * tempting alternatives — compare the instants, or strike through only when the trip is late — are
 * each wrong at one end, and the cases below are exactly those two ends.
 */
class ArrivalClockTest {

    @Test
    fun `a prediction that formats the same as the timetable corrects nothing`() {
        // A prediction seconds off the timetable prints the identical clock time. Comparing the
        // instants instead would strike "10:42 AM" through and replace it with "10:42 AM".
        val clock = arrivalClockOf(expected = "10:42 AM", scheduled = "10:42 AM")

        assertEquals("10:42 AM", clock.expected)
        assertNull("nothing to strike through", clock.corrects)
    }

    @Test
    fun `a prediction on a different clock minute corrects the timetable time`() {
        val clock = arrivalClockOf(expected = "10:47 AM", scheduled = "10:42 AM")

        assertEquals("10:47 AM", clock.expected)
        assertEquals("10:42 AM", clock.corrects)
    }

    @Test
    fun `an on-time trip still corrects a time that lands on the next minute`() {
        // Inside the +-90s ON_TIME_BAND, so the app calls this trip on time and colours it green — but
        // the rider's timetable said 10:42 and the bus is now due at 10:43, and that is the whole
        // point of printing the correction. A "strike through only when late" rule would miss it.
        val clock = arrivalClockOf(expected = "10:43 AM", scheduled = "10:42 AM")

        assertEquals("10:42 AM", clock.corrects)
    }

    @Test
    fun `an early prediction corrects the timetable time too`() {
        // Not just lateness: a bus running ahead of its timetable moves the rider's time as much as a
        // late one does.
        val clock = arrivalClockOf(expected = "10:38 AM", scheduled = "10:42 AM")

        assertEquals("10:42 AM", clock.corrects)
    }
}
