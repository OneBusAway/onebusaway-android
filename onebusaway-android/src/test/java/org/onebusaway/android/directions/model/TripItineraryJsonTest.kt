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
package org.onebusaway.android.directions.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * Covers the [List.toJson]/[String.toTripItineraries] round trip used to carry a plan result across
 * the trip-plan-monitor notification `Intent` ([org.onebusaway.android.directions.realtime.TripPlanMonitorService.notifyChange] /
 * [org.onebusaway.android.ui.tripplan.TripPlanScreen.maybeRestoreFromIntent]).
 */
class TripItineraryJsonTest {

    @Test
    fun `round-trips an itinerary through JSON`() {
        val itinerary = TripItinerary(
            startTime = ServerTime(1_700_000_000_000L),
            legs = listOf(TripLeg(mode = TripMode.BUS, tripId = "t1"))
        )

        val decoded = listOf(itinerary).toJson().toTripItineraries()

        assertEquals(listOf(itinerary), decoded)
    }

    /**
     * A corrupted/truncated extra (e.g. the app was updated between writing and reading a pending
     * notification's Intent) must degrade to "nothing to restore", not crash the activity on
     * notification re-entry.
     */
    @Test
    fun `malformed JSON decodes to an empty list instead of throwing`() {
        val decoded = "not valid json".toTripItineraries()

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `truncated JSON decodes to an empty list instead of throwing`() {
        val truncated = listOf(TripItinerary()).toJson().dropLast(10)

        assertTrue(truncated.toTripItineraries().isEmpty())
    }
}
