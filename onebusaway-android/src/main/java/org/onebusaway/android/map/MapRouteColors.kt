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
import org.onebusaway.android.map.render.RouteLineCase
import org.onebusaway.android.util.routeBadgeChipColor
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
 * Two deliberate exceptions, each for a reason that proves the rule:
 *
 *  - [mapRouteLineCaseColor]: a case exists to hold its line apart from the basemap, so it is the one route
 *    colour whose job is defined against a backdrop that flips. It follows the theme — contrasting with it —
 *    so the line it wraps doesn't have to.
 *  - [directionsRouteLinePalette]: in the directions view a line is read *against the drawer beside it*, a
 *    Material surface full of route badges naming those very legs, so there the line takes the badge's own
 *    theme-aware colour rather than this map's. Which of the two policies a line draws through is the
 *    [RouteLinePalette] its producer was handed.
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
 * The case (halo) drawn beneath a line of colour [lineColor] wearing [case] (#2082): that line's own hue and
 * chroma taken to the far end of the tone scale *away from the basemap* — the dark end on the light basemap,
 * the light end when [darkMode].
 *
 * How far out along that scale is [case]'s to say, and this is the map's second selection signal (#2226). A
 * [RouteLineCase.OUTLINE] — the hairline every directions ride wears — stops where a case still carries a
 * trace of its line's hue. A [RouteLineCase.SELECTION], the case the rider's own leg wears, goes to the
 * literal end and comes back achromatic. So the selected leg's edge is not merely *lighter* than the edges
 * around it on the dark basemap: it is the only white one, where the rest are tinted with the routes they
 * wrap. That categorical difference is what a rider actually picks out, and it is why the two tones can sit
 * as close as 1.29:1 and still read apart.
 *
 * The direction is deliberately the opposite of the halo convention for map *labels*, which carry the
 * background's own value to punch glyphs out of busy detail. Tried that way first and the case vanished on
 * device, for a reason specific to what this is: a route line is a saturated mid tone drawn over a mostly
 * empty basemap, so a case tinted *toward* the map lands on the map's own value and disappears into it. What
 * a line needs here is the contrast a label already has from its own ink.
 *
 * These are near-black and near-white, and that is the point: the case is a **separator**, not a second
 * colour. That is a deliberate trade — maximum tonal contrast is what makes a 1.5dp edge visible at all, and
 * the line's own colour is already saying which route it is.
 *
 * How much of that colour survives is not the same at the two ends, and #2226 is what the difference costs. At
 * the dark end sRGB holds little chroma, so a case there really is a near-black hairline. At the light end it
 * holds a great deal for the warm and green hues — a green line at chroma 68 cases at chroma 68 — so an outline
 * on the dark basemap comes out as a lighter, still-saturated version of its own line. That is fine for what an
 * outline is *for* (holding a faded ride off the basemap; it has never said anything about selection), and it
 * was fatal for the thing selection was being said with, since a case a rider can't pick out from its line
 * leaves nothing for its thickness to be read against. Hence the split: the selection case goes all the way to
 * tone 100, where the gamut has nothing left to give it and it lands on white.
 *
 * So the selection case is the one route colour on this map that keeps *no* hue at all, deliberately, and the
 * asymmetry runs the other way at the light basemap: tone 5 is still dark enough to carry a hue, so there the
 * selected case is a deeper version of its line rather than a colourless one. The signal in each theme is
 * whatever that theme's end of the scale can offer — white on the dark map, depth on the light one.
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
internal fun mapRouteLineCaseColor(lineColor: Int, darkMode: Boolean, case: RouteLineCase): Int = routeCasingColor(lineColor, caseTone(case, darkMode))

/**
 * How far out the tone scale [case] goes, for the theme it is drawn in.
 *
 * [RouteLineCase.NONE] answers alongside the outline rather than being rejected, because an uncased line can
 * still need this colour: the interline cutover slash is drawn in its line's case colour whether or not that
 * line asked for an edge of its own (see `InterlineSeamMark`), and a hairline joint's casing is what that
 * mark is. Nothing reads a NONE line's case *as a case* — the renderers only pair one when [case] isn't NONE.
 */
private fun caseTone(case: RouteLineCase, darkMode: Boolean): Double = when (case) {
    RouteLineCase.NONE, RouteLineCase.OUTLINE -> if (darkMode) MAP_ROUTE_CASE_TONE_LIGHT else MAP_ROUTE_CASE_TONE_DARK
    RouteLineCase.SELECTION -> if (darkMode) MAP_SELECTION_CASE_TONE_LIGHT else MAP_SELECTION_CASE_TONE_DARK
}

/**
 * Which policy renders a route line's colour, handed to whatever produces the lines rather than read from
 * ambient state — so a pure geometry helper still says, in its own signature, that a colour decision was
 * made somewhere.
 *
 * Two exist: [BASEMAP_ROUTE_LINE_PALETTE] for every line drawn against the basemap alone, and
 * [directionsRouteLinePalette] for the directions view, where the line is read next to the badges in the
 * drawer. A third would be a third answer to "what does a route line look like here", so add one only with
 * a backdrop that genuinely differs — not to tweak a single view.
 */
// Public, unlike the rest of this file: a palette is handed to the controllers' own entry points
// ([DirectionsMapController.start], [RouteMapController.start]), so it is part of their signature. What it
// renders *with* stays internal.
fun interface RouteLinePalette {

    /** [source]'s hue rendered by this palette, or null when it is absent or achromatic. */
    fun lineColor(source: Int?): Int?
}

/**
 * The map's own palette: one chroma and one tone whatever the hue, theme-independent (see the file
 * header). Every route line outside the directions view.
 */
val BASEMAP_ROUTE_LINE_PALETTE = RouteLinePalette { mapRouteLineColorOrNull(it) }

/**
 * The directions view's palette: a line takes the exact colour of the route badge that names it
 * ([routeBadgeChipColor]) — the agency's hue at the badge's capped chroma and light tone, flipping with the
 * theme as the badge does.
 *
 * The trade this makes, deliberately: these are *faded* colours, chosen to sit on a Material surface, so a
 * directions line carries less contrast against the light basemap than a [BASEMAP_ROUTE_LINE_PALETTE] line
 * would. That is the point — in directions the map is read together with the drawer's badges and spines, and
 * a leg being the same colour as its badge is worth more there than maximum contrast with the basemap. The
 * selected leg still separates itself with a case ([mapRouteLineCaseColor]), which is a tonal contrast and so
 * unaffected.
 *
 * [dark] is resolved when the lines are produced (the drawn itinerary is rebuilt on every plan, leg focus and
 * drill-in), not when they are drawn. A theme flipped while a plan is on screen therefore leaves the lines as
 * they were until the next redraw — the same as the case colour, which the renderer resolves once per created
 * line. The two themes' badge tones sit close together, so a stale line is a shade off, never illegible.
 */
fun directionsRouteLinePalette(dark: Boolean) = RouteLinePalette { routeBadgeChipColor(it, dark) }

private const val MAP_ROUTE_CHROMA = 75.0
private const val MAP_ROUTE_TONE = 55.0

// The two ends of the tone scale an ordinary case is pinned to, chosen for maximum contrast with the basemap
// it has to separate its line from. Not symmetric about MAP_ROUTE_TONE, and not meant to be: each is as far as
// a case can go while still reading as *this line's* edge, keeping enough of its hue to say so.
//
// Deliberately left short of the ends below, which is the whole of #2226: an outline that had already spent
// the scale would leave a selection nothing to step up into. What an outline gives up by stopping here it
// gives up knowingly — on the dark basemap the warm and green hues case at nearly their line's own chroma,
// so a green ride wears a green edge. It reads as an edge because it is a lighter one, and it never had to
// carry selection.
private const val MAP_ROUTE_CASE_TONE_DARK = 10.0
private const val MAP_ROUTE_CASE_TONE_LIGHT = 90.0

// ...and the ends themselves, which the rider's selected leg takes. The step is small in tone alone — 1.29:1
// against an outline on the dark basemap, 1.10:1 on the light one — and that is not what carries it. On the
// dark map tone 100 leaves the gamut with no chroma to give, so the selected case comes back white while
// every outline around it stays tinted with the route it wraps; being the only colourless edge on screen is
// a categorical difference, not a 1.29:1 one. On the light map tone 5 keeps its hue and reads as depth
// instead. Either way this rides on top of the weight step (0.75dp → 1.5dp per side), which remains the
// primary thing saying "this is the one you're looking at".
private const val MAP_SELECTION_CASE_TONE_DARK = 5.0
private const val MAP_SELECTION_CASE_TONE_LIGHT = 100.0
