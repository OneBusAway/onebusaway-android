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
package org.onebusaway.android.api.net

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody

/**
 * Reads a JSON response body as UTF-8 even when the server's `Content-Type` claims some other charset.
 *
 * The OBA "where" API answers every request with **`application/json;charset=ISO-8859-1`** while
 * emitting UTF-8 bytes. Retrofit's kotlinx-serialization converter decodes through
 * `ResponseBody.string()`, which believes that header, so every non-ASCII character in the API's text
 * arrived mojibaked: a service alert reading "What’s happening in Seattle today" (U+2019, wire bytes
 * `E2 80 99`) reached the screen as `What` + `â` + two unprintable C1 control characters + `s`. The
 * same corruption applied to every stop name, route name and headsign the region publishes with a
 * curly quote, an accent, or a dash.
 *
 * Overriding a server's stated charset is normally the wrong instinct, so the grounds are worth
 * stating rather than assumed:
 *  - **The format defines it.** RFC 8259 §8.1: "JSON text exchanged between systems that are not part
 *    of a closed ecosystem MUST be encoded using UTF-8." A public transit API is not a closed
 *    ecosystem, and the `charset` parameter is not even registered for `application/json`.
 *  - **The bytes agree.** The live Puget Sound API's response decodes cleanly as UTF-8 and yields the
 *    intended characters; decoded as the ISO-8859-1 it advertises, it yields the mojibake above. So
 *    this corrects a header that disagrees with its own body — it does not reinterpret the body.
 *
 * It is deliberately narrow, so it cannot silently re-encode something that really is Latin-1:
 *  - only JSON media types (`…/json`, `…/…+json`); an HTML error page keeps whatever it declares,
 *  - only when a charset is actually stated and is not already UTF-8. A response that states none is
 *    left alone — OkHttp already defaults those to UTF-8, so touching them would be noise.
 *
 * The body is re-wrapped rather than buffered: [okhttp3.ResponseBody.source] hands back the unread
 * stream, so this re-labels it and streams on.
 */
class JsonCharsetInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        val corrected = utf8JsonMediaTypeFor(body.contentType()) ?: return response
        return response.newBuilder()
            // Both the header and the body's own media type: the converter reads the latter, while
            // logging and any other interceptor reads the former, and they must not disagree.
            .header("Content-Type", corrected.toString())
            .body(body.source().asResponseBody(corrected, body.contentLength()))
            .build()
    }
}

/**
 * The UTF-8 media type [contentType] should have declared, or **null** to leave the response untouched
 * — the whole of [JsonCharsetInterceptor]'s decision, kept pure so `JsonCharsetInterceptorTest` can
 * cover the cases without a server.
 */
internal fun utf8JsonMediaTypeFor(contentType: MediaType?): MediaType? {
    if (contentType == null) return null
    // `application/json`, and the `+json` structured-suffix family (`application/problem+json`, …).
    if (contentType.subtype != "json" && !contentType.subtype.endsWith("+json")) return null
    // No charset stated: OkHttp already reads it as UTF-8, so there is nothing to correct.
    val declared = contentType.charset() ?: return null
    if (declared == Charsets.UTF_8) return null
    // Only the charset is wrong, so only the charset is replaced — every other parameter the server
    // attached (`profile` on a `+json` type, say) is carried through. OkHttp can look a parameter up by
    // name but not enumerate them, so the list is re-split off the original header text.
    val type = "${contentType.type}/${contentType.subtype}"
    val others = contentType.toString().splitParameters()
        .filterNot { it.substringBefore('=').trim().equals("charset", ignoreCase = true) }
    val rebuilt = (listOf(type) + others + "charset=utf-8").joinToString("; ")
    // The fallback is unreachable for anything OkHttp itself parsed, but a header rebuild must never be
    // the reason a good response fails: correct the charset alone rather than throw.
    return rebuilt.toMediaTypeOrNull() ?: "$type; charset=utf-8".toMediaType()
}

/**
 * The parameters of a `Content-Type` header, trimmed, with the leading type/subtype dropped.
 *
 * Split on the `;` that separate parameters — *not* on one inside a quoted value (RFC 2045 §5.1
 * `quoted-string`), which is part of the value and must survive the round trip intact.
 */
private fun String.splitParameters(): List<String> {
    val parameters = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var escaped = false
    for (c in this) {
        when {
            escaped -> escaped = false
            quoted && c == '\\' -> escaped = true
            c == '"' -> quoted = !quoted
            c == ';' && !quoted -> {
                parameters += current.toString()
                current.clear()
                continue
            }
        }
        current.append(c)
    }
    parameters += current.toString()
    return parameters.drop(1).map { it.trim() }
}
