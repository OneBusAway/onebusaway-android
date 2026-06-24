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
package org.onebusaway.android.map.render

/**
 * The live camera, published from each flavor's camera-idle listener (both flavors host an imperative
 * map) into `MapViewModel` so the reactive loaders can react to pan/zoom.
 *
 * A flavor-neutral *value* type (plain doubles, no Android `Location` / map-SDK `LatLng`), which is the
 * point: the old stop-load dedup compared `Location` by reference (relying on a cached instance) — a
 * smell. With a value type the dedup is honest value-equality.
 *
 * [center] + [latSpan]/[lonSpan] + [zoom] drive the stops request; [southWest]/[northEast] drive the
 * bike-station viewport request.
 */
data class CameraSnapshot(
    val center: GeoPoint,
    val zoom: Double,
    val latSpan: Double,
    val lonSpan: Double,
    val southWest: GeoPoint,
    val northEast: GeoPoint,
)
