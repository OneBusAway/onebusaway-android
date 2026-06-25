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
package org.onebusaway.android.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.request.ObaStopsForLocationRequest
import org.onebusaway.android.io.request.ObaStopsForLocationResponse
import org.onebusaway.android.map.render.CameraSnapshot

/**
 * Loads the stops visible in the current map viewport. Replaces the `StopsLoader`
 * `AsyncTaskLoader` formerly nested in `StopMapController`; couriers the raw
 * [ObaStopsForLocationResponse] because the stop overlay consumes the raw `io/elements` types.
 */
interface StopsRepository {
    /**
     * @return the stops-for-location response, or `success(null)` when there is no OBA REST API
     * endpoint to contact yet (no current region and no custom API URL) — the legacy loader
     * returned a null-bodied response in that case and the controller treated it as a no-op.
     */
    suspend fun getStops(center: Location, latSpan: Double, lonSpan: Double):
            Result<ObaStopsForLocationResponse?>
}

class DefaultStopsRepository @Inject constructor(@ApplicationContext private val context: Context) : StopsRepository {

    override suspend fun getStops(center: Location, latSpan: Double, lonSpan: Double):
            Result<ObaStopsForLocationResponse?> = obaApiCall {
        ObaStopsForLocationRequest.Builder(context, center)
            .setSpan(latSpan, lonSpan)
            .build()
            .call()
    }
}

/**
 * Runs a blocking OBA REST [block] on the IO dispatcher, wrapped in a [Result]. Returns
 * `success(null)` when there is no endpoint to contact yet (no current region and no custom API
 * URL) — the legacy map loaders produced a null-bodied response there and the controllers treat it
 * as a no-op. Shared by the map repositories that gate on an OBA endpoint.
 */
internal suspend fun <T> obaApiCall(block: () -> T): Result<T?> =
    withContext(Dispatchers.IO) {
        runCatching { if (!hasObaApiEndpoint()) null else block() }
    }

/**
 * True when there is an OBA REST API endpoint to contact — a current region or a manually entered
 * custom API URL. Mirrors the guard the legacy map loaders applied before calling the server.
 */
internal fun hasObaApiEndpoint(): Boolean {
    val app = Application.get()
    return app.currentRegion != null || !app.customApiUrl.isNullOrEmpty()
}

/**
 * The zoom/limit-exceeded half of the "is the last response still good for this viewport?" decision
 * (the legacy `StopsResponse.fulfills`), split out from the Android [Location] center comparison so
 * it can be unit-tested on the JVM. Assumes the caller already confirmed the centers match.
 *
 * @param hasResponse whether the last request produced a (non-null) response
 * @param lastLimitExceeded the last response's `limitExceeded` flag
 * @param lastZoom the zoom the last response was loaded at
 * @param newZoom the zoom of the new viewport
 * @return true if the last response still satisfies the new viewport (no reload needed)
 */
internal fun zoomFulfills(
    hasResponse: Boolean,
    lastLimitExceeded: Boolean,
    lastZoom: Double,
    newZoom: Double,
): Boolean {
    if (!hasResponse) {
        return true
    }
    // Zooming in past a capped response, or zooming out, both need a fresh load.
    if (newZoom > lastZoom && lastLimitExceeded) {
        return false
    }
    if (newZoom < lastZoom) {
        return false
    }
    return true
}

/**
 * The whole-request replacement for the legacy `StopsResponse.fulfills`: is the new viewport [next]
 * already satisfied by the last completed stop load, so no reload is needed? The reactive stop loader
 * in [MapViewModel] uses this in place of the controller's `lastResponse?.fulfills(request)` check.
 *
 * The legacy version compared the request center as an Android [Location] — which has *reference*
 * equality, so a fresh `getMapCenterAsLocation()` instance almost never matched and the center gate
 * rarely short-circuited. [CameraSnapshot.center] is a value type, so the center comparison here is
 * honest value-equality (the intended behavior; see [CameraSnapshot]). The zoom/limit-exceeded half
 * delegates to [zoomFulfills], unchanged.
 *
 * @param last the camera the last completed load was made at, or null if nothing has loaded yet
 * @param lastHadResponse whether that load produced a non-null response (a null response — e.g. no
 * API endpoint — fulfilled future same-center viewports, matching the legacy null-response no-op)
 * @param lastLimitExceeded that response's `limitExceeded` flag
 * @param next the new viewport
 */
internal fun stopRequestFulfilled(
    last: CameraSnapshot?,
    lastHadResponse: Boolean,
    lastLimitExceeded: Boolean,
    next: CameraSnapshot,
): Boolean {
    if (last == null) {
        return false
    }
    if (last.center != next.center) {
        return false
    }
    return zoomFulfills(lastHadResponse, lastLimitExceeded, last.zoom, next.zoom)
}
