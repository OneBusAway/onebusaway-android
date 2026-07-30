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
package org.onebusaway.android.map

import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.RouteBadgeRequest
import org.onebusaway.android.map.layout.placeRouteBadges
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_STREET_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineWidthProfile
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.COLOURLESS_RIDE_HUE_ANCHOR
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.inInterchangeableOrder
import org.onebusaway.android.util.riddenRouteHue

/**
 * How one trip-plan itinerary leg is stroked on the directions map (#2041).
 *
 * Every leg used to be one of two colours — a single green for anything transit, `Color.GRAY` for
 * everything else — so a walk, an own-bike ride and a bikeshare ride were indistinguishable, and the
 * route colour the option card and the directions drawer were already showing never reached the map at
 * all. Here each leg's stroke is decided from its mode, and a ride keeps its own route's hue, rendered by
 * the [RouteLinePalette] the caller hands in — [directionsRouteLinePalette] for every line the directions
 * view draws, which is the badge's own colour, so a leg is literally the same colour as the badge naming it
 * in the drawer beside it and on the option card above.
 *
 * Pure and free of `android.graphics` (the colour science is Material's own JVM-side HCT), so
 * `ItineraryLegStyleTest` covers the whole table on the JVM. Parsing the wire hex is the one part that
 * isn't, so it happens in the caller and arrives here already an ARGB int.
 */
internal data class ItineraryLegStyle(
    val color: Int,
    val widthProfile: RouteLineWidthProfile,
    val dash: RouteLineDash,
    val roundCaps: Boolean,
    val case: RouteLineCase
)

/**
 * The stroke vocabulary of an itinerary, narrowed from [TripMode] to the distinctions a rider reads off
 * the map. Notably [BIKESHARE] splits off [BIKE]: both are `TripMode.BICYCLE`, and telling "walk to the
 * dock, then ride" from "ride your own bike the whole way" is precisely what the flat grey hid.
 */
internal enum class ItineraryLegKind { WALK, BIKE, BIKESHARE, CAR, TRANSIT }

/**
 * Which stroke this leg draws. A bikeshare leg is a `BICYCLE` leg that *starts* at a rental dock, which is
 * how OTP models one: walk to the dock, ride, dock again. It reads only the near end deliberately — this
 * asks what the rider is doing for the whole leg, not where its docks are, which is
 * [DirectionsMapController.bikeStationIdsFromItinerary]'s question (it reads both ends, because a ride has
 * a station to show at each).
 */
internal fun TripLeg.legKind(): ItineraryLegKind = when {
    mode?.isTransit == true -> ItineraryLegKind.TRANSIT
    mode == TripMode.CAR -> ItineraryLegKind.CAR
    mode == TripMode.BICYCLE ->
        if (from.vertexType == TripVertexType.BIKESHARE) ItineraryLegKind.BIKESHARE else ItineraryLegKind.BIKE
    // Everything else walks, matching how the drawer's timeline classifies a leg it has no verb for.
    else -> ItineraryLegKind.WALK
}

/**
 * The stroke for a [kind] leg, given its already-parsed GTFS [routeColor] (null for every non-transit
 * kind, and for a ride whose agency publishes no colour), rendered by [palette].
 *
 * [palette] reaches the **ride** only. A ride is faded to the badge's own colour because it is read against
 * the badge that names it (see [directionsRouteLinePalette]); an on-street leg has no badge — the drawer
 * marks it with a mode glyph, not a route roundel — so there is no parity to keep, and nothing to buy for
 * the contrast it would cost. Every mode leg therefore keeps [BASEMAP_ROUTE_LINE_PALETTE], which is what
 * every other line on this map is drawn with.
 *
 * So the palette is now one more way the two kinds of leg differ, alongside width, dash and chevrons: a
 * ride carries its route's identity and is toned to match how that identity is written elsewhere, while a
 * mode leg only has to read as "you walk here" against the basemap.
 *
 * On-street legs are dashed and thinner than the ride they connect to, mirroring the directions
 * drawer, where a walk is a dashed spine and a ride a solid one. (The MapLibre renderer draws every
 * line solid, so there the distinction rests on colour and width alone.)
 *
 * **No itinerary leg stamps travel-direction chevrons**, which is why this table names no `directional` at
 * all — [RoutePolyline] defaults it off and nothing here turns it on. A dashed on-street stroke never could
 * carry them: the chevron texture is stamped along the line, so the dash chops it into fragments and the two
 * read as one confused broken line rather than as either. A ride dropped them with the badge palette — it is
 * drawn in a faded, badge-toned colour now and wears a hairline case to hold that colour off the basemap, and
 * an arrow texture under a hairline edge is noise rather than direction. Which way the rider is carried is
 * read from the drawer's ordered rows and from the leg's endpoint bulbs instead.
 *
 * A ride also carries [RouteLineCase.OUTLINE] for that reason — every ride, so the edge says nothing about
 * selection; the rider's selected leg steps up to [RouteLineCase.SELECTION] ([withCase]). A mode leg has no
 * case, as it has no fading to compensate for.
 */
