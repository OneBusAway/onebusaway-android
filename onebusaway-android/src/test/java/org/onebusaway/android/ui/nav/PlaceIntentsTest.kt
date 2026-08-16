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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.ui.nav.PlaceIntents.Place
import org.onebusaway.android.ui.nav.PlaceIntents.PlaceRequest
import org.onebusaway.android.ui.nav.PlaceIntents.parse
import org.onebusaway.android.ui.tripplan.TripEndpoint

/**
 * Unit tests for the place-intent vocabulary (#1936) — the `geo:` URIs, maps links and shared text
 * another app can name a place with. [parse] is pure over an already-projected intent, so the whole
 * vocabulary is covered here; only the `Intent` reads it sits behind need Android.
 */
class PlaceIntentsTest {

    private fun viewed(uri: String) = parse(PlaceRequest(dataUri = uri))

    private fun shared(text: String) = parse(PlaceRequest(sharedText = text))

    // --- geo: ---------------------------------------------------------------------------------------

    @Test
    fun `bare geo URI is the point itself`() {
        assertEquals(Place.Point(47.6097, -122.3422), viewed("geo:47.6097,-122.3422"))
    }

    @Test
    fun `geo zoom and RFC 5870 uncertainty are dropped`() {
        assertEquals(Place.Point(47.6097, -122.3422), viewed("geo:47.6097,-122.3422?z=17"))
        assertEquals(Place.Point(47.6097, -122.3422), viewed("geo:47.6097,-122.3422;u=35"))
    }

    /** RFC 5870 allows an altitude as a third component; it is accepted and ignored. */
    @Test
    fun `geo altitude is dropped`() {
        assertEquals(Place.Point(48.2, 16.3), viewed("geo:48.2,16.3,183"))
    }

    /** What Contacts emits for a postal address: the `geo:0,0` placeholder plus a form-encoded `q`. */
    @Test
    fun `geo placeholder with an address is a query`() {
        assertEquals(
            Place.Query("1600 Amphitheatre Parkway, Mountain View, CA"),
            viewed("geo:0,0?q=1600+Amphitheatre+Parkway%2C+Mountain+View%2C+CA")
        )
    }

    @Test
    fun `geo q may be a labelled coordinate`() {
        assertEquals(
            Place.Point(47.6097, -122.3422, "Pike Place Market"),
            viewed("geo:0,0?q=47.6097,-122.3422(Pike+Place+Market)")
        )
    }

    @Test
    fun `geo q may be a bare coordinate`() {
        assertEquals(Place.Point(47.6097, -122.3422), viewed("geo:0,0?q=47.6097,-122.3422"))
    }

    /** A sender that supplies both means "this position, called that" — the coordinate is not discarded. */
    @Test
    fun `geo q labels a real coordinate rather than replacing it`() {
        assertEquals(
            Place.Point(47.6097, -122.3422, "Pike Place Market"),
            viewed("geo:47.6097,-122.3422?q=Pike+Place+Market")
        )
    }

    @Test
    fun `geo scheme is case-insensitive`() {
        assertEquals(Place.Point(47.6097, -122.3422), viewed("GEO:47.6097,-122.3422"))
    }

    @Test
    fun `geo placeholder alone names nothing`() {
        assertNull(viewed("geo:0,0"))
    }

    @Test
    fun `out-of-range and non-numeric coordinates are not coordinates`() {
        assertNull(viewed("geo:91.0,-122.3"))
        assertNull(viewed("geo:47.6,-181.0"))
        assertNull(viewed("geo:north,west"))
    }

    // --- other schemes ------------------------------------------------------------------------------

    @Test
    fun `the app's own deep links and internal URIs are not places`() {
        assertNull(viewed("onebusaway://view-stop?stopID=1_75403"))
        assertNull(viewed("https://onebusaway.co/regions/1/stops/1_75403/trips?trip_id=1_18196913"))
        assertNull(viewed("content://com.joulespersecond.oba/stops/1_75403"))
    }

    // --- maps links ---------------------------------------------------------------------------------

    @Test
    fun `google maps classic q may be an address or a coordinate`() {
        assertEquals(
            Place.Query("Pike Place Market, Seattle"),
            viewed("https://maps.google.com/maps?q=Pike+Place+Market%2C+Seattle")
        )
        assertEquals(Place.Point(47.6097, -122.3422), viewed("https://maps.google.com/maps?q=47.6097,-122.3422"))
    }

    @Test
    fun `google maps api=1 search and directions name the destination`() {
        assertEquals(
            Place.Query("Space Needle"),
            viewed("https://www.google.com/maps/search/?api=1&query=Space+Needle")
        )
        assertEquals(
            Place.Query("Space Needle"),
            viewed("https://www.google.com/maps/dir/?api=1&origin=Pike+Place&destination=Space+Needle")
        )
    }

