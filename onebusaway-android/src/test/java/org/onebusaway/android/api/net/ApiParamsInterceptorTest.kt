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

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.ObaApi

/** Covers [withApiParams], the whole of [ApiParamsInterceptor]'s decision. */
class ApiParamsInterceptorTest {

    private val request = "https://example.org/api/where/current-time.json".toHttpUrl()

    private fun apply(customParams: List<Pair<String, String?>>) = withApiParams(
        url = request,
        appVersion = 7,
        appUid = "uid",
        apiKey = DEFAULT_KEY,
        customParams = customParams
    )

    /** Without a custom URL the request carries exactly the built-in parameters. */
    @Test
    fun addsTheDefaultsWhenNoCustomParams() {
        val url = apply(emptyList())

        assertEquals(listOf(DEFAULT_KEY), url.queryParameterValues("key"))
        assertEquals("7", url.queryParameter("app_ver"))
        assertEquals("uid", url.queryParameter("app_uid"))
        assertEquals(ObaApi.VERSION2, url.queryParameter("version"))
    }

    /** A server with its own API key: the custom key is sent instead of the built-in one, not as well. */
    @Test
    fun customKeyReplacesTheDefaultKey() {
        val url = apply(listOf("key" to "server-key"))

        assertEquals(listOf("server-key"), url.queryParameterValues("key"))
    }

    /** Any other custom parameter is passed through, and one named like a default replaces it. */
    @Test
    fun otherCustomParamsArePassedThroughAndOverrideDefaults() {
        val url = apply(listOf("extra" to "1", "version" to "3"))

        assertEquals("1", url.queryParameter("extra"))
        assertEquals(listOf("3"), url.queryParameterValues("version"))
        assertEquals(listOf(DEFAULT_KEY), url.queryParameterValues("key"))
    }

    /** The request's own path and query are left alone. */
    @Test
    fun keepsTheRequestsOwnQuery() {
        val url = withApiParams(
            url = "https://example.org/api/where/stop/1_75403.json?includeReferences=false".toHttpUrl(),
            appVersion = 0,
            appUid = null,
            apiKey = DEFAULT_KEY,
            customParams = listOf("key" to "server-key")
        )

        assertEquals("/api/where/stop/1_75403.json", url.encodedPath)
        assertEquals("false", url.queryParameter("includeReferences"))
        assertEquals(listOf("server-key"), url.queryParameterValues("key"))
    }

    private companion object {
        const val DEFAULT_KEY = "default-key"
    }
}
