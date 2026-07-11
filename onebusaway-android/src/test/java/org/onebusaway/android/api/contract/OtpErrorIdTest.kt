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
package org.onebusaway.android.api.contract

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [OtpErrorId]'s wire ids against the vendored `org.opentripplanner.api.ws.Message` jar they were
 * transcribed from (`Message(String, int)` constructor arguments, read via `javap`/CFR — see #1778) —
 * a transcription slip here would silently swap which user-facing error string a given OTP `/plan`
 * error response maps to.
 */
class OtpErrorIdTest {

    @Test
    fun idsMatchVendoredMessageEnum() {
        assertEquals(500, OtpErrorId.SYSTEM_ERROR.id)
        assertEquals(400, OtpErrorId.OUTSIDE_BOUNDS.id)
        assertEquals(404, OtpErrorId.PATH_NOT_FOUND.id)
        assertEquals(406, OtpErrorId.NO_TRANSIT_TIMES.id)
        assertEquals(408, OtpErrorId.REQUEST_TIMEOUT.id)
        assertEquals(413, OtpErrorId.BOGUS_PARAMETER.id)
        assertEquals(440, OtpErrorId.GEOCODE_FROM_NOT_FOUND.id)
        assertEquals(450, OtpErrorId.GEOCODE_TO_NOT_FOUND.id)
        assertEquals(460, OtpErrorId.GEOCODE_FROM_TO_NOT_FOUND.id)
        assertEquals(409, OtpErrorId.TOO_CLOSE.id)
        assertEquals(470, OtpErrorId.LOCATION_NOT_ACCESSIBLE.id)
        assertEquals(340, OtpErrorId.GEOCODE_FROM_AMBIGUOUS.id)
        assertEquals(350, OtpErrorId.GEOCODE_TO_AMBIGUOUS.id)
        assertEquals(360, OtpErrorId.GEOCODE_FROM_TO_AMBIGUOUS.id)
        assertEquals(370, OtpErrorId.UNDERSPECIFIED_TRIANGLE.id)
        assertEquals(371, OtpErrorId.TRIANGLE_NOT_AFFINE.id)
        assertEquals(372, OtpErrorId.TRIANGLE_OPTIMIZE_TYPE_NOT_SET.id)
        assertEquals(373, OtpErrorId.TRIANGLE_VALUES_NOT_SET.id)
    }

    @Test
    fun idsAreUnique() {
        val ids = OtpErrorId.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
