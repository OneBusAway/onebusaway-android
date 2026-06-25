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
package org.onebusaway.android.map.bike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opentripplanner.routing.bike_rental.BikeRentalStation

/**
 * Unit tests for [filterStations] — the directions-mode station filter ported from
 * `BikeLoaderCallbacks.onLoadFinished`, including the quirk that a non-null but empty filter shows
 * nothing at all (returns null) rather than clearing the overlay with an empty list.
 */
class BikeStationFilterTest {

    private fun station(id: String): BikeRentalStation =
        BikeRentalStation().apply { this.id = id }

    private val all = listOf(station("a"), station("b"), station("c"))

    @Test
    fun `null filter shows all stations`() {
        assertEquals(all, filterStations(all, selectedIds = null))
    }

    @Test
    fun `empty filter shows nothing at all`() {
        // null return == "leave the overlay untouched", distinct from an empty-list clear.
        assertNull(filterStations(all, selectedIds = emptyList()))
    }

    @Test
    fun `non-empty filter keeps only the selected ids`() {
        val filtered = filterStations(all, selectedIds = listOf("a", "c"))
        assertEquals(listOf("a", "c"), filtered?.map { it.id })
    }

    @Test
    fun `filter ids not present yield an empty list`() {
        assertEquals(emptyList<BikeRentalStation>(), filterStations(all, selectedIds = listOf("z")))
    }

    // --- bikeAction: the pure layer/mode gate from BikeshareMapController.updateData + showBikes ---

    @Test
    fun `outside directions, bikes follow the layer toggle`() {
        assertEquals(BikeAction.SHOW, bikeAction(isDirections = false, selectedIds = null, layerVisible = true))
        assertEquals(BikeAction.CLEAR, bikeAction(isDirections = false, selectedIds = null, layerVisible = false))
    }

    @Test
    fun `directions with stations always shows them, ignoring the toggle`() {
        assertEquals(
            BikeAction.SHOW,
            bikeAction(isDirections = true, selectedIds = listOf("a"), layerVisible = false)
        )
    }

    @Test
    fun `directions before its station filter is known leaves the overlay`() {
        // selectedIds == null in directions mode == "filter not computed yet" → don't touch the overlay.
        assertEquals(
            BikeAction.LEAVE,
            bikeAction(isDirections = true, selectedIds = null, layerVisible = true)
        )
    }

    @Test
    fun `directions with an empty station filter follows the toggle`() {
        // An itinerary with no bike stations: not a special case, just the toggle (then filterStations
        // returns null so nothing is drawn).
        assertEquals(
            BikeAction.SHOW,
            bikeAction(isDirections = true, selectedIds = emptyList(), layerVisible = true)
        )
        assertEquals(
            BikeAction.CLEAR,
            bikeAction(isDirections = true, selectedIds = emptyList(), layerVisible = false)
        )
    }
}
