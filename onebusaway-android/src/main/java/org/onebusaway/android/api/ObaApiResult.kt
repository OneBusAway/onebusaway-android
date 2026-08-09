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
import java.net.HttpURLConnection
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import retrofit2.HttpException

/**
 * Thrown when an OBA response carries a non-OK app-level [code] (a standard HTTP status mirrored in
 * the OBA envelope), or its body is absent. Carrying the [code] lets callers that need to distinguish
 * outcomes (e.g. 404 not-found vs a server error) map it to the right user message; it extends
 * [IOException] so callers that only care about "the request failed" still catch it uniformly.
 */
class ObaApiException(val code: Int) : IOException("OBA request failed (code $code)")

/**
 * Unwraps an OBA envelope to its payload, throwing [ObaApiException] when the app-level
 * [ObaEnvelope.code] is not OK or the body is absent. Centralizes the success policy so every
 * repository's `runCatching` maps the same failures to `Result.failure` instead of re-checking the
 * code/null per endpoint.
 */
fun <T> ObaEnvelope<T>.requireData(): T {
    if (code != HttpURLConnection.HTTP_OK || data == null) {
        throw ObaApiException(code)
    }
    return data
}

/**
 * True when this failure is the API's definitive "no such resource" answer rather than a transport,
 * server or decode failure — the distinction a caller needs to tell "the server says there is none"
 * from "we couldn't ask".
 *
 * An OBA server sets the HTTP status from the envelope code (`trip-for-vehicle` for an unassigned
 * vehicle answers HTTP 404 with `{"code":404,...}`), and Retrofit raises [HttpException] on a non-2xx
 * status before the body is ever decoded — so that is the form a 404 normally takes. [ObaApiException]
 * is the same answer read off the decoded envelope by [requireData], which is what a response carrying
 * the code under a 2xx status would produce; both mean not-found, so both count here.
 */
val Throwable.isNotFound: Boolean
    get() = when (this) {
        is ObaApiException -> code == HttpURLConnection.HTTP_NOT_FOUND
        is HttpException -> code() == HttpURLConnection.HTTP_NOT_FOUND
        else -> false
    }

/**
 * Asserts an OK app-level code, throwing [ObaApiException] otherwise — for endpoints whose response
 * carries no data payload (e.g. report-problem), where [requireData] can't be used because there's
 * nothing to return. Lets the caller's `runCatching` map a non-OK code to `Result.failure`.
 */
fun ObaEnvelope<*>.requireOk() {
    if (code != HttpURLConnection.HTTP_OK) {
        throw ObaApiException(code)
    }
}

/**
 * The list payload of a list endpoint, or empty when the app-level [ObaEnvelope.code] is not OK.
 * Unlike [requireData], a server error *code* yields no results rather than a failure — the
 * behavior list/search screens want (an error reads as "nothing found", not a crash). A transport
 * or parse failure still throws before reaching here, so callers' `runCatching` maps that to
 * `Result.failure`.
 */
fun <T> ObaEnvelope<ListWithReferences<T>>.listOrEmpty(): List<T> = if (code == HttpURLConnection.HTTP_OK) data?.list.orEmpty() else emptyList()
