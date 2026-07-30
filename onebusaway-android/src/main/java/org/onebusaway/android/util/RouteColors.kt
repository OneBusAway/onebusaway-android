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
package org.onebusaway.android.util

import android.annotation.SuppressLint
import androidx.core.graphics.toColorInt
import com.google.android.material.color.utilities.Hct
import kotlin.math.min

/**
 * Parses a route hex color to an Android ARGB int, or null when absent or malformed. The single
 * canonical parse used by the wire DTO color readers ([org.onebusaway.android.api.colorArgb]).
 *
 * OBA hands over a bare hex ("FDB71A") and OTP does too, but not every producer is that disciplined, so
 * a leading '#' is tolerated here rather than at each call site — this function is the one place that
 * decides what a route color on the wire may look like, and callers were otherwise obliged to strip the
 * '#' themselves before handing it over, which several independently did.
 */
fun parseObaHexColor(hex: String?): Int? = hex?.takeIf { it.isNotEmpty() }?.let {
    try {
        "#${it.trim().removePrefix("#")}".toColorInt()
    } catch (_: IllegalArgumentException) {
        // A malformed color is the caller's null case, not an error worth reporting: the wire is free to
        // send nonsense and every consumer already has a fallback for a route with no usable color.
        null
    }
}

/**
 * An agency's color in HCT, ready for a display policy to re-derive from — or null when it is absent or
 * achromatic (grey/black/white, below [ACHROMATIC_ROUTE_CHROMA]), leaving no hue to carry into a badge, a
 * spine or a map line. Each policy declines that case and falls back to something of its own; what they
 * share is this step, so they can't drift apart on which colors count as "grey" or on the opaque-alpha
 * normalization that precedes the test.
 *
 * The callers ([org.onebusaway.android.map.mapRouteLineColorOrNull] and [routeColorAtTone]) differ only in
 * what they do with the result — the map keeps the hue alone at its own fixed chroma/tone, the badge caps
 * the source's chroma against a theme.
 */
// Hct is Material Components' vendored color-science util (LIBRARY_GROUP); no public equivalent exists,
// so this is deliberate long-term use, not a migration to track (same as AdjacencyRouteColors).
@SuppressLint("RestrictedApi")
fun routeColorHctOrNull(routeColor: Int?): Hct? {
    val source = routeColor?.let { Hct.fromInt(it or 0xFF000000.toInt()) } ?: return null
    return source.takeIf { it.chroma >= ACHROMATIC_ROUTE_CHROMA }
}

/** The chroma floor [routeColorHctOrNull] applies; exposed so a test can assert against the same bar. */
const val ACHROMATIC_ROUTE_CHROMA = 5.0

/**
 * [routeColor]'s hue re-derived in HCT at [tone], with its chroma capped for the active theme — the
 * theme-aware route colour policy, as opposed to the map's basemap-neutral one
 * ([org.onebusaway.android.map.mapRouteLineColorOrNull]). Null when the source is absent or achromatic
 * (grey/black/white — no hue to keep), which is every caller's cue to fall back to a neutral.
 *
 * Capping rather than replacing the chroma is what makes these colours read as *faded*: an agency handing
 * over a fully saturated hue is muted to the theme's ceiling, while one that publishes an already-soft
 * colour keeps it. [tone] stays the caller's decision, because the same hue is wanted at different
 * lightnesses depending on what is drawn — a filled chip, the glyph on it, a spine on the surface
 * ([org.onebusaway.android.ui.compose.components.routeLineColors]) — and it is the one channel each of
 * those has to choose for itself.
 *
 * Lives here, in ARGB ints, rather than in `LineBadge.kt` where it grew: the directions map draws its
 * route lines in exactly [routeBadgeChipColor] so a line matches the badge naming it, and the map package
 * can't reach a Compose component (nor wants Compose's `Color` in its render state).
 */
// Hct is Material Components' vendored color-science util (LIBRARY_GROUP); no public equivalent exists,
// so this is deliberate long-term use, not a migration to track (same as AdjacencyRouteColors).
@SuppressLint("RestrictedApi")
fun routeColorAtTone(routeColor: Int?, dark: Boolean, tone: Double): Int? {
    val source = routeColorHctOrNull(routeColor) ?: return null
    val chroma = min(source.chroma, if (dark) ROUTE_CHROMA_CAP_DARK else ROUTE_CHROMA_CAP_LIGHT)
    return Hct.from(source.hue, chroma, tone).toInt()
}

