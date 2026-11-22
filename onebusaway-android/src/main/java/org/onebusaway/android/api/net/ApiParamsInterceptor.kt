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

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.onebusaway.android.api.ObaApi

/**
 * Appends the shared OBA query parameters — api key, protocol version, and app identifiers — to every
 * request. The request already carries the correct region host (the Retrofit client [ObaApiProvider]
 * builds is bound to the live base URL), so this only layers on the cross-cutting params; it does no
 * host rewriting.
 *
 * Query parameters on a user-entered custom API URL override these defaults (see [withApiParams]).
 */
class ApiParamsInterceptor(private val resolver: ObaEndpointResolver) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = withApiParams(
            url = request.url,
            appVersion = resolver.appVersion,
            appUid = resolver.appUid,
            apiKey = resolver.apiKey,
            customParams = resolver.customQueryParams()
        )
        return chain.proceed(request.newBuilder().url(url).build())
    }
}

/**
 * [url] with the shared OBA query parameters added. [customParams] (from a custom API URL's query)
 * win over the defaults: a custom `key` replaces the built-in API key rather than being sent
 * alongside it, and any other custom parameter replaces a default of the same name.
 */
internal fun withApiParams(
    url: HttpUrl,
    appVersion: Int,
    appUid: String?,
    apiKey: String,
    customParams: List<Pair<String, String?>>
): HttpUrl = url.newBuilder().apply {
    if (appVersion != 0) addQueryParameter("app_ver", appVersion.toString())
    appUid?.let { addQueryParameter("app_uid", it) }
    addQueryParameter("version", ObaApi.VERSION2)
    addQueryParameter("key", apiKey)
    customParams.forEach { (name, value) -> setQueryParameter(name, value) }
}.build()
