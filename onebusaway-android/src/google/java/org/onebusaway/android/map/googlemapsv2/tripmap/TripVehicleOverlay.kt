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

import android.content.Context
import android.graphics.Color
import android.location.Location
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.onebusaway.android.R
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.map.googlemapsv2.AnimationUtil
import org.onebusaway.android.map.googlemapsv2.MapHelpV2
import org.onebusaway.android.map.googlemapsv2.MapIconUtils
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.UIUtils

private const val ANIMATE_DURATION_MS = 600
internal const val MARKER_Z_INDEX = 3f
internal const val POLYLINE_WIDTH_PX = 44f

/** Shifts hue by 180 degrees to produce a color that contrasts with the input. */
private fun contrastingColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color or 0xFF000000.toInt(), hsv)
    hsv[0] = (hsv[0] + 180f) % 360f
    return Color.HSVToColor(hsv)
}

/**
 * Renders the live vehicle data on the trip map: vehicle position marker, data-received marker, and
 * PDF estimate overlay. Updated per-frame by the extrapolation controller.
 */
class TripVehicleOverlay(
        private val map: GoogleMap,
        private val context: Context,
        private val shapeData: Polyline,
        private val routeColor: Int,
        private val routeType: Int?
) {
    private val overlayColor = contrastingColor(routeColor)

    // --- Vehicle marker ---
    private var vehicleMarker: Marker? = null
    private var lastFixTime = 0L
    private var animatingUntil = 0L

    private val vehicleIcon by lazy {
        MapIconUtils.createCircleIcon(context, R.drawable.ic_vehicle_position)
    }

    // --- Data-received marker ---
    private var dataReceivedMarker: Marker? = null
    private var dataReceivedInfoShown = false
    private var lastDataReceivedUpdateTime = 0L

    private val dataReceivedIcon by lazy { MapIconUtils.createDataReceivedIcon(context) }

    // --- Estimate overlay ---
    private var estimateOverlay: DistanceEstimateOverlay? = null

    fun activate(vehiclePosition: LatLng?) {
        if (routeType == null || !ObaRoute.isGradeSeparated(routeType)) {
            if (vehiclePosition != null) {
                estimateOverlay =
                        DistanceEstimateOverlay(shapeData, overlayColor).also {
                            it.create(map, context, POLYLINE_WIDTH_PX, vehiclePosition)
                        }
            }
        }
    }

    fun deactivate() {
        removeDataReceivedMarker()
        vehicleMarker?.remove()
        vehicleMarker = null
        estimateOverlay?.destroy()
        estimateOverlay = null
    }

    // --- Vehicle marker ---

    fun hideVehicleMarker() {
        vehicleMarker?.isVisible = false
    }

    fun updateVehiclePosition(location: Location?, anchor: ObaTripStatus?, now: Long) {
        if (location == null) return

        val marker = vehicleMarker
        if (marker == null) {
            vehicleMarker =
                    map.addMarker(
                            MarkerOptions()
                                    .position(MapHelpV2.makeLatLng(location))
                                    .icon(vehicleIcon)
                                    .title(context.getString(R.string.marker_best_estimate))
                                    .snippet(
                                            context.getString(R.string.marker_best_estimate_snippet)
                                    )
                                    .anchor(0.5f, 0.5f)
                                    .flat(true)
                                    .zIndex(MARKER_Z_INDEX)
                    )
            return
        }

        marker.isVisible = true
        val target = MapHelpV2.makeLatLng(location)
        val fixTime = anchor?.lastUpdateTime ?: 0L
        val freshData = lastFixTime != 0L && fixTime != lastFixTime
        lastFixTime = fixTime

        when {
            freshData -> {
                AnimationUtil.animateMarkerTo(marker, target, ANIMATE_DURATION_MS)
                animatingUntil = now + ANIMATE_DURATION_MS
            }
            now >= animatingUntil -> marker.position = target
        }
    }

    // --- Estimate overlay ---

    fun updateEstimateOverlays(distribution: ProbDistribution?) {
        val overlay = estimateOverlay ?: return
        if (distribution == null) {
            overlay.hide()
            return
        }
        overlay.update(distribution)
    }

    fun hideEstimateOverlays() {
        estimateOverlay?.hide()
    }

    fun handleEstimateLabelClick(marker: Marker) = estimateOverlay?.handleClick(marker) ?: false

    // --- Data-received marker ---

    fun showOrUpdateDataReceivedMarker(latest: ObaTripStatus, now: Long) {
        val updateTime = latest.lastUpdateTime
        val newData = updateTime != lastDataReceivedUpdateTime
        if (!newData && dataReceivedMarker != null) return
        lastDataReceivedUpdateTime = updateTime

        val label = if (updateTime > 0) UIUtils.formatElapsedTime(now - updateTime) else ""

        val pos = latest.position ?: return
        val latLng = MapHelpV2.makeLatLng(pos)

        val marker = dataReceivedMarker
        if (marker != null) {
            marker.position = latLng
            marker.snippet = label
            if (dataReceivedInfoShown) marker.showInfoWindow()
        } else {
            dataReceivedMarker =
                    map.addMarker(
                            MarkerOptions()
                                    .position(latLng)
                                    .icon(dataReceivedIcon)
                                    .anchor(0.5f, 0.5f)
                                    .flat(true)
                                    .title(context.getString(R.string.marker_most_recent_data))
                                    .snippet(label)
                                    .zIndex(MARKER_Z_INDEX)
                    )
        }
    }

    fun handleDataReceivedClick(marker: Marker): Boolean {
        if (marker != dataReceivedMarker) return false
        dataReceivedInfoShown = !dataReceivedInfoShown
        if (dataReceivedInfoShown) marker.showInfoWindow() else marker.hideInfoWindow()
        return true
    }

    fun removeDataReceivedMarker() {
        dataReceivedMarker?.remove()
        dataReceivedMarker = null
        dataReceivedInfoShown = false
        lastDataReceivedUpdateTime = 0
    }
}
