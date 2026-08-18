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

import org.onebusaway.android.map.render.ITINERARY_RIDE_WIDTH_PROFILE
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.map.render.RouteLineMark
import org.onebusaway.android.map.render.RoutePolyline
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.haversineDistance

/**
 * Presenting a trip-plan leg's **ridden segment** over its route in route focus: draw the route's approach
 * to the boarding point, the segment cased on top, keep only the segment's stops, and pick which colour the
 * segment is drawn from ([riddenSpanColorSource] — the source, not the rendering, which stays with the
 * caller's palette). Pure, over flavor-neutral [GeoPoint]/[RoutePolyline]/[ObaStop] (like
 * [RouteViewGeometry] / [projectStopsOntoPolylines]), so it stays JVM-testable and out of
 * [RouteMapController]'s state plumbing.
 */

/** How close a stop must sit to the ridden path to count as "on the segment" — well below transit stop
 *  spacing, so the nearest off-segment stop is never wrongly included. */
const val SEGMENT_STOP_TOLERANCE_METERS = 50.0

/** How close a board/alight anchor must sit to a direction shape variant to count as travelling that
 *  variant ([upstreamTo]). Same 50 m drift class as [SEGMENT_STOP_TOLERANCE_METERS],
 *  but its own knob: retuning stop inclusion must not silently retune variant applicability. */
const val VARIANT_MATCH_TOLERANCE_METERS = 50.0

/** A ridden segment worth drawing/framing: a polyline needs at least two points. Below that it's the
 *  "no segment" case — plain route focus (whole route drawn/framed, all stops kept). */
internal fun List<GeoPoint>.isDrawableSegment() = size >= 2

/**
 * One route's share of a ride the rider drilled into — the board→alight span of a single itinerary leg.
 *
 * A ride is a list of these rather than one polyline because a stay-aboard interline (#2000) is one ride on
 * *several* routes: the vehicle carries on but its route changes underneath the rider. Drawn as a single
 * line, such a ride had to pick one route's colour for the whole thing and had no interior to mark, so the
 * cutover the itinerary map shows (#2127) vanished the moment the rider tapped in to look closer. Split, each
 * span takes its own route's colour and the seam is an end again — which is where a [RouteLineMark] goes.
 *
 * [routeId] is the OBA route id the span is ridden as (null when it couldn't be resolved), used to colour it;
 * [startsCutover] is true when the vehicle *changed route* onto this span, straight off the same
 * `Interlines.chains` transitions the drawer and the itinerary map read, so all three mark the same joins. A
 * self-interline — one route reversing onto itself — is a span boundary with no cutover, exactly as it is a
 * seam the drawer announces nothing at.
 *
 * [plannedColor] is the GTFS colour the *plan* published for that route, already parsed — the same source
 * the itinerary's own line for this leg was styled from — carried along so the span can be drawn before
 * [routeId]'s route has loaded (see [riddenSpanColorSource]). Null when the plan published none.
 *
 * [interchangeableColors] are the parsed GTFS colours of the routes the rider may board *in place of* this
 * one (#2010), in the order the ride's badge names them, and they are what keeps the ride striped once the
 * rider drills into it (#2100/#2241). Carried on the span for the same reason [plannedColor] is: the fact
 * belongs to the plan, and the route session the ride is drawn in has no other way to learn it. Empty for a
 * ride with no alternative — and so for every leg of a stay-aboard interline, which admits none.
 */
data class RiddenSpan(
    val points: List<GeoPoint>,
    val routeId: String? = null,
    val plannedColor: Int? = null,
    val startsCutover: Boolean = false,
    val interchangeableColors: List<Int?> = emptyList()
)

/**
 * What a ridden span's own route load has landed with: the colour that route publishes, which may be none
 * ([publishedColor] null — an agency that states no colour, or a response that carried no route to state
 * one). Wrapping it is what lets `null` *itself* mean "hasn't landed": the two are opposite answers, and a
 * bare `Int?` folds them into one value — see [riddenSpanColorSource], whose whole rule is telling them
 * apart.
 */
@JvmInline
internal value class LoadedSpanRoute(val publishedColor: Int?)

