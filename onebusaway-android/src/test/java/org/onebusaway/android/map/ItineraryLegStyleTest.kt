/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_STREET_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.ACHROMATIC_ROUTE_CHROMA
import org.onebusaway.android.util.GeoPoint

/**
 * The directions map's per-leg stroke policy (#2041). The point of these is the *distinctions*: before
 * this, every on-street leg was one grey and every ride one green, so the assertions that matter are
 * that the kinds don't collide and that a ride keeps its agency's hue.
 */
@SuppressLint("RestrictedApi") // Hct, Material's vendored color-science util; see AdjacencyRouteColors.kt.
class ItineraryLegStyleTest {

    private val bikeShareDock = TripPlace(vertexType = TripVertexType.BIKESHARE, bikeShareId = "dock-1")

    @Test
    fun `a bicycle leg from a rental dock is bikeshare, from anywhere else own-bike`() {
        assertEquals(
            ItineraryLegKind.BIKESHARE,
            TripLeg(mode = TripMode.BICYCLE, from = bikeShareDock).legKind()
        )
        assertEquals(
            ItineraryLegKind.BIKE,
            TripLeg(mode = TripMode.BICYCLE, from = TripPlace(name = "Home")).legKind()
        )
    }

    @Test
    fun `every transit mode is a ride, and an unknown mode walks`() {
        listOf(TripMode.BUS, TripMode.RAIL, TripMode.FERRY, TripMode.TRAM, TripMode.SUBWAY).forEach {
            assertEquals(ItineraryLegKind.TRANSIT, TripLeg(mode = it).legKind())
        }
        assertEquals(ItineraryLegKind.WALK, TripLeg(mode = TripMode.WALK).legKind())
        assertEquals(ItineraryLegKind.CAR, TripLeg(mode = TripMode.CAR).legKind())
        // A leg OTP left unlabelled is walked, matching how the drawer's timeline classifies it.
        assertEquals(ItineraryLegKind.WALK, TripLeg(mode = null).legKind())
    }

    @Test
    fun `no two leg kinds share a colour`() {
        val colors = ItineraryLegKind.entries.map { itineraryLegStyle(it, routeColor = null).color }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun `on-street legs are dashed and thinner than the ride they connect to`() {
        val ride = itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null)
        assertEquals(RouteLineDash.NONE, ride.dash)
        assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, ride.widthProfile)

