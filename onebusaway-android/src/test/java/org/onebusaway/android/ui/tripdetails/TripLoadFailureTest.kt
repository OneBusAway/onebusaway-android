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
package org.onebusaway.android.ui.tripdetails

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.RateLimitedException
import retrofit2.HttpException
import retrofit2.Response

/**
 * The failure [DefaultTripDetailsRepository] reports for a trip load with nothing to fall back on.
 * It must say whether the load was rate-limited: that is how [TripDetailsViewModel] tells a refusal
 * it waits out from a real failure (#2416).
 */
class TripLoadFailureTest {

    @Test
    fun aRateLimitIsFlaggedHoweverItArrived() {
        // Held back locally during a cooldown, the OBA envelope's 429, and the AWS edge's bare status.
        val edge = HttpException(Response.error<Unit>(429, "".toResponseBody()))
        for (refusal in listOf(RateLimitedException(5.seconds), ObaApiException(429), edge)) {
            val failure = tripLoadFailure(refusal) { "message" }

            assertTrue(refusal.toString(), failure.rateLimited)
            assertSame(refusal, failure.cause)
        }
    }

    @Test
    fun anOrdinaryFailureIsNotFlagged() {
        assertFalse(tripLoadFailure(IOException("timeout")) { "message" }.rateLimited)
        assertFalse(tripLoadFailure(ObaApiException(404)) { "message" }.rateLimited)
        assertFalse(tripLoadFailure(null) { "message" }.rateLimited)
    }

    @Test
    fun theMessageIsTheOneForTheFailuresObaCode() {
        val messageFor = { code: Int -> "error $code" }

        assertEquals("error 404", tripLoadFailure(ObaApiException(404), messageFor).message)
        // Anything that isn't an OBA-stated failure reads as a transport error.
        assertEquals("error ${ObaApi.OBA_IO_EXCEPTION}", tripLoadFailure(IOException("timeout"), messageFor).message)
        assertEquals("error ${ObaApi.OBA_IO_EXCEPTION}", tripLoadFailure(null, messageFor).message)
    }
}
