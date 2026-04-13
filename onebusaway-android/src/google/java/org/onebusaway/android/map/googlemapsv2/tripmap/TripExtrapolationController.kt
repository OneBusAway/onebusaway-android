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
package org.onebusaway.android.map.googlemapsv2.tripmap

import android.util.Log
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.extrapolation.data.Trip
import org.onebusaway.android.map.googlemapsv2.ThrottledFrameLoop

private const val TAG = "TripExtrapolationCtl"

/**
 * Owns the per-frame extrapolation loop for a single trip on the trip map view. Computes positions
 * and distributions each frame, then delegates all rendering to [TripVehicleOverlay].
 *
 * Runs entirely on the main thread: the choreographer drives the frame loop, and TripDataManager
 * mutations are also main-thread, so [trip] fields are read directly without synchronization.
 */
class TripExtrapolationController
internal constructor(private val vehicleOverlay: TripVehicleOverlay, private val trip: Trip) {
    private val frameLoop = ThrottledFrameLoop(::doFrame)

    fun start() = frameLoop.start()

    fun stop() = frameLoop.stop()

    private fun doFrame(now: Long) {
        val shapeData = trip.polyline ?: return
        val result =
                try {
                    trip.extrapolate(now)
                } catch (e: RuntimeException) {
                    // Programming-error path (e.g. require() failure in the gamma model on a
                    // degenerate
                    // schedule). Log so it surfaces, then skip this frame; the next frame will
                    // retry.
                    Log.w(TAG, "Extrapolation failed for ${trip.tripId}", e)
                    return
                }

        when (result) {
            is ExtrapolationResult.Success -> {
                val distribution = result.distribution
                val medianDist = distribution.median()
                if (medianDist.isFinite()) {
                    val loc = shapeData.interpolate(medianDist)
                    if (loc != null) {
                        vehicleOverlay.updateVehiclePosition(loc, trip.anchor, now)
                    }
                    vehicleOverlay.updateEstimateOverlays(distribution)
                } else {
                    // Bisect could not converge on a quantile — treat as a failed
                    // extrapolation frame rather than propagating NaN into rendering.
                    vehicleOverlay.hideVehicleMarker()
                    vehicleOverlay.hideEstimateOverlays()
                }
            }
            else -> {
                vehicleOverlay.hideVehicleMarker()
                vehicleOverlay.hideEstimateOverlays()
            }
        }

        trip.anchor?.let { vehicleOverlay.showOrUpdateDataReceivedMarker(it, now) }
    }
}
