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

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.onebusaway.android.util.routeColorHctOrNull

/**
 * What a route line looks like on the map: one chroma and one tone, whatever the hue and wherever the
 * hue came from — an agency's GTFS colour ([mapRouteLineColorOrNull]) or one the app assigned itself
 * ([mapRouteLineColor], which [adjacencyRouteColors] spreads around the circle). A generated line and a
 * GTFS one therefore differ only in hue, which is the one thing that carries meaning.
 *
 * **Every** route line drawn on this map goes through here — the route picker's full-route view, the
 * ridden segment under a tapped directions leg, focused-stop adjacency, a selected vehicle's trip and its
 * continuation hint, an itinerary's transit legs, and the badges that label them. Before, a route drawn
 * from route focus used the agency's raw hex while the same route drawn under adjacency or in an
 * itinerary used a generated or re-toned colour, so one route changed colour as the user moved between
 * views of it. The hue still comes from wherever it always did; only the rendering is now shared.
 *
 * Deliberately theme-independent, unlike the badge/spine tones in `LineBadge.kt`: those sit on a
 * Material surface and flip with it, while these sit on a basemap that carries its own light and dark
 * styles, at a mid tone legible against both. The two policies share only
 * [routeColorHctOrNull] — reading an agency colour and deciding whether it has a hue worth keeping — so a
 * route reads as the same *hue* on the map as in the drawer beside it, rendered for its own backdrop.
 *
 * [mapRouteLineCaseColor] is the one deliberate exception, and for the reason that proves the rule: a case
 * exists to hold its line apart from the basemap, so it is the one route colour whose job is defined against
 * a backdrop that flips. It follows the theme — contrasting with it — so the line it wraps doesn't have to.
 */
// Hct is Material Components' vendored color-science util (LIBRARY_GROUP); no public equivalent
// exists, so this is deliberate long-term use, not a migration to track (same as LineBadge).
@SuppressLint("RestrictedApi")
internal fun mapRouteLineColor(hue: Double): Int = Hct.from(hue, MAP_ROUTE_CHROMA, MAP_ROUTE_TONE).toInt()

/**
 * [source]'s hue at the map's route chroma/tone, or null when it is absent or achromatic — grey, black
 * or white, with no hue to carry over. A caller decides for itself what to draw instead, since the right
 * substitute depends on what the line is (see `ItineraryLegStyle`).
 */
@SuppressLint("RestrictedApi")
internal fun mapRouteLineColorOrNull(source: Int?): Int? = routeColorHctOrNull(source)?.let { mapRouteLineColor(it.hue) }

/**
 * The case (halo) drawn beneath a selected line of colour [lineColor] (#2082): that very line's own hue and
 * chroma, moved [MAP_ROUTE_CASE_TONE_DELTA] tones *against the basemap* — **darker** on the light basemap,
 * lighter when [darkMode]. Same hue, so the case reads as part of its line rather than as a second line
 * beside it; a tone apart in the direction the map isn't, so the pair separates from the basemap.
 *
 * The direction is deliberately the opposite of the halo convention for map *labels*, which carry the
 * background's own value to punch glyphs out of busy detail. Tried that way first and the case vanished on
 * device, for a reason specific to what this is: a route line is a saturated mid tone drawn over a mostly
 * empty basemap, so a case tinted *toward* the map lands on the map's own value and disappears into it. What
 * a line needs here is the contrast a label already has from its own ink.
 *
 * This is the one deliberate exception to this file's theme independence (see above) — precisely because its
 * whole job is to hold the line apart from a backdrop that itself flips.
 *
 * Anchored on [lineColor]'s own tone, not on [MAP_ROUTE_TONE], so a case contrasts with *its own* trunk even
 * for a line that never went through [mapRouteLineColor] (the render layer's fallback). Clamped to the tone
 * range, so a line already near white or black yields the nearest case it can rather than an out-of-range
 * colour. Reads the hue off [lineColor] for the same reason: a case is derived from a line that is already
 * drawn, whatever its colour came from. An achromatic line yields an achromatic case.
 */
@SuppressLint("RestrictedApi")
internal fun mapRouteLineCaseColor(lineColor: Int, darkMode: Boolean): Int = with(Hct.fromInt(lineColor)) {
    val delta = if (darkMode) MAP_ROUTE_CASE_TONE_DELTA else -MAP_ROUTE_CASE_TONE_DELTA
    Hct.from(hue, chroma, (tone + delta).coerceIn(MIN_TONE, MAX_TONE)).toInt()
}

private const val MAP_ROUTE_CHROMA = 75.0
private const val MAP_ROUTE_TONE = 55.0

// Far enough from the line's own tone to read as an outline rather than as a shade of the same stroke, and
// close enough to stay recognisably its colour. Kept a tone delta rather than a pair of fixed colours so a
// case tracks whatever hue its line is drawn in — and so the two themes can't drift into different amounts
// of contrast.
//
// Also what makes a case *bright* rather than muddy, which is why it isn't larger: the sRGB gamut holds much
// less chroma at the ends of the tone scale than in the middle, so a case pushed far from its line loses its
// colour on the way — dark and murky one side, washed out the other. Pulling the delta in keeps both themes'
// cases nearer the hue's chroma peak, where the colour actually survives.
private const val MAP_ROUTE_CASE_TONE_DELTA = 18.0

private const val MIN_TONE = 0.0
private const val MAX_TONE = 100.0
