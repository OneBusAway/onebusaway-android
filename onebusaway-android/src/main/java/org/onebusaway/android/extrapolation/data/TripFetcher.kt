/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.request.ObaShapeRequest
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/**
 * One-shot background fetches for per-trip resources (schedules and shapes) that the polling
 * responses don't carry. Pure fetch orchestration: results are written into [TripStore] on the
 * main thread, and the only state held here is in-flight request deduplication.
 *
 * Thread safety: all public methods must be called from the main thread. Fetches run on a private
 * executor and post their results back to the main thread.
 */
object TripFetcher {

    private const val TAG = "TripFetcher"

    private val fetchExecutor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingScheduleFetches: MutableSet<String> = HashSet()
    private val pendingShapeFetches: MutableSet<String> = HashSet()

    /**
     * Backfills schedules and shapes for every active trip in a trips-for-route response. Called
     * by the route poller after the response has been recorded into [TripStore].
     */
    fun ensureSchedulesAndShapes(response: ObaTripsForRouteResponse) {
        response.forEachActiveTrip { tripId, _, activeTrip ->
            ensureSchedule(tripId)
            val shapeId = activeTrip.shapeId
            if (shapeId != null) {
                ensureShape(tripId, shapeId)
            }
        }
    }

    fun ensureSchedule(tripId: String) {
        ensureFetched(
                tripId = tripId,
                pending = pendingScheduleFetches,
                isCached = { TripStore.getTrip(tripId)?.schedule != null },
                fetch = {
                    val ctx = Application.get().applicationContext
                    ObaTripDetailsRequest.Builder(ctx, tripId)
                            .setIncludeSchedule(true)
                            .setIncludeStatus(false)
                            .setIncludeTrip(false)
                            .build()
                            .call()
                            ?.schedule
                },
                onSuccess = { schedule -> TripStore.putSchedule(tripId, schedule) }
        )
    }

    @JvmOverloads
    fun ensureShape(
            tripId: String,
            shapeId: String,
            onReady: ((Polyline) -> Unit)? = null,
            onError: (() -> Unit)? = null
    ) {
        // Cached-hit fast path: still hops through mainHandler.post so callers always
        // observe the callback asynchronously, regardless of cache state.
        val cached = TripStore.getTrip(tripId)?.polyline
        if (cached != null) {
            if (onReady != null) mainHandler.post { onReady(cached) }
            return
        }
        ensureFetched(
                tripId = tripId,
                pending = pendingShapeFetches,
                isCached = { TripStore.getTrip(tripId)?.polyline != null },
                fetch = {
                    val ctx = Application.get().applicationContext
                    val response = ObaShapeRequest.newRequest(ctx, shapeId).call()
                    val points = response?.points
                    if (points != null && points.isNotEmpty()) Polyline(points) else null
                },
                onSuccess = { polyline ->
                    TripStore.putPolyline(tripId, polyline)
                    if (onReady != null) onReady(polyline)
                },
                onError = onError
        )
    }

    /**
     * Fetches a value off the main thread and applies the result on the main thread, with
     * pending-fetch deduplication. Failed fetches are retried naturally on the next call (typically
     * the next poll tick), which is already rate-limited by the polling interval — no extra
     * failure-cap or backoff logic is needed.
     *
     * [fetch] runs on the fetch executor; [onSuccess] and [onError] run on the main thread.
     */
    private fun <T : Any> ensureFetched(
            tripId: String,
            pending: MutableSet<String>,
            isCached: () -> Boolean,
            fetch: () -> T?,
            onSuccess: (T) -> Unit,
            onError: (() -> Unit)? = null
    ) {
        if (isCached()) return
        if (!pending.add(tripId)) return
        fetchExecutor.execute {
            val result: T? =
                    try {
                        fetch()
                    } catch (e: Exception) {
                        Log.e(TAG, "Fetch failed for $tripId", e)
                        null
                    }
            mainHandler.post {
                pending.remove(tripId)
                if (result != null) {
                    onSuccess(result)
                } else {
                    Log.d(TAG, "Fetch for $tripId yielded no data")
                    if (onError != null) onError()
                }
            }
        }
    }
}
