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
package org.onebusaway.android.ui.nav

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SavedStateHandle.consumeStopReveal] — the typed read + atomic consume for the
 * `RESULT_MAP_STOP_*` keys [NavController.revealStopOnMap] writes. Verifies the complete-reveal read, the
 * corrupted/partial case ([StopReveal] dropped), and that all three keys are always cleared.
 */
class MapRevealTest {

    @Test
    fun `reads a complete stop reveal and clears all three keys`() {
        val handle = SavedStateHandle(
            mapOf(
                RESULT_MAP_STOP_ID to "stop_1",
                RESULT_MAP_STOP_LAT to 47.6,
                RESULT_MAP_STOP_LON to -122.3,
            )
        )

        val reveal = handle.consumeStopReveal()

        assertEquals(StopReveal("stop_1", 47.6, -122.3), reveal)
        assertNull(handle.get<String>(RESULT_MAP_STOP_ID))
        assertNull(handle.get<Double>(RESULT_MAP_STOP_LAT))
        assertNull(handle.get<Double>(RESULT_MAP_STOP_LON))
    }

    @Test
    fun `drops a partial reveal but still consumes the keys`() {
        // The corrupted / half-restored case: a stop id without its coordinates.
        val handle = SavedStateHandle(mapOf(RESULT_MAP_STOP_ID to "stop_1"))

        val reveal = handle.consumeStopReveal()

        assertNull(reveal)
        assertNull(handle.get<String>(RESULT_MAP_STOP_ID))
    }

    @Test
    fun `returns null when nothing was staged`() {
        assertNull(SavedStateHandle().consumeStopReveal())
    }
}
