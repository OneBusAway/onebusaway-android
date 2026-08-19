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
package org.onebusaway.android.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.util.GeoPoint

/**
 * Covers `FocusedStop.fromIntent` — the entry-boundary half of a stop reveal (#1898), where a launch
 * intent may carry a stop id alone. Runs on a device because `Bundle` is stubbed in unit tests, and
 * `Bundle` is exactly what's under test: the location is read by key *presence*, so an absent extra
 * and a deliberately-zero coordinate must not read alike.
 */
@RunWith(AndroidJUnit4::class)
class StopIntentFocusTest {

    // Method names are camelCase, not the backtick-with-spaces style the JVM unit tests use: these get
    // dexed, and D8 rejects spaces in a SimpleName below DEX version 040.

    @Test
    fun readsAnIdOnlyIntentAsAnUnlocatedFocus() {
        val focus = FocusedStop.fromIntent(Intent().putExtra(MapParams.STOP_ID, "1_75403"))

        assertEquals("1_75403", focus?.id)
        assertNull(focus?.point)
    }

    @Test
    fun readsTheCarriedLocation() {
        val focus = FocusedStop.fromIntent(
            Intent()
                .putExtra(MapParams.STOP_ID, "1_75403")
                .putExtra(MapParams.STOP_NAME, "Pine St & 3rd Ave")
                .putExtra(MapParams.STOP_CODE, "75403")
                .putExtra(MapParams.CENTER_LAT, 47.6)
                .putExtra(MapParams.CENTER_LON, -122.3)
        )

        assertEquals("Pine St & 3rd Ave", focus?.name)
        assertEquals("75403", focus?.code)
        assertEquals(GeoPoint(47.6, -122.3), focus?.point)
    }

    /** Null island is a location a stop can genuinely sit at; only an absent key means "unknown". */
    @Test
    fun keepsAZeroCoordinate() {
        val focus = FocusedStop.fromIntent(
            Intent()
                .putExtra(MapParams.STOP_ID, "1_75403")
                .putExtra(MapParams.CENTER_LAT, 0.0)
                .putExtra(MapParams.CENTER_LON, 0.0)
        )

        assertEquals(GeoPoint(0.0, 0.0), focus?.point)
    }

    /** The producer never writes half a pair; a lone coordinate is no location at all. */
    @Test
    fun ignoresALoneCoordinate() {
        val focus = FocusedStop.fromIntent(
            Intent()
                .putExtra(MapParams.STOP_ID, "1_75403")
                .putExtra(MapParams.CENTER_LAT, 47.6)
        )

        assertNull(focus?.point)
    }

    @Test
    fun readsNoStopFromAPlainLaunch() {
        assertNull(FocusedStop.fromIntent(Intent()))
    }
}
