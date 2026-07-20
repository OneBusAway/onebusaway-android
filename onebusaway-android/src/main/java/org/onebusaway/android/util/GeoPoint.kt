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
package org.onebusaway.android.util

/**
 * A geographic point, flavor-neutral (carries no Google/maplibre `LatLng` dependency) and free of any
 * `android.location.Location` dependency — the project's neutral lat/lon primitive. It lives in `util`
 * (alongside [SphericalGeometry] and [PolylineDecoder]) so lower layers can produce and consume it
 * without reaching up into `map/render`.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** A [GeoPoint] from a nullable lat/lon pair, or null when either coordinate is missing. */
fun geoPointOrNull(latitude: Double?, longitude: Double?): GeoPoint? =
    if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
