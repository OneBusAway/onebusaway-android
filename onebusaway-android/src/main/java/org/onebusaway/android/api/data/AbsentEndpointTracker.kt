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

/**
 * A one-way, in-memory latch of the OBA deployments found not to serve one optional endpoint, keyed
 * by the endpoint URL requests go to. Only an explicit HTTP 404 ([isEndpointAbsent]) records a
 * deployment; nothing clears it, so an upgraded server is re-probed on the next launch. Subclasses
 * name the endpoint and explain why its support has to be discovered rather than configured.
 *
 * Reads and writes come from queries on their own coroutines, hence the synchronized access rather
 * than a plain field.
 */
abstract class AbsentEndpointTracker {

    private val unsupported = mutableSetOf<String>()

    /** Whether [obaBaseUrl] is already known not to serve the endpoint. Unknown (and null) endpoints
     *  read as supported (un-probed), which is what makes the first query the probe. */
    @Synchronized
    fun isKnownUnsupported(obaBaseUrl: String?): Boolean = obaBaseUrl != null && obaBaseUrl in unsupported

    /** Record that the deployment at [obaBaseUrl] answered HTTP 404 for the endpoint. */
    @Synchronized
    fun recordAbsent(obaBaseUrl: String?) {
        obaBaseUrl?.let(unsupported::add)
    }
}
