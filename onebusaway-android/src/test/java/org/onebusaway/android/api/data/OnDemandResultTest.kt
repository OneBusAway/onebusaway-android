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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.ObaApiException
import retrofit2.HttpException
import retrofit2.Response

/**
 * How a failed on-demand call is classified. Only the probe (`services-for-location`) may conclude
 * "this deployment has no `/api/ondemand`", and only from a raw HTTP 404 (`isEndpointAbsent`); every
 * other failure — including a 404 on `service/{id}`, which is an ordinary not-found — is transient.
 */
class OnDemandResultTest {

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "".toResponseBody("text/html".toMediaType())))

    @Test
    fun `success is Loaded`() {
        assertEquals(OnDemandResult.Loaded(listOf("a")), Result.success(listOf("a")).toOnDemandResult(probe = true))
    }

    @Test
    fun `a raw 404 on the probe is Unsupported`() {
        assertEquals(OnDemandResult.Unsupported, Result.failure<Unit>(http(404)).toOnDemandResult(probe = true))
    }

    @Test
    fun `a raw 404 off the probe is a transient failure`() {
        val cause = http(404)
        val result = Result.failure<Unit>(cause).toOnDemandResult(probe = false)
        assertTrue(result is OnDemandResult.Failed)
        assertSame(cause, (result as OnDemandResult.Failed).cause)
    }

    @Test
    fun `an OBA envelope 404 is not an absent endpoint even on the probe`() {
        val result = Result.failure<Unit>(ObaApiException(404)).toOnDemandResult(probe = true)
        assertTrue(result is OnDemandResult.Failed)
    }

    @Test
    fun `other failures are transient`() {
        assertTrue(Result.failure<Unit>(http(500)).toOnDemandResult(probe = true) is OnDemandResult.Failed)
        assertTrue(Result.failure<Unit>(IOException("offline")).toOnDemandResult(probe = true) is OnDemandResult.Failed)
    }
}
