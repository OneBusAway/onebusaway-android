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
package org.onebusaway.android.ondemand

import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.api.data.GEOMETRY_DETAIL_FULL
import org.onebusaway.android.api.data.OnDemandDataSource
import org.onebusaway.android.api.data.OnDemandResult
import org.onebusaway.android.models.ServiceArea

/**
 * Full-geometry rings per `(deployment, serviceId)` for the process lifetime (spec §2.7): fetched
 * lazily for matched services, never simplified, shared by the nearest-edge titles, the pan-to-edge
 * action and the dock thumbnail. A failed fetch is not cached, so the next need retries it.
 */
@Singleton
class OnDemandGeometryCache @Inject constructor(private val dataSource: OnDemandDataSource) {

    private val areas = HashMap<Pair<String, String>, List<ServiceArea>>()

    /** The cached rings, without fetching. */
    fun peek(deployment: String, serviceId: String): List<ServiceArea>? = synchronized(areas) { areas[deployment to serviceId] }

    /** The rings, fetching once; null when the fetch fails (or the deployment turns out unsupported). */
    suspend fun areas(deployment: String, serviceId: String): List<ServiceArea>? {
        peek(deployment, serviceId)?.let { return it }
        val fetched = when (val result = dataSource.service(serviceId, GEOMETRY_DETAIL_FULL)) {
            is OnDemandResult.Loaded -> result.value.areas
            is OnDemandResult.Failed, OnDemandResult.Unsupported -> return null
        }
        synchronized(areas) { areas[deployment to serviceId] = fetched }
        return fetched
    }

    fun clear() = synchronized(areas) { areas.clear() }
}
