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
package org.onebusaway.android.map.render

/**
 * One graded slice of the uncertainty band: a sub-polyline of the route shape, drawn at [colorArgb]
 * with the per-slice alpha already baked into the ARGB. Brighter slices are where the vehicle is
 * more likely to be (higher PDF). Flavor-neutral — the renderer draws each as a polyline.
 */
data class BandSegment(val points: List<GeoPoint>, val colorArgb: Int)

/**
 * The "most recent data" marker: where the vehicle was last actually reported by the server, and how
 * old that fix is. The renderer places a marker at [point] and formats [ageMillis] into a label
 * ("12 sec ago"). Present only while the fix is recent enough to be worth showing.
 */
data class DataAgeMarker(val point: GeoPoint, val ageMillis: Long)

/**
 * The per-frame trip-focus overlay: a single vehicle's extrapolated position estimate and its
 * uncertainty, rendered over the route shape. Built ~20×/second by the map view-model from the
 * driver's color-free `TripExtrapolation` (applying the band hue) and pushed into
 * [MapRenderState.tripOverlay] — its own flow, so a per-frame update doesn't recompose the rest of
 * the map (stops, routes, the route-mode vehicles).
 *
 * Flavor-neutral: every field is a plain [GeoPoint]/[BandSegment], so both the Google and maplibre
 * renderers draw the same overlay (the feature originally implemented it for Google only). On a
 * frame where extrapolation has no usable estimate, [vehiclePoint]/[fastEstimatePoint] are null and
 * [band] is empty, but [dataAge] still shows the last server fix.
 *
 * @property vehiclePoint the best (median) position estimate, or null when there is no estimate
 * @property fastEstimatePoint the optimistic (high-quantile) "best case" position, or null
 * @property band the graded uncertainty band over the route shape (empty when no estimate)
 * @property dataAge the last server-reported position + its age, or null when stale/absent
 * @property fixTimeMs the latest AVL fix's timestamp — constant between fixes, so a change signals
 *   fresh data; the renderer animates the markers to their new positions when it changes
 */
data class TripOverlay(
    val vehiclePoint: GeoPoint? = null,
    val fastEstimatePoint: GeoPoint? = null,
    val band: List<BandSegment> = emptyList(),
    val dataAge: DataAgeMarker? = null,
    val fixTimeMs: Long = 0L,
)
