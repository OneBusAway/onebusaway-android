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

import kotlin.math.round

/** Shared zoom interval for route-detail styling. */
const val DETAIL_RAMP_START_ZOOM = 11f
const val DETAIL_RAMP_END_ZOOM = 16f

/** Route-line scale: half width when zoomed out, full width at zoom 16 and above. */
const val ROUTE_DETAIL_DISTANT_SCALE = 0.5f

/** The ordinary route stroke thickness at the close end of the detail ramp. */
const val ROUTE_LINE_WIDTH_DP = 10f

/**
 * A complete route-line width policy: its close-zoom [thicknessDp], the zoom interval over which it
 * grows, and its distant-zoom multiplier. Keeping these together lets presentation modes share an
 * exact policy instead of independently copying a base width and relying on renderer-global ramp
 * constants.
 */
data class RouteLineWidthProfile(
    val thicknessDp: Float,
    val rampStartZoom: Float = DETAIL_RAMP_START_ZOOM,
    val fullThicknessZoom: Float = DETAIL_RAMP_END_ZOOM,
    val distantThicknessMultiplier: Float = ROUTE_DETAIL_DISTANT_SCALE
) {
    fun multiplierAt(zoom: Float): Float = detailZoomRamp(
        zoom,
        startZoom = rampStartZoom,
        endZoom = fullThicknessZoom,
        distantValue = distantThicknessMultiplier,
        closeValue = 1f
    )

    fun thicknessAt(zoom: Float): Float = thicknessDp * multiplierAt(zoom)
}

/** Ordinary route presentation, before a route is selected from a focused stop. */
val ROUTE_LINE_WIDTH_PROFILE = RouteLineWidthProfile(ROUTE_LINE_WIDTH_DP)

/**
 * Adjacent routes shown in stop focus, before any route is selected. They recede to half the ordinary
 * stroke so stop focus reads distinctly from the selected-route state, where one route is thickened
 * instead (#1985).
 */
val ADJACENT_ROUTE_LINE_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.5f
)

/** Shared by single-route view and a route selected from focused-stop mode. */
val FOCUSED_ROUTE_LINE_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 1.5f
)

/** Contextual sibling routes shown underneath a route selected from focused-stop mode. */
val DEEMPHASIZED_ROUTE_LINE_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.275f
)

/**
 * The selected transit route upstream of where the rider boards it — where the bus is coming from. It is the
 * same route as the ridden segment, so it is cased like it (see [RoutePolyline.case]) and steps down in width
 * at the boarding point.
 *
 * The thinnest of the itinerary weights, because it is the least committed geometry on the map: the rider
 * won't travel it, and it's here to say where the vehicle arrives from. Its case, not its width, is what
 * connects it to the ride — which is what lets it be this thin without being mistaken for context.
 *
 * Thin enough that it is what bounds a case's width: it takes [RouteLineCase.APPROACH] rather than the full
 * [RouteLineCase.SELECTION] precisely because a selection case adds 4.5dp, more than this line's own 3.5dp,
 * and the thinnest line on the map would then draw as a band with a coloured core instead of a line with an
 * edge. Same colour, lighter weight. `RouteLineWidthProfileTest` pins the pairing.
 *
 * It used to be the map's faintest line (2dp) *and* dashed, which put it within a hair of the receded
 * itinerary legs around it — the two competed instead of reading as different things (#2082).
 */
val ITINERARY_APPROACH_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.35f
)

/**
 * The rest of a rider's itinerary retained around a focused transit leg. It sits between the selected
 * ridden segment and the thinner, untraveled remainder of that transit route: still visibly part of the
 * journey, but no longer competing with the leg being read.
 *
 * This deliberately has its own semantic profile even though it currently shares the adjacent-route
 * value. The two presentations describe different things and can be tuned independently later.
 */
val ITINERARY_CONTEXT_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.5f
)

/**
 * The rider's **parked** trip, drawn under the map they are exploring (#2053).
 *
 * Well under a route line, and deliberately so: the ghost is not part of what the rider is reading, it is
 * a reminder of where they are going, and a trip that competed with the stop they just tapped would make
 * exploring worse rather than safer. But it was drawn at the map's faintest weight and lost that argument
 * the other way — a trace too thin to pick out of a busy basemap says a trip is parked without saying
 * which one, which is the whole of its job. It keeps each leg's own colour: thin, not mute.
 */
val PINNED_TRIP_GHOST_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.525f
)

/**
 * A trip-plan itinerary's transit legs (#2041). The itinerary is the only thing the directions map is
 * showing — nothing competes with it — so a ride draws at the focused-route weight, and *is* that
 * profile rather than a second copy of its multiplier. It carries its own name because the two are read
 * against different backdrops (route focus sits over sibling routes and nearby stops, directions over a
 * bare basemap), so directions is the thing to give a value of its own the day they should differ.
 */
val ITINERARY_RIDE_WIDTH_PROFILE = FOCUSED_ROUTE_LINE_WIDTH_PROFILE

