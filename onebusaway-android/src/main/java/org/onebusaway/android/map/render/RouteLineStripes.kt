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
package org.onebusaway.android.map.render

import kotlin.math.floor
import kotlin.math.roundToInt
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline

/**
 * Striping a line shared by several routes (#2100): a line that carries [RoutePolyline.stripeColors] is cut
 * into equal runs along its length, cycling through its own colour and then each of the others in turn, so
 * one drawn corridor says all of the routes a rider may ride it on.
 *
 * A directions leg the rider may board any of several routes for was drawn in exactly one of their colours —
 * whichever route the planner picked — while the badge sitting on it named them all, each in its own colour
 * (#2083). So the map answered "which of these am I looking at?" with a colour that meant nothing, and a
 * rider matching the line to the drawer beside it found half the ride's routes unaccounted for.
 *
 * **Stripes run along the line, not beside it.** Side by side was the other candidate, and it is what a
 * printed transit map does — but neither line API this app draws through offsets a stroke (gms polylines
 * have no such option, and neither does the classic maplibre annotation), so it would mean generating
 * parallel geometry per stripe, mitre by mitre, and then splitting a corridor only 15dp wide into slivers of
 * it. Sequential stripes need no new geometry beyond cutting the line the rider is already being shown, and
 * every stripe keeps the full weight of the ride.
 *
 * A run holds its length on the *screen*, tied to the line's own stroke width ([STRIPE_LENGTH_IN_WIDTHS]),
 * which is why this is a render pass and not something the producer could have done: at overview zoom a
 * ride drawn in metre-length runs is a blur of alternating flecks, and drilled into one leg it is a single
 * flat colour from edge to edge. Two bounds keep it readable at the extremes — every colour appears at least
 * once however short the line, and no line is cut into more than [MAX_STRIPES] runs however long it is (a
 * cap on native lines as much as on visual noise, since each run is drawn as one).
 */
internal class StripeRoutePolylinePass : RoutePolylineRenderPass {
    override fun apply(
        polylines: List<RoutePolyline>,
        context: RoutePolylineRenderContext
    ): List<RoutePolyline> {
        if (polylines.none { it.stripeColors.isNotEmpty() }) return polylines
        // Without a camera there is no screen to hold a stripe's length on, so the lines stay whole and
        // draw in their planned colour — the pre-#2100 appearance — until the first camera settles.
        val zoom = context.camera?.zoom ?: return polylines
        return polylines.flatMap { it.striped(zoom) }
    }
}

/**
 * How long one stripe is, as a multiple of the line's own stroke width. Expressed against the width rather
 * than in absolute dp so the rhythm belongs to the line: a ride recedes to half its weight at overview zoom
 * ([RouteLineWidthProfile]), and stripes that didn't recede with it would crowd into a dotted texture there.
 * Long enough to read as a run of colour rather than as a dash — this line is solid, and a stripe must not
 * start saying what [RouteLineDash] says.
 */
private const val STRIPE_LENGTH_IN_WIDTHS = 2.5

/**
 * The most runs one line is cut into. Well over what a viewport holds — a stripe is a few tens of dp, so a
 * screen shows around ten of them — so the cap binds only on a line running far off the edges of the map,
 * where the runs beyond it cost native lines (two apiece, since each is cased) and buy nothing to look at.
 */
internal const val MAX_STRIPES = 24

/**
 * This line cut into its stripes for a camera at [zoom], or the line alone when it isn't striped (which is
 * every line but a shared directions ride) or is too degenerate to cut.
 *
 * The end marks go to the ends of the *ride*, not of each run: the first run keeps the line's
 * [RoutePolyline.startMark] and the last its [RoutePolyline.endMark], and every internal cut is left
 * unmarked. A bulb at each stripe boundary would read as a dozen alight-and-boards along one ride.
 */
private fun RoutePolyline.striped(zoom: Double): List<RoutePolyline> {
    if (stripeColors.isEmpty()) return listOf(this)
    val shape = Polyline(points)
    val length = shape.lengthMeters
    if (length <= 0.0) return listOf(this)

    val colors = listOf(color) + stripeColors
    // Never more runs than the cap, and never fewer than one per colour — in that order, so a line with
    // more routes than the cap still shows every one of them rather than losing the ones past it.
    val stripes = (length / stripeLengthMeters(zoom))
        .roundToInt()
        .coerceAtMost(MAX_STRIPES)
        .coerceAtLeast(colors.size)
    val stride = length / stripes
    return (0 until stripes).map { index ->
        // A measurable line always cuts, so this is the contract holding rather than a run being dropped:
        // dropping one would leave a gap mid-ride, where declining to cut at all just draws it as it was.
        val run = shape.subPolyline(index * stride, (index + 1) * stride) ?: return listOf(this)
        copy(
            color = colors[index % colors.size],
            points = run,
            stripeColors = emptyList(),
            startMark = if (index == 0) startMark else RouteLineMark.NONE,
            endMark = if (index == stripes - 1) endMark else RouteLineMark.NONE
        )
    }
}

/**
 * One stripe's length on the ground, for a camera at [zoom]: [STRIPE_LENGTH_IN_WIDTHS] of this line's drawn
 * width, converted through the Web-Mercator ground resolution at the line's own latitude.
 *
 * The line's latitude rather than the camera's, and a whole zoom level rather than the exact one, both so
 * that the cut is stable while the rider reads the map: it is *geometry*, so anything it varies with
 * redraws every native line the moment the camera settles there. Panning across a leg — which moves the
 * camera's latitude but not the leg's — leaves it untouched, and a pinch redraws once per zoom level
 * crossed rather than continuously. (The badges quantize their scale ramp for the same reason.)
 */
private fun RoutePolyline.stripeLengthMeters(zoom: Double): Double {
    val quantizedZoom = floor(zoom)
    // The ordinary route width stands in for a line that named no profile, which is the width the renderers
    // themselves fall back to; no producer stripes such a line today, the one striped line being a ride.
    val widthDp = (widthProfile ?: ROUTE_LINE_WIDTH_PROFILE).thicknessAt(quantizedZoom.toFloat())
    return widthDp * STRIPE_LENGTH_IN_WIDTHS * metersPerPixel(points.midpointLatitude(), quantizedZoom)
}

/**
 * The latitude of the vertex nearest this line's middle. A single leg spans far too little latitude for the
 * choice among its vertices to move the stripe length; picking one of them, rather than an average that
 * every added vertex would nudge, is what makes the answer stable across a re-simplification.
 */
private fun List<GeoPoint>.midpointLatitude(): Double = this[size / 2].latitude
