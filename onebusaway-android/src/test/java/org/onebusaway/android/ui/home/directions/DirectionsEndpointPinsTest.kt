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
package org.onebusaway.android.ui.home.directions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.map.ItineraryPins
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.util.GeoPoint

/** The current-location endpoint's claim on a map pin, which it gives up to the blue dot (#2111). */
class DirectionsEndpointPinsTest {

    private val here = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3)
    private val pressed = TripEndpoint.MapPoint(lat = 47.7, lon = -122.2)
    private val named = TripEndpoint.Geocoded("Pike Place Market", lat = 47.61, lon = -122.34)

    @Test
    fun `a resolved endpoint is pinned at its own coordinates`() {
        assertEquals(GeoPoint(47.7, -122.2), pressed.pinPoint())
        assertEquals(GeoPoint(47.61, -122.34), named.pinPoint())
    }

    @Test
    fun `the current location is not pinned - the blue dot already marks it`() {
        assertNull(here.pinPoint())
    }

    @Test
    fun `a half-typed endpoint has nowhere to pin yet`() {
        assertNull(TripEndpoint.FreeText("Pike Pl").pinPoint())
    }

    @Test
    fun `an endpoint the geocoder left without coordinates has nowhere to pin`() {
        assertNull(TripEndpoint.AddressBook("Mum", lat = null, lon = null).pinPoint())
    }

    @Test
    fun `a trip between two named places wears both terminus pins`() {
        assertEquals(ItineraryPins(start = true, end = true), params(named, pressed).itineraryPins())
    }

    @Test
    fun `a trip from the current location drops its start pin only`() {
        assertEquals(ItineraryPins(start = false, end = true), params(here, named).itineraryPins())
    }

    @Test
    fun `a trip to the current location drops its end pin only`() {
        assertEquals(ItineraryPins(start = true, end = false), params(named, here).itineraryPins())
    }

    @Test
    fun `a plan restored without its request wears both pins`() {
        // A notification re-entry doesn't reconstruct the request, so nothing says an end was the
        // rider's own location — draw both rather than guess one away.
        assertEquals(ItineraryPins(start = true, end = true), null.itineraryPins())
    }

    private fun params(from: TripEndpoint, to: TripEndpoint) = TripPlanParams(
        from = from,
        to = to,
        dateTimeMillis = 0L,
        arriving = false,
        modes = TripModeSelection(),
        wheelchair = false,
        optimizeTransfers = false,
        maxWalkMeters = null
    )
}
