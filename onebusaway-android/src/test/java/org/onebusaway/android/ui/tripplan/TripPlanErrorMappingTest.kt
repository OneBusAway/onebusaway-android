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
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.OtpErrorId
import org.onebusaway.android.api.graphql.type.InputField
import org.onebusaway.android.api.graphql.type.RoutingErrorCode
import org.onebusaway.android.ui.tripplan.TripPlanError.Category

/**
 * Verifies both planner error classifiers ([otp1ErrorFor], [otp2ErrorFor]) map every wire code onto
 * the right [TripPlanError.Category] and preserve the specific detail string. Pure JVM — the res IDs
 * are compared as [R] constants (never resolved to text), so no Android runtime is needed.
 */
class TripPlanErrorMappingTest {

    private fun assertError(expectedCategory: Category, expectedDetail: Int, actual: TripPlanError) {
        assertEquals(expectedCategory, actual.category)
        assertEquals(expectedDetail, actual.detailRes)
    }

    // ---- OTP1 (REST error codes) ----

    // Every OtpErrorId member and the (category, detail) it must classify into. The exhaustiveness
    // assertion in the test below fails if a new code is added to the enum without an entry here.
    private val otp1Expected = mapOf(
        OtpErrorId.SYSTEM_ERROR to (Category.REQUEST to R.string.tripplanner_error_system),
        OtpErrorId.OUTSIDE_BOUNDS to (Category.NO_ROUTE to R.string.tripplanner_error_outside_bounds),
        OtpErrorId.PATH_NOT_FOUND to (Category.NO_ROUTE to R.string.tripplanner_error_path_not_found),
        OtpErrorId.NO_TRANSIT_TIMES to (Category.SCHEDULE to R.string.tripplanner_error_no_transit_times),
        OtpErrorId.REQUEST_TIMEOUT to (Category.CONNECTIVITY to R.string.tripplanner_error_request_timeout),
        OtpErrorId.BOGUS_PARAMETER to (Category.REQUEST to R.string.tripplanner_error_bogus_parameter),
        OtpErrorId.GEOCODE_FROM_NOT_FOUND to (Category.LOCATION to R.string.tripplanner_error_geocode_from_not_found),
        OtpErrorId.GEOCODE_TO_NOT_FOUND to (Category.LOCATION to R.string.tripplanner_error_geocode_to_not_found),
        OtpErrorId.GEOCODE_FROM_TO_NOT_FOUND to (Category.LOCATION to R.string.tripplanner_error_geocode_from_to_not_found),
        OtpErrorId.TOO_CLOSE to (Category.NO_ROUTE to R.string.tripplanner_error_too_close),
        OtpErrorId.LOCATION_NOT_ACCESSIBLE to (Category.LOCATION to R.string.tripplanner_error_location_not_accessible),
        OtpErrorId.GEOCODE_FROM_AMBIGUOUS to (Category.LOCATION to R.string.tripplanner_error_geocode_from_ambiguous),
        OtpErrorId.GEOCODE_TO_AMBIGUOUS to (Category.LOCATION to R.string.tripplanner_error_geocode_to_ambiguous),
        OtpErrorId.GEOCODE_FROM_TO_AMBIGUOUS to (Category.LOCATION to R.string.tripplanner_error_geocode_from_to_ambiguous),
        OtpErrorId.UNDERSPECIFIED_TRIANGLE to (Category.REQUEST to R.string.tripplanner_error_triangle),
        OtpErrorId.TRIANGLE_NOT_AFFINE to (Category.REQUEST to R.string.tripplanner_error_triangle),
        OtpErrorId.TRIANGLE_OPTIMIZE_TYPE_NOT_SET to (Category.REQUEST to R.string.tripplanner_error_triangle),
        OtpErrorId.TRIANGLE_VALUES_NOT_SET to (Category.REQUEST to R.string.tripplanner_error_triangle)
    )

    @Test
    fun otp1_classifies_every_error_code() {
        assertEquals(OtpErrorId.entries.toSet(), otp1Expected.keys)
        for ((code, expected) in otp1Expected) {
            val (category, detail) = expected
            assertError(category, detail, otp1ErrorFor(code.id))
        }
    }

    @Test
    fun otp1_unknown_code_falls_back() {
        assertError(Category.REQUEST, R.string.tripplanner_error_not_defined, otp1ErrorFor(-1))
        assertEquals(TripPlanError.Unknown, otp1ErrorFor(-1))
    }

    // ---- OTP2 (GraphQL RoutingErrorCode) ----

    @Test
    fun otp2_no_route() {
        assertError(
            Category.NO_ROUTE,
            R.string.tripplanner_error_outside_bounds,
            otp2ErrorFor(RoutingErrorCode.OUTSIDE_BOUNDS, null)
        )
        assertError(
            Category.NO_ROUTE,
            R.string.tripplanner_error_path_not_found,
            otp2ErrorFor(RoutingErrorCode.NO_STOPS_IN_RANGE, null)
        )
    }

    @Test
    fun otp2_schedule() {
        assertError(
            Category.SCHEDULE,
            R.string.tripplanner_error_no_transit_times,
            otp2ErrorFor(RoutingErrorCode.NO_TRANSIT_CONNECTION, null)
        )
        assertError(
            Category.SCHEDULE,
            R.string.tripplanner_error_no_transit_times,
            otp2ErrorFor(RoutingErrorCode.NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW, null)
        )
        assertError(
            Category.SCHEDULE,
            R.string.tripplanner_error_outside_service_period,
            otp2ErrorFor(RoutingErrorCode.OUTSIDE_SERVICE_PERIOD, null)
        )
    }

    @Test
    fun otp2_location_depends_on_input_field() {
        assertError(
            Category.LOCATION,
            R.string.tripplanner_error_geocode_from_not_found,
            otp2ErrorFor(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.FROM)
        )
        assertError(
            Category.LOCATION,
            R.string.tripplanner_error_geocode_to_not_found,
            otp2ErrorFor(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.TO)
        )
        // No input field named -> still a location problem, generic detail.
        assertError(
            Category.LOCATION,
            R.string.tripplanner_error_not_defined,
            otp2ErrorFor(RoutingErrorCode.LOCATION_NOT_FOUND, null)
        )
    }

    @Test
    fun otp2_walking_better_than_transit_maps_to_too_close() {
        // Reaches otp2ErrorFor only in the same-location (no-itinerary) case — the too-close result,
        // matching OTP1's TOO_CLOSE; it never advises walking (#1947). When a walk route survives it
        // is returned as a normal itinerary by resolveOtp2Plan and this code is never hit.
        assertError(
            Category.NO_ROUTE,
            R.string.tripplanner_error_too_close,
            otp2ErrorFor(RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT, null)
        )
    }

    @Test
    fun otp2_unknown_falls_back() {
        assertEquals(TripPlanError.Unknown, otp2ErrorFor(RoutingErrorCode.UNKNOWN__, null))
    }
}
