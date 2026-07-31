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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * Where the "you get to this stop" rule lands in a Board row's live ETA strip (#2125): after every
 * departure the rider can't be there for, before the first one they can.
 */
class DeparturesBeforeTest {

    private fun minutes(vararg m: Long) = m.map { ServerTime(it * 60_000L) }

    @Test
    fun `the rule follows the departures that leave before the rider gets there`() {
        // Departures at 2, 9 and 17 minutes; the rider's walk lands at 10.
        assertEquals(2, departuresBefore(minutes(2, 9, 17), ServerTime(10 * 60_000L)))
    }

    @Test
    fun `a rider already at the stop puts the rule ahead of every departure`() {
        assertEquals(0, departuresBefore(minutes(2, 9, 17), ServerTime(0L)))
    }

    @Test
    fun `a rider arriving after everything the feed knows puts the rule at the end`() {
        assertEquals(3, departuresBefore(minutes(2, 9, 17), ServerTime(60 * 60_000L)))
    }

    @Test
    fun `the departure the plan boards is not ruled out by its own arrival time`() {
        // An exact tie is the common case, not an edge one: an OTP plan whose walk is timed to the
        // vehicle hands back the same instant for both. Counting it as missed would dim the very ride
        // the row is about.
        assertEquals(1, departuresBefore(minutes(2, 9, 17), ServerTime(9 * 60_000L)))
    }

    @Test
    fun `an empty strip has nothing to rule`() {
        assertEquals(0, departuresBefore(emptyList(), ServerTime(9 * 60_000L)))
    }
}
