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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.map.render.GeoPoint

/**
 * Unit tests for [stopRequestFulfilled] — the whole-request "can the reactive stop loader reuse the
 * last load for this viewport?" decision that replaced the legacy `StopsResponse.fulfills`. The
 * zoom/limit branch delegates to [zoomFulfills] (covered by [StopsFulfillsTest]); these tests cover
 * the wiring: the no-prior-load case, the null-response case, and — the point of [CameraSnapshot] —
 * that the center comparison is honest *value*-equality rather than the old `Location` reference
 * identity.
 */
class StopRequestFulfilledTest {

    private fun cam(lat: Double, lon: Double, zoom: Double): CameraSnapshot =
        CameraSnapshot(
            center = GeoPoint(lat, lon),
            zoom = zoom,
            latSpan = 0.0,
            lonSpan = 0.0,
            southWest = GeoPoint(lat - 0.01, lon - 0.01),
            northEast = GeoPoint(lat + 0.01, lon + 0.01),
        )

    @Test
    fun `no prior load always needs a load`() {
        assertFalse(
            stopRequestFulfilled(
                last = null,
                lastHadResponse = true,
                lastLimitExceeded = false,
                next = cam(47.6, -122.3, 16.0),
            )
        )
    }

    @Test
    fun `same viewport reuses the last load`() {
        val c = cam(47.6, -122.3, 16.0)
        assertTrue(
            stopRequestFulfilled(
                last = c,
                lastHadResponse = true,
                lastLimitExceeded = false,
                next = cam(47.6, -122.3, 16.0),
            )
        )
    }

    @Test
    fun `a different center needs a load`() {
        assertFalse(
            stopRequestFulfilled(
                last = cam(47.6, -122.3, 16.0),
                lastHadResponse = true,
                lastLimitExceeded = false,
                next = cam(47.61, -122.3, 16.0),
            )
        )
    }

    @Test
    fun `same center from a fresh instance still reuses the last load (value equality)`() {
        // The whole reason for the value-typed center: two distinct CameraSnapshot/GeoPoint instances
        // at the same coordinates must be treated as equal (the legacy Location compare was by
        // reference, so this case wrongly forced a reload).
        val last = cam(47.6, -122.3, 16.0)
        val next = cam(47.6, -122.3, 16.0)
        assertTrue(last !== next)
        assertTrue(
            stopRequestFulfilled(
                last = last,
                lastHadResponse = true,
                lastLimitExceeded = false,
                next = next,
            )
        )
    }

    @Test
    fun `zooming in past a capped response at the same center needs a load`() {
        assertFalse(
            stopRequestFulfilled(
                last = cam(47.6, -122.3, 14.0),
                lastHadResponse = true,
                lastLimitExceeded = true,
                next = cam(47.6, -122.3, 16.0),
            )
        )
    }

    @Test
    fun `a null prior response fulfills the same viewport (no-op load)`() {
        // hasResponse=false short-circuits zoomFulfills to true: a same-center viewport after a
        // null-bodied response (e.g. no API endpoint yet) is treated as already satisfied.
        assertTrue(
            stopRequestFulfilled(
                last = cam(47.6, -122.3, 16.0),
                lastHadResponse = false,
                lastLimitExceeded = false,
                next = cam(47.6, -122.3, 18.0),
            )
        )
    }
}
