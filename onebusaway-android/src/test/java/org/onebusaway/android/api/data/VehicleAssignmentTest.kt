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
package org.onebusaway.android.api.data

import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import retrofit2.HttpException
import retrofit2.Response

/**
 * How a `trip-for-vehicle` outcome becomes a coach-number row's caption. The distinction under test is
 * that only the server's own 404 means "not in service" — every other failure leaves the assignment
 * unknown, so a lookup that never got an answer is never rendered as one.
 */
class VehicleAssignmentTest {

    @Test
    fun aResolvedTripIsTheRide() {
        val trip = VehicleTrip(
            tripId = "1_800587510",
            routeId = "1_100263",
            routeShortName = "7",
            routeColor = null,
            headsign = "Prentice St Via Rainier Ave S"
        )

        assertEquals(VehicleAssignment.OnTrip(trip), vehicleAssignment(Result.success(trip)))
    }

    @Test
    fun theServers404IsNotInService() {
        // How an unassigned vehicle actually answers: OBA sets the HTTP status from the envelope code,
        // so Retrofit raises this before the body is decoded.
        assertEquals(VehicleAssignment.NotInService, vehicleAssignment(Result.failure(httpError(404))))
        // The same answer read off a decoded envelope, which is what requireData raises.
        assertEquals(VehicleAssignment.NotInService, vehicleAssignment(Result.failure(ObaApiException(404))))
    }

    @Test
    fun aFailedLookupIsUnknownRatherThanNotInService() {
        listOf(
            SocketTimeoutException("timeout"),
            IOException("connection reset"),
            httpError(500),
            ObaApiException(500),
            IllegalArgumentException("malformed body")
        ).forEach { failure ->
            assertEquals(
                "$failure should leave the assignment unknown",
                VehicleAssignment.Unknown,
                vehicleAssignment(Result.failure(failure))
            )
        }
    }

    @Test
    fun aTripTheResponseDoesntDescribeIsUnknown() {
        // The server said the vehicle is on a trip; its references just didn't say which. That's not
        // grounds for reporting the coach as off duty.
        assertEquals(VehicleAssignment.Unknown, vehicleAssignment(Result.success(null)))
    }

    private fun httpError(code: Int) = HttpException(
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
    )
}
