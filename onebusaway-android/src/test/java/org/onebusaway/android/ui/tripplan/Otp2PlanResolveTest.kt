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

import com.apollographql.apollo.api.Error as GraphQlError
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloNetworkException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
 * Also covers [resolveOtp2Response] — the tier above it — which decides between a transport failure,
 * the GraphQL `errors` array and the data itself, and which of those gets to explain an empty result;
 * and [otp2ErrorDiagnostic], which decides what a GraphQL error is allowed to write down.
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
            edges = listOf(walkEdge())
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
            edges = emptyList()
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
            edges = emptyList()
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

    // ---- resolveOtp2Response: which tier explains the failure (#2023) ----

    /**
     * The tier order: a transport failure is reported on `exception` and outranks anything the server
     * managed to say, because the response never really arrived.
     */
    @Test
    fun transportFailure_outranksGraphQlErrors() {
        val error = failureFrom(
            data = null,
            errors = listOf(graphQlError("ValidationError")),
            exception = ApolloNetworkException("connect timed out")
        ).error

        assertEquals(TripPlanError.Connectivity, error)
    }

    /** A GraphQL-errors-only response classifies from the errors and carries the server's account. */
    @Test
    fun graphQlErrorsOnly_classifyAndCarryTheDiagnostic() {
        val failure = failureFrom(data = null, errors = listOf(graphQlError("ValidationError", reluctanceMessage)))

        assertEquals(TripPlanError.RequestRejected, failure.error)
        // Asserted on the structural half, which every build renders — see the redaction tests below
        // for the message text, which is deliberately build-type dependent.
        assertTrue(failure.message.orEmpty().contains("ValidationError"))
    }

    /**
     * The case a `data != null` early return used to swallow: a GraphQL error nulls only the field it
     * hit, so an execution-tier failure arrives as `{"planConnection": null}` *with* an `errors` entry.
     * That is not a search that came back empty, and must not read as "cannot find route".
     */
    @Test
    fun nulledPlanConnection_classifiesFromTheErrorsNotAsNoRoute() {
        val failure = failureFrom(
            data = PlanQuery.Data(planConnection = null),
            errors = listOf(graphQlError("DataFetchingException"))
        )

        assertEquals(TripPlanError.Unknown, failure.error)
        assertTrue(failure.message.orEmpty().contains("DataFetchingException"))
    }

    /** The same shape with a deterministic rejection points the rider at the trip options instead. */
    @Test
    fun nulledPlanConnection_withDeterministicRejection_advisesChangingTripOptions() {
        val failure = failureFrom(
            data = PlanQuery.Data(planConnection = null),
            errors = listOf(graphQlError("ValidationError"))
        )

        assertEquals(TripPlanError.RequestRejected, failure.error)
    }

    /** GraphQL permits partial results: a renderable plan is returned even when errors accompany it. */
    @Test
    fun itineraries_outrankGraphQlErrors() {
        val data = planData(routingErrors = emptyList(), edges = listOf(walkEdge()))

        val itineraries = resolveOtp2Response(data, listOf(graphQlError("DataFetchingException")), null)

        assertEquals(1, itineraries.size)
        assertEquals(TripMode.WALK, itineraries[0].legs[0].mode)
    }

    /** A `routingErrors` entry is the most specific account there is, so it outranks the GraphQL tier. */
    @Test
    fun routingError_outranksGraphQlErrors() {
        val data = planData(
            routingErrors = listOf(routingError(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.FROM)),
            edges = emptyList()
        )

        val error = failureFrom(data, listOf(graphQlError("ValidationError"))).error

        assertEquals(TripPlanError.Category.LOCATION, error.category)
        assertEquals(R.string.tripplanner_error_geocode_from_not_found, error.detailRes)
    }

    /** With no errors of any kind, an empty result is still the plain no-route result. */
    @Test
    fun emptyResultWithoutErrors_isStillNoRoute() {
        val data = planData(routingErrors = emptyList(), edges = emptyList())

        assertEquals(TripPlanError.NoRoute, failureFrom(data).error)
    }

    /** A response with nothing on it at all — no data, no errors, no exception — stays unclassified. */
    @Test
    fun emptyResponse_isUnknown() {
        assertEquals(TripPlanError.Unknown, failureFrom(data = null).error)
    }

    /**
     * An empty `errors` array is not the server saying something: it must not be mistaken for wording,
     * or an ordinary empty result would classify as an unknown failure instead of a no-route one.
     */
    @Test
    fun emptyErrorsArray_readsAsNoErrorsAtAll() {
        val data = planData(routingErrors = emptyList(), edges = emptyList())

        assertEquals(TripPlanError.NoRoute, failureFrom(data, errors = emptyList()).error)
    }

    /**
     * Without wording of its own the exception still renders its cause, as `IOException(Throwable)` did
     * before [TripPlanException] took a message — otherwise wrapping a transport failure lost its text.
     */
    @Test
    fun transportFailure_messageFallsBackToTheCause() {
        val cause = ApolloNetworkException("connect timed out")

        val failure = failureFrom(data = null, exception = cause)

        assertEquals(cause, failure.cause)
        assertEquals(cause.toString(), failure.message)
    }

    // ---- otp2ErrorDiagnostic: what a GraphQL error is allowed to write down ----

    /**
     * The privacy rule: the plan's arguments include the rider's origin and destination, and
     * graphql-java renders a rejected value straight into the message — so outside a debug build the
     * message text is dropped entirely rather than scrubbed (scrubbing free text would be a heuristic).
     */
    @Test
    fun diagnostic_dropsServerTextOutsideDebugBuilds() {
        val coordinateMessage = "Variable 'origin' has an invalid value: argument " +
            "'origin.location.coordinate' with value 'ObjectValue{latitude=47.6205, longitude=-122.3493}'"
        val errors = listOf(graphQlError("ValidationError", coordinateMessage))

        val diagnostic = otp2ErrorDiagnostic(errors, includeServerText = false).orEmpty()

        assertFalse(diagnostic.contains("47.6205"))
        assertFalse(diagnostic.contains("-122.3493"))
        assertFalse(diagnostic.contains(coordinateMessage))
        // The half that classifies the failure is composed by the server, not quoted from us, so it stays.
        assertTrue(diagnostic.contains("ValidationError"))
    }

    /** A debug build — where the message is actually read (#2023's workflow) — keeps it in full. */
    @Test
    fun diagnostic_keepsServerTextInDebugBuilds() {
        val errors = listOf(graphQlError("ValidationError", reluctanceMessage))

        val diagnostic = otp2ErrorDiagnostic(errors, includeServerText = true).orEmpty()

        assertTrue(diagnostic.contains(reluctanceMessage))
        assertTrue(diagnostic.contains("ValidationError"))
    }

    /** The structural fields name which query element failed, and survive the redaction. */
    @Test
    fun diagnostic_keepsPathAndDocumentLocations() {
        val error = GraphQlError.Builder("boom")
            .extensions(mapOf("classification" to "DataFetchingException"))
            .path(listOf("planConnection", "edges"))
            .locations(listOf(GraphQlError.Location(12, 5)))
            .build()

        val diagnostic = otp2ErrorDiagnostic(listOf(error), includeServerText = false).orEmpty()

        assertTrue(diagnostic.contains("planConnection.edges"))
        assertTrue(diagnostic.contains("line 12:5"))
    }

    /**
     * `classification` is the one extensions key whose meaning this app has verified; anything else the
     * server chose to attach could quote request values just as the message does, so it is dropped.
     */
    @Test
    fun diagnostic_dropsUnexaminedExtensionsKeys() {
        val error = GraphQlError.Builder("boom")
            .extensions(mapOf("classification" to "ValidationError", "rejectedValue" to "47.6205,-122.3493"))
            .build()

        val diagnostic = otp2ErrorDiagnostic(listOf(error), includeServerText = false).orEmpty()

        assertTrue(diagnostic.contains("ValidationError"))
        assertFalse(diagnostic.contains("47.6205"))
    }

    /** An error with no classification still renders something that says as much. */
    @Test
    fun diagnostic_withoutClassification_saysUnclassified() {
        val diagnostic = otp2ErrorDiagnostic(listOf(GraphQlError.Builder("boom").build()), includeServerText = false)

        assertEquals("unclassified", diagnostic)
    }

    /** Every error in the array is rendered, so a mixed list is diagnosable as a whole. */
    @Test
    fun diagnostic_rendersEveryError() {
        val errors = listOf(graphQlError("DataFetchingException"), graphQlError("ValidationError"))

        val diagnostic = otp2ErrorDiagnostic(errors, includeServerText = false).orEmpty()

        assertTrue(diagnostic.contains("DataFetchingException"))
        assertTrue(diagnostic.contains("ValidationError"))
    }

    /** No errors is not an empty diagnostic but the absence of one — the callers branch on null. */
    @Test
    fun diagnostic_ofNoErrors_isNull() {
        assertNull(otp2ErrorDiagnostic(null, includeServerText = true))
        assertNull(otp2ErrorDiagnostic(emptyList(), includeServerText = true))
    }

    // ---- fixtures ----

    /** The motivating #2023 rejection, verbatim from the issue's live OTP2 response. */
    private val reluctanceMessage = "Validation error (WrongType@[planConnection]) : argument " +
        "'preferences.street.walk.reluctance' with value 'FloatValue{value=0.08}' is not a valid " +
        "'Reluctance' - Reluctance needs to be between 0.1 and 100000.0"

    private fun failureFrom(
        data: PlanQuery.Data?,
        errors: List<GraphQlError>? = null,
        exception: ApolloException? = null
    ) = assertThrows(TripPlanException::class.java) { resolveOtp2Response(data, errors, exception) }

    private fun graphQlError(classification: String, message: String = "boom") = GraphQlError.Builder(message)
        .extensions(mapOf("classification" to classification))
        .build()

    private fun planData(
        routingErrors: List<PlanQuery.RoutingError>,
        edges: List<PlanQuery.Edge>
    ) = PlanQuery.Data(
        planConnection = PlanQuery.PlanConnection(
            searchDateTime = "2026-07-11T10:00:00-07:00",
            routingErrors = routingErrors,
            edges = edges
        )
    )

    private fun routingError(code: RoutingErrorCode, inputField: InputField? = null) = PlanQuery.RoutingError(code = code, description = code.name, inputField = inputField)

    private fun walkEdge(): PlanQuery.Edge {
        val leg = PlanQuery.Leg(
            mode = Mode.WALK,
            duration = 2400.0,
            distance = 3200.0,
            realTime = null,
            interlineWithPreviousLeg = null,
            start = PlanQuery.Start(scheduledTime = "2026-07-11T10:00:00-07:00", estimated = null),
            end = PlanQuery.End(scheduledTime = "2026-07-11T10:40:00-07:00", estimated = null),
            from = from("Origin", 47.60, -122.30),
            to = to("Destination", 47.62, -122.34),
            route = null,
            trip = null,
            stopCalls = emptyList(),
            legGeometry = null,
            steps = null,
            nextLegs = null,
            alerts = emptyList()
        )
        val node = PlanQuery.Node(
            start = "2026-07-11T10:00:00-07:00",
            end = "2026-07-11T10:40:00-07:00",
            duration = 2400L,
            numberOfTransfers = 0,
            legs = listOf(leg)
        )
        return PlanQuery.Edge(node = node)
    }

    private fun placeFields(name: String, lat: Double, lon: Double) = PlaceFields(name, lat, lon, null, null, null, null)

    private fun from(name: String, lat: Double, lon: Double) = PlanQuery.From(__typename = "Place", placeFields = placeFields(name, lat, lon))

    private fun to(name: String, lat: Double, lon: Double) = PlanQuery.To(__typename = "Place", placeFields = placeFields(name, lat, lon))
}
