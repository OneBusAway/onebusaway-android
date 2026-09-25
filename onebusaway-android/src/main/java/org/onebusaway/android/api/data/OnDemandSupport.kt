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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which OBA deployments have been found not to serve `/api/ondemand` — the same shape, key and
 * lifetime as [NearbyArrivalsSupport], for the same reasons: the namespace is a property of the
 * server (maglev has it, onebusaway-application-modules does not), no directory field records it, so
 * it is discovered by the probe and believed only from an explicit HTTP 404 ([isEndpointAbsent]).
 * In memory only, keyed by the OBA base URL, so a region switch needs no reset and an upgraded
 * server is re-probed on the next launch.
 */
@Singleton
class OnDemandSupport @Inject constructor() {

    private val unsupported = mutableSetOf<String>()

    /** Whether [obaBaseUrl] is already known not to serve the namespace; unknown and null read as un-probed. */
    @Synchronized
    fun isKnownUnsupported(obaBaseUrl: String?): Boolean = obaBaseUrl != null && obaBaseUrl in unsupported

    /** Record that the deployment at [obaBaseUrl] answered HTTP 404 on `services-for-location`. */
    @Synchronized
    fun recordAbsent(obaBaseUrl: String?) {
        obaBaseUrl?.let(unsupported::add)
    }
}
