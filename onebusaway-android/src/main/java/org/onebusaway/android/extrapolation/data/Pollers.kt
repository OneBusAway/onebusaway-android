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
@file:JvmName("Pollers")

package org.onebusaway.android.extrapolation.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.ObaApi
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.io.request.ObaTripsForRouteRequest
import org.onebusaway.android.io.request.ObaTripsForRouteResponse

private const val DEFAULT_POLL_INTERVAL_MS = 10_000L
private const val TAG = "Pollers"

/**
 * Polls trip details every [intervalMs] and records responses into [TripDataManager]. Lifecycle is
 * owned by the caller: call [start] in onResume, [stop] in onPause.
 */
class TripDetailsPoller
@JvmOverloads
constructor(private val tripId: String, private val intervalMs: Long = DEFAULT_POLL_INTERVAL_MS) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val ctx = Application.get().applicationContext
        job =
                MainScope().launch {
                    while (isActive) {
                        try {
                            val localTimeMs = System.currentTimeMillis()
                            val response =
                                    withContext(Dispatchers.IO) {
                                        ObaTripDetailsRequest.newRequest(ctx, tripId).call()
                                    }
                            if (response.code == ObaApi.OBA_OK) {
                                TripDataManager.recordTripDetailsResponse(
                                        tripId,
                                        response,
                                        localTimeMs
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch trip details for $tripId", e)
                        }
                        delay(intervalMs)
                    }
                }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Polls trips-for-route every [intervalMs], records responses into [TripDataManager], and delivers
 * each response to an optional callback on the main thread. Lifecycle is owned by the caller.
 */
class RoutePoller
@JvmOverloads
constructor(
        private val routeId: String,
        private val callback: ResponseCallback? = null,
        private val intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    fun interface ResponseCallback {
        fun onResponse(response: ObaTripsForRouteResponse)
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val ctx = Application.get().applicationContext
        job =
                MainScope().launch {
                    while (isActive) {
                        try {
                            val localTimeMs = System.currentTimeMillis()
                            val response =
                                    withContext(Dispatchers.IO) {
                                        ObaTripsForRouteRequest.Builder(ctx, routeId)
                                                .setIncludeStatus(true)
                                                .build()
                                                .call()
                                    }
                            if (response.code == ObaApi.OBA_OK) {
                                TripDataManager.recordTripsForRouteResponse(response, localTimeMs)
                                callback?.onResponse(response)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch trips for route $routeId", e)
                        }
                        delay(intervalMs)
                    }
                }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Fire-and-forget one-shot trip details fetch for UI refresh actions. Records the result into
 * [TripDataManager]; does not notify callers on success or failure.
 */
fun fetchTripDetailsOnce(tripId: String) {
    val ctx = Application.get().applicationContext
    MainScope().launch {
        try {
            val localTimeMs = System.currentTimeMillis()
            val response =
                    withContext(Dispatchers.IO) {
                        ObaTripDetailsRequest.newRequest(ctx, tripId).call()
                    }
            if (response.code == ObaApi.OBA_OK) {
                TripDataManager.recordTripDetailsResponse(tripId, response, localTimeMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed one-shot trip details fetch for $tripId", e)
        }
    }
}
