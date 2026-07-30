/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_STREET_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.ACHROMATIC_ROUTE_CHROMA
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.riddenRouteHue
import org.onebusaway.android.util.routeBadgeChipColor

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
        val colors = ItineraryLegKind.entries.map { itineraryLegStyle(it, routeColor = null, palette = DIRECTIONS).color }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun `on-street legs are dashed and thinner than the ride they connect to`() {
        val ride = itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null, palette = DIRECTIONS)
        assertEquals(RouteLineDash.NONE, ride.dash)
        assertEquals(ITINERARY_RIDE_WIDTH_PROFILE, ride.widthProfile)

        listOf(ItineraryLegKind.WALK, ItineraryLegKind.BIKE, ItineraryLegKind.BIKESHARE, ItineraryLegKind.CAR)
            .forEach { kind ->
                val street = itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS)
                assertEquals("$kind should be dashed", RouteLineDash.TRAIL, street.dash)
                assertEquals(ITINERARY_STREET_WIDTH_PROFILE, street.widthProfile)
            }
        assertTrue(ITINERARY_STREET_WIDTH_PROFILE.thicknessDp < ITINERARY_RIDE_WIDTH_PROFILE.thicknessDp)
    }

    @Test
    fun `every ride is outlined and no mode leg is, so the outline can't be read as selection`() {
        assertEquals(RouteLineCase.OUTLINE, itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null, palette = DIRECTIONS).case)
        ItineraryLegKind.entries.filterNot { it == ItineraryLegKind.TRANSIT }.forEach { kind ->
            assertEquals("$kind should carry no case", RouteLineCase.NONE, itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS).case)
        }
    }

    @Test
    fun `only transit legs draw circular endpoint bulbs`() {
        assertTrue(itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null, palette = DIRECTIONS).roundCaps)
        ItineraryLegKind.entries.filterNot { it == ItineraryLegKind.TRANSIT }.forEach { kind ->
            assertFalse("$kind should keep flat endpoints", itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS).roundCaps)
        }
    }

    @Test
    fun `an interline hides only the shared bulbs`() {
        val first = TripLeg(mode = TripMode.BUS)
        val continuation = TripLeg(mode = TripMode.RAIL, interlineWithPreviousLeg = true)
        val walk = TripLeg(mode = TripMode.WALK)
        val legs = listOf(first, continuation, walk)

        assertEquals(ItineraryLegCaps(start = true, end = false), itineraryLegCaps(legs, 0))
        assertEquals(ItineraryLegCaps(start = false, end = true), itineraryLegCaps(legs, 1))
        assertEquals(ItineraryLegCaps(start = true, end = true), itineraryLegCaps(legs, 2))
    }

    @Test
    fun `on the basemap palette a ride keeps its agency's hue, at the map's own chroma and tone`() {
        // Two reds an agency might publish: one washed out, one nearly black. Only the hue survives, so
        // the map draws them the same — the hue is the route's identity, the rest is this map's rendering.
        val pale = Hct.fromInt(itineraryLegStyle(ItineraryLegKind.TRANSIT, Hct.from(25.0, 20.0, 85.0).toInt(), BASEMAP).color)
        val dark = Hct.fromInt(itineraryLegStyle(ItineraryLegKind.TRANSIT, Hct.from(25.0, 90.0, 20.0).toInt(), BASEMAP).color)

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
    fun `the palette reaches the ride and leaves every mode leg on the map's own rendering`() {
        // A ride is faded to its badge's colour because it is read beside that badge. A walk, bike or car
        // leg has no badge at all — the drawer marks it with a mode glyph — so it keeps the basemap
        // rendering every other line on this map uses, and the palette must not touch it.
        val streets = ItineraryLegKind.entries.filterNot { it == ItineraryLegKind.TRANSIT }
        streets.forEach { kind ->
            val palettes = listOf(BASEMAP, DIRECTIONS, directionsRouteLinePalette(dark = true))
                .map { itineraryLegStyle(kind, routeColor = null, palette = it).color }
            assertEquals("$kind should be palette-invariant", 1, palettes.toSet().size)
        }

        // And the ride is not: it is the whole point of handing a palette in.
        val ride = { palette: RouteLinePalette -> itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor = null, palette = palette).color }
        assertNotEquals(ride(BASEMAP), ride(DIRECTIONS))
    }

    @Test
    fun `a ride in the directions view is drawn in exactly its route badge's colour`() {
        // The whole point of the directions palette: the line under a ride and the badge naming it in the
        // drawer are one colour, not two policies that happen to land near each other. Asserted against
        // [routeBadgeChipColor] itself — the chip reads the same function — so a change to the badge's tone
        // or chroma cap moves the map line with it or fails here.
        val agencyRed = Hct.from(25.0, 90.0, 45.0).toInt()

        listOf(false, true).forEach { dark ->
            assertEquals(
                "ride colour (dark=$dark)",
                routeBadgeChipColor(agencyRed, dark),
                itineraryLegStyle(ItineraryLegKind.TRANSIT, agencyRed, directionsRouteLinePalette(dark)).color
            )
        }
    }

    @Test
    fun `the directions palette is faded and theme-aware where the basemap one is neither`() {
        // An agency's vivid hue is muted to the badge's soft chroma cap and taken to its light tone, and the
        // two themes differ — none of which is true of the basemap palette, which renders one chroma and one
        // tone whatever the theme (the basemap carries its own light/dark styles).
        val agencyRed = Hct.from(25.0, 90.0, 45.0).toInt()
        fun ride(palette: RouteLinePalette) = Hct.fromInt(itineraryLegStyle(ItineraryLegKind.TRANSIT, agencyRed, palette).color)

        val basemap = ride(BASEMAP)
        val light = ride(directionsRouteLinePalette(dark = false))
        val night = ride(directionsRouteLinePalette(dark = true))

        // Same route, so the hue is the same in all three: only the rendering changed.
        listOf(light, night).forEach { assertEquals(basemap.hue, it.hue, HUE_TOLERANCE_DEGREES) }
        // Faded: less saturated and lighter than the basemap line.
        listOf(light, night).forEach {
            assertTrue("chroma ${it.chroma} should be under the basemap's ${basemap.chroma}", it.chroma < basemap.chroma)
            assertTrue("tone ${it.tone} should be above the basemap's ${basemap.tone}", it.tone > basemap.tone)
        }
        // Theme-aware: the two themes render the same route differently. Deliberately not asserted as
        // "the dark theme is more saturated" — each theme's chroma ceiling is its own *cap* (which
        // `RouteColorsTest` pins at a shared tone), but what a hue can actually hold is decided by the sRGB
        // gamut at that theme's fill tone, so which theme comes out more saturated varies by hue.
        assertNotEquals(light.toInt(), night.toInt())
    }

    @Test
    fun `a colourless ride's line is the colour a badge gives that ride, not a fallback of its own`() {
        // The WSF case: every WSF route publishes an empty colour, and the two surfaces each substituted
        // something — the map the colourless-ride anchor, the badge the theme's neutral chip — so a ferry
        // drew a coral line beside a grey roundel. Both now read [riddenRouteHue], and this is the assertion
        // that fails if either grows a private fallback again.
        val greyPublished = 0xFF808080.toInt() // achromatic: published, but no hue to keep

        listOf(false, true).forEach { dark ->
            val badge = routeBadgeChipColor(riddenRouteHue(routeColor = null), dark)
            listOf(null, greyPublished).forEach { source ->
                assertEquals(
                    "colourless ride line for $source (dark=$dark)",
                    badge,
                    itineraryLegStyle(ItineraryLegKind.TRANSIT, source, directionsRouteLinePalette(dark)).color
                )
            }
        }
    }

    @Test
    fun `a ride whose agency publishes no usable colour falls back rather than going grey`() {
        val nearBlack = 0xFF101010.toInt() // achromatic: a hue-preserving re-tone has nothing to keep

        listOf(null, nearBlack).forEach { source ->
            val fallback = itineraryLegStyle(ItineraryLegKind.TRANSIT, source, DIRECTIONS)
            assertTrue(
                "fallback for $source was achromatic",
                Hct.fromInt(fallback.color).chroma > ACHROMATIC_ROUTE_CHROMA
            )
        }
    }

    @Test
    fun `every leg kind draws a colour with a hue`() {
        ItineraryLegKind.entries.forEach { kind ->
            val color = itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS).color
            assertTrue("$kind was achromatic", Hct.fromInt(color).chroma > ACHROMATIC_ROUTE_CHROMA)
        }
    }

    @Test
    fun `focusing a leg cases it and leaves the rest of the trip alone`() {
        val lines = tripOf(walk = 0, ride = 1, walk2 = 2)

        val focused = lines.withLegFocus(setOf(1))

        // Nothing is dropped — the whole trip is still drawn.
        assertEquals(lines.size, focused.size)
        // Exactly the focused leg takes the selection case; that step up in edge weight *is* the selection
        // signal (#2082) — every ride already wears the hairline outline.
        val (cased, plain) = focused.partition { it.case == RouteLineCase.SELECTION }
        assertEquals(listOf(ITINERARY_RIDE_WIDTH_PROFILE), cased.map { it.widthProfile })
        // The focused leg is last, so its case and stroke draw over the legs around it.
        assertEquals(cased.single(), focused.last())
        // Nothing but the case changes: every leg keeps the weight, colour and dash that say what kind of
        // leg it is, so width is free to mean only that. Compared as sets — focusing reorders the list, which
        // is how the focused leg comes to draw last.
        assertEquals(
            lines.map { it.line }.toSet(),
            // Undo only the selection step: a ride drops back to the outline it always wore, and the mode
            // legs beside it are compared exactly as they were drawn.
            focused.map { if (it.case == RouteLineCase.SELECTION) it.copy(case = RouteLineCase.OUTLINE) else it }.toSet()
        )
        assertEquals(2, plain.size)
    }

    @Test
    fun `a case goes to the end of the tone scale away from the basemap, keeping its line's hue`() {
        // A case goes *against* the basemap — near-black on the light map, near-white on the dark one.
        // Device-checked twice over: tinting it toward the map put it at the map's own value and it vanished,
        // and a mid-way tone was still too weak at this width to register.
        //
        // At those tones sRGB holds little chroma, so a case ends up mostly a separator rather than a second
        // colour — a deliberate trade for contrast. What must survive is the *hue*, so the trace of colour
        // that's left is its own line's and not a neighbouring one's.
        ItineraryLegKind.entries.forEach { kind ->
            val line = Hct.fromInt(itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS).color)

            listOf(false to 10.0, true to 90.0).forEach { (darkMode, expectedTone) ->
                val case = Hct.fromInt(mapRouteLineCaseColor(line.toInt(), darkMode))
                assertEquals("$kind case tone (darkMode=$darkMode)", expectedTone, case.tone, CHANNEL_TOLERANCE)
                assertEquals("$kind case hue (darkMode=$darkMode)", line.hue, case.hue, HUE_TOLERANCE_DEGREES)
            }
        }
    }

    @Test
    fun `a case's tone belongs to the theme, not to the line it wraps`() {
        // What a case has to contrast with is the basemap, which sits at a fixed value per theme. So two lines
        // at quite different tones get the same case tone — this is the assertion an offset-from-the-line
        // policy (which is what the earlier passes tried) would fail.
        val pale = Hct.from(36.0, 40.0, 85.0)
        val deep = Hct.from(36.0, 40.0, 25.0)

        listOf(false, true).forEach { darkMode ->
            assertEquals(caseTone(pale, darkMode), caseTone(deep, darkMode), CHANNEL_TOLERANCE)
        }
    }

    @Test
    fun `a folded interline chain focuses as one ride, not as its first leg`() {
        // #2000: several itinerary legs the rider stays aboard through, read (and tapped) as one ride.
        val focused = tripOf(walk = 0, ride = 1, walk2 = 2).withLegFocus(setOf(1, 2))

        assertEquals(2, focused.count { it.case == RouteLineCase.SELECTION })
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

    private fun caseTone(line: Hct, darkMode: Boolean) = Hct.fromInt(mapRouteLineCaseColor(line.toInt(), darkMode)).tone

    /** A walk → ride → walk trip as drawn lines, at the given leg indices. */
    private fun tripOf(walk: Int, ride: Int, walk2: Int) = listOf(
        legLine(walk, ItineraryLegKind.WALK),
        legLine(ride, ItineraryLegKind.TRANSIT),
        legLine(walk2, ItineraryLegKind.BIKE)
    )

    @Test
    fun `no itinerary leg stamps travel-direction chevrons`() {
        // An on-street leg never could: the chevrons are a texture stamped along the stroke, so its dash
        // chops them into fragments. A ride dropped them with the badge palette — a faded line under a
        // hairline case reads as noise with an arrow texture on it, and the trip's direction is already read
        // from the drawer's ordered rows and the leg's endpoint bulbs. Asserted on the drawn line, since the
        // style table deliberately names no `directional` for anything to get wrong.
        assertTrue(tripOf(walk = 0, ride = 1, walk2 = 2).none { it.line.directional })
    }

    private fun legLine(legIndex: Int, kind: ItineraryLegKind): ItineraryLegLine {
        val style = itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS)
        return ItineraryLegLine(
            legIndex,
            RoutePolyline(
                style.color,
                listOf(GeoPoint(47.6, -122.3), GeoPoint(47.7, -122.4)),
                widthProfile = style.widthProfile,
                dash = style.dash,
                case = style.case,
                roundStartCap = style.roundCaps,
                roundEndCap = style.roundCaps
            )
        )
    }

    private companion object {
        // The palette the directions view actually draws with, and the one every other view does. Most cases
        // here are about a leg's *shape* rather than its colour, and take the directions palette because that
        // is the only palette an itinerary leg is ever stroked with.
        val DIRECTIONS: RouteLinePalette = directionsRouteLinePalette(dark = false)

        val BASEMAP: RouteLinePalette = BASEMAP_ROUTE_LINE_PALETTE

        // The re-tone clamps to each hue's own sRGB gamut limit, so a hue can shift a degree or two.
        const val HUE_TOLERANCE_DEGREES = 3.0

        // Chroma likewise clamps to the gamut: a hue that can't hold the full chroma at this tone lands
        // slightly under it. Wide enough to absorb that, far too narrow to hide a different policy.
        const val CHANNEL_TOLERANCE = 2.0
    }
}
