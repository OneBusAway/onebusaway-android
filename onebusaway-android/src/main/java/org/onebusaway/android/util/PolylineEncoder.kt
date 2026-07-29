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
 * The inverse of [PolylineDecoder]. The app almost always consumes encoded polylines rather than
 * producing them; this exists because a destination reminder persists the shape it is monitoring
 * into its stored plan, and the fetch path that supplies that shape has already decoded it (the
 * shape endpoint's raw string does not survive `TripVehiclesDataSource.shape`). Encoding is an order
 * of magnitude more compact than a list of coordinate pairs in JSON, which matters for a value that
 * crosses a Binder transaction as an Intent extra.
 *
 * Google encoded polyline algorithm, precision 1e5 — the same one [PolylineDecoder] reads. Round
 * trips through the decoder are pinned by `PolylineEncoderTest`.
 */
fun encodePolyline(points: List<GeoPoint>): String {
    val encoded = StringBuilder()
    var previousLatitude = 0
    var previousLongitude = 0
    points.forEach { point ->
        val latitude = Math.round(point.latitude * 1e5).toInt()
        val longitude = Math.round(point.longitude * 1e5).toInt()
        encoded.appendSignedValue(latitude - previousLatitude)
        encoded.appendSignedValue(longitude - previousLongitude)
        previousLatitude = latitude
        previousLongitude = longitude
    }
    return encoded.toString()
}

private fun StringBuilder.appendSignedValue(value: Int) {
    // Zig-zag: the sign becomes the low bit, so small negative deltas stay short. The result is
    // non-negative, hence the arithmetic shift below is safe.
    var remaining = if (value < 0) (value shl 1).inv() else value shl 1
    while (remaining >= 0x20) {
        append(((0x20 or (remaining and 0x1f)) + 63).toChar())
        remaining = remaining shr 5
    }
    append((remaining + 63).toChar())
}
