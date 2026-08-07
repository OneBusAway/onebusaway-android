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
 * Which regions have been found not to serve arrivals-and-departures-for-location (#2107).
 *
 * The endpoint reached onebusaway-application-modules in 2022, so whether a region answers it is a
 * property of how old that deployment is — a fact no directory field records. `RegionDto` carries
 * `supportsObaDiscoveryApis` / `supportsObaRealtimeApis` but nothing for this, and adding one would
 * mean the regions directory, the bundled `regions_v3.json`, and `tools/check-regions-drift.py`'s
 * `CHECKED_FIELDS` all had to agree before a single rider benefited. So it is discovered instead:
 * ask once, and believe an explicit HTTP 404 ([isEndpointAbsent]).
 *
 * **Held in memory only, deliberately.** Persisting the verdict would pin a region off across
 * launches, so a deployment that upgraded would need either a TTL — a magic number, and one nobody
 * can derive — or a manual invalidation with nothing to invalidate against. Re-probing each process
 * costs one request per unsupported region per launch, which is nothing next to being wrong until a
 * reinstall.
 *
 * Keyed by region id, since the verdict is about one deployment; a region switch needs no reset,
 * because an unknown id is simply un-probed. Reads and writes come from the arrivals query on its
 * own coroutines, hence the synchronized access rather than a plain field.
 */
@Singleton
class NearbyArrivalsSupport @Inject constructor() {

    private val unsupported = mutableSetOf<Long>()

    /** Whether [regionId] is already known not to serve the endpoint. Unknown ids read as supported
     *  (un-probed), which is what makes the first query the probe. */
    @Synchronized
    fun isKnownUnsupported(regionId: Long?): Boolean = regionId != null && regionId in unsupported

    /** Record that [regionId]'s server answered 404 for the endpoint. */
    @Synchronized
    fun recordAbsent(regionId: Long?) {
        regionId?.let(unsupported::add)
    }
}
