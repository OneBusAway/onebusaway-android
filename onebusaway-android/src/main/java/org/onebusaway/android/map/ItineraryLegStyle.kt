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

import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.map.layout.RouteBadgePath
import org.onebusaway.android.map.layout.RouteBadgeRequest
import org.onebusaway.android.map.layout.placeRouteBadges
import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.ITINERARY_STREET_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.RouteLineDash
import org.onebusaway.android.map.render.RouteLineWidthProfile
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.util.GeoPoint

/**
 * How one trip-plan itinerary leg is stroked on the directions map (#2041).
 *
 * Every leg used to be one of two colours — a single green for anything transit, `Color.GRAY` for
 * everything else — so a walk, an own-bike ride and a bikeshare ride were indistinguishable, and the
 * route colour the option card and the directions drawer were already showing never reached the map at
 * all. Here each leg's stroke is decided from its mode, and a ride keeps its own route's hue, rendered by
 * [mapRouteLineColor] at the same chroma and tone as every other route line on this map — so a leg reads
 * as one colour whether the rider looks at the map, the drawer beside it or the option card above.
 *
 * Pure and free of `android.graphics` (the colour science is Material's own JVM-side HCT), so
 * `ItineraryLegStyleTest` covers the whole table on the JVM. Parsing the wire hex is the one part that
 * isn't, so it happens in the caller and arrives here already an ARGB int.
 */
internal data class ItineraryLegStyle(
    val color: Int,
    val widthProfile: RouteLineWidthProfile,
    val dash: RouteLineDash,
    val directional: Boolean
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
 * kind, and for a ride whose agency publishes no colour).
 *
 * On-street legs are dashed and thinner than the ride they connect to, mirroring the directions
 * drawer, where a walk is a dashed spine and a ride a solid one. (The MapLibre renderer draws every
 * line solid, so there the distinction rests on colour and width alone.)
 *
 * They also drop the travel-direction chevrons, which a dashed stroke can't carry: the chevron texture
 * is stamped along the line, so the dash pattern chops it into fragments and the two read as one
 * confused broken line rather than as either. A ride keeps them — it's solid, and which way along the
 * corridor you're carried is worth saying.
 */
