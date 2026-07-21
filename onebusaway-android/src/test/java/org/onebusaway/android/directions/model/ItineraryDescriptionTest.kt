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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [ItineraryDescription.itineraryMatches] — identity compares the normalized
 * (feed-prefix-stripped) trip ids, so the same trip matches across the OTP1/OTP2 id vocabularies.
 */
class ItineraryDescriptionTest {

    private fun desc(vararg tripIds: String) = ItineraryDescription(tripIds.toList(), endDate = null)

    @Test
    fun sameTripAcrossSchemesMatches() {
        // OTP1 bare id vs OTP2 feed-prefixed id for the same GTFS trip.
        assertTrue(desc("trip_5").itineraryMatches(desc("1:trip_5")))
        assertTrue(desc("1:trip_5").itineraryMatches(desc("trip_5")))
    }

    @Test
    fun underscoreIsPreservedInTheEntityId() {
        assertTrue(desc("agency_trip_5").itineraryMatches(desc("1:agency_trip_5")))
    }

    @Test
    fun multiLegOrderIsSignificant() {
        assertTrue(desc("a", "b").itineraryMatches(desc("1:a", "2:b")))
        assertFalse(desc("a", "b").itineraryMatches(desc("b", "a")))
    }

    @Test
    fun distinctTripsDoNotMatch() {
        assertFalse(desc("trip_5").itineraryMatches(desc("trip_6")))
        // Different entities that merely share a feed prefix must not collapse together.
        assertFalse(desc("1:trip_5").itineraryMatches(desc("1:trip_6")))
    }

    @Test
    fun normalizedTripIdsStripTheFeedPrefixButKeepRawIds() {
        val d = desc("1:trip_5", "kcm:102574")
        assertEquals(listOf("trip_5", "102574"), d.normalizedTripIds)
        // Raw ids are retained as stored (that is what the monitor persists).
        assertEquals(listOf("1:trip_5", "kcm:102574"), d.tripIds)
    }
}