/**
 * The colour a ridden span is drawn from, for a palette to render: its own route's published colour once
 * that route's load has landed ([loaded]), else the colour the plan gave it ([RiddenSpan.plannedColor]).
 * Null when neither publishes one — a colour left unstated, which the renderer resolves to its default,
 * exactly as it does for a route line whose route publishes nothing usable.
 *
 * The plan answers for the load window, which is a network round trip the rider spends looking at the map
 * (#2186). Nothing answered for it before: the span was drawn in the *shown route's* colour, which until
 * the load lands is the renderer's `DEFAULT_ROUTE_LINE_COLOR` — so tapping a leg flashed the ride pure blue
 * before it settled into its route's colour. The plan already published that colour; it just wasn't asked.
 *
 * A landed load then answers alone, its own missing colour included, rather than the plan filling in for it:
 * the corridor beneath the span is drawn from that same load (`directionPolylines`), which states no colour
 * for it either — so both reach the renderer's default together, where a span that kept a planned colour
 * would be a line its own approach couldn't match. That is why the load is passed as [LoadedSpanRoute] and
 * not as the colour it carries: "hasn't landed" and "landed, publishes nothing" take opposite branches here,
 * so they must stay distinguishable all the way from the caller, including for a load that landed with no
 * route in it at all.
 *
 * A span whose [RiddenSpan.routeId] never resolved is handed no load at all — not the ride's shown route,
 * which is not what it is ridden as — so the plan answers for it permanently rather than for a window, as it
 * does for a route whose load failed. The caller decides what a span with neither is (see
 * [RouteMapController.spanColor]); here it is the same "nothing published a colour" null as any other.
 */
internal fun riddenSpanColorSource(span: RiddenSpan, loaded: LoadedSpanRoute?): Int? = if (loaded == null) span.plannedColor else loaded.publishedColor

/** The whole ride as one path, for the questions that are about the ride and not about its routes: where
 *  the rider boards and alights, what to frame, which stops are on it. */
internal fun List<RiddenSpan>.riddenPath(): List<GeoPoint> = flatMap { it.points }

/** Whether [riddenPath] would be drawable, answered without building it — the per-frame vehicle sampler asks
 *  this once per route per frame, and only wants the emptiness. */
internal fun List<RiddenSpan>.isDrawableRide(): Boolean = sumOf { it.points.size } >= 2

/**
 * Compose the route's polylines when [spans] are highlighted: the route [base] upstream of the boarding
 * point drawn as the selected line's approach, then the rider's [itineraryContext], then the ridden span(s),
 * each in the colour [colorOf] gives it and striped with the colours [stripeColorsOf] gives it. This order
 * makes the visual hierarchy match the semantics: where the vehicle comes from, the committed journey, the
 * current leg. Without a drawable span, retains the ordinary plain-route ordering: itinerary context beneath
 * [base], with no approach restyle or selected overlay.
 *
 * **The ride is the leg the rider tapped, drawn again — not a route line that happens to share its shape.**
 * It keeps [ITINERARY_RIDE_WIDTH_PROFILE], the very weight it had as a leg of the itinerary; it keeps the
 * stripes of a ride the rider may board either route for (#2100), which drilling in used to erase, so that
 * looking closer at a shared ride stopped saying it was shared (#2241); and it keeps the marks its ends
 * carried there. It says it is the *selected* one with a case rather than by out-widening its surroundings
 * (#2082). Its approach carries the same case, so the two read as one route line stepping down where the
 * rider boards. Neither carries direction chevrons (#2129): like every other itinerary line, the ride is
 * marked selected by its case and weight alone.
 *
 * The end marks follow the same rule the itinerary map's own caps do ([itineraryLegCaps]), stated here over
 * span position because a ride's spans *are* its legs: a bulb where the rider gets on and off, an
 * [RouteLineMark.INTERLINE_CUT] where the vehicle changed route under them ([RiddenSpan.startsCutover]), and
 * nothing at a seam they sit through. The bulbs are why the ride reads as a ride here rather than as a piece
 * of corridor, and the cut is why drilling into an interlined ride shows the rider *more* about it rather
 * than losing the one thing that said the route changes mid-ride.
 *
 * [stripeColorsOf] is a lambda for the same reason [colorOf] is: this file picks which colours a ride is
 * drawn *from*, and the rendering of them stays with the caller's palette (see the file header).
 */
