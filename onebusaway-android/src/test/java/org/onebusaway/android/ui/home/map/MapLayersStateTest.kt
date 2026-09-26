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
package org.onebusaway.android.ui.home.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.map.Basemap

class MapLayersStateTest {

    private val defaults = LayerPrefs.DEFAULTS

    @Test
    fun `defaults read zones on and both rental tiles off`() {
        val state = layersUiState(defaults, onDemandTileVisible = true, rentalsEnabled = true, hasBasemapChoice = true)
        assertEquals(Basemap.STANDARD, state.basemap)
        assertEquals(listOf(LayerTileId.ON_DEMAND_ZONES to true), state.transit.map { it.id to it.enabled })
        assertEquals(listOf(LayerTileId.BIKES to false, LayerTileId.SCOOTERS to false), state.rentals.map { it.id to it.enabled })
        assertFalse(state.differsFromDefaults)
        assertEquals(1, state.enabledCount)
    }

    @Test
    fun `groups and the basemap hide when unsupported, disabled or unavailable`() {
        val state = layersUiState(defaults, onDemandTileVisible = false, rentalsEnabled = false, hasBasemapChoice = false)
        assertNull(state.basemap)
        assertTrue(state.transit.isEmpty())
        assertTrue(state.rentals.isEmpty())
        assertTrue(state.isEmpty)
    }

    @Test
    fun `turning a rental tile on lights the master and off drops it when nothing else is on`() {
        val bikesOn = defaults.toggled(LayerTileId.BIKES)
        assertTrue(bikesOn.rentalsMaster)
        assertTrue(bikesOn.bikes)
        assertEquals(true, layersUiState(bikesOn, true, true, true).rentals.first { it.id == LayerTileId.BIKES }.enabled)
        val scootersToo = bikesOn.toggled(LayerTileId.SCOOTERS)
        val bikesOff = scootersToo.toggled(LayerTileId.BIKES)
        assertTrue("scooters still on keeps the master", bikesOff.rentalsMaster)
        assertFalse(bikesOff.bikes)
        val allOff = bikesOff.toggled(LayerTileId.SCOOTERS)
        assertFalse(allOff.rentalsMaster)
        assertFalse(allOff.scooters)
        assertTrue(allOff.differsFrom(defaults))
    }

    @Test
    fun `a fresh install with the bikes preference already true still reads off until the master is on`() {
        val state = layersUiState(defaults.copy(bikes = true), true, true, true)
        assertFalse(state.rentals.first { it.id == LayerTileId.BIKES }.enabled)
        assertFalse(state.differsFromDefaults)
    }
}
