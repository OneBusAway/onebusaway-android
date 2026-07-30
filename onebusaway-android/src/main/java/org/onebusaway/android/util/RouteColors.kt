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
 * The callers ([org.onebusaway.android.map.mapRouteLineColorOrNull] and `LineBadge.kt`'s
 * `tonedRouteColor`) differ only in what they do with the result — the map keeps the hue alone at its own
 * fixed chroma/tone, the badge caps the source's chroma against a theme.
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
