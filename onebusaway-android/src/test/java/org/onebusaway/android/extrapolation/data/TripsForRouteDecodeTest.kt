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
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry
import java.io.File

/**
 * Ports the trips-for-route coverage of the instrumented AdaptersTest onto the modernized DTO path:
 * decodes the same HART fixture into the io/client envelope, adapts it through [asRouteTrips] (DTO →
 * the legacy interfaces) and distills [toObservations], asserting the same anchors. JVM-only since
 * [toObservations] reads no Android types (the vehicle position — which needs Location — is exercised
 * by the instrumented ExtrapolatedVehiclesTest).
 */
class TripsForRouteDecodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun decode(path: String): ObaEnvelope<ListWithReferences<TripDetailsEntry>> =
        json.decodeFromString(File(path).readText())

    @Test
    fun distillsOneObservationPerActiveTrip() {
        val routeTrips = decode("src/androidTest/res/raw/trips_for_route_hart_5.json").asRouteTrips()
        val observations = routeTrips.toObservations()

        assertEquals(38, observations.size)
        val first = observations[0]
        assertEquals("Hillsborough Area Regional Transit_101446", first.tripId)
        assertEquals(1444073094612L, first.serverTimeMs.epochMs)
        assertEquals(1444017600000L, first.serviceDate)
        // Resolved through the refs: trip -> route -> type 3 (bus).
        assertEquals(3, first.routeType)
        // Each observation is keyed by the trip its vehicle reported as active.
        observations.forEach { assertEquals(it.status.activeTripId, it.tripId) }
    }

    @Test
    fun noStatusesYieldsNoObservations() {
        val routeTrips =
            decode("src/androidTest/res/raw/trips_for_route_hart_5_no_status.json").asRouteTrips()
        assertTrue(routeTrips.toObservations().isEmpty())
    }
}
