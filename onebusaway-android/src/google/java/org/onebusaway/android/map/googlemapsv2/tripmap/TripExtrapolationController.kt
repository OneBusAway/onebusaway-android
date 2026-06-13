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
import org.onebusaway.android.extrapolation.data.TripStore.lookupTripState
import org.onebusaway.android.map.googlemapsv2.ThrottledFrameLoop

private const val TAG = "TripExtrapolationCtl"

/**
 * Owns the per-frame extrapolation loop for a single trip on the trip map view. Computes positions
 * and distributions each frame, then delegates all rendering to [TripVehicleOverlay].
 *
 * Each frame reads one consistent TripState snapshot from the trip store (a single map lookup)
 * and computes everything from it; data updates land as new snapshots between frames.
 */
class TripExtrapolationController
internal constructor(
        private val vehicleOverlay: TripVehicleOverlay,
        private val tripId: String
) {
    private val frameLoop = ThrottledFrameLoop(::doFrame)

    fun start() = frameLoop.start()

    fun stop() = frameLoop.stop()

    private fun doFrame(now: Long) {
        val state = lookupTripState(tripId)
        val shapeData = state?.polyline
        if (state == null || shapeData == null) {
            // Nothing to render this frame (trip evicted or shape not hydrated) — don't
            // leave a previous frame's marker on screen
            hideFrameOverlays()
            return
        }
        val result =
                try {
                    state.extrapolate(now)
                } catch (e: IllegalArgumentException) {
                    // require() failure in the gamma model on a degenerate schedule. Log so it
                    // surfaces, then skip this frame; the next frame retries. Anything else
                    // propagates rather than degrading silently at 20fps.
                    Log.w(TAG, "Extrapolation failed for $tripId", e)
                    hideFrameOverlays()
                    return
                }

        when (result) {
            is ExtrapolationResult.Success -> {
                val distribution = result.distribution
                val medianDist = distribution.median()
                val loc = if (medianDist.isFinite()) shapeData.interpolate(medianDist) else null
                if (loc != null) {
                    vehicleOverlay.updateVehiclePosition(loc, state.anchor, now)
                    vehicleOverlay.updateEstimateOverlays(distribution)
                } else {
                    // Bisect could not converge on a quantile, or interpolation failed on a
                    // degenerate polyline — treat the frame as failed rather than leaving a
                    // stale marker or propagating NaN into rendering.
                    hideFrameOverlays()
                }
            }
            else -> hideFrameOverlays()
        }

        state.anchor?.let { vehicleOverlay.showOrUpdateDataReceivedMarker(it, now) }
    }

    /** Every failed frame renders the same way: no vehicle marker, no estimate overlays. */
    private fun hideFrameOverlays() {
        vehicleOverlay.hideVehicleMarker()
        vehicleOverlay.hideEstimateOverlays()
    }
}
