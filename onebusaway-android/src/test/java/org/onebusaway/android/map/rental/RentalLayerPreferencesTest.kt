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
package org.onebusaway.android.map.rental

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.testing.FakePreferencesRepository

/**
 * Unit tests for [drawnRentalLayers] and [observeDrawnRentalLayers] — which rental layers the map
 * draws, from the master switch, the two mode toggles and the button's own visibility (#2168, #2392).
 */
class RentalLayerPreferencesTest {

    private val both = setOf(RentalLayer.BIKES, RentalLayer.SCOOTERS)

    @Test
    fun `the master draws whichever modes are on`() {
        assertEquals(both, drawnRentalLayers(buttonShown = true, rentalsVisible = true, bikesVisible = true, scootersVisible = true))
        assertEquals(
            setOf(RentalLayer.SCOOTERS),
            drawnRentalLayers(buttonShown = true, rentalsVisible = true, bikesVisible = false, scootersVisible = true)
        )
    }

    @Test
    fun `the master off draws nothing, whatever the modes say`() {
        assertEquals(
            emptySet<RentalLayer>(),
            drawnRentalLayers(buttonShown = true, rentalsVisible = false, bikesVisible = true, scootersVisible = true)
        )
    }

    @Test
    fun `a hidden button draws nothing, whatever the layer switches say`() {
        assertEquals(
            emptySet<RentalLayer>(),
            drawnRentalLayers(buttonShown = false, rentalsVisible = true, bikesVisible = true, scootersVisible = true)
        )
    }

    @Test
    fun `hiding the button through the preference alone reaches the observed layers`() = runTest {
        // The Settings switch writes the preference directly rather than through the map, so the map
        // has to see the write by observing it.
        val prefs = FakePreferencesRepository().apply {
            setBoolean(R.string.preference_key_show_rental_button, true)
            setBoolean(R.string.preference_key_layer_bikeshare_visible, true)
            setBoolean(R.string.preference_key_layer_bikes_visible, true)
            setBoolean(R.string.preference_key_layer_scooters_visible, true)
        }
        assertEquals(both, prefs.observeDrawnRentalLayers().first())

        prefs.setBoolean(R.string.preference_key_show_rental_button, false)
        assertEquals(emptySet<RentalLayer>(), prefs.observeDrawnRentalLayers().first())

        prefs.setBoolean(R.string.preference_key_show_rental_button, true)
        assertEquals(both, prefs.observeDrawnRentalLayers().first())
    }
}
