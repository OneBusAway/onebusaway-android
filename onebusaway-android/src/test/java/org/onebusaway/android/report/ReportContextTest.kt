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
package org.onebusaway.android.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round-trip tests for the [ReportContext] nav-arg codec — the field mapping/ordering the report flow rides on. */
class ReportContextTest {

    private val fullTrip = TripReportContext(
        tripId = "t1", routeId = "r1", shortName = "8", routeLongName = "Capitol Hill",
        headsign = "Mount Baker", vehicleId = "v9", stopId = "1_75403",
        serviceDate = 1_700_000_000_000L, predicted = true,
        predictedArrivalTime = 11L, predictedDepartureTime = 22L,
        scheduledArrivalTime = 33L, scheduledDepartureTime = 44L,
        hasTripStatus = true, scheduleDeviation = -120L,
        lastKnownLat = 47.61, lastKnownLon = -122.34,
    )

    @Test
    fun `full context round-trips every field`() {
        val ctx = ReportContext(
            stopId = "1_75403", stopName = "Pine St & 3rd Ave (N)", stopCode = "75403",
            lat = 47.6062, lon = -122.3321, locationString = "near 3rd & Pine",
            agencyName = "Metro Transit", blockId = "block-7", trip = fullTrip,
        )
        assertEquals(ctx, ReportContext.decode(ctx.encode()))
    }

    @Test
    fun `null or empty decodes to an empty context`() {
        assertEquals(ReportContext(), ReportContext.decode(null))
        assertEquals(ReportContext(), ReportContext.decode(""))
    }

    @Test
    fun `stop-only context carries no trip`() {
        val ctx = ReportContext(
            stopId = "s1", stopName = "Name", stopCode = "c", lat = 1.0, lon = 2.0,
            locationString = "loc",
        )
        val round = ReportContext.decode(ctx.encode())
        assertEquals(ctx, round)
        assertNull(round.trip)
    }

    @Test
    fun `a trip without status keeps its nullable location null`() {
        val ctx = ReportContext(trip = fullTrip.copy(hasTripStatus = false, scheduleDeviation = 0L, lastKnownLat = null, lastKnownLon = null))
        val round = ReportContext.decode(ctx.encode())
        assertEquals(ctx, round)
        assertNull(round.trip?.lastKnownLat)
    }

    @Test
    fun `values containing the delimiter and commas survive`() {
        val ctx = ReportContext(stopName = "A|B,C/D 123|", locationString = "x|y|z", trip = fullTrip.copy(headsign = "to|wn, ave"))
        assertEquals(ctx, ReportContext.decode(ctx.encode()))
    }
}
