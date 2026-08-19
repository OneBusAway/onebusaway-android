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
package org.onebusaway.android.ui.tripresults

import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * JVM tests for [resolvedAt] — the one place the two shapes of "how the rider gets to this
 * stop" collapse to a comparable instant, and so the whole of what putting a clock to them means.
 */
class ReachStopTest {

    @Test
    fun aWalkKeepsItsDistanceAsTheClockRuns() {
        // Measured *from now* when the plan names no departure to wait for, so the rule holds still at the
        // rider's walking distance while the strip's pills flow past it — "if you set off now" (#2227).
        val onFoot = ReachStop.OnFoot(4.minutes, notBefore = null)

        assertEquals(ServerTime(14 * 60_000L), onFoot.resolvedAt(ServerTime(10 * 60_000L)))
        assertEquals(ServerTime(24 * 60_000L), onFoot.resolvedAt(ServerTime(20 * 60_000L)))
    }

    @Test
    fun aWalkAPlanHasNotStartedYet_waitsForThePlansOwnDeparture() {
        // A depart-at plan read hours early: the rider sets off at the departure they asked for, not the
        // moment they opened the itinerary, so the rule stands at that departure plus the walk however
        // early the strip polls (#2248). Otherwise this leg would offer live departures while every later
        // leg of the same plan reported nothing boardable.
        val onFoot = ReachStop.OnFoot(4.minutes, notBefore = ServerTime(60 * 60_000L))

        assertEquals(ServerTime(64 * 60_000L), onFoot.resolvedAt(ServerTime(10 * 60_000L)))
        assertEquals(ServerTime(64 * 60_000L), onFoot.resolvedAt(ServerTime(59 * 60_000L)))
    }

    @Test
    fun aWalkPastThePlansDeparture_setsOffNow() {
        // Once the planned departure is behind us the floor stops binding and the rule resumes trailing
        // the clock — the rider is setting out now, like any other. The boundary is the first assertion:
        // the two cases meet at the planned departure rather than jumping at it.
        val onFoot = ReachStop.OnFoot(4.minutes, notBefore = ServerTime(60 * 60_000L))

        assertEquals(ServerTime(64 * 60_000L), onFoot.resolvedAt(ServerTime(60 * 60_000L)))
        assertEquals(ServerTime(74 * 60_000L), onFoot.resolvedAt(ServerTime(70 * 60_000L)))
    }

    @Test
    fun anArrivalStandsWhereThePlanPutIt() {
        // An absolute moment the plan commits to, so the clock it is handed makes no difference — which
        // is also why a strip ruled at one never needs a ticking clock.
        val onArrival = ReachStop.OnArrival(ServerTime(14 * 60_000L))

        assertEquals(ServerTime(14 * 60_000L), onArrival.resolvedAt(ServerTime(10 * 60_000L)))
        assertEquals(ServerTime(14 * 60_000L), onArrival.resolvedAt(ServerTime(20 * 60_000L)))
    }
}
