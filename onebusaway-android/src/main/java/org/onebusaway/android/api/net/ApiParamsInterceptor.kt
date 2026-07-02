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
import okhttp3.Response
import org.onebusaway.android.api.ObaApi

/**
 * Appends the shared OBA query parameters — api key, protocol version, and app identifiers — to every
 * request. The request already carries the correct region host (the Retrofit client [ObaApiProvider]
 * builds is bound to the live base URL), so this only layers on the cross-cutting params; it does no
 * host rewriting.
 */
class ApiParamsInterceptor(private val resolver: ObaEndpointResolver) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder().apply {
            if (resolver.appVersion != 0) addQueryParameter("app_ver", resolver.appVersion.toString())
            resolver.appUid?.let { addQueryParameter("app_uid", it) }
            addQueryParameter("version", ObaApi.VERSION2)
            addQueryParameter("key", resolver.apiKey)
        }.build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
