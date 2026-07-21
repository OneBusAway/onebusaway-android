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
package org.onebusaway.android.directions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** JVM unit tests for [gtfsEntitySuffix], the sanctioned OTP entity-id normalization (colon-only). */
class OtpGtfsIdsTest {

    @Test
    fun stripsTheFeedPrefixAfterTheColon() {
        assertEquals("102574", gtfsEntitySuffix("kcm:102574"))
        assertEquals("2LINE", gtfsEntitySuffix("40:2LINE"))
        assertEquals("trip_5", gtfsEntitySuffix("1:trip_5"))
    }

    @Test
    fun passesAnUnprefixedIdThrough() {
        // An OTP1 bare id has no feed prefix and must survive unchanged, so it compares equal to the
        // normalized OTP2 form of the same trip.
        assertEquals("trip_5", gtfsEntitySuffix("trip_5"))
    }

    @Test
    fun doesNotSplitOnUnderscore() {
        // Underscore is NOT a delimiter here (OBA ids are agency_entity, and GTFS ids contain
        // underscores). Only the colon feed-prefix is stripped.
        assertEquals("agency_trip_5", gtfsEntitySuffix("agency_trip_5"))
        assertEquals("agency_trip_5", gtfsEntitySuffix("1:agency_trip_5"))
    }

    @Test
    fun onlyTheFirstColonDelimitsTheFeed() {
        assertEquals("route:42", gtfsEntitySuffix("feed:route:42"))
    }

    @Test
    fun nullAndBlankReturnNull() {
        assertNull(gtfsEntitySuffix(null))
        assertNull(gtfsEntitySuffix(""))
    }
}
