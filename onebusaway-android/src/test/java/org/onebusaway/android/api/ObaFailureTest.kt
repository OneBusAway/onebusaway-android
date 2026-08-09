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
package org.onebusaway.android.api

import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Which non-2xx responses count as the OBA layer's own answer. The distinction matters because
 * [isNotFound] is read as "the server says there is no such resource" — so a 404 that OBA didn't write
 * (a proxy's, or a deployment that doesn't serve the endpoint) must not be adopted as one.
 */
class ObaFailureTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun anObaEnvelopeBodyBecomesAnObaApiException() {
        // Verbatim from api.pugetsound.onebusaway.org for an unassigned vehicle: the envelope is the
        // body, and the HTTP status mirrors its code.
        val failure = httpError(404, """{"code":404,"currentTime":1786250725829,"text":"resource not found","version":2}""")

        val normalized = failure.asObaFailure(json)

        assertEquals(404, (normalized as ObaApiException).code)
        assertTrue(normalized.isNotFound)
    }

    @Test
    fun aBodyThatIsntAnObaEnvelopeStaysTransport() {
        // A fronting proxy's 404 for a path the OBA deployment never saw.
        val proxy = httpError(404, "<html><body>404 Not Found</body></html>")
        val empty = httpError(404, "")
        // JSON, but from something else — no envelope code, so nothing states the resource is missing.
        val otherJson = httpError(404, """{"error":"not found"}""")

        listOf(proxy, empty, otherJson).forEach { failure ->
            // One call per failure: reading the body consumes it, as it would in production.
            val normalized = failure.asObaFailure(json)
            assertSame(failure, normalized)
            assertFalse(normalized.isNotFound)
        }
    }

    @Test
    fun anEnvelopeCodeThatContradictsTheStatusIsNotAdopted() {
        // A body whose code doesn't match the status it arrived under isn't this response's answer.
        val failure = httpError(502, """{"code":404,"text":"resource not found","version":2}""")

        assertSame(failure, failure.asObaFailure(json))
    }

    @Test
    fun aBodyThatCantBeReadLeavesTheFailureAlone() {
        val failure = HttpException(
            Response.error<Unit>(
                404,
                object : ResponseBody() {
                    override fun contentType(): MediaType = "application/json".toMediaType()

                    override fun contentLength(): Long = -1

                    override fun source(): BufferedSource = throw IOException("stream closed")
                }
            )
        )

        assertSame(failure, failure.asObaFailure(json))
    }

    @Test
    fun aNonHttpFailurePassesThrough() {
        val failure = IOException("connection reset")

        assertSame(failure, failure.asObaFailure(json))
    }

    @Test
    fun onlyA404CountsAsNotFound() {
        assertTrue(ObaApiException(404).isNotFound)
        assertFalse(ObaApiException(500).isNotFound)
        assertFalse(IOException("connection reset").isNotFound)
    }

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType()))
    )
}
