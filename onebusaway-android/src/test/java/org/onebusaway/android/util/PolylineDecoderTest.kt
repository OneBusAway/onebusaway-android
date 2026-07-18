/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests decoding the Google "encoded polyline" points via [PolylineDecoder.decode].
 *
 * Runs on plain JVM: [PolylineDecoder.decode] returns flavor-neutral [org.onebusaway.android.map.render.GeoPoint]s
 * with no `android.location.Location` dependency, so no device/emulator is needed. The thin
 * `decodeLine` adapter that mints `Location` at the boundary is smoke-checked on-device by the
 * instrumented `PolylineDecoderAdapterTest`.
 */
class PolylineDecoderTest {

    @Test
    fun decodesTwoPoints() {
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC", 2)
        assertEquals(2, points.size)
        assertEquals(38.5, points[0].latitude, 0.0)
        assertEquals(-120.2, points[0].longitude, 0.0)
        assertEquals(40.7, points[1].latitude, 0.0)
        assertEquals(-120.95, points[1].longitude, 0.0)
    }

    @Test
    fun decodesThreePoints() {
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3)
        assertEquals(3, points.size)
        assertEquals(43.252, points[2].latitude, 0.0)
        assertEquals(-126.453, points[2].longitude, 0.0)
    }

    /** The `numPoints` argument is only an allocation hint; the result reflects the actual content. */
    @Test
    fun numPointsIsOnlyAllocationHint() {
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC", 1)
        assertEquals(2, points.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeNumPoints() {
        PolylineDecoder.decode("_p~iF~ps|U", -1)
    }
}
