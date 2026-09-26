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
import java.net.HttpURLConnection
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.adapters.toOnDemandService
import org.onebusaway.android.api.adapters.toOnDemandServices
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.util.GeoPoint

/** The `geometryDetail` every screen that draws a zone asks for: drawable straight onto the map, ~15 KB per zone. */
const val GEOMETRY_DETAIL_SIMPLIFIED = "simplified"

/** The `geometryDetail` for a caller that never draws the service (the arrivals card): no geometry at all. */
const val GEOMETRY_DETAIL_NONE = "none"

/** The `geometryDetail` for the nearest-edge geometry (spec §2.7): the verbatim feed rings, cached per service. */
const val GEOMETRY_DETAIL_FULL = "full"

/** The radius, in metres, every rider/centre and exact-point probe asks for (spec §2.1). */
const val ONDEMAND_PROBE_RADIUS_METERS = 5_000

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
     * probe**: see [isProbeUnsupported] for every answer this reads as [OnDemandResult.Unsupported].
     */
    suspend fun servicesForViewport(viewport: CameraSnapshot): OnDemandResult<List<OnDemandService>>

    /**
     * Point mode (spec §2.1): every service whose area contains [point] or lies within [radiusMeters]
     * of it, each carrying `matchReason` and per-area `distanceToArea` / `nearestPointOnBoundary`.
     * A probe like [servicesForViewport]: see [isProbeUnsupported].
     */
    suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String = GEOMETRY_DETAIL_NONE): OnDemandResult<List<OnDemandService>>

    /**
     * One service by combined id, with [geometryDetail] geometry ([GEOMETRY_DETAIL_SIMPLIFIED] or
     * [GEOMETRY_DETAIL_NONE]). A 404 is [OnDemandResult.Failed] (not found).
     */
    suspend fun service(id: String, geometryDetail: String = GEOMETRY_DETAIL_SIMPLIFIED): OnDemandResult<OnDemandService>

    /** Every on-demand service of an agency. A 404 is [OnDemandResult.Failed] (unknown agency). */
    suspend fun servicesForAgency(agencyId: String): OnDemandResult<List<OnDemandService>>
}

/**
 * Classifies a call's outcome. Only a [probe] may read a failure as an absent endpoint; every other
 * failure stays transient. Pure, so the policy is JVM-tested without a network or `android.util.Log`
 * (which the data source below adds).
 */
internal fun <T> Result<T>.toOnDemandResult(probe: Boolean): OnDemandResult<T> = fold(
    onSuccess = { OnDemandResult.Loaded(it) },
    onFailure = { cause -> if (probe && isProbeUnsupported(cause)) OnDemandResult.Unsupported else OnDemandResult.Failed(cause) }
)

/**
 * Whether [cause] is one of the ways a deployment without `/api/ondemand` answers a probe (spec
 * §2.10): a raw HTTP 404 ([isEndpointAbsent]), an OBA envelope stating `code == 404`, or a 2xx
 * response whose body never decoded as the OBA envelope at all. That last case is what a stock
 * maglev `main` does: an unregistered path falls through to its catch-all `index.html`, so the
 * "probe" gets HTTP 200 `text/html`.
 *
 * The envelope's own JSON decoding is the only step in this pipeline that can throw
 * [SerializationException] — [requireData] and the `api.adapters` mapping functions work on already
 * -typed Kotlin objects and raise [org.onebusaway.android.api.ObaApiException] or
 * [IllegalArgumentException] instead, so a malformed record inside an otherwise valid envelope (a
 * model-adaption error) is never mistaken for an absent endpoint here; it stays [OnDemandResult.Failed].
 */
internal fun isProbeUnsupported(cause: Throwable): Boolean = isEndpointAbsent(cause) ||
    (cause is ObaApiException && cause.code == HttpURLConnection.HTTP_NOT_FOUND) ||
    cause is SerializationException

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

    override suspend fun servicesNear(point: GeoPoint, radiusMeters: Int, geometryDetail: String): OnDemandResult<List<OnDemandService>> = api.call { service ->
        service.onDemandServicesForLocation(
            lat = point.latitude,
            lon = point.longitude,
            radius = radiusMeters,
            geometryDetail = geometryDetail
        ).requireData().toOnDemandServices()
    }.toOnDemandResult(probe = true).logged("services-near")

    override suspend fun service(id: String, geometryDetail: String): OnDemandResult<OnDemandService> = api.call { service ->
        service.onDemandService(id, geometryDetail).requireData().toOnDemandService()
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
