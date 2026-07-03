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
package org.onebusaway.android.extrapolation

import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.map.render.DataAgeMarker
import org.onebusaway.android.map.render.GeoPoint

/**
 * The display-free output of the extrapolation producer. These types carry only geometry and model
 * quantities — positions, the probability-weighted band, the source observation — and deliberately
 * know nothing about how they are drawn (color, icons). The map view-model maps them to the render
 * models (`TripOverlay`/`VehicleMarker`), applying the display policy (the contrasting band hue, the
 * live-vs-scheduled icon) at that boundary.
 */

/**
 * One graded segment of the uncertainty band: a sub-polyline of the route shape and its [weight] in
 * `0..1` — how likely the vehicle is to be on it (higher PDF → higher weight). The color-free
 * precursor of the render `BandSegment`: the display layer maps the weight to an alpha over its chosen
 * hue. (Distinct from [BandSlice], the upstream distance-interval slice that this is projected from.)
 */
data class WeightedBandSegment(val points: List<GeoPoint>, val weight: Float)

/**
 * A single trip's per-frame extrapolation onto its route shape: the median [vehiclePoint] estimate,
 * the optimistic [fastEstimatePoint] ("best case"), the graded uncertainty [band], the last server
 * fix ([dataAge]), and the latest AVL fix instant ([fixTimeMs], constant between fixes so a change
 * signals fresh data). On a frame with no usable estimate the points are null and the band empty, but
 * [dataAge] still reports the last fix.
 */
data class TripExtrapolation(
    val vehiclePoint: GeoPoint? = null,
    val fastEstimatePoint: GeoPoint? = null,
    val band: List<WeightedBandSegment> = emptyList(),
    val dataAge: DataAgeMarker? = null,
    val fixTimeMs: Long = 0L,
)

/**
 * One route-mode vehicle dead-reckoned to the frame [point], carrying its source [status] (for
 * identity + the renderer's info window/icon) and the [fixTimeMs] its position was anchored at (so the
 * renderer animates only across a fresh-AVL jump).
 *
 * [bearing] is the vehicle's movement direction along the route shape at [point] (compass degrees,
 * 0°=N clockwise), so the marker's direction arrow follows the extrapolation glide. It is [Float.NaN]
 * when there's no shape to take a tangent from (the marker then falls back to the server orientation).
 */
data class ExtrapolatedVehicle(
    val point: GeoPoint,
    val bearing: Float,
    val fixTimeMs: Long,
    val status: ObaTripStatus,
    // Live-vs-scheduled, decided at draw time from whatever produced [point] (the extrapolation anchor
    // when [point] is extrapolated, else the current status), so the icon/info-window can't disagree
    // with the position the marker is actually drawn at. See #1621.
    val isRealtime: Boolean,
)