    @Test
    fun `google maps place page carries its name and its coordinate`() {
        assertEquals(
            Place.Point(47.6097, -122.3422, "Pike Place Market"),
            viewed("https://www.google.com/maps/place/Pike+Place+Market/@47.6097,-122.3422,17z/data=!3m1")
        )
    }

    @Test
    fun `apple maps coordinate beats its own place name, which becomes the label`() {
        assertEquals(
            Place.Point(47.6097, -122.3422, "Home"),
            viewed("https://maps.apple.com/?q=Home&ll=47.6097,-122.3422")
        )
    }

    @Test
    fun `openstreetmap marker is a coordinate pair`() {
        assertEquals(
            Place.Point(47.6097, -122.3422),
            viewed("https://www.openstreetmap.org/?mlat=47.6097&mlon=-122.3422")
        )
    }

    /**
     * The URL Google Contacts builds for a postal address, captured off a Pixel 7 Pro. The `%0A` between
     * street and city decodes to a real newline — which a single-line form field truncates at and a
     * geocoder can do nothing with — so the whole address has to survive as one line.
     */
    @Test
    fun `a contacts address URL keeps its whole address on one line`() {
        assertEquals(
            Place.Query("14420 75th Ave NE Bothell, WA 98011"),
            viewed("https://www.google.com/maps?daddr=14420+75th+Ave+NE%0ABothell,+WA+98011&entry=ml&utm_campaign=ml-ardl-mgrc")
        )
    }

    @Test
    fun `a labelled coordinate keeps its label on one line`() {
        assertEquals(
            Place.Point(47.6097, -122.3422, "Pike Place Market"),
            viewed("geo:0,0?q=47.6097,-122.3422(Pike%0APlace%0AMarket)")
        )
    }

    @Test
    fun `an unknown maps host is not read`() {
        assertNull(viewed("https://maps.example.com/?q=Pike+Place+Market"))
    }

    @Test
    fun `a maps host with nothing readable on it names nothing`() {
        assertNull(viewed("https://www.google.com/maps"))
    }

    // --- shared text --------------------------------------------------------------------------------

    @Test
    fun `plain shared text is a query`() {
        assertEquals(Place.Query("400 Broad St, Seattle, WA 98109"), shared("400 Broad St, Seattle, WA 98109"))
    }

    @Test
    fun `a readable maps link inside shared text wins over its prose`() {
        assertEquals(
            Place.Point(47.6097, -122.3422),
            shared("Look at this\nhttps://www.openstreetmap.org/?mlat=47.6097&mlon=-122.3422")
        )
    }

    /**
     * How a Google Maps share arrives: a place name and a short link only the network can expand. The
     * link is dropped from the query rather than geocoded along with the name.
     */
    @Test
    fun `an unreadable link leaves its prose as the query`() {
        assertEquals(
            Place.Query("Pike Place Market"),
            shared("Pike Place Market\nhttps://maps.app.goo.gl/AbCdEf123")
        )
    }

    @Test
    fun `a geo URI shared as text is read as one`() {
        assertEquals(Place.Point(47.6097, -122.3422), shared("geo:47.6097,-122.3422"))
    }

    @Test
    fun `a share with nothing but an unreadable link names nothing`() {
        assertNull(shared("https://maps.app.goo.gl/AbCdEf123"))
    }

    @Test
    fun `an empty share names nothing`() {
        assertNull(shared("   "))
        assertNull(parse(PlaceRequest()))
    }

    // --- as a plan endpoint -------------------------------------------------------------------------

    @Test
    fun `a named point becomes a geocoded endpoint carrying its name`() {
        assertEquals(
            TripEndpoint.Geocoded("Space Needle", lat = 47.6205, lon = -122.3493),
            Place.Point(47.6205, -122.3493, "Space Needle").toEndpoint()
        )
    }

    /** Nothing to call it, so it becomes the same kind of endpoint a map pick makes. */
    @Test
    fun `an unnamed point becomes a map-point endpoint`() {
        assertEquals(
            TripEndpoint.MapPoint(lat = 47.6205, lon = -122.3493),
            Place.Point(47.6205, -122.3493).toEndpoint()
        )
    }

    // --- precedence ---------------------------------------------------------------------------------

    @Test
    fun `the data URI is read before the shared text`() {
        assertEquals(
            Place.Point(47.6097, -122.3422),
            parse(PlaceRequest(dataUri = "geo:47.6097,-122.3422", sharedText = "somewhere else"))
        )
    }

    @Test
    fun `an unreadable data URI falls through to the shared text`() {
        assertEquals(
            Place.Query("Pike Place Market"),
            parse(PlaceRequest(dataUri = "content://whatever/1", sharedText = "Pike Place Market"))
        )
    }
}
