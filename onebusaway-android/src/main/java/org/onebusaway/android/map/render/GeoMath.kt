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

import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.haversineDistance

/**
 * Great-circle distance in meters between two points. Takes flavor-neutral [GeoPoint]s and delegates
 * to the shared [haversineDistance], which matches the server's distance-along-trip values (same
 * Earth radius); neither carries an Android dependency, so callers stay pure / JVM-testable.
 */
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double =
    haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)

/**
 * Web-Mercator ground resolution at zoom 0 on the equator, in meters per pixel (256px tiles). Scale to
 * a given zoom/latitude with `× cos(lat) / 2^zoom`. The single source for this constant, shared by the
 * route-render pipeline and the map-ping radius math.
 */
internal const val METERS_PER_PIXEL_AT_EQUATOR_ZOOM_ZERO = 156543.03392804097