internal fun itineraryLegStyle(kind: ItineraryLegKind, routeColor: Int?): ItineraryLegStyle = when (kind) {
    ItineraryLegKind.TRANSIT -> ItineraryLegStyle(
        // The agency's own colour when it has a usable one; otherwise every ride shares the transit
        // anchor, as they all did before. Giving colourless rides distinct auto-assigned hues (the way
        // focused-stop adjacency does) is the rest of #2041, not this pass.
        color = mapRouteLineColorOrNull(routeColor) ?: anchorColor(TRANSIT_HUE_ANCHOR),
        widthProfile = ITINERARY_RIDE_WIDTH_PROFILE,
        dash = RouteLineDash.NONE,
        directional = true
    )

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

/**
 * The drawn itinerary composed around a leg focus (#2048): every leg the rider isn't looking at thinned
 * to the same faint context weight a route takes under its ridden segment, and the focused leg(s)
 * re-appended last so they draw at full weight on top. Focusing a leg therefore *recedes* the rest of
 * the trip rather than erasing it — the rider keeps where this leg sits in the whole journey, which is
 * exactly what a leg drawn alone can't say.
 *
 * [focusedLegIndices] is a set of leg indices rather than one index because a folded interline chain
 * (#2000) is several itinerary legs the rider reads — and taps — as a single ride.
 *
 * Returns the lines unchanged when nothing is focused (the itinerary overview), and when the focus names
 * no drawn leg (a leg that carried no geometry): there is nothing to raise, so nothing is lowered.
 */
internal fun List<ItineraryLegLine>.withLegFocus(focusedLegIndices: Set<Int>): List<RoutePolyline> {
    val (focused, rest) = partition { it.legIndex in focusedLegIndices }
    if (focused.isEmpty()) return map { it.line }
    return rest.map { it.line }.asDeemphasizedRouteUnderlay() + focused.map { it.line }
}

/**
 * One itinerary leg with geometry to draw: its index in the itinerary, the leg itself, its decoded
 * points, and the stroke its line and its label share.
 */
internal data class ItineraryDrawableLeg(
    val index: Int,
    val leg: TripLeg,
    val points: List<GeoPoint>,
    val style: ItineraryLegStyle
)

/**
 * One label per route ridden in a drawn itinerary (#2066), so a transit line on the directions map says
 * which route it is without the rider having to match it to the drawer beside it. Two legs of one route
 * — a stay-aboard interline, or a route the itinerary returns to — share a single label.
 */
internal fun itineraryRouteBadges(legs: List<ItineraryDrawableLeg>): List<RouteBadge> {
    val rides = legs.mapNotNull { drawable ->
        if (drawable.leg.mode?.isTransit != true) return@mapNotNull null
        // A route that names itself in no way at all has nothing to label the line with.
        val name = drawable.leg.routeDisplayLabel() ?: return@mapNotNull null
        // Grouped by the wire route id, or — for an OTP1 response, which names a route without
        // identifying it — by the name it displays. That key never leaves this grouping, so a name
        // standing in for an id can't reach anywhere that would treat it as one.
        Ride(drawable.leg.routeId ?: name, name, drawable)
    }.groupBy(Ride::identity)
    return placeRouteBadges(
        rides.map { (_, ridden) ->
            val ride = ridden.first()
            RouteBadgeRequest(
                routeShortName = ride.name,
                // Exactly the colour its own line is stroked with, so a label and its line can't disagree.
                color = ride.drawable.style.color,
                paths = ridden.map { RouteBadgePath(it.drawable.points) }
                // No tap target: see [RouteBadge.tap].
            )
        }
    )
}

private data class Ride(val identity: String, val name: String, val drawable: ItineraryDrawableLeg)

private fun street(hueAnchor: Int) = ItineraryLegStyle(
    color = anchorColor(hueAnchor),
    widthProfile = ITINERARY_STREET_WIDTH_PROFILE,
    dash = RouteLineDash.TRAIL,
    directional = false
)

/**
 * A mode's hue [anchor] rendered at the map's route chroma and tone, so a mode leg carries exactly the
 * weight a route line does. The elvis stands in for a `!!`: [mapRouteLineColorOrNull] declines only an
 * achromatic source, and every anchor below is chromatic — which `ItineraryLegStyleTest` holds to, by
 * asserting every leg kind draws a colour above the achromatic floor.
 */
private fun anchorColor(anchor: Int): Int = mapRouteLineColorOrNull(anchor) ?: anchor

// The mode hue anchors. Only each colour's *hue* survives — [mapRouteLineColor] supplies the chroma and
// tone — so these read as "walking is green", not as literal strokes, and tuning one means moving it
// around the hue circle. They're spread far apart, and away from the transit anchor, so no two modes of
// one itinerary read as the same thing.
private const val WALK_HUE_ANCHOR = 0xFF1D9914.toInt()
private const val BIKE_HUE_ANCHOR = 0xFF007E8F.toInt()

// Takes its hue from the bikeshare map layer's own colour (`R.color.layer_bikeshare_color`), so a
// bikeshare leg is drawn in the family of the dock markers at both its ends. A literal rather than a
// resource read: only the hue is wanted, and this file has no `Context` to resolve one with.
private const val BIKESHARE_HUE_ANCHOR = 0xFF3A4677.toInt()
private const val CAR_HUE_ANCHOR = 0xFF8A6D00.toInt()

// The fallback for a ride whose agency publishes no usable colour. It was OTP's green, which walking now
// owns — a colourless ride takes the terracotta walking vacated rather than sit a few degrees off it.
private const val TRANSIT_HUE_ANCHOR = 0xFFC4400F.toInt()
