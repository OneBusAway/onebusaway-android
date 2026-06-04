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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaShapeRequest
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.util.Polyline

/**
 * The demand-driven half of the trip data layer's network sources, counterpart to the time-driven
 * pollers in Pollers.kt. The split is by trigger and by how the data ages:
 * - Pollers re-fetch **volatile** data (vehicle status ages in seconds) on a recurring interval
 *   while a screen is watching.
 * - TripFetcher performs **one-shot** fetches on demand: [ensureSchedule]/[ensureShape] memoize
 *   immutable per-trip resources (skip when cached, dedup in-flight, fetch once), and
 *   [fetchTripDetailsOnce] serves explicit user-triggered refreshes.
 *
 * Results are written into [TripStore] on the main thread; the only state held here is in-flight
 * request deduplication.
 *
 * Threading: all public methods must be called from the main thread. Fetches run on
 * [Dispatchers.IO], bounded by [fetchSemaphore]; results are applied back on the main thread.
 */
object TripFetcher {

    private const val TAG = "TripFetcher"
    private const val MAX_CONCURRENT_FETCHES = 2

    /** Singleton-lifetime scope on the main dispatcher; one-shot jobs need no cancellation. */
    private val scope = MainScope()

    /**
     * Bounds concurrent backfill fetches so a route poll observing dozens of trips doesn't fan
     * out into dozens of simultaneous API requests.
     */
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

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
        // Cached-hit fast path: still hops through a launch so callers always observe the
        // callback asynchronously, regardless of cache state.
        val cached = TripStore.getTrip(tripId)?.polyline
        if (cached != null) {
            if (onReady != null) scope.launch { onReady(cached) }
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
     * Fire-and-forget one-shot trip details fetch for UI refresh actions. Records the result into
     * [TripStore]; does not notify callers on success or failure. Unlike the ensure* methods this
     * always fetches — status is volatile, so there is no cache to consult.
     */
    fun fetchTripDetailsOnce(tripId: String) {
        val ctx = Application.get().applicationContext
        scope.launch {
            try {
                val localTimeMs = System.currentTimeMillis()
                val response =
                        withContext(Dispatchers.IO) {
                            ObaTripDetailsRequest.newRequest(ctx, tripId).call()
                        }
                if (response.code == ObaApi.OBA_OK) {
                    TripStore.recordTripDetailsResponse(tripId, response, localTimeMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed one-shot trip details fetch for $tripId", e)
            }
        }
    }

    /**
     * Fetches a value off the main thread and applies the result on the main thread, with
     * pending-fetch deduplication. Failed fetches are retried naturally on the next call (typically
     * the next poll tick), which is already rate-limited by the polling interval — no extra
     * failure-cap or backoff logic is needed.
     *
     * [fetch] runs on [Dispatchers.IO] under [fetchSemaphore]; [onSuccess] and [onError] run on
     * the main thread.
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
        scope.launch {
            val result: T? =
                    try {
                        fetchSemaphore.withPermit { withContext(Dispatchers.IO) { fetch() } }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fetch failed for $tripId", e)
                        null
                    }
            pending.remove(tripId)
            if (result != null) {
                onSuccess(result)
            } else {
                Log.d(TAG, "Fetch for $tripId yielded no data")
                onError?.invoke()
            }
        }
    }
}
