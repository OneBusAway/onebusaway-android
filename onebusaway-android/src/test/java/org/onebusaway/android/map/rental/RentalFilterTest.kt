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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [filterRentals] — the directions-mode filter ported from
 * `BikeLoaderCallbacks.onLoadFinished`, including the quirk that a non-null but empty filter shows
 * nothing at all (returns null) rather than clearing the overlay with an empty list — plus
 * [rentalAction] and [bikeRentalUrl].
 */
class RentalFilterTest {

    private fun place(id: String): RentalPlace = RentalPlace(id = id)

    private val all = listOf(place("a"), place("b"), place("c"))

    private val bikes = setOf(RentalLayer.BIKES)

    @Test
    fun `null filter shows all places`() {
        assertEquals(all, filterRentals(all, selectedIds = null))
    }

    @Test
    fun `empty filter shows nothing at all`() {
        // null return == "leave the overlay untouched", distinct from an empty-list clear.
        assertNull(filterRentals(all, selectedIds = emptyList()))
    }

    @Test
    fun `non-empty filter keeps only the selected ids`() {
        val filtered = filterRentals(all, selectedIds = listOf("a", "c"))
        assertEquals(listOf("a", "c"), filtered?.map { it.id })
    }

    @Test
    fun `filter ids not present yield an empty list`() {
        assertEquals(emptyList<RentalPlace>(), filterRentals(all, selectedIds = listOf("z")))
    }

    // --- rentalAction: the pure layer/mode gate from BikeshareMapController.updateData + showBikes ---

    @Test
    fun `outside directions, rentals follow the layer toggles`() {
        assertEquals(RentalAction.SHOW, rentalAction(isDirections = false, selectedIds = null, visibleLayers = bikes))
        assertEquals(
            RentalAction.CLEAR,
            rentalAction(isDirections = false, selectedIds = null, visibleLayers = emptySet())
        )
    }

    /** Either layer keeps the one shared fetch alive — scooters alone are enough (#2168). */
    @Test
    fun `the scooters layer alone keeps the loader running`() {
        assertEquals(
            RentalAction.SHOW,
            rentalAction(isDirections = false, selectedIds = null, visibleLayers = setOf(RentalLayer.SCOOTERS))
        )
    }

    @Test
    fun `directions with rentals always shows them, ignoring the toggles`() {
        assertEquals(
            RentalAction.SHOW,
            rentalAction(isDirections = true, selectedIds = listOf("a"), visibleLayers = emptySet())
        )
    }

    @Test
    fun `directions before its filter is known leaves the overlay`() {
        // selectedIds == null in directions mode == "filter not computed yet" → don't touch the overlay.
        assertEquals(
            RentalAction.LEAVE,
            rentalAction(isDirections = true, selectedIds = null, visibleLayers = bikes)
        )
    }

    @Test
    fun `directions with an empty rental filter follows the toggles`() {
        // An itinerary with no rentals: not a special case, just the toggles (then filterRentals
        // returns null so nothing is drawn).
        assertEquals(
            RentalAction.SHOW,
            rentalAction(isDirections = true, selectedIds = emptyList(), visibleLayers = bikes)
        )
        assertEquals(
            RentalAction.CLEAR,
            rentalAction(isDirections = true, selectedIds = emptyList(), visibleLayers = emptySet())
        )
    }

    // --- bikeRentalUrl: the OTP1 url-structure selection (the doubled-path fix) ---

    @Test
    fun `new structure inserts routers default for a server-rooted base`() {
        // Tampa/HART form: otpBaseUrl is the OTP server root.
        assertEquals(
            "https://otp.prod.obahart.org/otp/routers/default/bike_rental" +
                "?lowerLeft=27.9,-82.5&upperRight=28.1,-82.4",
            bikeRentalUrl(
                "https://otp.prod.obahart.org/otp",
                useOldUrlStructure = false,
                27.9,
                -82.5,
                28.1,
                -82.4
            )
        )
    }

    @Test
    fun `old structure appends bike_rental directly to a router-rooted base`() {
        // Puget Sound form: otpBaseUrl already ends in routers/default — the new structure would
        // double it (the bug being fixed), so the old structure appends bike_rental directly.
        assertEquals(
            "https://otp.prod.sound.obaweb.org/otp/routers/default/bike_rental" +
                "?lowerLeft=47.5,-122.4&upperRight=47.7,-122.2",
            bikeRentalUrl(
                "https://otp.prod.sound.obaweb.org/otp/routers/default",
                useOldUrlStructure = true,
                47.5,
                -122.4,
                47.7,
                -122.2
            )
        )
    }
}
