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
package org.onebusaway.android.extrapolation.data

import org.onebusaway.android.api.data.asRouteTrips

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry
import java.io.File

/**
 * Ports the trip-details cases of the retired instrumented AdaptersTest onto the modernized DTO path:
 * decodes the same trip-details fixtures into the io/client envelope, adapts via the single-entry
 * [asRouteTrips] and distills [toObservations] — one observation for an active vehicle, none when the
 * response has no status or no active trip. JVM-only ([toObservations] reads no Android types).
 */
class TripDetailsObservationsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun decode(fixture: String): ObaEnvelope<EntryWithReferences<TripDetailsEntry>> =
        json.decodeFromString(File("src/androidTest/res/raw/$fixture").readText())

    @Test
    fun distillsOneObservation() {
        val observations = decode("trip_details_hart_1389962.json").asRouteTrips().toObservations()

        assertEquals(1, observations.size)
        val observation = observations[0]
        assertEquals("Hillsborough Area Regional Transit_1389962", observation.tripId)
        assertEquals(1545886800000L, observation.serviceDate)
        // Resolved through the refs: trip 1389962 -> route 16 -> type 3 (bus).
        assertEquals(3, observation.routeType)
    }

    @Test
    fun noStatusYieldsNoObservations() {
        assertTrue(decode("trip_1_18196913.json").asRouteTrips().toObservations().isEmpty())
    }

    @Test
    fun noActiveTripIdYieldsNoObservations() {
        assertTrue(
            decode("trip_details_hart_1389962_no_active_trip.json").asRouteTrips().toObservations()
                .isEmpty()
        )
    }
}
