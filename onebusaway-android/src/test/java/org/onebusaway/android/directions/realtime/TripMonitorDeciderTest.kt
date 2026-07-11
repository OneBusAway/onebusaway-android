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
package org.onebusaway.android.directions.realtime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.time.ServerTime
import java.util.concurrent.TimeUnit

/**
 * JVM unit tests for [TripMonitorDecider] — the itinerary-diff decision extracted from the legacy
 * `RealtimeChecker.checkForItineraryChange`.
 */
class TripMonitorDeciderTest {

    private val thresholdSeconds = TimeUnit.MINUTES.toSeconds(2)

    /** Builds a single-transit-leg itinerary with the given trip id and end time (epoch millis). */
    private fun itinerary(tripId: String, endTimeMillis: Long): TripItinerary {
        val leg = TripLeg(
            mode = TripMode.BUS,
            realTime = true,
            tripId = tripId,
            endTime = ServerTime(endTimeMillis),
        )
        return TripItinerary(legs = listOf(leg))
    }

    private fun monitoring(tripId: String, endTimeMillis: Long): ItineraryDescription =
        ItineraryDescription(itinerary(tripId, endTimeMillis))

    @Test
    fun emptyResults_stops() {
        val result = TripMonitorDecider.decide(monitoring("t1", 1_000L), emptyList(), thresholdSeconds)
        assertEquals(MonitorResult.Stop, result)
    }

    @Test
    fun matchingItinerary_withinThreshold_keepsMonitoring() {
        val end = 1_000_000L
        val result = TripMonitorDecider.decide(
            monitoring("t1", end),
            listOf(itinerary("t1", end + TimeUnit.SECONDS.toMillis(30))), // 30s later, under 2 min
            thresholdSeconds,
        )
        assertEquals(MonitorResult.KeepMonitoring, result)
    }

    @Test
    fun matchingItinerary_delayedBeyondThreshold_reportsPositiveDeviation() {
        val end = 1_000_000L
        val result = TripMonitorDecider.decide(
            monitoring("t1", end),
            listOf(itinerary("t1", end + TimeUnit.MINUTES.toMillis(5))), // 5 min late
            thresholdSeconds,
        )
        assertEquals(MonitorResult.Deviation(TimeUnit.MINUTES.toSeconds(5)), result)
    }

    @Test
    fun matchingItinerary_earlyBeyondThreshold_reportsNegativeDeviation() {
        val end = 1_000_000L
        val result = TripMonitorDecider.decide(
            monitoring("t1", end),
            listOf(itinerary("t1", end - TimeUnit.MINUTES.toMillis(5))), // 5 min early
            thresholdSeconds,
        )
        assertEquals(MonitorResult.Deviation(-TimeUnit.MINUTES.toSeconds(5)), result)
    }

    @Test
    fun noMatchingItinerary_reportsChanged() {
        val result = TripMonitorDecider.decide(
            monitoring("t1", 1_000_000L),
            listOf(itinerary("t2", 2_000_000L)),
            thresholdSeconds,
        )
        assertEquals(MonitorResult.ItineraryChanged, result)
    }
}
