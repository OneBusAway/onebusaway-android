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

import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.onebusaway.android.extrapolation.TripExtrapolation
import org.onebusaway.android.extrapolation.extrapolationFromState
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.map.render.BandSegment
import org.onebusaway.android.map.render.CameraCommand
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.map.render.TripOverlay
import org.onebusaway.android.map.render.TripStopDot
import org.onebusaway.android.util.Polyline

/**
 * The speed-estimation **trip-focus** use case: a dedicated single-trip live view. It draws only one
 * trip's shape (a thicker line) + its scheduled-stop dots, frames the camera to it, then drives the
 * live extrapolated overlay (the moving estimate + the confidence band) at ~20 Hz, tinting the band
 * with a color that contrasts the line. A cold driver over [MapHost]; the producer is color-free, so
 * the display tint lives here.
 *
 * [enter] starts the view for a trip; [exit] stops the frame loop and clears the trip overlay + dots.
 * The owner is responsible for leaving its prior mode before [enter] and restoring one after [exit].
 */
class TripFocusController(
    private val host: MapHost,
    private val tripObservationRepository: TripObservationRepository,
    private val scope: CoroutineScope,
) {

    private val renderState get() = host.renderState

    private var job: Job? = null

    /**
     * Enter the trip-focus view for [tripId]: draw its shape (tinted [routeColorArgb]) + scheduled-stop
     * dots, frame to it, and drive the live extrapolated overlay (its band tinted to contrast the line).
     */
    fun enter(tripId: String, routeColorArgb: Int) {
        job?.cancel()
        renderState.clearTripStops()
        // The live extrapolated overlay (the moving estimate + the confidence band): the renderer pulls
        // a frame from this sampler each display frame. The producer is color-free; we tint the band
        // here with a color that contrasts the line, so it reads against the trip shape.
        val bandColor = contrastingColor(routeColorArgb)
        renderState.setTripOverlaySampler { nowMs ->
            extrapolationFromState(tripObservationRepository.lookupTripState(tripId), nowMs)?.toOverlay(bandColor)
        }
        job = scope.launch {
            // Keep this trip's volatile status fresh while the overlay is on screen; the repository
            // records each poll into the store the sampler reads. Child of this job, so it stops on exit.
            launch { tripObservationRepository.tripDetailsStream(tripId).collect { /* recorded into the store */ } }
            // Draw just this trip's shape (a thicker single-trip line) + the scheduled-stop dots, and
            // frame to it, once the shape + schedule hydrate.
            val shape = awaitTripShape(tripId) ?: return@launch
            val stopTimes = tripObservationRepository.lookupTripState(tripId)?.schedule?.stopTimes
            renderState.setTripStops(
                stopTimes.orEmpty().mapNotNull { stopTime ->
                    shape.interpolate(stopTime.distanceAlongTrip)?.let { TripStopDot(it.toGeoPoint()) }
                }
            )
            renderState.setRoutePolylines(
                listOf(
                    RoutePolyline(
                        color = routeColorArgb,
                        points = shape.points.map { it.toGeoPoint() },
                        widthDp = TRIP_LINE_WIDTH_DP,
                    )
                )
            )
            host.dispatchCamera(CameraCommand.FitToRoute)
        }
    }

    /** Leave trip-focus: stop the keep-fresh poll and clear the trip overlay sampler + scheduled-stop dots. */
    fun exit() {
        job?.cancel()
        job = null
        renderState.setTripOverlaySampler(null)
        renderState.setSelectedTripMarker(null)
        renderState.clearTripStops()
    }

    /** Shifts hue by 180° to produce a color that contrasts with [color] (the trip line's color). */
    private fun contrastingColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color or 0xFF000000.toInt(), hsv)
        hsv[0] = (hsv[0] + 180f) % 360f
        return Color.HSVToColor(hsv)
    }

    /**
     * Resolves this trip's shape, returning the cached [Polyline] if present, otherwise polling the
     * store until the details response carries the trip's shapeId and then fetching it. Cancellation-
     * cooperative via [delay], so a cancelled trip-focus job breaks out.
     */
    private suspend fun awaitTripShape(tripId: String): Polyline? {
        repeat(MAX_SHAPE_POLL_ATTEMPTS) {
            val state = tripObservationRepository.lookupTripState(tripId)
            state?.polyline?.let { return it }
            val shapeId = state?.tripDetailsResponse?.getTrip(tripId)?.shapeId?.takeIf { it.isNotEmpty() }
            if (shapeId != null) {
                tripObservationRepository.ensureShape(tripId, shapeId)?.let { return it }
            }
            delay(SHAPE_POLL_INTERVAL_MS)
        }
        // The shape never resolved (bad/missing shapeId). Give up instead of polling the store every
        // SHAPE_POLL_INTERVAL_MS for the rest of the session; the trip-focus view just won't draw a line.
        return null
    }

    /**
     * Composites the display [bandColorArgb] onto a color-free [TripExtrapolation] to produce the
     * render [TripOverlay]: each band slice's model weight becomes the hue's alpha. The contrasting
     * hue is a display decision; the extrapolation knows nothing about it.
     */
    private fun TripExtrapolation.toOverlay(bandColorArgb: Int): TripOverlay {
        val baseRgb = bandColorArgb and 0x00FFFFFF
        return TripOverlay(
            vehiclePoint = vehiclePoint,
            fastEstimatePoint = fastEstimatePoint,
            band = band.map { slice ->
                val alpha = (slice.weight.coerceIn(0f, 1f) * 255f).roundToInt()
                BandSegment(slice.points, (alpha shl 24) or baseRgb)
            },
            dataAge = dataAge,
            fixTimeMs = fixTimeMs,
        )
    }

    companion object {
        // The trip-focus map's single-trip line — thicker than a route-mode line to read as a focused
        // view. The matching band/markers draw on top of it.
        private const val TRIP_LINE_WIDTH_DP = 8f

        // Poll the store for this trip's shape every interval until it hydrates, capped so a permanently
        // missing shapeId can't poll forever. 120 × 500ms ≈ 60s — generous for a slow details fetch.
        private const val SHAPE_POLL_INTERVAL_MS = 500L
        private const val MAX_SHAPE_POLL_ATTEMPTS = 120
    }
}
