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

/**
 * The OTP2 base-URL → gtfs GraphQL endpoint resolution (#1780). A region's `otpBaseGraphqlUrl`
 * (and the custom-URL setting) is the OTP mount base through `…/otp`; the client appends the fixed
 * `/gtfs/v1` gtfs GraphQL mount, mirroring how the OTP1 path appends `/plan` to its own base.
 */
class Otp2GraphQlEndpointTest {

    @Test
    fun appendsGtfsV1ToOtpBase() {
        // The Puget Sound OTP2 server this wires up (#1780) — base verified to serve the OTP root,
        // base + /gtfs/v1 verified to serve the GraphQL endpoint.
        assertEquals(
            "https://peq6qe6fei.execute-api.us-west-2.amazonaws.com/prod/otp/gtfs/v1",
            otp2GraphQlEndpoint("https://peq6qe6fei.execute-api.us-west-2.amazonaws.com/prod/otp")
        )
    }

    @Test
    fun toleratesTrailingSlashWithoutDoublingIt() {
        assertEquals(
            "https://example.opentripplanner.org/otp/gtfs/v1",
            otp2GraphQlEndpoint("https://example.opentripplanner.org/otp/")
        )
    }
}