/**
 * The route badge chip's own fill: [routeColor] at the badge tone for the active theme. The single
 * definition of that colour, shared by the chip itself
 * ([org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors]) and by the directions map's
 * route lines, so a ride's line and the badge that names it are the same colour rather than two policies
 * that happen to agree today.
 *
 * Null for an absent or achromatic source, as [routeColorAtTone] is — the chip takes a neutral theme
 * container and a map line falls back to whatever its own kind calls for.
 */
fun routeBadgeChipColor(routeColor: Int?, dark: Boolean): Int? = routeColorAtTone(routeColor, dark, if (dark) BADGE_CHIP_TONE_DARK else BADGE_CHIP_TONE_LIGHT)

/**
 * The text drawn on a [routeBadgeChipColor] fill: the same route colour taken to a deep tone, so the name
 * reads as part of its badge rather than as black ink dropped on it.
 *
 * Paired with the fill deliberately, and in the same file: the two tones are only correct *together* — the
 * chip's legibility is the contrast between them, which `RouteColorsTest` holds to 4.5:1 — so a fill tone
 * can't be darkened for a less-pastel badge without its text following. Kept as a colour rather than a
 * per-fill luminance test because the tone is fixed by the theme, so the answer is too.
 *
 * Null for an absent or achromatic source, exactly when the fill is: the chip then takes the neutral theme
 * container/content pair instead.
 */
fun routeBadgeChipTextColor(routeColor: Int?, dark: Boolean): Int? = routeColorAtTone(routeColor, dark, if (dark) BADGE_CHIP_TEXT_TONE_DARK else BADGE_CHIP_TEXT_TONE_LIGHT)

// The saturation ceiling a re-derived route colour may reach, per theme, and the badge chip's tones
// (0=black … 100=white). Together these decide how *pastel* a route reads: chroma is how much of the
// agency's colour survives, tone how light the result sits. Each hue still clamps to its own sRGB gamut
// limit, so a low cap mutes vivid hues — orange → brown.
//
// Deliberately tuned away from pastel: a route's colour is identity, and washed-out fills made two nearby
// hues hard to tell apart — on the chip, and more so on the directions map, where the same colours are
// stroked over a basemap. So the caps sit well above a pastel's, while staying under the map's own chroma
// (75) — this is still the *faded* rendering, just not a wash.
//
// The light theme is the one that had the problem, and its tone is why: a light fill can hold little chroma
// before leaving the sRGB gamut, so most light hues were clamped well under their cap and came out pale
// whatever the cap said. Its fill therefore sits lower than the dark theme's — the opposite of what a
// "lighter theme, lighter chip" reading would suggest — with the text tone darkened in step to hold the
// contrast. The dark theme needed neither move.
private const val ROUTE_CHROMA_CAP_LIGHT = 48.0
private const val ROUTE_CHROMA_CAP_DARK = 70.0

private const val BADGE_CHIP_TONE_LIGHT = 73.0
private const val BADGE_CHIP_TONE_DARK = 74.0

private const val BADGE_CHIP_TEXT_TONE_LIGHT = 25.0
private const val BADGE_CHIP_TEXT_TONE_DARK = 20.0

/**
 * [color] re-toned to [tone], keeping its hue and chroma — the casing step, shared by the two things on the
 * map that outline something in a deepened or lightened version of its own colour: a route line's case
 * (`mapRouteLineCaseColor`, #2082) and a continuation badge's outline
 * ([org.onebusaway.android.map.render.ContinuationBadgeBitmaps.routeBadgeOutlineColor]).
 *
 * The two deliberately pick *different* tones — a 1.5dp hairline under a route line needs far more contrast
 * than a badge's outline — so the tone stays the caller's decision. What they share is this: which channels
 * survive, and the opaque-alpha normalization ahead of the conversion, exactly as [routeColorHctOrNull] does
 * it. Written once so the two casings can't drift apart on either.
 *
 * Unlike [routeColorHctOrNull] this does not decline an achromatic source: a grey line still needs an
 * outline, and re-toning grey yields a lighter or darker grey, which is the right answer for it.
 */
@SuppressLint("RestrictedApi")
fun routeCasingColor(color: Int, tone: Double): Int = with(Hct.fromInt(color or 0xFF000000.toInt())) {
    Hct.from(hue, chroma, tone).toInt()
}
