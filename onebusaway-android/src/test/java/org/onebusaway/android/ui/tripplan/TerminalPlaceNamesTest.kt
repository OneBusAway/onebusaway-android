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
package org.onebusaway.android.ui.tripplan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripPlace

/**
 * JVM tests for [withTerminalPlaceNames] — the plan boundary stamping what the user actually picked onto
 * the trip's two ends, in place of the "Origin"/"Destination" placeholders OTP returns for a trip routed
 * between bare coordinates (#2006).
 */
class TerminalPlaceNamesTest {

    private fun leg(from: String?, to: String?) = TripLeg(from = TripPlace(name = from), to = TripPlace(name = to))

    private val twoLegs = TripItinerary(
        legs = listOf(leg("Origin", "Pine St & 3rd Ave"), leg("Pine St & 3rd Ave", "Destination"))
    )

    @Test
    fun `names the first leg's origin and the last leg's destination`() {
        val named = listOf(twoLegs).withTerminalPlaceNames("Pike Place Market", "Sea-Tac Airport")

        val legs = named.single().legs
        assertEquals("Pike Place Market", legs.first().from.name)
        assertEquals("Sea-Tac Airport", legs.last().to.name)
        // The interior of the trip is the transit network's own names — untouched.
        assertEquals("Pine St & 3rd Ave", legs.first().to.name)
        assertEquals("Pine St & 3rd Ave", legs.last().from.name)
    }

    @Test
    fun `every itinerary is named, since they all share the trip's two ends`() {
        val named = listOf(twoLegs, twoLegs, twoLegs).withTerminalPlaceNames("Pike Place Market", "Sea-Tac Airport")

        assertEquals(3, named.size)
        named.forEach { assertEquals("Pike Place Market", it.legs.first().from.name) }
    }

    @Test
    fun `a single-leg trip is both of its own terminals`() {
        val walkOnly = listOf(TripItinerary(legs = listOf(leg("Origin", "Destination"))))

        val leg = walkOnly.withTerminalPlaceNames("Pike Place Market", "Sea-Tac Airport").single().legs.single()
        assertEquals("Pike Place Market", leg.from.name)
        assertEquals("Sea-Tac Airport", leg.to.name)
    }

    @Test
    fun `a name we do not have leaves OTP's own name alone`() {
        val named = listOf(twoLegs).withTerminalPlaceNames(null, "  ")

        val legs = named.single().legs
        assertEquals("Origin", legs.first().from.name)
        assertEquals("Destination", legs.last().to.name)
    }

    @Test
    fun `nothing to name returns the itineraries untouched`() {
        val planned = listOf(twoLegs)
        assertSame(planned, planned.withTerminalPlaceNames(null, null))
    }

    @Test
    fun `a legless itinerary has no terminals to name`() {
        val empty = listOf(TripItinerary())
        assertEquals(empty, empty.withTerminalPlaceNames("Pike Place Market", "Sea-Tac Airport"))
    }
}
