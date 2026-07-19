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
package org.onebusaway.android.ui.tripplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.graphql.PlanQuery
import org.onebusaway.android.api.graphql.fragment.PlaceFields
import org.onebusaway.android.api.graphql.type.InputField
import org.onebusaway.android.api.graphql.type.Mode
import org.onebusaway.android.api.graphql.type.RoutingErrorCode
import org.onebusaway.android.directions.model.TripMode

/**
 * Covers [resolveOtp2Plan]: the rule that OTP2 itineraries win over a coexisting `routingErrors`
 * entry, and that fatal errors (which always arrive with empty `edges`) still classify as before.
 *
 * The regression this guards is #1947: OTP2 emits [RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT]
 * *while keeping* the walk-only itinerary in the response, and the old code threw that advisory
 * before ever reading the itinerary — hiding a valid walk route behind a "Try walking instead"
 * message. Pure JVM: builds Apollo-generated [PlanQuery.Data] fixtures directly, no Apollo/HTTP.
 */
class Otp2PlanResolveTest {

    /**
     * The core fix: a `WALKING_BETTER_THAN_TRANSIT` error alongside a surviving walk itinerary must
     * yield the walk itinerary, not throw the advisory.
     */
    @Test
    fun walkingBetterThanTransit_withWalkItinerary_returnsTheWalk() {
        val data = planData(
            routingErrors = listOf(routingError(RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT)),
            edges = listOf(walkEdge()),
        )

        val itineraries = resolveOtp2Plan(data)

        assertEquals(1, itineraries.size)
        assertEquals(TripMode.WALK, itineraries[0].legs[0].mode)
    }

    /**
     * The same-location degenerate case (OTP's `SameEdgeAdjuster`) emits the same code but with no
     * itineraries — with nothing to show, it surfaces as the "too close" no-route result, never as a
     * "try walking" advisory (#1947).
     */
    @Test
    fun walkingBetterThanTransit_withoutItineraries_throwsTooClose() {
        val data = planData(
            routingErrors = listOf(routingError(RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT)),
            edges = emptyList(),
        )

        val error = assertThrows(TripPlanException::class.java) { resolveOtp2Plan(data) }.error
        assertEquals(TripPlanError.Category.NO_ROUTE, error.category)
        assertEquals(R.string.tripplanner_error_too_close, error.detailRes)
    }

    /** A fatal error (always empty `edges`) still classifies exactly as before the fix. */
    @Test
    fun fatalError_withoutItineraries_throwsClassifiedError() {
        val data = planData(
            routingErrors = listOf(routingError(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.FROM)),
            edges = emptyList(),
        )

        val error = assertThrows(TripPlanException::class.java) { resolveOtp2Plan(data) }.error
        assertEquals(TripPlanError.Category.LOCATION, error.category)
        assertEquals(R.string.tripplanner_error_geocode_from_not_found, error.detailRes)
    }

    /** No itineraries and no error is a plain no-route result. */
    @Test
    fun noItinerariesNoError_throwsNoRoute() {
        val data = planData(routingErrors = emptyList(), edges = emptyList())

        val error = assertThrows(TripPlanException::class.java) { resolveOtp2Plan(data) }.error
        assertEquals(TripPlanError.NoRoute, error)
    }

    // ---- fixtures ----

    private fun planData(
        routingErrors: List<PlanQuery.RoutingError>,
        edges: List<PlanQuery.Edge>,
    ) = PlanQuery.Data(
        planConnection = PlanQuery.PlanConnection(
            searchDateTime = "2026-07-11T10:00:00-07:00",
            routingErrors = routingErrors,
            edges = edges,
        ),
    )

    private fun routingError(code: RoutingErrorCode, inputField: InputField? = null) =
        PlanQuery.RoutingError(code = code, description = code.name, inputField = inputField)

    private fun walkEdge(): PlanQuery.Edge {
        val leg = PlanQuery.Leg(
            mode = Mode.WALK,
            duration = 2400.0,
            distance = 3200.0,
            realTime = null,
            start = PlanQuery.Start(scheduledTime = "2026-07-11T10:00:00-07:00", estimated = null),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:40:00-07:00", estimated = null),
            from = from("Origin", 47.60, -122.30),
            to = to("Destination", 47.62, -122.34),
            route = null,
            trip = null,
            legGeometry = null,
            steps = null,
        )
        val node = PlanQuery.Node(
            start = "2026-07-11T10:00:00-07:00",
            end = "2026-07-11T10:40:00-07:00",
            duration = 2400L,
            numberOfTransfers = 0,
            legs = listOf(leg),
        )
        return PlanQuery.Edge(node = node)
    }

    private fun placeFields(name: String, lat: Double, lon: Double) =
        PlaceFields(name, lat, lon, null, null, null, null)

    private fun from(name: String, lat: Double, lon: Double) =
        PlanQuery.From(__typename = "Place", placeFields = placeFields(name, lat, lon))

    private fun to(name: String, lat: Double, lon: Double) =
        PlanQuery.To(__typename = "Place", placeFields = placeFields(name, lat, lon))
}
