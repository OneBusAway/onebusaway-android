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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "directions from/to here" pairing rule (#2092), which the ViewModel delegates to. Kept here
 * rather than in the ViewModel test because a real fix means an `android.location.Location`, which a
 * plain JVM test can't build — this side of the seam is plain coordinates.
 */
class TripPlanFormStateTest {

    private val here = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3)
    private val pressed = TripEndpoint.MapPoint(lat = 47.7, lon = -122.2)
    private val named = TripEndpoint.Geocoded("Pike Place Market", lat = 47.61, lon = -122.34)

    @Test
    fun `a long-pressed destination pairs an empty origin with the current location`() {
        val paired = TripPlanFormState().withEndpointPaired(TripEndpointSlot.TO, pressed, here)

        assertEquals(pressed, paired.to)
        assertEquals(here, paired.from)
        assertTrue(paired.canSubmit) // both ends resolved: the host plans without further input
    }

    @Test
    fun `a long-pressed origin pairs an empty destination with the current location`() {
        val paired = TripPlanFormState().withEndpointPaired(TripEndpointSlot.FROM, pressed, here)

        assertEquals(pressed, paired.from)
        assertEquals(here, paired.to)
        assertTrue(paired.canSubmit)
    }

    @Test
    fun `pairing never overwrites an endpoint the rider already resolved`() {
        val form = TripPlanFormState(from = named)

        val paired = form.withEndpointPaired(TripEndpointSlot.TO, pressed, here)

        assertEquals(named, paired.from)
        assertEquals(pressed, paired.to)
    }

    @Test
    fun `pairing never overwrites a half-typed query`() {
        val form = TripPlanFormState(to = TripEndpoint.FreeText("pike pl"))

        val paired = form.withEndpointPaired(TripEndpointSlot.FROM, pressed, here)

        assertEquals(TripEndpoint.FreeText("pike pl"), paired.to)
        assertEquals(pressed, paired.from)
        assertFalse(paired.canSubmit)
    }

    @Test
    fun `an endpoint the rider cleared is empty again, so it pairs`() {
        // The ✕ leaves exactly the never-set state, and pairing reads what the field holds now.
        val form = TripPlanFormState(from = named).withEndpoint(TripEndpointSlot.FROM, TripEndpoint.FreeText())

        val paired = form.withEndpointPaired(TripEndpointSlot.TO, pressed, here)

        assertEquals(here, paired.from)
    }

    @Test
    fun `without a location fix the other endpoint is left empty`() {
        val paired = TripPlanFormState().withEndpointPaired(TripEndpointSlot.TO, pressed, here = null)

        assertEquals(pressed, paired.to)
        assertEquals(TripEndpoint.FreeText(), paired.from)
        assertFalse(paired.canSubmit)
    }

    @Test
    fun `pairing drops both endpoints' stale suggestions`() {
        val form = TripPlanFormState(fromSuggestions = listOf(named), toSuggestions = listOf(named))

        val paired = form.withEndpointPaired(TripEndpointSlot.TO, pressed, here)

        assertTrue(paired.fromSuggestions.isEmpty())
        assertTrue(paired.toSuggestions.isEmpty())
    }

    @Test
    fun `only a blank free-text endpoint counts as empty`() {
        assertTrue(TripEndpoint.FreeText().isEmpty)
        assertTrue(TripEndpoint.FreeText("   ").isEmpty)
        assertFalse(TripEndpoint.FreeText("pike").isEmpty)
        assertFalse(named.isEmpty)
        assertFalse(pressed.isEmpty)
        assertFalse(here.isEmpty)
    }
}