        listOf(ItineraryLegKind.WALK, ItineraryLegKind.BIKE, ItineraryLegKind.BIKESHARE, ItineraryLegKind.CAR)
            .forEach { kind ->
                val street = itineraryLegStyle(kind, routeColor = null)
                assertEquals("$kind should be dashed", RouteLineDash.TRAIL, street.dash)
                assertEquals(ITINERARY_STREET_WIDTH_PROFILE, street.widthProfile)
            }
        assertTrue(ITINERARY_STREET_WIDTH_PROFILE.thicknessDp < ITINERARY_RIDE_WIDTH_PROFILE.thicknessDp)
    }

    @Test
    fun `no leg both dashes and stamps chevrons`() {
        // The chevrons are a texture stamped along the stroke, so a dash pattern chops them into
        // fragments and the two read as one broken line rather than as either.
        ItineraryLegKind.entries.forEach { kind ->
            val style = itineraryLegStyle(kind, routeColor = null)
            assertFalse("$kind dashes and stamps chevrons", style.dash != RouteLineDash.NONE && style.directional)
        }
        assertTrue(itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null).directional)
    }

    @Test
    fun `a ride keeps its agency's hue, at the map's own chroma and tone`() {
        // Two reds an agency might publish: one washed out, one nearly black. Only the hue survives, so
        // the map draws them the same — the hue is the route's identity, the rest is this map's rendering.
        val pale = Hct.fromInt(itineraryLegStyle(ItineraryLegKind.TRANSIT, Hct.from(25.0, 20.0, 85.0).toInt()).color)
        val dark = Hct.fromInt(itineraryLegStyle(ItineraryLegKind.TRANSIT, Hct.from(25.0, 90.0, 20.0).toInt()).color)

        assertEquals(25.0, pale.hue, HUE_TOLERANCE_DEGREES)
        assertEquals(pale.hue, dark.hue, HUE_TOLERANCE_DEGREES)

        // And that rendering is the adjacency lines' own, so the two schemes can't drift apart. Compared
        // in HCT rather than as ARGB ints: a source hue round-trips through the sRGB gamut with a
        // fraction of a degree of drift, which moves the packed int without moving the colour.
        val adjacency = Hct.fromInt(adjacencyRouteColors(listOf("only")).getValue("only"))
        listOf(pale, dark).forEach {
            assertEquals(adjacency.chroma, it.chroma, CHANNEL_TOLERANCE)
            assertEquals(adjacency.tone, it.tone, CHANNEL_TOLERANCE)
        }
    }

    @Test
    fun `a ride whose agency publishes no usable colour falls back rather than going grey`() {
        val nearBlack = 0xFF101010.toInt() // achromatic: a hue-preserving re-tone has nothing to keep

        listOf(null, nearBlack).forEach { source ->
            val fallback = itineraryLegStyle(ItineraryLegKind.TRANSIT, source)
            assertTrue(
                "fallback for $source was achromatic",
                Hct.fromInt(fallback.color).chroma > ACHROMATIC_ROUTE_CHROMA
            )
        }
    }

    @Test
    fun `every leg kind draws a colour with a hue`() {
        ItineraryLegKind.entries.forEach { kind ->
            val color = itineraryLegStyle(kind, routeColor = null).color
            assertTrue("$kind was achromatic", Hct.fromInt(color).chroma > ACHROMATIC_ROUTE_CHROMA)
        }
    }

    @Test
    fun `focusing a leg cases it and leaves the rest of the trip alone`() {
        val lines = tripOf(walk = 0, ride = 1, walk2 = 2)

        val focused = lines.withLegFocus(setOf(1))

        // Nothing is dropped — the whole trip is still drawn.
        assertEquals(lines.size, focused.size)
        // Exactly the focused leg is cased; that case *is* the selection signal (#2082).
        val (cased, plain) = focused.partition { it.cased }
        assertEquals(listOf(ITINERARY_RIDE_WIDTH_PROFILE), cased.map { it.widthProfile })
        // The focused leg is last, so its case and stroke draw over the legs around it.
        assertEquals(cased.single(), focused.last())
        // Nothing but the case changes: every leg keeps the weight, colour and dash that say what kind of
        // leg it is, so width is free to mean only that. Compared as sets — focusing reorders the list, which
        // is how the focused leg comes to draw last.
        assertEquals(lines.map { it.line }.toSet(), focused.map { it.copy(cased = false) }.toSet())
        assertEquals(2, plain.size)
    }

    @Test
    fun `a case is its own line's hue, tuned away from it in the direction the basemap went`() {
        val line = Hct.fromInt(itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null).color)
        val onLightMap = Hct.fromInt(mapRouteLineCaseColor(line.toInt(), darkMode = false))
        val onDarkMap = Hct.fromInt(mapRouteLineCaseColor(line.toInt(), darkMode = true))

        // Same hue either way, so a case reads as part of its line rather than as a second line beside it.
        listOf(onLightMap, onDarkMap).forEach {
            assertEquals(line.hue, it.hue, HUE_TOLERANCE_DEGREES)
        }
        // A halo separates a line from its surroundings by carrying the *background's* value, so it goes
        // light on the light basemap and dark on the dark one — a fixed dark case would sink into a dark map.
        assertTrue("light-map case tone ${onLightMap.tone} should exceed line tone ${line.tone}", onLightMap.tone > line.tone)
        assertTrue("dark-map case tone ${onDarkMap.tone} should be below line tone ${line.tone}", onDarkMap.tone < line.tone)
    }

    @Test
    fun `a case stays in range for a line already at the end of the tone scale`() {
        // Nothing on this map is drawn near-white or near-black, but a case must degrade to the nearest one
        // it can rather than asking for an out-of-range tone.
        listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()).forEach { extreme ->
            listOf(true, false).forEach { darkMode ->
                val tone = Hct.fromInt(mapRouteLineCaseColor(extreme, darkMode)).tone
                assertTrue("tone $tone out of range", tone in 0.0..100.0)
            }
        }
    }

    @Test
    fun `a folded interline chain focuses as one ride, not as its first leg`() {
        // #2000: several itinerary legs the rider stays aboard through, read (and tapped) as one ride.
        val focused = tripOf(walk = 0, ride = 1, walk2 = 2).withLegFocus(setOf(1, 2))

        assertEquals(2, focused.count { it.cased })
    }

    @Test
    fun `the overview draws every leg at its own weight`() {
        val lines = tripOf(walk = 0, ride = 1, walk2 = 2)

        // No focus at all, and a focus naming a leg that carried no geometry, are the same case: there
        // is nothing to raise, so nothing is lowered.
        listOf(emptySet<Int>(), setOf(7)).forEach { focus ->
            assertEquals(lines.map { it.line }, lines.withLegFocus(focus))
        }
    }

    /** A walk → ride → walk trip as drawn lines, at the given leg indices. */
    private fun tripOf(walk: Int, ride: Int, walk2: Int) = listOf(
        legLine(walk, ItineraryLegKind.WALK),
        legLine(ride, ItineraryLegKind.TRANSIT),
        legLine(walk2, ItineraryLegKind.BIKE)
    )

    private fun legLine(legIndex: Int, kind: ItineraryLegKind): ItineraryLegLine {
        val style = itineraryLegStyle(kind, routeColor = null)
        return ItineraryLegLine(
            legIndex,
            RoutePolyline(
                style.color,
                listOf(GeoPoint(47.6, -122.3), GeoPoint(47.7, -122.4)),
                widthProfile = style.widthProfile,
                directional = style.directional,
                dash = style.dash
            )
        )
    }

    private companion object {
        // The re-tone clamps to each hue's own sRGB gamut limit, so a hue can shift a degree or two.
        const val HUE_TOLERANCE_DEGREES = 3.0

        // Chroma likewise clamps to the gamut: a hue that can't hold the full chroma at this tone lands
        // slightly under it. Wide enough to absorb that, far too narrow to hide a different policy.
        const val CHANNEL_TOLERANCE = 2.0
    }
}
