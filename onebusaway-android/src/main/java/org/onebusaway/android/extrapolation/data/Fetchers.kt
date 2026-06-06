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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import org.onebusaway.android.app.Application
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.request.ObaShapeRequest
import org.onebusaway.android.io.request.ObaTripDetailsRequest
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.SingleFlight

/*
 * Pure, demand-driven fetch functions for immutable per-trip resources. These functions know
 * nothing about TripStore: they take IDs, return values, and leave caching and hydration
 * decisions to the caller. The only state here is SingleFlight dedup — concurrent callers for the
 * same resource share one fetch, so no caller can be silently dropped and slow networks can't
 * pile up duplicate requests — and rate limiting is a property of the dispatcher, not logic.
 *
 * Store-hydrating sources (the pollers, one-shot refreshes, and the route-poll backfill that
 * composes these fetchers with TripStore) live in Pollers.kt.
 *
 * Threading: all public functions must be called from the main thread. Fetches run on
 * [fetchDispatcher]; results are returned on the main thread. Failures resolve to null; callers
 * retry naturally on their next attempt (typically the next poll tick), which is already
 * rate-limited by the polling interval.
 */

private const val MAX_CONCURRENT_FETCHES = 2
private const val TAG = "Fetchers"

/** Process-lifetime scope on the main dispatcher; fetches are never cancelled centrally. */
private val fetchScope = MainScope()

/**
 * All fetches run on this IO-dispatcher view, which allows at most [MAX_CONCURRENT_FETCHES] to
 * execute at once — so a route-poll backfill observing dozens of trips can't fan out into dozens
 * of simultaneous API requests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val fetchDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_FETCHES)

private val scheduleFetches = SingleFlight<String, ObaTripSchedule>(fetchScope)
private val shapeFetches = SingleFlight<String, Polyline>(fetchScope)

/** Fetches the trip's schedule. Null when the fetch fails; a later call retries. */
suspend fun fetchTripSchedule(tripId: String): ObaTripSchedule? =
        scheduleFetches.run(tripId) {
            withContext(fetchDispatcher) {
                val ctx = Application.get().applicationContext
                ObaTripDetailsRequest.Builder(ctx, tripId)
                        .setIncludeSchedule(true)
                        .setIncludeStatus(false)
                        .setIncludeTrip(false)
                        .build()
                        .call()
                        ?.schedule
            }.also {
                // Error-coded responses resolve to null without throwing; make that visible
                if (it == null) Log.w(TAG, "Schedule fetch for $tripId yielded no schedule")
            }
        }

/**
 * Fetches a shape polyline. Keyed by shapeId, so trips sharing a shape share one fetch. Null when
 * the fetch fails; a later call retries.
 */
suspend fun fetchShape(shapeId: String): Polyline? =
        shapeFetches.run(shapeId) {
            withContext(fetchDispatcher) {
                val ctx = Application.get().applicationContext
                val points = ObaShapeRequest.newRequest(ctx, shapeId).call()?.points
                if (points != null && points.isNotEmpty()) Polyline(points) else null
            }.also {
                // Error-coded responses resolve to null without throwing; make that visible
                if (it == null) Log.w(TAG, "Shape fetch for $shapeId yielded no polyline")
            }
        }
