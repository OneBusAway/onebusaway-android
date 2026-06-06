/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.test

import androidx.test.InstrumentationRegistry.getTargetContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.app.Application
import org.onebusaway.android.extrapolation.data.toObservations
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripsForRouteRequest
import org.onebusaway.android.io.test.ObaTestCase
import org.onebusaway.android.mock.MockRegion

/**
 * Tests the response adapters (Adapters.kt) against mock API responses from /res/raw: the
 * distillation of trip-details and trips-for-route responses into TripObservations, including
 * the skip guards for responses without a status or an active trip ID.
 */
class AdaptersTest : ObaTestCase() {

    companion object {
        private const val HART_TRIP_ID = "Hillsborough Area Regional Transit_1389962"
        private const val HART_ROUTE_ID = "Hillsborough Area Regional Transit_5"
    }

    // --- ObaTripDetailsResponse.toObservations ---

    @Test
    fun detailsResponseDistillsOneObservation() {
        Application.get().setCurrentRegion(MockRegion.getTampa(getTargetContext()))
        val response =
                ObaTripDetailsRequest.Builder(getTargetContext(), HART_TRIP_ID).build().call()
        assertOK(response)

        val observations = response.toObservations()
        assertEquals(1, observations.size)

        val observation = observations[0]
        assertEquals(HART_TRIP_ID, observation.tripId)
        assertSame(response.status, observation.status)
        assertEquals(response.currentTime, observation.serverTimeMs)
        assertEquals(1545886800000L, observation.serviceDate)
        // Resolved through the refs: trip 1389962 -> route 16 -> type 3 (bus)
        assertEquals(3, observation.routeType ?: -1)
    }

    @Test
    fun detailsResponseWithoutStatusYieldsNoObservations() {
        // Puget Sound fixture: schedule and refs present, no vehicle status
        val response =
                ObaTripDetailsRequest.Builder(getTargetContext(), "1_18196913").build().call()
        assertOK(response)
        assertNull(response.status)

        assertTrue(response.toObservations().isEmpty())
    }

    @Test
    fun detailsResponseWithoutActiveTripIdYieldsNoObservations() {
        Application.get().setCurrentRegion(MockRegion.getTampa(getTargetContext()))
        val response =
                ObaTripDetailsRequest.Builder(
                                getTargetContext(),
                                "${HART_TRIP_ID}_no_active_trip"
                        )
                        .build()
                        .call()
        assertOK(response)
        // Status present but the vehicle reports no active trip (e.g. between runs)
        assertNotNull(response.status)
        assertNull(response.status!!.activeTripId)

        assertTrue(response.toObservations().isEmpty())
    }

    // --- ObaTripsForRouteResponse.toObservations ---

    @Test
    fun tripsForRouteResponseDistillsOneObservationPerActiveTrip() {
        Application.get().setCurrentRegion(MockRegion.getTampa(getTargetContext()))
        val response =
                ObaTripsForRouteRequest.Builder(getTargetContext(), HART_ROUTE_ID)
                        .setIncludeStatus(true)
                        .build()
                        .call()
        assertOK(response)

        val observations = response.toObservations()
        assertEquals(38, observations.size)

        val first = observations[0]
        assertEquals("Hillsborough Area Regional Transit_101446", first.tripId)
        assertEquals(response.currentTime, first.serverTimeMs)
        assertEquals(1444017600000L, first.serviceDate)
        assertEquals(3, first.routeType ?: -1)

        // Each observation is keyed by the trip its vehicle reported as active
        for (observation in observations) {
            assertEquals(observation.status.activeTripId, observation.tripId)
        }
    }

    @Test
    fun tripsForRouteResponseWithoutStatusesYieldsNoObservations() {
        Application.get().setCurrentRegion(MockRegion.getTampa(getTargetContext()))
        val response =
                ObaTripsForRouteRequest.Builder(getTargetContext(), HART_ROUTE_ID)
                        .build()
                        .call()
        assertOK(response)

        assertTrue(response.toObservations().isEmpty())
    }
}
