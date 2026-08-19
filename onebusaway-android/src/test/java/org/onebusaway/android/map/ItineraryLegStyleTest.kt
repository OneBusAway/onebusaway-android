/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegAlternative
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripVehicleRental
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_STREET_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.ACHROMATIC_ROUTE_CHROMA
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.encodePolyline
import org.onebusaway.android.util.riddenRouteHue
import org.onebusaway.android.util.routeBadgeChipColor

/**
 * The directions map's per-leg stroke policy (#2041). The point of these is the *distinctions*: before
 * this, every on-street leg was one grey and every ride one green, so the assertions that matter are
 * that the kinds don't collide and that a ride keeps its agency's hue.
 */
@SuppressLint("RestrictedApi") // Hct, Material's vendored color-science util; see AdjacencyRouteColors.kt.
class ItineraryLegStyleTest {

    private val bikeShareDock = TripPlace(vertexType = TripVertexType.BIKESHARE, rental = TripVehicleRental(id = "dock-1"))

    @Test
    fun `a bicycle leg OTP flagged as hired is bikeshare, any other is own-bike`() {
        assertEquals(
            ItineraryLegKind.BIKESHARE,
            TripLeg(mode = TripMode.BICYCLE, rentedVehicle = true, from = bikeShareDock).legKind()
        )
        assertEquals(
            ItineraryLegKind.BIKE,
            TripLeg(mode = TripMode.BICYCLE, from = TripPlace(name = "Home")).legKind()
        )
        // The flag, not the dock the leg starts at (#2159): a rider setting off from beside a rental
        // dock on their own bike is riding their own bike, and OTP is the one that knows which.
        assertEquals(
            ItineraryLegKind.BIKE,
            TripLeg(mode = TripMode.BICYCLE, from = bikeShareDock).legKind()
        )
        // ...and a hired ride keeps its stroke wherever it starts, which a dockless one may be nowhere
        // in particular.
        assertEquals(
            ItineraryLegKind.BIKESHARE,
            TripLeg(mode = TripMode.BICYCLE, rentedVehicle = true, from = TripPlace(name = "Kerb")).legKind()
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
        val first = TripLeg(mode = TripMode.BUS, routeId = "45")
        val continuation = TripLeg(mode = TripMode.RAIL, routeId = "75", interlineWithPreviousLeg = true)
        val walk = TripLeg(mode = TripMode.WALK)

        assertEquals(
            listOf(
                ItineraryLegCaps(start = true, end = false),
                ItineraryLegCaps(start = false, end = true, startSeam = true),
                ItineraryLegCaps(start = true, end = true)
            ),
            itineraryLegCaps(listOf(first, continuation, walk))
        )
    }

    @Test
    fun `only a route change is cut, and it is cut where the drawer says stay on board`() {
        // #2127: the rider's vehicle keeps going but its route changes, which no bulb can say — a bulb pair
        // means alight and board. The cut goes on the leg continued *onto*, and only when the route really
        // changed: a 12 reversing onto itself (a self-interline) leaves the ride unchanged, so there's
        // nothing to mark, exactly as the drawer announces no transition for one.
        val crossRoute = listOf(
            TripLeg(mode = TripMode.BUS, routeId = "45"),
            TripLeg(mode = TripMode.BUS, routeId = "75", interlineWithPreviousLeg = true)
        )
        val selfInterline = listOf(
            TripLeg(mode = TripMode.BUS, routeId = "12"),
            TripLeg(mode = TripMode.BUS, routeId = "12", interlineWithPreviousLeg = true)
        )

        assertEquals(listOf(false, true), cutLegs(crossRoute))
        assertEquals(listOf(false, false), cutLegs(selfInterline))
        // Two legs that name no route at all are the same route as far as anything can tell, so they are
        // read as a self-interline — the exact-id rule [Interlines] states, not a second guess at it here.
        assertEquals(
            listOf(false, false),
            cutLegs(listOf(TripLeg(mode = TripMode.BUS), TripLeg(mode = TripMode.RAIL, interlineWithPreviousLeg = true)))
        )
        // And nothing is cut where two separate rides meet — that join keeps its two bulbs.
        assertEquals(
            listOf(false, false),
            cutLegs(listOf(TripLeg(mode = TripMode.BUS, routeId = "45"), TripLeg(mode = TripMode.BUS, routeId = "75")))
        )
    }

    @Test
    fun `a cut line is never also bulbed at that end`() {
        // The two marks answer the same question — what happens to the rider at this join — so a line that
        // carries both would be saying "stay aboard" and "get off" in one place.
        val legs = listOf(
            TripLeg(mode = TripMode.BUS, routeId = "45"),
            TripLeg(mode = TripMode.BUS, routeId = "75", interlineWithPreviousLeg = true),
            TripLeg(mode = TripMode.BUS, routeId = "8", interlineWithPreviousLeg = true)
        )

        itineraryLegCaps(legs).forEachIndexed { index, caps ->
            assertFalse("leg $index was both cut and bulbed", caps.startSeam && caps.start)
        }
    }

    /** Which legs begin at an interline cutover, in leg order. */
    private fun cutLegs(legs: List<TripLeg>) = itineraryLegCaps(legs).map { it.startSeam }

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
    fun `an outline goes to the end of the tone scale away from the basemap, keeping its line's hue`() {
        // A case goes *against* the basemap — near-black on the light map, near-white on the dark one.
        // Device-checked twice over: tinting it toward the map put it at the map's own value and it vanished,
        // and a mid-way tone was still too weak at this width to register.
        //
        // An outline stops short of the very end, keeping enough chroma to read as *this line's* edge, so the
        // hue is asserted in both themes. The end itself belongs to the selection case below.
        ItineraryLegKind.entries.forEach { kind ->
            val line = Hct.fromInt(itineraryLegStyle(kind, routeColor = null, palette = DIRECTIONS).color)

            listOf(false to 10.0, true to 90.0).forEach { (darkMode, expectedTone) ->
                val case = caseHct(line, darkMode)
                assertEquals("$kind case tone (darkMode=$darkMode)", expectedTone, case.tone, CHANNEL_TOLERANCE)
                assertEquals("$kind case hue (darkMode=$darkMode)", line.hue, case.hue, HUE_TOLERANCE_DEGREES)
            }
        }
    }

    @Test
    fun `the selected leg's case clears its own line on the dark basemap, whatever the line's hue`() {
        // #2226: selection is said with case *thickness* (#2082), which a rider can only read when the case is
        // visibly not the line. The selected leg used to case at the outline's own tone 90, and at that tone
        // sRGB still holds a saturated version of the warm and green hues — so a green line got a green case
        // and the highlight didn't register in dark mode.
        //
        // Swept over the hue circle rather than run on the three leg colours because that is where it failed:
        // how much chroma survives a re-tone is a property of the hue's own gamut, so a policy can be correct
        // for the blues and wrong for the greens, which is exactly what the old one was. Tone 100 is past the
        // end of every hue's gamut, which is what makes this hold for all of them at once.
        (0 until 360 step 10).forEach { hue ->
            val line = Hct.fromInt(mapRouteLineColor(hue.toDouble()))
            val case = caseHct(line, darkMode = true, case = RouteLineCase.SELECTION)

            assertTrue(
                "hue $hue: case tone ${case.tone} is only ${case.tone - line.tone} above its line's ${line.tone}",
                case.tone - line.tone >= MIN_SELECTION_CASE_TONE_STEP
            )
            assertTrue(
                "hue $hue: case kept chroma ${case.chroma} of its line's ${line.chroma} — a colour, not an edge",
                case.chroma <= MAX_SELECTION_CASE_CHROMA
            )
        }
    }

    @Test
    fun `the selected leg's case takes the very end of the scale, an outline stops short of it`() {
        // The second half of #2226: what separates the rider's leg from the rides beside it is not the tone
        // step (1.29:1 on the dark basemap, 1.10:1 on the light one — neither is a difference on its own) but
        // that on the dark map only the selected case is *colourless*, every outline around it staying tinted
        // with the route it wraps. That is asserted by the hue-circle case above; asserted here is the policy
        // it rests on — a selection is never cased less far out than an outline beside it, in either theme.
        val onDark = caseTone(ANY_MAP_LINE, darkMode = true, case = RouteLineCase.SELECTION)
        val onLight = caseTone(ANY_MAP_LINE, darkMode = false, case = RouteLineCase.SELECTION)

        assertEquals(100.0, onDark, CHANNEL_TOLERANCE)
        assertEquals(5.0, onLight, CHANNEL_TOLERANCE)
        assertTrue(onDark > caseTone(ANY_MAP_LINE, darkMode = true, case = RouteLineCase.OUTLINE))
        assertTrue(onLight < caseTone(ANY_MAP_LINE, darkMode = false, case = RouteLineCase.OUTLINE))
    }

    @Test
    fun `an uncased line answers with the outline's tone, for the interline cut that reads it`() {
        // The cutover slash takes its line's case colour whether or not that line wears a case (see
        // `InterlineSeamMark`) — it *is* a hairline joint's casing. So NONE resolves rather than throwing or
        // falling through to the selection tone, and it resolves to the hairline's own.
        listOf(false, true).forEach { darkMode ->
            assertEquals(
                caseTone(ANY_MAP_LINE, darkMode, case = RouteLineCase.OUTLINE),
                caseTone(ANY_MAP_LINE, darkMode, case = RouteLineCase.NONE),
                CHANNEL_TOLERANCE
            )
        }
    }

    @Test
    fun `the approach cases in the selection's colour, which is why it is a weight and not a colour`() {
        // The approach and the ride it leads into have to read as one route line stepping down at the
        // boarding point, and they only do while their cases are the same colour — so APPROACH is a lighter
        // *weight* of the selection case, never a tone of its own. Nothing else checks this: the two live in
        // the same `when` arm, and moving one out would compile and pass everything but this.
        listOf(false, true).forEach { darkMode ->
            assertEquals(
                caseTone(ANY_MAP_LINE, darkMode, case = RouteLineCase.SELECTION),
                caseTone(ANY_MAP_LINE, darkMode, case = RouteLineCase.APPROACH),
                CHANNEL_TOLERANCE
            )
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

    /** [line]'s case as an [Hct], the one spelling of that conversion in this file. */
    private fun caseHct(line: Hct, darkMode: Boolean, case: RouteLineCase = RouteLineCase.OUTLINE) = Hct.fromInt(mapRouteLineCaseColor(line.toInt(), darkMode, case))

    private fun caseTone(line: Hct, darkMode: Boolean, case: RouteLineCase = RouteLineCase.OUTLINE) = caseHct(line, darkMode, case).tone

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

    @Test
    fun `a ride is striped with every other route it may be taken on, in the label's order`() {
        // #2100: the badge on this line names both routes, each in its own colour, while the line itself
        // was stroked in whichever one the planner picked.
        val ride = drawableRide(
            routeColor = 0xFF008000.toInt(),
            substitutes = listOf(substitute("2 Line", 0xFF0000FF.toInt()), substitute("1 Line", 0xFFFF0000.toInt()))
        )

        // The 1 Line's colour then the 2 Line's, which is neither the order they were offered in nor the
        // planner's choice first — it is the order the label stacks them in.
        assertEquals(
            listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()).map { rideColor(it) },
            ride.stripeColors(DIRECTIONS)
        )
    }

    @Test
    fun `the colours along a shared ride are the colours of the badge sitting on it`() {
        // The claim the stripes are for: a rider matching the line to the label above it finds every route
        // accounted for. Pinned here because the two are built apart — the label from the routes it names,
        // the stripes from the colours the line takes — and prose is all that has held them together.
        val ride = drawableRide(
            routeColor = 0xFF008000.toInt(),
            substitutes = listOf(substitute("1 Line", 0xFFFF0000.toInt()), substitute("A Line", 0xFF0000FF.toInt()))
        )
        val badge = itineraryRouteBadges(listOf(ride), DIRECTIONS).single()

        assertEquals(
            badge.routes.map { it.color }.toSet(),
            (listOf(ride.style.color) + ride.stripeColors(DIRECTIONS)).toSet()
        )
    }

    @Test
    fun `a colourless route stripes in whatever colour the view draws a colourless route in`() {
        // The rule compares an alternative against the colour its line already is, so both have to be
        // stated in the same space or the comparison is between two spellings of one colour. The two views
        // spell it differently: the itinerary puts a colourless ride on the shared anchor, while a route
        // session draws it the way the corridor beneath it is drawn — the renderer's own default. Each
        // resolves its own, and in both a colourless alternative drops out of a colourless ride's stripes
        // rather than striping it with itself.
        val itineraryColour = riddenLineColor(null, DIRECTIONS)
        assertEquals(
            emptyList<Int>(),
            rideStripeColors(listOf(null), lineColor = itineraryColour, colorOf = { riddenLineColor(it, DIRECTIONS) })
        )

        val routeViewColour = DEFAULT_ROUTE_LINE_COLOR
        assertEquals(
            emptyList<Int>(),
            rideStripeColors(listOf(null), lineColor = routeViewColour, colorOf = { DIRECTIONS.lineColor(it) ?: DEFAULT_ROUTE_LINE_COLOR })
        )
    }

    @Test
    fun `a ride with no alternative is not striped at all`() {
        assertEquals(emptyList<Int>(), drawableRide(routeColor = 0xFF008000.toInt()).stripeColors(DIRECTIONS))
    }

    @Test
    fun `routes drawn in one colour stripe once, and never in the colour the line already is`() {
        // A stripe carries no name, so two routes an agency publishes the same colour for are one stripe;
        // and a substitute matching the planned route would stripe the line with the colour it already is.
        // Both of these read as "the alternative is drawn here", when nothing is.
        val ride = drawableRide(
            routeColor = 0xFF008000.toInt(),
            substitutes = listOf(
                substitute("A", 0xFF0000FF.toInt()),
                substitute("B", 0xFF0000FF.toInt()),
                substitute("C", 0xFF008000.toInt())
            )
        )

        assertEquals(listOf(rideColor(0xFF0000FF.toInt())), ride.stripeColors(DIRECTIONS))
    }

    @Test
    fun `one builder draws the trip, and the views of it are reductions of what it draws`() {
        // #2246: there were two builders — this one without marks or stripes, and the directions
        // controller's own walk with both — so the parked trip could never draw what the read trip did,
        // and "does this view drop stripes?" had two answers. One builder now draws at full fidelity and
        // every view states what it takes away, which is the only shape in which they can be compared.
        val drawn = drawnItinerary(interchangeableRideItinerary(), DIRECTIONS, parseRouteColor = ::testHexColor)

        val ride = drawn.lines.single().line
        // Full fidelity: the ride the rider may board either route for is striped, and bulbed at the ends
        // where they get on and off.
        assertEquals(listOf(rideColor(testHexColor(ALTERNATIVE_ROUTE_COLOR)!!)), ride.stripeColors)
        assertEquals(RouteLineMark.BULB, ride.startMark)
        assertEquals(RouteLineMark.BULB, ride.endMark)
        // The legs come back beside the lines because the route labels are anchored on them.
        assertEquals(listOf(ride.color), drawn.legs.map { it.style.color })

        // And the ghost is that same line, reduced — not a second, quieter idea of the trip.
        val ghost = drawn.lines.map { it.line }.asPinnedTripGhost().single()
        assertEquals(ride.color, ghost.color)
        assertEquals(ride.points, ghost.points)
        assertEquals(emptyList<Int>(), ghost.stripeColors)
        assertEquals(RouteLineMark.NONE, ghost.startMark)
        assertEquals(RouteLineMark.NONE, ghost.endMark)
    }

    @Test
    fun `a leg with nothing drawable is left out, and does not shift the ones that are`() {
        // A leg that carried no shape draws no line, so a position in the drawn list is not a leg index —
        // which is the whole reason a line is paired with the index it came from.
        val itinerary = TripItinerary(
            legs = listOf(
                TripLeg(mode = TripMode.WALK),
                transitLeg(PLANNED_ROUTE_COLOR, listOf(GeoPoint(47.6, -122.3), GeoPoint(47.7, -122.4)))
            )
        )

        val drawn = drawnItinerary(itinerary, DIRECTIONS, parseRouteColor = ::testHexColor)

        assertEquals(listOf(1), drawn.lines.map { it.legIndex })
    }

    /** An itinerary of one ride, offered on a second route the rider may board in its place (#2010). */
    private fun interchangeableRideItinerary() = TripItinerary(
        startTime = ServerTime(0L),
        legs = listOf(
            transitLeg(
                routeColor = PLANNED_ROUTE_COLOR,
                points = listOf(GeoPoint(47.6, -122.3), GeoPoint(47.7, -122.4)),
                alternatives = listOf(
                    TripLegAlternative(
                        routeId = "route-2",
                        routeShortName = "2 Line",
                        routeColor = ALTERNATIVE_ROUTE_COLOR,
                        duration = 20.minutes,
                        fromStopId = "1_500",
                        toStopId = "1_501"
                    )
                )
            )
        )
    )

    private fun transitLeg(
        routeColor: String?,
        points: List<GeoPoint>,
        alternatives: List<TripLegAlternative> = emptyList()
    ) = TripLeg(
        mode = TripMode.RAIL,
        routeId = "route-1",
        routeShortName = "1 Line",
        routeColor = routeColor,
        duration = 20.minutes,
        startTime = ServerTime(0L),
        endTime = ServerTime(0L) + 20.minutes,
        from = TripPlace(name = "Board", stopId = "1_500"),
        to = TripPlace(name = "Alight", stopId = "1_501"),
        legGeometry = TripLegGeometry(encodePolyline(points), points.size),
        alternatives = alternatives
    )

    /** The colour this map draws a ride whose agency publishes [routeColor] in. */
    private fun rideColor(routeColor: Int) = itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor, DIRECTIONS).color

    /** A transit leg as the controller hands it over, with the routes offered in its place. */
    private fun drawableRide(routeColor: Int?, substitutes: List<ItinerarySubstitute> = emptyList()) = ItineraryDrawableLeg(
        index = 0,
        leg = TripLeg(mode = TripMode.RAIL, routeId = "route-1", routeShortName = "Line"),
        points = listOf(GeoPoint(47.6, -122.3), GeoPoint(47.7, -122.4)),
        style = itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor, DIRECTIONS),
        interchangeable = substitutes
    )

