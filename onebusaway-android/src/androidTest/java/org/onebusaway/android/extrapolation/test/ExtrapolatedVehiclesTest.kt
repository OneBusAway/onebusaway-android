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

import org.onebusaway.android.api.data.asRouteTrips

import androidx.test.InstrumentationRegistry.getTargetContext
import androidx.test.runner.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.extrapolatedVehicles
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.mock.Resources

/**
 * The per-vehicle live position producer [extrapolatedVehicles] against a trips-for-route response.
 * The fixture (trips_for_route_extrapolation.json) carries one trip of each shape the function must
 * handle, so a single response exercises every branch:
 *  - trip_A: route_1, position + lastKnownLocation, status default  -> kept
 *  - trip_B: route_2, position only                                 -> kept only when route_2 asked
 *  - trip_C: route_1, status CANCELED                               -> skipped
 *  - trip_D: activeTripId "trip_missing" absent from refs           -> skipped (no NPE)
 *  - trip_E: route_1, no position and no lastKnownLocation          -> skipped (no NPE)
 *  - trip_F: no status at all                                       -> skipped
 *
 * trip_D and trip_E are the regression guards: before the null-safety fix, the unresolved trip ref
 * NPE'd on `getTrip(activeTripId).routeId` and the missing position NPE'd on `GeoPoint(raw.latitude…)`,
 * crashing the whole vehicle layer on a frame.
 */
@RunWith(AndroidJUnit4::class)
class ExtrapolatedVehiclesTest {

    private val noState: (String?) -> TripState? = { null }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Decode the same fixture through the io/client DTO path the production fetch now uses.
    private fun response(): ObaEnvelope<ListWithReferences<TripDetailsEntry>> =
        Resources.read(getTargetContext(), Resources.getTestUri("trips_for_route_extrapolation"))
            .use { json.decodeFromString(it.readText()) }

    @Test
    fun skipsCanceledMissingRefAndPositionlessVehiclesWithoutCrashing() {
        // route_1 + route_2 requested: only trip_A and trip_B survive; the canceled, ref-less, and
        // positionless trips are dropped rather than throwing.
        val vehicles = extrapolatedVehicles(response().asRouteTrips(),setOf("route_1", "route_2"), nowMs = 1_000_000L, lookupState = noState)

        assertEquals(listOf("trip_A", "trip_B"), vehicles.map { it.status.activeTripId })
    }

    @Test
    fun placesVehicleAtLastKnownLocationAndReportsFixTimeWhenNoState() {
        val vehicles = extrapolatedVehicles(response().asRouteTrips(),setOf("route_1"), nowMs = 1_000_000L, lookupState = noState)

        assertEquals(1, vehicles.size)
        val vehicle = vehicles[0]
        assertEquals("trip_A", vehicle.status.activeTripId)
        // lastKnownLocation (47.20) wins over position (47.10).
        assertEquals(47.20, vehicle.point.latitude, 1e-6)
        assertEquals(-122.20, vehicle.point.longitude, 1e-6)
        // With no state, the fix instant is the reported lastUpdateTime.
        assertEquals(1000L, vehicle.fixTimeMs)
        // No shape to take a tangent from, so the bearing is NaN (the marker falls back to orientation).
        assertTrue(vehicle.bearing.isNaN())
    }

    @Test
    fun fallsBackToPositionWhenNoLastKnownLocation() {
        val vehicles = extrapolatedVehicles(response().asRouteTrips(),setOf("route_2"), nowMs = 1_000_000L, lookupState = noState)

        assertEquals(1, vehicles.size)
        val vehicle = vehicles[0]
        assertEquals("trip_B", vehicle.status.activeTripId)
        assertEquals(47.30, vehicle.point.latitude, 1e-6)
        assertEquals(-122.30, vehicle.point.longitude, 1e-6)
    }

    @Test
    fun filtersToRequestedRoutesOnly() {
        // route_3 serves none of the trips.
        assertTrue(extrapolatedVehicles(response().asRouteTrips(),setOf("route_3"), nowMs = 1_000_000L, lookupState = noState).isEmpty())
        // Empty request -> nothing.
        assertTrue(extrapolatedVehicles(response().asRouteTrips(),emptySet(), nowMs = 1_000_000L, lookupState = noState).isEmpty())
    }

    // A trips-for-route response with two vehicles on route_1 heading opposite directions (GTFS
    // directionId 0 vs 1) — the "show vehicles on map" direction filter's fixture.
    private fun directionResponse(): ObaEnvelope<ListWithReferences<TripDetailsEntry>> =
        Resources.read(getTargetContext(), Resources.getTestUri("trips_for_route_direction_filter"))
            .use { json.decodeFromString(it.readText()) }

    @Test
    fun keepsOnlyTheRequestedDirectionWhenDirectionIdGiven() {
        val trips = directionResponse().asRouteTrips()
        // directionId 0 -> only the outbound vehicle.
        assertEquals(
            listOf("trip_out"),
            extrapolatedVehicles(trips, setOf("route_1"), nowMs = 1_000_000L, directionId = 0, lookupState = noState)
                .map { it.status.activeTripId }
        )
        // directionId 1 -> only the inbound vehicle (the opposite-direction bus the old view showed).
        assertEquals(
            listOf("trip_in"),
            extrapolatedVehicles(trips, setOf("route_1"), nowMs = 1_000_000L, directionId = 1, lookupState = noState)
                .map { it.status.activeTripId }
        )
    }

    @Test
    fun keepsBothDirectionsWhenDirectionIdNull() {
        val vehicles = extrapolatedVehicles(
            directionResponse().asRouteTrips(), setOf("route_1"), nowMs = 1_000_000L, lookupState = noState
        )
        assertEquals(listOf("trip_out", "trip_in"), vehicles.map { it.status.activeTripId })
    }

    @Test
    fun usesStateAnchorTimeWhenStatePresent() {
        // A shapeless state still anchors fixTimeMs (so the renderer sees a stable fix instant); the
        // point falls back to the raw fix because there's no polyline to dead-reckon along.
        val anchored: (String?) -> TripState? = { tripId ->
            if (tripId == "trip_A") TripState("trip_A", anchorLocalTimeMs = 999_000L) else null
        }

        val vehicle = extrapolatedVehicles(response().asRouteTrips(),setOf("route_1"), nowMs = 1_000_000L, lookupState = anchored).single()

        assertEquals(999_000L, vehicle.fixTimeMs)
        assertEquals(47.20, vehicle.point.latitude, 1e-6)
    }
}
