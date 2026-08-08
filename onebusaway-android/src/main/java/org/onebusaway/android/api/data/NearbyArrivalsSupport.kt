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
 * Which OBA deployments have been found not to serve arrivals-and-departures-for-location (#2107).
 *
 * The endpoint reached onebusaway-application-modules in 2022, so whether a server answers it is a
 * property of how old that deployment is — a fact no directory field records. `RegionDto` carries
 * `supportsObaDiscoveryApis` / `supportsObaRealtimeApis` but nothing for this, and adding one would
 * mean the regions directory, the bundled `regions_v3.json`, and `tools/check-regions-drift.py`'s
 * `CHECKED_FIELDS` all had to agree before a single rider benefited. So it is discovered instead:
 * ask once, and believe an explicit HTTP 404 ([isEndpointAbsent]).
 *
 * **Held in memory only, deliberately.** Persisting the verdict would pin a deployment off across
 * launches, so a server that upgraded would need either a TTL — a magic number, and one nobody can
 * derive — or a manual invalidation with nothing to invalidate against. Re-probing each process costs
 * one request per unsupported deployment per launch, which is nothing next to being wrong until a
 * reinstall.
 *
 * **Keyed by the OBA base URL, not by `Region.id`.** The verdict is a statement about the *server*
 * that answered, and the region is only how the app happens to reach one: a directory refresh can
 * repoint an existing region's `obaBaseUrl` at a different deployment under the same id, and a
 * deep-link-added custom region is a host the directory never named. Keying on our primary key would
 * carry a 404 from one server across to another and leave the drawer switched off where it would in
 * fact work. An unknown endpoint is simply un-probed, so a region switch needs no reset.
 *
 * The one thing this key does not distinguish is a user-entered custom API URL
 * (`preference_key_oba_api_url`, applied by `ObaEndpointResolver` ahead of the region): overriding it
 * without changing regions reuses the region's verdict. That preference is a developer-facing escape
 * hatch, and the cost is one stale in-memory verdict until the next launch.
 *
 * Reads and writes come from the arrivals query on its own coroutines, hence the synchronized access
 * rather than a plain field.
 */
@Singleton
class NearbyArrivalsSupport @Inject constructor() {

    private val unsupported = mutableSetOf<String>()

    /** Whether [obaBaseUrl] is already known not to serve the endpoint. Unknown (and null) endpoints
     *  read as supported (un-probed), which is what makes the first query the probe. */
    @Synchronized
    fun isKnownUnsupported(obaBaseUrl: String?): Boolean = obaBaseUrl != null && obaBaseUrl in unsupported

    /** Record that the deployment at [obaBaseUrl] answered 404 for the endpoint. */
    @Synchronized
    fun recordAbsent(obaBaseUrl: String?) {
        obaBaseUrl?.let(unsupported::add)
    }
}