/**
 * An itinerary's on-street legs — walking, cycling, bikeshare. Drawn narrower than the ride it connects
 * to: a sidewalk or bike lane is a thinner thing than a transit corridor, and the step down at each
 * transition is a second cue (besides colour) for where one mode hands off to the next.
 */
val ITINERARY_STREET_WIDTH_PROFILE = ROUTE_LINE_WIDTH_PROFILE.copy(
    thicknessDp = ROUTE_LINE_WIDTH_DP * 0.9f
)

/**
 * The number of steps the ramp between a badge profile's two ends is quantized into — see
 * [RouteBadgeScaleProfile.scaleAt] for why a label's scale is discrete where a line's width is not.
 */
private const val ROUTE_BADGE_SCALE_STEPS = 16f

/**
 * How a route label's drawn size answers the camera: a uniform scale on the pill
 * [ContinuationBadgeBitmaps.badge] draws, ramped linearly across [rampStartZoom]..[rampEndZoom] and flat
 * on either side of it.
 *
 * A profile rather than a bare function for the reason [RouteLineWidthProfile] is one: it is carried
 * unresolved on the label and resolved by each renderer against its live camera, so the day one kind of
 * label should answer the camera differently from the rest, that is a value it carries rather than a
 * branch in each renderer. Every label takes the same one today ([ROUTE_BADGE_SCALE_PROFILE]).
 */
data class RouteBadgeScaleProfile(
    val distantScale: Float,
    val closeScale: Float,
    val rampStartZoom: Float = DETAIL_RAMP_START_ZOOM,
    val rampEndZoom: Float = DETAIL_RAMP_END_ZOOM
) {
    /**
     * The scale to draw at under a camera at [zoom], **quantized** to [ROUTE_BADGE_SCALE_STEPS] steps.
     *
     * A route line's width can take the ramp's raw value because setting a native polyline's width is
     * free. A label's can't: its scale sizes a rasterized bitmap and keys the icon cache
     * ([ContinuationBadgeBitmaps.badgeKey]), so a raw camera float means every settle inside the ramp
     * yields a scale never seen before — a guaranteed cache miss, a fresh `Canvas` draw and native
     * texture per badge, and a cache filling with entries that can never be hit again. Discrete steps
     * bound the whole thing to a handful of bitmaps per label for a session.
     *
     * This is the same move [org.onebusaway.android.map.googlemapsv2.GoogleRouteStopBitmapLayer] already
     * makes for the identical ramp, where it rounds the stop diameter to whole pixels and keys on that.
     * Sixteenths are fine enough that a step is a ~6% size change — smaller than the jump a settle-only
     * update makes anyway, since labels resize on camera idle rather than continuously.
     */
    fun scaleAt(zoom: Float): Float {
        val raw = detailZoomRamp(
            zoom,
            startZoom = rampStartZoom,
            endZoom = rampEndZoom,
            distantValue = distantScale,
            closeValue = closeScale
        )
        return round(raw * ROUTE_BADGE_SCALE_STEPS) / ROUTE_BADGE_SCALE_STEPS
    }
}

/**
 * What a route label drawn on a line does about the camera, on every view that draws one — a directions
 * itinerary's rides (#2102) and a focused stop's adjacency labels (#2195): the shared detail ramp,
 * pointing the same way as everything else it rides with, at half size when zoomed out and full at zoom
 * 16 and above.
 *
 * A label is a fixed number of screen pixels, so at an overview zoom it is enormous relative to the
 * geometry it annotates — several of them, anchored at line midpoints that are themselves close together
 * out there, crowd each other and cover the shape the rider is trying to read. Receding with the route
 * lines and stop circles around it ([RouteLineWidthProfile], [focusedRouteStopScale]) keeps the whole view
 * scaling as one thing, and hands the label its full size at the zoom where there's room for it.
 *
 * This is the **default** a [RouteBadge] is born with rather than a schedule each producer opts into, and
 * that is the lesson of #2195: #2102 gave the itinerary's labels a schedule while the default stayed a
 * fixed pill, and adjacency — the older producer, in the more-used view — was simply left behind in it.
 * A new kind of label should inherit what every other label does; a producer that wants
 * something else says so with a profile of its own name, which is also how the two views would diverge
 * (they are read against different backdrops, so they may yet) without a branch in a renderer.
 *
 * Deliberately a starting point to be tuned against the real map, not a settled value.
 */
val ROUTE_BADGE_SCALE_PROFILE = RouteBadgeScaleProfile(
    distantScale = ROUTE_DETAIL_DISTANT_SCALE,
    closeScale = 1f
)

/** Route-line scale retained for unprofiled lines and vehicle markers. */
fun routeLineWidthScale(zoom: Float): Float = ROUTE_LINE_WIDTH_PROFILE.multiplierAt(zoom)

/** Linear interpolation machinery for zoom-dependent map-detail styling. */
internal fun detailZoomRamp(
    zoom: Float,
    startZoom: Float,
    endZoom: Float,
    distantValue: Float,
    closeValue: Float
): Float {
    val progress = ((zoom - startZoom) / (endZoom - startZoom)).coerceIn(0f, 1f)
    return distantValue + (closeValue - distantValue) * progress
}