internal fun routePolylinesWithSegment(
    base: List<RoutePolyline>,
    spans: List<RiddenSpan>,
    colorOf: (RiddenSpan) -> Int?,
    itineraryContext: List<RoutePolyline> = emptyList(),
    stripeColorsOf: (RiddenSpan) -> List<Int> = { emptyList() }
): List<RoutePolyline> {
    val ridden = spans.filter { it.points.isDrawableSegment() }
    val overlay = ridden.mapIndexed { index, span ->
        RoutePolyline(
            color = colorOf(span),
            points = span.points,
            stripeColors = stripeColorsOf(span),
            widthProfile = ITINERARY_RIDE_WIDTH_PROFILE,
            case = RouteLineCase.SELECTION,
            startMark = when {
                span.startsCutover -> RouteLineMark.INTERLINE_CUT
                index == 0 -> RouteLineMark.BULB
                else -> RouteLineMark.NONE
            },
            endMark = if (index == ridden.lastIndex) RouteLineMark.BULB else RouteLineMark.NONE
        )
    }
    if (overlay.isEmpty()) return itineraryContext + base
    return base.asSelectedRouteApproach() + itineraryContext + overlay
}

/**
 * Keep a direction's geometry only through [anchor], the boarding point. Each receiver entry is an
 * independent shape **variant** of the direction — stops-for-route supplies one complete travel-ordered
 * polyline per distinct trip shape (see `RouteMap.polylinesByDirection`), never consecutive fragments of
 * one line (#2104). Every variant the boarding point sits on is clipped at its own boarding projection;
 * a variant that never touches the boarding point isn't an approach to it and is dropped. When the
 * boarding point is off every variant (a leg-geometry mismatch), the single nearest variant is clipped
 * so the approach still draws.
 *
 * Variants that diverge only *after* the boarding point clip to the same approach, so the result is
 * de-duplicated: the shared trunk is drawn (and cased) once, not once per variant. That only catches
 * exactly-equal geometry — variants encoding the same street with different vertex sampling stay
 * distinct and still draw over each other.
 */
internal fun List<RoutePolyline>.upstreamTo(anchor: GeoPoint?): List<RoutePolyline> {
    anchor ?: return this
    val projections = variantProjections(anchor)
    return projections.travelledVariants()
        .ifEmpty { listOfNotNull(projections.minByOrNull { it.projection.distanceToPoint }) }
        .mapNotNull { (line, path, projection) ->
            path.subPolyline(0.0, projection.distanceAlong)
                ?.takeIf { it.isDrawableSegment() && it.first() != it.last() }
                ?.let { line.copy(points = it) }
        }
        .distinct()
}

private data class VariantProjection(
    val line: RoutePolyline,
    val path: Polyline,
    val projection: Polyline.Projection
)

/** Every variant paired with its nearest-[anchor] projection (a variant with no geometry drops out).
 *  Built once per call so the O(n) [Polyline] construction is shared by the tolerance filter, the
 *  callers' fallbacks, and the clip/bound they produce. */
private fun List<RoutePolyline>.variantProjections(anchor: GeoPoint): List<VariantProjection> = mapNotNull { line ->
    val path = Polyline(line.points)
    path.nearestProjection(anchor.latitude, anchor.longitude)
        ?.let { VariantProjection(line, path, it) }
}

/** The variants the anchor actually travels: those it sits on within [VARIANT_MATCH_TOLERANCE_METERS]. */
private fun List<VariantProjection>.travelledVariants(): List<VariantProjection> = filter { it.projection.distanceToPoint <= VARIANT_MATCH_TOLERANCE_METERS }

/**
 * Keep only the stops within [toleranceMeters] of [segment]'s path — the ride's stops, not the whole
 * route's. All stops are kept when there's no drawable segment (plain route focus). [segment] must be the
 * clipped board→alight polyline (not the full route line), or every stop falls within tolerance and none
 * are dropped.
 */
internal fun List<ObaStop>.onSegment(
    segment: List<GeoPoint>,
    toleranceMeters: Double = SEGMENT_STOP_TOLERANCE_METERS
): List<ObaStop> {
    val line = segment.takeIf { it.isDrawableSegment() }?.let { Polyline(it) } ?: return this
    return filter { stop ->
        val nearest = line.nearestPoint(stop.latitude, stop.longitude) ?: return@filter false
        haversineDistance(stop.latitude, stop.longitude, nearest.latitude, nearest.longitude) <= toleranceMeters
    }
}