    private fun substitute(displayName: String, routeColor: Int?) = ItinerarySubstitute(
        InterchangeableRoute(
            routeId = "route-for-$displayName",
            displayName = displayName,
            // The colour reaches the styling already parsed, so the wire field plays no part here.
            routeColor = null,
            agencyId = null,
            agencyName = null,
            headsign = null
        ),
        routeColor
    )

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
                startMark = if (style.roundCaps) RouteLineMark.BULB else RouteLineMark.NONE,
                endMark = if (style.roundCaps) RouteLineMark.BULB else RouteLineMark.NONE
            )
        )
    }

    /** Stands in for `parseObaHexColor`, which needs `android.graphics`: the builder takes it as a lambda. */
    private fun testHexColor(hex: String?): Int? = hex?.removePrefix("#")?.toLongOrNull(16)?.toInt()?.let { 0xFF000000.toInt() or it }

    private companion object {
        // Two published GTFS colours, as they arrive on the wire.
        const val PLANNED_ROUTE_COLOR = "0072BC"

        const val ALTERNATIVE_ROUTE_COLOR = "00A94F"

        // The palette the directions view actually draws with, and the one every other view does. Most cases
        // here are about a leg's *shape* rather than its colour, and take the directions palette because that
        // is the only palette an itinerary leg is ever stroked with.
        val DIRECTIONS: RouteLinePalette = directionsRouteLinePalette(dark = false)

        val BASEMAP: RouteLinePalette = BASEMAP_ROUTE_LINE_PALETTE

        // A map-palette line of no particular hue, for the cases that are about the *tone* a case lands on —
        // which is a property of the theme and the weight, never of the line (see the case just below).
        val ANY_MAP_LINE: Hct = Hct.fromInt(mapRouteLineColor(hue = 250.0))

        // The re-tone clamps to each hue's own sRGB gamut limit, so a hue can shift a degree or two.
        const val HUE_TOLERANCE_DEGREES = 3.0

        // Chroma likewise clamps to the gamut: a hue that can't hold the full chroma at this tone lands
        // slightly under it. Wide enough to absorb that, far too narrow to hide a different policy.
        const val CHANNEL_TOLERANCE = 2.0

        // What the selected leg's case has to clear on the dark basemap to read as an edge on its line rather
        // than as a lighter shade of it (#2226): a tonal step of the dark end's order, and so little of the
        // line's own chroma left that no hue comes back tinted at all. The policy delivers 45 and 2.9, so
        // these are floors on it and not restatements of it — an outline's tone 90 fails both.
        const val MIN_SELECTION_CASE_TONE_STEP = 40.0
        const val MAX_SELECTION_CASE_CHROMA = 10.0
    }
}
