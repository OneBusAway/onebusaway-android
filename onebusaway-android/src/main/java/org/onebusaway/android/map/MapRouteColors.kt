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
import org.onebusaway.android.util.routeCasingColor
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
 * The case (halo) drawn beneath a selected line of colour [lineColor] (#2082): that line's own hue and chroma
 * taken to the far end of the tone scale *away from the basemap* — [MAP_ROUTE_CASE_TONE_DARK] on the light
 * basemap, [MAP_ROUTE_CASE_TONE_LIGHT] when [darkMode].
 *
 * The direction is deliberately the opposite of the halo convention for map *labels*, which carry the
 * background's own value to punch glyphs out of busy detail. Tried that way first and the case vanished on
 * device, for a reason specific to what this is: a route line is a saturated mid tone drawn over a mostly
 * empty basemap, so a case tinted *toward* the map lands on the map's own value and disappears into it. What
 * a line needs here is the contrast a label already has from its own ink.
 *
 * These are near-black and near-white, and that is the point: the case is a **separator**, not a second
 * colour. The sRGB gamut holds little chroma at either end of the tone scale, so a case here keeps only a
 * trace of its line's hue — measured across the leg hues, as little as chroma 13 where its line carries 75.
 * That is a deliberate trade: maximum tonal contrast is what makes a 1.5dp edge visible at all, and the
 * line's own colour is already saying which route it is. (Its *hue* does survive, within a couple of degrees,
 * so what colour is left is the right one.)
 *
 * Absolute tones rather than an offset from [lineColor]'s own tone: what a case has to contrast with is the
 * basemap, which sits at a fixed value per theme, so the target is a property of the theme and not of the line
 * it wraps. The re-tone itself is [routeCasingColor], shared with the continuation badge's outline so the map's
 * two casings can't drift apart on which channels survive; only the tones differ, and deliberately — a 1.5dp
 * hairline needs far more contrast than a badge outline.
 *
 * This is the one deliberate exception to this file's theme independence (see above) — precisely because its
 * whole job is to hold the line apart from a backdrop that itself flips.
 */
internal fun mapRouteLineCaseColor(lineColor: Int, darkMode: Boolean): Int = routeCasingColor(
    lineColor,
    if (darkMode) MAP_ROUTE_CASE_TONE_LIGHT else MAP_ROUTE_CASE_TONE_DARK
)

private const val MAP_ROUTE_CHROMA = 75.0
private const val MAP_ROUTE_TONE = 55.0

// The two ends of the tone scale a case is pinned to, chosen for maximum contrast with the basemap it has to
// separate its line from. Not symmetric about MAP_ROUTE_TONE, and not meant to be: each is as far as it can
// usefully go without becoming literal black or white.
private const val MAP_ROUTE_CASE_TONE_DARK = 10.0
private const val MAP_ROUTE_CASE_TONE_LIGHT = 90.0
