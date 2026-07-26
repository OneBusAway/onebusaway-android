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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.R

/**
 * Covers [otp2GraphQlErrorFor]: the GraphQL transport/validation tier that, before #2023, had no
 * classification at all — every `errors` array collapsed into [TripPlanError.Unknown]'s "Sorry,
 * something went wrong. Please try again.", which is wrong advice for a request the server will
 * refuse identically every time.
 *
 * Pure JVM: builds `com.apollographql.apollo.api.Error` values directly, no Apollo client or HTTP.
 */
class Otp2GraphQlErrorTest {

    /**
     * The motivating case (#2023): an out-of-range `walk.reluctance` comes back as a graphql-java
     * `ValidationError`. Resending is futile, so this must not be the generic try-again result — it
     * points the rider at the trip options, which is where the cause is.
     */
    @Test
    fun validationError_advisesChangingTripOptions() {
        val error = otp2GraphQlErrorFor(
            listOf(
                graphQlError(
                    message = "Validation error (WrongType@[planConnection]) : argument " +
                        "'preferences.street.walk.reluctance' with value 'FloatValue{value=0.08}' is not " +
                        "a valid 'Reluctance' - Reluctance needs to be between 0.1 and 100000.0",
                    classification = "ValidationError"
                )
            )
        )

        assertEquals(TripPlanError.Category.REQUEST, error.category)
        assertEquals(R.string.tripplanner_error_bogus_parameter, error.detailRes)
    }

    /** A query the parser rejected is just as deterministic as one the validator rejected. */
    @Test
    fun invalidSyntax_advisesChangingTripOptions() {
        val error = otp2GraphQlErrorFor(listOf(graphQlError(classification = "InvalidSyntax")))

        assertEquals(TripPlanError.Category.REQUEST, error.category)
        assertEquals(R.string.tripplanner_error_bogus_parameter, error.detailRes)
    }

    /**
     * An execution-tier failure may well be transient, so "try again" stays honest advice there — the
     * deterministic wording is reserved for requests that genuinely cannot succeed as sent.
     */
    @Test
    fun dataFetchingException_keepsTheGenericRetryResult() {
        val error = otp2GraphQlErrorFor(listOf(graphQlError(classification = "DataFetchingException")))

        assertEquals(TripPlanError.Unknown, error)
    }

    /** A server that sends no `extensions.classification` falls back to the pre-#2023 behaviour. */
    @Test
    fun missingClassification_keepsTheGenericRetryResult() {
        val error = otp2GraphQlErrorFor(listOf(graphQlError(classification = null)))

        assertEquals(TripPlanError.Unknown, error)
    }

    /** A classification value we don't recognize is treated as "could be transient", not deterministic. */
    @Test
    fun unknownClassification_keepsTheGenericRetryResult() {
        val error = otp2GraphQlErrorFor(listOf(graphQlError(classification = "SomeFutureClassification")))

        assertEquals(TripPlanError.Unknown, error)
    }

    /**
     * `errors` is a list, and a single deterministic rejection anywhere in it means the request as sent
     * can't succeed — the whole response is that, regardless of what accompanies it.
     */
    @Test
    fun anyDeterministicRejectionInTheList_advisesChangingTripOptions() {
        val error = otp2GraphQlErrorFor(
            listOf(
                graphQlError(classification = "DataFetchingException"),
                graphQlError(classification = "ValidationError")
            )
        )

        assertEquals(TripPlanError.Category.REQUEST, error.category)
        assertEquals(R.string.tripplanner_error_bogus_parameter, error.detailRes)
    }

    /** Defensive: an empty list carries no evidence of a deterministic rejection. */
    @Test
    fun noErrors_keepsTheGenericRetryResult() {
        assertEquals(TripPlanError.Unknown, otp2GraphQlErrorFor(emptyList()))
    }

    /** A non-string `classification` is ignored rather than crashing the classifier. */
    @Test
    fun nonStringClassification_keepsTheGenericRetryResult() {
        val error = otp2GraphQlErrorFor(
            listOf(GraphQlError.Builder("boom").extensions(mapOf("classification" to 42)).build())
        )

        assertEquals(TripPlanError.Unknown, error)
    }

    // ---- fixtures ----

    private fun graphQlError(message: String = "boom", classification: String?) = GraphQlError.Builder(message)
        .apply { classification?.let { extensions(mapOf("classification" to it)) } }
        .build()
}