internal fun itineraryLegStyle(
    kind: ItineraryLegKind,
    routeColor: Int?,
    palette: RouteLinePalette
): ItineraryLegStyle = when (kind) {
    ItineraryLegKind.TRANSIT -> ItineraryLegStyle(
        color = anchorColor(riddenRouteHue(routeColor), palette),
        widthProfile = ITINERARY_RIDE_WIDTH_PROFILE,
        dash = RouteLineDash.NONE,
        roundCaps = true,
        case = RouteLineCase.OUTLINE
    )

    // Deliberately not [palette]: see the note above — a mode leg has no badge to match, so it stays on
    // the map's own rendering rather than being faded for a parity that doesn't exist.
    ItineraryLegKind.WALK -> street(WALK_HUE_ANCHOR)
    ItineraryLegKind.BIKE -> street(BIKE_HUE_ANCHOR)
    ItineraryLegKind.BIKESHARE -> street(BIKESHARE_HUE_ANCHOR)
    ItineraryLegKind.CAR -> street(CAR_HUE_ANCHOR)
}

/**
 * One drawn itinerary leg, paired with its index in the itinerary. The pairing is what lets a focus be
 * expressed in *leg* terms: a leg that carries no geometry draws no line, so a position in the drawn
 * list is not a leg index.
 */
internal data class ItineraryLegLine(val legIndex: Int, val line: RoutePolyline)

/** Bulb-bearing ends of one itinerary leg; an interline continuation has no visible internal seam. */
internal data class ItineraryLegCaps(val start: Boolean, val end: Boolean)

internal fun itineraryLegCaps(legs: List<TripLeg>, index: Int): ItineraryLegCaps {
    val leg = legs[index]
    val transit = leg.mode?.isTransit == true
    val continuesPrevious = transit &&
        leg.interlineWithPreviousLeg &&
        legs.getOrNull(index - 1)?.mode?.isTransit == true
    val next = legs.getOrNull(index + 1)
    val continuesIntoNext = transit && next?.mode?.isTransit == true && next.interlineWithPreviousLeg
    return ItineraryLegCaps(start = !continuesPrevious, end = !continuesIntoNext)
}

/**
 * The drawn itinerary composed around a leg focus (#2048): the focused leg(s) cased ([withCase]) and
 * re-appended last so they draw on top. Focusing a leg therefore *marks* it within the whole journey rather
 * than erasing — or restyling — the rest of the trip, so the rider keeps where this leg sits in the journey,
 * which is exactly what a leg drawn alone can't say.
 *
 * The rest of the trip used to be thinned to a faint context weight instead, which said "selected" with the
 * one channel already spoken for: every other leg's width says what *kind* of leg it is (a ride, an on-street
 * hop), so overloading width left neither reading clearly, and the thinned legs landed within a hair of the
 * upstream route line drawn beside them (#2082). Width now means only kind; the case means selected.
 *
 * [focusedLegIndices] is a set of leg indices rather than one index because a folded interline chain
 * (#2000) is several itinerary legs the rider reads — and taps — as a single ride.
 *
 * Returns the lines unchanged when nothing is focused (the itinerary overview), and when the focus names
 * no drawn leg (a leg that carried no geometry): there is nothing to mark.
 */
internal fun List<ItineraryLegLine>.withLegFocus(focusedLegIndices: Set<Int>): List<RoutePolyline> {
    val (focused, rest) = partition { it.legIndex in focusedLegIndices }
    if (focused.isEmpty()) return map { it.line }
    return rest.map { it.line } + focused.map { it.line.withCase() }
}

/**
 * One itinerary leg with geometry to draw: its index in the itinerary, the leg itself, its decoded
 * points, the stroke its line and its label share, and the other routes the rider may board in its place
 * (empty for all but an interchangeable ride).
 */
internal data class ItineraryDrawableLeg(
    val index: Int,
    val leg: TripLeg,
    val points: List<GeoPoint>,
    val style: ItineraryLegStyle,
    val interchangeable: List<ItinerarySubstitute> = emptyList()
)

/**
 * A route the rider may board in place of a leg's planned one (#2010), with its GTFS colour *already
 * parsed* — the one thing the caller has to do for it, since parsing the wire hex needs
 * `android.graphics` and everything here stays JVM-pure (see the file header). The candidate itself is
 * carried whole rather than reduced to a name, so a label can group on the route **id** OTP gave it:
 * two routes may publish the same short name, and a name is only ever an identity of last resort
 * ([itineraryRouteBadges]).
 */
internal data class ItinerarySubstitute(val route: InterchangeableRoute, val routeColor: Int?)

