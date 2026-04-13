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
import android.location.Location
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline as MapPolyline
import com.google.android.gms.maps.model.PolylineOptions
import org.onebusaway.android.R
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.map.googlemapsv2.MapHelpV2
import org.onebusaway.android.map.googlemapsv2.MapIconUtils
import org.onebusaway.android.util.Polyline

private const val SEGMENT_Z_INDEX = 2f
private const val FAST_ESTIMATE_QUANTILE = 0.90
private const val PDF_LOW_QUANTILE = 0.01
private const val PDF_HIGH_QUANTILE = 0.99
private const val DEFAULT_SEGMENT_COUNT = 15

/**
 * Renders the distance estimate visualization on the trip map: a set of opacity-graded polyline
 * segments showing the PDF over distance, plus a fast-estimate icon marker at the 90th percentile
 * position. Takes a [ProbDistribution] over distance each frame.
 */
class DistanceEstimateOverlay(
        private val shape: Polyline,
        private val baseColor: Int,
        private val segmentCount: Int = DEFAULT_SEGMENT_COUNT
) {
    private val edgeDistances = DoubleArray(segmentCount + 1)
    private val pdfValues = DoubleArray(segmentCount)
    private val rgb = baseColor and 0x00FFFFFF
    private val scratchLocation = Location("")
    private val latLngBuffer = mutableListOf<LatLng>()

    private var segments: Array<MapPolyline>? = null
    private var fastEstimateMarker: Marker? = null

    fun create(map: GoogleMap, context: Context, polylineWidth: Float, initialPosition: LatLng) {
        val icon = MapIconUtils.createCircleIcon(context, R.drawable.ic_fast_estimate)
        segments =
                Array(segmentCount) {
                    map.addPolyline(
                            PolylineOptions().width(polylineWidth).color(0).zIndex(SEGMENT_Z_INDEX)
                    )
                }
        fastEstimateMarker =
                map.addMarker(
                        MarkerOptions()
                                .position(initialPosition)
                                .icon(icon)
                                .title(context.getString(R.string.marker_fast_estimate))
                                .snippet(context.getString(R.string.marker_fast_estimate_snippet))
                                .anchor(0.5f, 0.5f)
                                .flat(true)
                                .zIndex(MARKER_Z_INDEX)
                                .visible(false)
                )
    }

    fun destroy() {
        segments?.forEach { it.remove() }
        segments = null
        fastEstimateMarker?.remove()
        fastEstimateMarker = null
    }

    fun hide() {
        segments?.forEach { it.isVisible = false }
        fastEstimateMarker?.isVisible = false
    }

    // --- Per-frame update ---

    /**
     * Updates all overlay geometry from the distance distribution. Quantiles are distances along
     * the trip; PDF values determine segment opacity.
     */
    fun update(distribution: ProbDistribution) {
        updatePdfSegments(distribution)
        updateFastEstimateMarker(distribution)
    }

    private fun updatePdfSegments(distribution: ProbDistribution) {
        val segs = segments ?: return

        val distLo = distribution.quantile(PDF_LOW_QUANTILE)
        val distHi = distribution.quantile(PDF_HIGH_QUANTILE)
        // Guard against non-finite quantiles (bisect returning NaN on a pathological
        // CDF, or the caller passing an unbounded range). Hide stale segments so we
        // don't render the previous frame's geometry indefinitely.
        if (!distLo.isFinite() || !distHi.isFinite() || distHi <= distLo) {
            segs.forEach { it.isVisible = false }
            return
        }
        val segWidth = (distHi - distLo) / segmentCount

        var maxPdf = 0.0
        for (i in 0..segmentCount) {
            edgeDistances[i] = distLo + segWidth * i
        }
        for (i in 0 until segmentCount) {
            val midDist = distLo + segWidth * (i + 0.5)
            pdfValues[i] = distribution.pdf(midDist)
            if (pdfValues[i] > maxPdf) maxPdf = pdfValues[i]
        }

        segs.forEachIndexed { i, seg ->
            if (shape.subPolylineMapInto(
                            edgeDistances[i],
                            edgeDistances[i + 1],
                            latLngBuffer,
                            scratchLocation
                    ) { MapHelpV2.makeLatLng(it) }
            ) {
                val alpha = if (maxPdf > 0) (255 * pdfValues[i] / maxPdf).toInt() else 0
                seg.points = latLngBuffer
                seg.color = (alpha shl 24) or rgb
                seg.isVisible = true
            } else {
                seg.isVisible = false
            }
        }
    }

    private fun updateFastEstimateMarker(distribution: ProbDistribution) {
        val marker = fastEstimateMarker ?: return
        val dist = distribution.quantile(FAST_ESTIMATE_QUANTILE)
        if (dist.isFinite() && shape.interpolateInto(dist, scratchLocation)) {
            marker.position = MapHelpV2.makeLatLng(scratchLocation)
            marker.isVisible = true
        } else {
            marker.isVisible = false
        }
    }

    /** Returns true if the clicked marker was the fast estimate icon. */
    fun handleClick(marker: Marker): Boolean {
        val m = fastEstimateMarker ?: return false
        if (marker != m) return false
        if (m.isInfoWindowShown) m.hideInfoWindow() else m.showInfoWindow()
        return true
    }
}
