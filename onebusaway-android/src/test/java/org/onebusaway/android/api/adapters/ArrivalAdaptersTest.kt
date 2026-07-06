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
package org.onebusaway.android.api.adapters

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.TripStatus

/**
 * JVM unit tests for the wire→domain arrival adapter's predicted-instant derivation (issue #1688).
 *
 * When a trip is tracked (`predicted` + a `tripStatus`), the adapter derives the predicted instant
 * from OBA's canonical `predicted = scheduled + scheduleDeviation` rather than the absolute
 * `predicted{Arrival,Departure}Time` wire field. The server computes both from the same deviation,
 * so this is a no-op for healthy data but repairs the WSF "Seattle" terminal (stop `95_7`) defect
 * where the absolute `predictedDepartureTime` is pinned ~15h stale while `scheduleDeviation` is sane.
 *
 * Numbers are the live values captured in the issue (`currentTime = 1783117313123`).
 */
class ArrivalAdaptersTest {

    @Test
    fun `corrupt predicted departure is repaired from scheduleDeviation`() {
        // WSF entry 1: Seattle -> Bremerton, origin terminal (stopSequence 0). The absolute
        // predictedDepartureTime is ~15h stale; scheduleDeviation is a sane +60s.
        val ad = ArrivalDeparture(
            stopSequence = 0,
            predicted = true,
            scheduledDepartureTime = 1_783_120_500_000L,
            predictedDepartureTime = 1_783_062_899_000L, // garbage, ~15h in the past
            tripStatus = TripStatus(predicted = true, scheduleDeviation = 60),
        ).asArrivalData()

        // scheduled 1783120500000 + 60s -> a sane ~+54 min departure, not the -900 min garbage.
        assertEquals(1_783_120_560_000L, ad.predictedDepartureTime.epochMs)
    }

    @Test
    fun `healthy predicted arrival is derived to the same value the server sent (no-op)`() {
        // WSF entry 3: Bainbridge -> Seattle, terminating (stopSequence 1). Here the server's own
        // absolute predictedArrivalTime already equals scheduled + scheduleDeviation.
        val ad = ArrivalDeparture(
            stopSequence = 1,
            predicted = true,
            scheduledArrivalTime = 1_783_117_800_000L,
            predictedArrivalTime = 1_783_118_580_000L, // == scheduled + 780s
            tripStatus = TripStatus(predicted = true, scheduleDeviation = 780),
        ).asArrivalData()

        assertEquals(1_783_118_580_000L, ad.predictedArrivalTime.epochMs)
    }

    @Test
    fun `an early (negative) deviation subtracts from the scheduled time`() {
        val ad = ArrivalDeparture(
            stopSequence = 0,
            predicted = true,
            scheduledDepartureTime = 1_783_120_500_000L,
            predictedDepartureTime = 1_783_062_899_000L, // ignored when tracked
            tripStatus = TripStatus(predicted = true, scheduleDeviation = -120),
        ).asArrivalData()

        assertEquals(1_783_120_500_000L - 120_000L, ad.predictedDepartureTime.epochMs)
    }

    @Test
    fun `without a trip status the absolute predicted time is used (no deviation to derive from)`() {
        val absolute = 1_783_119_500_000L
        val ad = ArrivalDeparture(
            stopSequence = 5,
            predicted = true,
            scheduledArrivalTime = 1_783_119_110_000L,
            predictedArrivalTime = absolute,
            tripStatus = null,
        ).asArrivalData()

        assertEquals(absolute, ad.predictedArrivalTime.epochMs)
    }

    @Test
    fun `a non-positive absolute predicted sentinel still collapses to zero without a trip status`() {
        // #1687 closed-stop sentinel path is unchanged when there is no tripStatus to derive from.
        val ad = ArrivalDeparture(
            stopSequence = 5,
            predicted = true,
            scheduledArrivalTime = 1_783_119_110_000L,
            predictedArrivalTime = -1L,
            tripStatus = null,
        ).asArrivalData()

        assertEquals(0L, ad.predictedArrivalTime.epochMs)
    }

    @Test
    fun `a zero scheduled anchor falls back to the absolute predicted time instead of deviation-only garbage`() {
        // The wire defaults an absent scheduled time to 0. Deriving 0 + deviation would be a
        // ~epoch-1970 instant that passes the downstream `> 0` prediction gate and reproduces the
        // garbage ETA this fix removes; the scheduled>0 guard must fall back to the absolute value.
        val absolute = 1_783_119_500_000L
        val ad = ArrivalDeparture(
            stopSequence = 0,
            predicted = true,
            scheduledDepartureTime = 0L, // absent scheduled anchor
            predictedDepartureTime = absolute, // valid absolute prediction still present
            tripStatus = TripStatus(predicted = true, scheduleDeviation = 60),
        ).asArrivalData()

        assertEquals(absolute, ad.predictedDepartureTime.epochMs)
    }

    @Test
    fun `an unpredicted arrival keeps the sentinel-normalized absolute time`() {
        // predicted=false: even with a tripStatus present we do not synthesize scheduled+deviation;
        // there is no prediction, so the absolute (here absent) instant collapses to 0.
        val ad = ArrivalDeparture(
            stopSequence = 5,
            predicted = false,
            scheduledArrivalTime = 1_783_119_110_000L,
            predictedArrivalTime = 0L,
            tripStatus = TripStatus(scheduleDeviation = 300),
        ).asArrivalData()

        assertEquals(0L, ad.predictedArrivalTime.epochMs)
    }
}