/**
 * One label per route ridden in a drawn itinerary (#2066), so a transit line on the directions map says
 * which route it is without the rider having to match it to the drawer beside it. Two legs of one route
 * — a stay-aboard interline, or a route the itinerary returns to — share a single label.
 *
 * A ride the rider may board any of several routes for is labelled with all of them, stacked (#2083):
 * interchangeability is a fact about the ride, not about which route the planner picked, so the label has
 * to say the same thing as the drawer's joined badge beside it (`legBadge`) — the same routes, in the same
 * order ([inInterchangeableOrder]) — rather than naming one route on a line the rider is being told to
 * board either of two. A leg with no alternatives is labelled with its planned route alone, which is what
 * an OTP1 plan (no candidates at all) yields for every leg.
 */
internal fun itineraryRouteBadges(
    legs: List<ItineraryDrawableLeg>,
    palette: RouteLinePalette
): List<RouteBadge> {
    val rides = legs.mapNotNull { drawable ->
        if (drawable.leg.mode?.isTransit != true) return@mapNotNull null
        // A route that names itself in no way at all has nothing to label the line with.
        val name = drawable.leg.routeDisplayLabel() ?: return@mapNotNull null
        // Grouped by the wire route id, or — for an OTP1 response, which names a route without
        // identifying it — by the name it displays. That key never leaves this grouping, so a name
        // standing in for an id can't reach anywhere that would treat it as one.
        //
        // The alternatives are part of the identity, not just cargo: two legs of one route can be offered
        // different ones (the corridor they share ends), and a single label carrying the union would claim
        // on both segments what is only true of one. When they agree — the ordinary case, and every leg of
        // an interline chain, which is offered none at all — the legs still share one label. They key by
        // route id, which a candidate always has, so two alternatives that merely read alike stay apart.
        val identity = RideIdentity(
            drawable.leg.routeId ?: name,
            drawable.interchangeable.map { it.route.routeId }.toSet()
        )
        Ride(identity, name, drawable)
    }.groupBy(Ride::identity)
    return placeRouteBadges(
        rides.map { (_, ridden) ->
            val ride = ridden.first()
            RouteBadgeRequest(
                routes = ride.badgedRoutes(palette),
                paths = ridden.map { RouteBadgePath(it.drawable.points) }
                // No tap target: see [RouteBadge.tap].
            )
        }
    )
}

private data class RideIdentity(val route: String, val interchangeableRouteIds: Set<String>)

private data class Ride(val identity: RideIdentity, val name: String, val drawable: ItineraryDrawableLeg) {
    /**
     * The routes this label names: the planned one in exactly the colour its own line is stroked with — so
     * a label and its line can't disagree — joined by whatever is interchangeable with it, each named the
     * way the drawer names it and drawn in the colour this map gives that route's line, which is the colour
     * it actually takes when the rider drills into the leg and the whole corridor is drawn (#2063).
     */
    fun badgedRoutes(palette: RouteLinePalette): List<BadgedRoute> = (
        listOf(BadgedRoute(name, drawable.style.color)) +
            drawable.interchangeable.map { substitute ->
                BadgedRoute(
                    substitute.route.displayName,
                    itineraryLegStyle(ItineraryLegKind.TRANSIT, substitute.routeColor, palette).color
                )
            }
        ).inInterchangeableOrder(BadgedRoute::routeShortName)
}

private fun street(hueAnchor: Int) = ItineraryLegStyle(
    color = anchorColor(hueAnchor, BASEMAP_ROUTE_LINE_PALETTE),
    widthProfile = ITINERARY_STREET_WIDTH_PROFILE,
    dash = RouteLineDash.TRAIL,
    roundCaps = false,
    case = RouteLineCase.NONE
)

/**
 * A mode's hue [anchor] rendered by [palette] exactly as a route's own colour is, so a mode leg carries
 * exactly the weight a route line does. The elvis stands in for a `!!`: a palette declines only an
 * achromatic source, and every anchor below is chromatic — which `ItineraryLegStyleTest` holds to, by
 * asserting every leg kind draws a colour above the achromatic floor.
 */
private fun anchorColor(anchor: Int, palette: RouteLinePalette): Int = palette.lineColor(anchor) ?: anchor

// The mode hue anchors. Only each colour's *hue* survives — [BASEMAP_ROUTE_LINE_PALETTE] supplies the
// chroma and tone — so these read as "walking is green", not as literal strokes, and tuning one means moving
// it around the hue circle. They're spread far apart, and away from the transit anchor, so no two modes of
// one itinerary read as the same thing.
private const val WALK_HUE_ANCHOR = 0xFF1D9914.toInt()
private const val BIKE_HUE_ANCHOR = 0xFF007E8F.toInt()

// Takes its hue from the bikeshare map layer's own colour (`R.color.layer_bikeshare_color`), so a
// bikeshare leg is drawn in the family of the dock markers at both its ends. A literal rather than a
// resource read: only the hue is wanted, and this file has no `Context` to resolve one with.
private const val BIKESHARE_HUE_ANCHOR = 0xFF3A4677.toInt()
private const val CAR_HUE_ANCHOR = 0xFF8A6D00.toInt()
