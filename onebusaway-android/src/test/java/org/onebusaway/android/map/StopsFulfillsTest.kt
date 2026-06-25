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

/**
 * Unit tests for [zoomFulfills] — the zoom/limit-exceeded half of the legacy
 * `StopsResponse.fulfills` redundant-load avoidance, the branch logic that decides whether a map
 * pan/zoom can reuse the last response instead of hitting the server. (The Android `Location`
 * center comparison is left in the controller; only this JVM-pure half is tested.)
 */
class StopsFulfillsTest {

    @Test
    fun `no prior response always needs a load`() {
        // hasResponse=false short-circuits to fulfilled=true regardless of zoom/limit.
        assertTrue(zoomFulfills(hasResponse = false, lastLimitExceeded = true, lastZoom = 10.0, newZoom = 14.0))
        assertTrue(zoomFulfills(hasResponse = false, lastLimitExceeded = false, lastZoom = 10.0, newZoom = 8.0))
    }

    @Test
    fun `zooming in past a limit-exceeded response needs a reload`() {
        assertFalse(zoomFulfills(hasResponse = true, lastLimitExceeded = true, lastZoom = 10.0, newZoom = 12.0))
    }

    @Test
    fun `zooming in is fine when the last response was not capped`() {
        assertTrue(zoomFulfills(hasResponse = true, lastLimitExceeded = false, lastZoom = 10.0, newZoom = 12.0))
    }

    @Test
    fun `zooming out always needs a reload`() {
        assertFalse(zoomFulfills(hasResponse = true, lastLimitExceeded = false, lastZoom = 10.0, newZoom = 8.0))
        assertFalse(zoomFulfills(hasResponse = true, lastLimitExceeded = true, lastZoom = 10.0, newZoom = 8.0))
    }

    @Test
    fun `same zoom reuses the last response`() {
        assertTrue(zoomFulfills(hasResponse = true, lastLimitExceeded = false, lastZoom = 10.0, newZoom = 10.0))
        assertTrue(zoomFulfills(hasResponse = true, lastLimitExceeded = true, lastZoom = 10.0, newZoom = 10.0))
    }
}
