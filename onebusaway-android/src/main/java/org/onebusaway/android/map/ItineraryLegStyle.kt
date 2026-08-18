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
import org.onebusaway.android.directions.model.Interlines
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.directions.model.substitutableRoutes
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.RouteBadgeRequest
import org.onebusaway.android.map.layout.placeRouteBadges
import org.onebusaway.android.map.render.BadgedRoute
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_STREET_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RouteBadgeTap
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineMark
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
 * Which stroke this leg draws. A bikeshare leg is a `BICYCLE` leg OTP flagged as ridden on a hired vehicle
 * ([TripLeg.rentedVehicle], the server's own `rentedBike`) — the same fact the option card's
 * [streetMode][org.onebusaway.android.ui.tripresults.streetMode] reads, so the line under a card can't be
 * a plain bike while the card's glyph says bikeshare. It used to be inferred from the leg *starting* at a
 * rental place, which asked where the docks were in order to answer what the rider is doing (#2159).
 */
internal fun TripLeg.legKind(): ItineraryLegKind = when {
    mode?.isTransit == true -> ItineraryLegKind.TRANSIT
    mode == TripMode.CAR -> ItineraryLegKind.CAR
    mode == TripMode.BICYCLE ->
        if (rentedVehicle) ItineraryLegKind.BIKESHARE else ItineraryLegKind.BIKE
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
 * selection; the rider's selected leg steps up to [RouteLineCase.SELECTION]. A mode leg has no
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
 * How one itinerary is drawn: every leg that has geometry, decoded and styled once, and the line that
 * strokes each of them.
 *
 * The legs come back beside the lines because the route labels are anchored on them
 * ([itineraryRouteBadges]), so a label cannot end up a different colour from the line it names.
 */
internal data class DrawnItinerary(
    val legs: List<ItineraryDrawableLeg>,
    val lines: List<ItineraryLegLine>
)

/**
 * Draw [itinerary] — **at full fidelity**: every leg's colour, weight, dash and case, a shared ride's
 * stripes (#2100), and the mark each end is finished with (#2084, #2127).
 *
 * The single answer to "how is this trip drawn", and deliberately the only one. Every view of a trip is
 * a *reduction* of what this returns — the parked trip's ghost ([asPinnedTripGhost], #2053), the journey
 * kept around a leg a rider drilled into ([asItineraryContext], #2048) — so each of them states what it
 * takes away from a line the rider has already seen. There used to be two builders instead: this one,
 * which left out marks and stripes, and the same walk again inside
 * [org.onebusaway.android.map.DirectionsMapController], which put them in. A builder that merely leaves
 * a feature out cannot be reduced *from* — it can only quietly differ, which is what let the ghost draw
 * a trip that could never carry what the drawn one did, and made "does this view drop stripes?" a
 * question with two answers.
 *
 * [parseRouteColor] turns a wire hex into an ARGB int. It is injected because that is the one part that
 * needs `android.graphics`, which keeps everything here JVM-pure (see the file header) — and it is
 * spent on the leg's own route and on every route offered in its place alike.
 */
internal fun drawnItinerary(
    itinerary: TripItinerary,
    palette: RouteLinePalette,
    parseRouteColor: (String?) -> Int?
): DrawnItinerary {
    val legs = itinerary.legs
    // The routes the *drawer* offers for each leg, not the raw interchangeable set: `substitutableRoutes`
    // is already narrowed by what the rest of the plan needs of this leg (a leg of a stay-aboard
    // interline admits no substitute at all, #2000 x #2010), so the label on a line and the badge in the
    // drawer beside it name one ride the same way.
    //
    // Its alignment to the legs is stated rather than defended against, in the same terms
    // [org.onebusaway.android.ui.tripresults.ModeSymbols.forLegs] states it for the same list: a short
    // list read defensively would quietly label a ride with fewer routes than it can be taken on — the
    // rider is told to wait for the 1 Line when the 2 Line would also do — and nothing downstream could
    // tell that from a leg that genuinely has no alternatives. Misalignment is a bug in the analysis, so
    // say so where it is read.
    val substitutes = itinerary.substitutableRoutes()
    require(substitutes.size == legs.size) {
        "substitutable routes must be index-aligned to legs (${substitutes.size} vs ${legs.size})"
    }
    // A leg with fewer than two points draws nothing, which is the whole of what "has geometry" means
    // here — `decodedPoints` already answers empty for a geometry the wire declared no length for.
    val drawable = legs.mapIndexedNotNull { legIndex, leg ->
        val points = leg.legGeometry?.decodedPoints().orEmpty()
        if (points.size < 2) return@mapIndexedNotNull null
        ItineraryDrawableLeg(
            legIndex,
            leg,
            points,
            itineraryLegStyle(leg.legKind(), parseRouteColor(leg.routeColor), palette),
            substitutes[legIndex].map { route -> ItinerarySubstitute(route, parseRouteColor(route.routeColor)) }
        )
    }
    // Every leg's points run in travel order, but no itinerary leg stamps chevrons any more — see
    // [itineraryLegStyle], which also decides the hairline case a ride wears.
    val caps = itineraryLegCaps(legs)
    return DrawnItinerary(
        legs = drawable,
        lines = drawable.map { ItineraryLegLine(it.index, it.line(caps[it.index], palette)) }
    )
}

/** The line one drawable leg is stroked with, finished at each end by its own [caps]. */
private fun ItineraryDrawableLeg.line(caps: ItineraryLegCaps, palette: RouteLinePalette) = RoutePolyline(
    style.color,
    points,
    // The other routes this ride may be taken on, striped through it (#2100) so the line says what its
    // badge does; empty for every ride offering no alternative.
    stripeColors = stripeColors(palette),
    widthProfile = style.widthProfile,
    dash = style.dash,
    case = style.case,
    // A cutover (#2127) and a bulb are alternatives, not additions — the cut goes precisely where the
    // ride runs on and the bulb is therefore withheld, which is what this `when` says and what a pair of
    // booleans left each renderer to decide for itself.
    startMark = when {
        caps.startSeam -> RouteLineMark.INTERLINE_CUT
        style.roundCaps && caps.start -> RouteLineMark.BULB
        else -> RouteLineMark.NONE
    },
    endMark = if (style.roundCaps && caps.end) RouteLineMark.BULB else RouteLineMark.NONE
)

/**
 * One drawn itinerary leg, paired with its index in the itinerary. The pairing is what lets a focus be
 * expressed in *leg* terms: a leg that carries no geometry draws no line, so a position in the drawn
 * list is not a leg index.
 */
internal data class ItineraryLegLine(val legIndex: Int, val line: RoutePolyline)

/**
 * Bulb-bearing ends of one itinerary leg, plus whether it begins at an interline **cutover** (#2127).
 *
 * An interline continuation has no visible internal seam — no bulbs, because the rider never gets off — but
 * a continuation onto a *different* route is cut across at that join ([RouteLineMark.INTERLINE_CUT]). The two
 * are one decision made in one place: a bulb pair says "alight here, board there", so the join a rider
 * stays seated through has to be marked as something else or not at all, and until now it was not at all.
 * [startSeam] therefore never coincides with [start] — a cut goes exactly where the bulb is withheld.
 */
internal data class ItineraryLegCaps(val start: Boolean, val end: Boolean, val startSeam: Boolean = false)

/**
 * How every leg of [legs] is finished at each end, index-aligned to it.
 *
 * Whole-itinerary rather than per leg because that is what the questions read: which legs a ride continues
 * through, and which of those joins change route. The cutovers are exactly the transitions
 * [Interlines.chains] reports, so the map cuts the line in the same places the drawer tells the rider to stay
 * on board, and nowhere else — a self-interline (the same route reversing onto itself) contributes none,
 * since nothing about the ride changed there.
 */
internal fun itineraryLegCaps(legs: List<TripLeg>): List<ItineraryLegCaps> {
    val seamLegs = Interlines.chains(legs).flatMapTo(mutableSetOf()) { it.transitionLegIndices }
    return legs.mapIndexed { index, leg ->
        val transit = leg.mode?.isTransit == true
        val continuesPrevious = transit &&
            leg.interlineWithPreviousLeg &&
            legs.getOrNull(index - 1)?.mode?.isTransit == true
        val next = legs.getOrNull(index + 1)
        val continuesIntoNext = transit && next?.mode?.isTransit == true && next.interlineWithPreviousLeg
        ItineraryLegCaps(
            start = !continuesPrevious,
            end = !continuesIntoNext,
            startSeam = index in seamLegs
        )
    }
}

/**
 * The drawn itinerary composed around a leg focus (#2048): the focused leg(s) cased ([RouteLineCase.SELECTION]) and
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
    return rest.map { it.line } + focused.map { it.line.copy(case = RouteLineCase.SELECTION) }
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
 *
 * Each label is a tap target for its own ride (#2101), carrying the legs it names so the tap lands on the
 * same focus the drawer's row for that ride does. It deliberately does *not* lead to the route's own map,
 * which is what a label on this view must never do: the rider is reading a trip, and a stray tap must not
 * trade the whole itinerary for one route (see [RouteBadgeTap]).
 *
 * These follow the zoom (#2102), receding with the lines they name rather than holding a fixed pixel size
 * — on the map's one label schedule, which a focused stop's adjacency labels also take (#2195); see
 * [RouteBadge.scale].
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
                paths = ridden.map { RouteBadgePath(it.drawable.points) },
                tap = RouteBadgeTap.FocusItineraryRide(ridden.mapTo(mutableSetOf()) { it.drawable.index })
            )
        }
    )
}

/**
 * The colours a ride's line is striped with (#2100): the routes the rider may board in its place, each in
 * the colour this map gives *that* route's line, and never the planned route's own colour, which the line
 * is already stroked in ([RoutePolyline.stripeColors] cycles through that first).
 *
 * In the label's own order among themselves ([inInterchangeableOrder]), the planned route leading because the
 * line is stroked in it — so the colours a rider reads along the line are the colours of the badge sitting on
 * it, arrived at from the route the plan picked. Deduplicated by **colour**, rather than by the name a badge
 * dedupes on ([itineraryRouteBadges]): a stripe carries no name, so two routes an agency publishes one colour for
 * — including the two that publish none at all, and share the colourless hue — have one stripe between them
 * rather than two the rider can't tell apart. That is also why a substitute matching the planned route's
 * colour drops out entirely: it would stripe the line with the colour it already is.
 */
internal fun ItineraryDrawableLeg.stripeColors(palette: RouteLinePalette): List<Int> = rideStripeColors(
    interchangeable.inInterchangeableOrder { it.route.displayName }.map { it.routeColor },
    lineColor = style.color,
    palette = palette
)

/**
 * The stripe rule itself, over already-ordered [alternatives] — each an agency's raw published colour, or
 * null where it publishes none — and the [lineColor] the ride is already stroked in.
 *
 * Split out from [stripeColors] so the *drilled-into* ride can stripe by the same rule (#2241). That ride
 * is drawn by a route session, from [org.onebusaway.android.map.RiddenSpan]s rather than from the drawable
 * legs here, and it used to be drawn as a plain line: tapping a shared ride to look at it closer was
 * exactly when the map stopped saying it was two routes. One rule, spent from both places, is what makes
 * the line the rider taps and the line they get back the same line.
 */
internal fun rideStripeColors(
    alternatives: List<Int?>,
    lineColor: Int?,
    palette: RouteLinePalette
): List<Int> = alternatives
    .map { riddenLineColor(it, palette) }
    .distinct()
    .filterNot { it == lineColor }

/** The colour this map draws a ride on [routeColor]'s route in, were the rider to drill into it (#2063). */
internal fun riddenLineColor(routeColor: Int?, palette: RouteLinePalette): Int = itineraryLegStyle(ItineraryLegKind.TRANSIT, routeColor, palette).color

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
                BadgedRoute(substitute.route.displayName, riddenLineColor(substitute.routeColor, palette))
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
