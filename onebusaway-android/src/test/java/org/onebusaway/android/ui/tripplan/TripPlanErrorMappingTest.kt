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

    @Test
    fun otp1_connectivity_and_request() {
        assertError(Category.CONNECTIVITY, R.string.tripplanner_error_request_timeout,
            otp1ErrorFor(OtpErrorId.REQUEST_TIMEOUT.id))
        assertError(Category.REQUEST, R.string.tripplanner_error_system,
            otp1ErrorFor(OtpErrorId.SYSTEM_ERROR.id))
        assertError(Category.REQUEST, R.string.tripplanner_error_bogus_parameter,
            otp1ErrorFor(OtpErrorId.BOGUS_PARAMETER.id))
        assertError(Category.REQUEST, R.string.tripplanner_error_triangle,
            otp1ErrorFor(OtpErrorId.TRIANGLE_NOT_AFFINE.id))
    }

    @Test
    fun otp1_no_route() {
        assertError(Category.NO_ROUTE, R.string.tripplanner_error_outside_bounds,
            otp1ErrorFor(OtpErrorId.OUTSIDE_BOUNDS.id))
        assertError(Category.NO_ROUTE, R.string.tripplanner_error_path_not_found,
            otp1ErrorFor(OtpErrorId.PATH_NOT_FOUND.id))
        assertError(Category.NO_ROUTE, R.string.tripplanner_error_too_close,
            otp1ErrorFor(OtpErrorId.TOO_CLOSE.id))
    }

    @Test
    fun otp1_schedule() {
        assertError(Category.SCHEDULE, R.string.tripplanner_error_no_transit_times,
            otp1ErrorFor(OtpErrorId.NO_TRANSIT_TIMES.id))
    }

    @Test
    fun otp1_location() {
        assertError(Category.LOCATION, R.string.tripplanner_error_geocode_from_not_found,
            otp1ErrorFor(OtpErrorId.GEOCODE_FROM_NOT_FOUND.id))
        assertError(Category.LOCATION, R.string.tripplanner_error_geocode_to_ambiguous,
            otp1ErrorFor(OtpErrorId.GEOCODE_TO_AMBIGUOUS.id))
        assertError(Category.LOCATION, R.string.tripplanner_error_location_not_accessible,
            otp1ErrorFor(OtpErrorId.LOCATION_NOT_ACCESSIBLE.id))
    }

    @Test
    fun otp1_unknown_code_falls_back() {
        assertError(Category.REQUEST, R.string.tripplanner_error_not_defined, otp1ErrorFor(-1))
        assertEquals(TripPlanError.Unknown, otp1ErrorFor(-1))
    }

    // ---- OTP2 (GraphQL RoutingErrorCode) ----

    @Test
    fun otp2_no_route() {
        assertError(Category.NO_ROUTE, R.string.tripplanner_error_outside_bounds,
            otp2ErrorFor(RoutingErrorCode.OUTSIDE_BOUNDS, null))
        assertError(Category.NO_ROUTE, R.string.tripplanner_error_path_not_found,
            otp2ErrorFor(RoutingErrorCode.NO_STOPS_IN_RANGE, null))
    }

    @Test
    fun otp2_schedule() {
        assertError(Category.SCHEDULE, R.string.tripplanner_error_no_transit_times,
            otp2ErrorFor(RoutingErrorCode.NO_TRANSIT_CONNECTION, null))
        assertError(Category.SCHEDULE, R.string.tripplanner_error_no_transit_times,
            otp2ErrorFor(RoutingErrorCode.NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW, null))
        assertError(Category.SCHEDULE, R.string.tripplanner_error_outside_service_period,
            otp2ErrorFor(RoutingErrorCode.OUTSIDE_SERVICE_PERIOD, null))
    }

    @Test
    fun otp2_location_depends_on_input_field() {
        assertError(Category.LOCATION, R.string.tripplanner_error_geocode_from_not_found,
            otp2ErrorFor(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.FROM))
        assertError(Category.LOCATION, R.string.tripplanner_error_geocode_to_not_found,
            otp2ErrorFor(RoutingErrorCode.LOCATION_NOT_FOUND, InputField.TO))
        // No input field named -> still a location problem, generic detail.
        assertError(Category.LOCATION, R.string.tripplanner_error_not_defined,
            otp2ErrorFor(RoutingErrorCode.LOCATION_NOT_FOUND, null))
    }

    @Test
    fun otp2_advisory_and_unknown() {
        assertError(Category.ADVISORY, R.string.tripplanner_error_walking_better_than_transit,
            otp2ErrorFor(RoutingErrorCode.WALKING_BETTER_THAN_TRANSIT, null))
        assertEquals(TripPlanError.Unknown, otp2ErrorFor(RoutingErrorCode.UNKNOWN__, null))
    }
}
