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
import kotlin.time.Duration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
 * Only [ObaApiException] can carry that answer: it's what [requireData] raises off a decoded envelope,
 * and what [asObaFailure] maps a non-2xx response to *once it has confirmed the body is an OBA
 * envelope stating the same code*. A bare [HttpException] deliberately doesn't count — a 404 from a
 * proxy, or from a deployment that doesn't serve the endpoint at all, says nothing about the resource.
 */
val Throwable.isNotFound: Boolean
    get() = this is ObaApiException && code == HttpURLConnection.HTTP_NOT_FOUND

/**
 * True when this failure is an HTTP 429 "Too Many Requests" refusal — the cue to slow down rather than
 * retry promptly.
 *
 * Unlike [isNotFound], a bare [HttpException] counts here: the refusal that matters most doesn't come
 * from the OBA layer. Puget Sound's 429s are answered by the AWS edge in front of the server
 * (`Server: awselb/2.0`, a non-envelope JSON body), so [asObaFailure] leaves them an [HttpException];
 * the OBA server's own per-key limiter answers with an envelope, which arrives as [ObaApiException].
 * Either way the status itself says "too many requests", whoever sent it.
 *
 * A [RateLimitedException] counts too: it is the same refusal, just answered locally, without sending
 * the request, while the deployment is still cooling down from one.
 */
val Throwable.isRateLimited: Boolean
    get() = this is RateLimitedException ||
        (this is ObaApiException && code == HTTP_TOO_MANY_REQUESTS) ||
        (this is HttpException && code() == HTTP_TOO_MANY_REQUESTS)

/**
 * A request that was never sent, because its deployment answered another request with a 429 less than
 * a cooldown ago (see [org.onebusaway.android.api.net.RateLimitGate]). Extends [IOException] like
 * [ObaApiException], so callers that only care about "the request failed" treat it as one.
 *
 * It carries no stack trace: the message says all there is to say, and every data source logs each
 * failure, so a cooldown's refusals would otherwise flood logcat with traces of requests never sent.
 */
class RateLimitedException(retryIn: Duration) : IOException("OBA request not sent: rate-limited for another $retryIn") {
    override fun fillInStackTrace(): Throwable = this
}

/** HTTP 429; [HttpURLConnection] has no constant for it. */
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * The OBA answer behind a non-2xx response, as an [ObaApiException] — or this throwable unchanged when
 * the response didn't come from the OBA API itself.
 *
 * An OBA server sets the HTTP status from the envelope code and still writes the envelope as the body
 * (`trip-for-vehicle` for an unassigned vehicle answers HTTP 404 with
 * `{"code":404,"currentTime":…,"text":"resource not found","version":2}`), but Retrofit raises
 * [HttpException] on a non-2xx status before that body is ever decoded. Normalizing here — in the one
 * place every call passes through ([org.onebusaway.android.api.net.ObaApiProvider.call]) — gives
 * callers a single failure type to classify codes on, instead of each rediscovering that the same
 * app-level code arrives as two different exception types.
 *
 * The body is required to decode as an envelope **whose code equals the HTTP status**, so only the OBA
 * layer's own answer is adopted: a proxy's HTML 404, or a JSON error object from something else, fails
 * that check and stays an [HttpException]. Reading the error body is safe to do on the calling thread —
 * Retrofit buffers it in memory before constructing the [HttpException], so no I/O happens here — and a
 * body that can't be read anyway leaves the transport failure exactly as it arrived, since a body we
 * never saw states nothing about the resource.
 */
internal fun Throwable.asObaFailure(json: Json): Throwable {
    if (this !is HttpException) return this
    val body = try {
        response()?.errorBody()?.string().orEmpty()
    } catch (_: IOException) {
        return this
    }
    val envelope = try {
        json.decodeFromString<ObaEnvelope<JsonElement>>(body)
    } catch (_: SerializationException) {
        null
    }
    return if (envelope != null && envelope.code == code()) ObaApiException(envelope.code) else this
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
