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

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.api.adapters.toOnDemandService
import org.onebusaway.android.api.adapters.toOnDemandServices
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandService

/** The `geometryDetail` every screen asks for: drawable straight onto the map, ~15 KB per zone. */
const val GEOMETRY_DETAIL_SIMPLIFIED = "simplified"

/** One on-demand fetch, or the verdict that this region cannot serve the namespace. */
sealed interface OnDemandResult<out T> {

    /** The response, resolved to domain objects. A list may be empty — nothing covers this viewport. */
    data class Loaded<T>(val value: T) : OnDemandResult<T>

    /**
     * This deployment does not implement `/api/ondemand`: `services-for-location` answered HTTP 404.
     * A durable fact about the server, not an error to retry — see [isEndpointAbsent].
     */
    data object Unsupported : OnDemandResult<Nothing>

    /** A transient failure (transport, timeout, a non-OK OBA envelope code, a not-found). Retry later. */
    data class Failed(val cause: Throwable) : OnDemandResult<Nothing>
}

/** Fetches on-demand services (GTFS-Flex) from the modernized OBA client. Never throws. */
interface OnDemandDataSource {

    /**
     * Every service whose area or stops intersect [viewport], with simplified geometry. **The only
     * probe**: a raw HTTP 404 here yields [OnDemandResult.Unsupported]; nothing else does.
     */
    suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>>

    /** One service by combined id, with simplified geometry. A 404 is [OnDemandResult.Failed] (not found). */
    suspend fun service(id: String): OnDemandResult<OnDemandService>

    /** Every on-demand service of an agency. A 404 is [OnDemandResult.Failed] (unknown agency). */
    suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>>
}

/**
 * Classifies a call's outcome. Only a [probe] may read a raw HTTP 404 as an absent endpoint; an OBA
 * envelope 404 (`ObaApiException`) and every other failure stay transient. Pure, so the policy is
 * JVM-tested without a network or `android.util.Log` (which the data source below adds).
 */
internal fun <T> Result<T>.toOnDemandResult(probe: Boolean): OnDemandResult<T> = fold(
    onSuccess = { OnDemandResult.Loaded(it) },
    onFailure = { cause -> if (probe && isEndpointAbsent(cause)) OnDemandResult.Unsupported else OnDemandResult.Failed(cause) }
)

class DefaultOnDemandDataSource @Inject constructor(
    private val api: ObaApiProvider
) : OnDemandDataSource {

    override suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>> = api.call { service ->
        service.onDemandServicesForLocation(
            lat = viewport.center.latitude,
            lon = viewport.center.longitude,
            latSpan = viewport.latSpan,
            lonSpan = viewport.lonSpan,
            geometryDetail = GEOMETRY_DETAIL_SIMPLIFIED
        ).requireData().toOnDemandServices()
    }.toOnDemandResult(probe = true).logged("services-for-location")

    override suspend fun service(id: String): OnDemandResult<OnDemandService> = api.call { service ->
        service.onDemandService(id, GEOMETRY_DETAIL_SIMPLIFIED).requireData().toOnDemandService()
    }.toOnDemandResult(probe = false).logged("service($id)")

    override suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>> = api.call { service ->
        service.onDemandServicesForAgency(agencyId, GEOMETRY_DETAIL_SIMPLIFIED).requireData().toOnDemandServices()
    }.toOnDemandResult(probe = false).logged("services-for-agency($agencyId)")

    private fun <T> OnDemandResult<T>.logged(what: String): OnDemandResult<T> = also {
        when (it) {
            is OnDemandResult.Failed -> Log.e(TAG, "on-demand $what failed", it.cause)
            OnDemandResult.Unsupported -> Log.i(TAG, "Region does not serve /api/ondemand")
            is OnDemandResult.Loaded -> Unit
        }
    }

    private companion object {
        const val TAG = "OnDemandDataSource"
    }
}
