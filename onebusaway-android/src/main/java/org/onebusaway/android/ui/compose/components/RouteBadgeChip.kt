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
package org.onebusaway.android.ui.compose.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One route on a badge: its short name and (nullable) GTFS color as an ARGB int. */
data class RouteBadge(val shortName: String, val routeColor: Int?)

/** The badge's corner rounding — barely rounded, matching the roundels elsewhere in the app. */
private val BADGE_SHAPE = RoundedCornerShape(1.dp)

/**
 * How far the divider between two routes leans, as a fraction of the badge's height: it sits at the
 * segment edge at mid-height and shifts by half this either way, so it reads as a "/" between the names
 * rather than as a vertical seam.
 */
private const val SLASH_SLANT_RATIO = 0.5f

/** The width of the badge's outline and of the line where two routes meet inside it. */
private val BADGE_LINE_WIDTH = 1.dp

/**
 * The badge's outline, and the line where two of its routes meet: black in either theme. The chips
 * themselves are pale in light mode and deep in dark mode ([rememberRouteBadgeColors]) but always
 * colored, so a black line reads against any of them — and keeps two routes that happen to share a
 * color (or have none) from running together into one name.
 */
private val BADGE_LINE_COLOR = Color.Black

/**
 * A small route roundel — the route's short name on a chip tinted from its GTFS color (via
 * [rememberRouteBadgeColors]), or a neutral chip when the route has no color. The compact form used
 * where several routes sit in a row (the stop-focus header's subordinate routes, the trip-plan option
 * cards), as opposed to the large square [LineBadge]. [scale] enlarges the whole chip (text + padding)
 * proportionally — e.g. the directions board badge uses 1.5×.
 */
@Composable
fun RouteBadgeChip(shortName: String, routeColor: Int?, modifier: Modifier = Modifier, scale: Float = 1f) {
    val (container, content) = rememberRouteBadgeColors(routeColor)
    val base = MaterialTheme.typography.labelMedium
    // Scale every text metric together — the line box with the glyphs (labelMedium's 16sp line height
    // would clip a 1.5x-scaled name) and the tracking with both. TextUnit's own `* Float` keeps each
    // value's unit (and leaves an unspecified one unspecified), so no metric needs a special case.
    val style = base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = base.lineHeight * scale,
        letterSpacing = base.letterSpacing * scale
    )
    Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = BADGE_SHAPE
    ) {
        Text(
            text = shortName,
            style = style,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 3.dp * scale, vertical = 1.dp * scale)
        )
    }
}

/**
 * The same roundel for a set of routes that are ridden interchangeably (#2010) — "1 Line/2 Line" for a
 * pair of lines sharing the same track between two stops. One chip, not several: the names sit side by
 * side on a single background divided by a slash-like diagonal, each half in its own route color, so
 * the badge reads as one choice of several routes rather than as a sequence of separate legs.
 *
 * Each segment paints its own band, overhanging its neighbours by half the lean; siblings paint left to
 * right, so each band's left edge cleanly overwrites the previous band's overhang and the colors meet
 * exactly on the diagonal. A [BADGE_LINE_COLOR] hairline is then drawn along that meeting line, and the
 * whole chip is outlined in the same color — so the badge reads as one bounded object holding two
 * names, even when its routes share a color or have none. The chip is clipped to [BADGE_SHAPE], which
 * trims the outermost bands' overhang back to the badge's own edges.
 *
 * A single-route list is the plain chip above, outlined to match: these badges sit side by side in the
 * trip planner (`[2] [1 Line/2 Line]`), so they have to be bounded the same way. The plain chip's other
 * callers keep their un-outlined roundel. [scale] enlarges everything proportionally, as on the plain
 * chip.
 */
@Composable
fun RouteBadgeChip(routes: List<RouteBadge>, modifier: Modifier = Modifier, scale: Float = 1f) {
    val outlined = modifier.border(BADGE_LINE_WIDTH, BADGE_LINE_COLOR, BADGE_SHAPE)
    if (routes.size == 1) {
        val route = routes.first()
        RouteBadgeChip(route.shortName, route.routeColor, outlined, scale)
        return
    }
    val base = MaterialTheme.typography.labelMedium
    val style = base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = base.lineHeight * scale,
        letterSpacing = base.letterSpacing * scale
    )
    Row(outlined.clip(BADGE_SHAPE).height(IntrinsicSize.Min)) {
        routes.forEachIndexed { index, route ->
            val (container, content) = rememberRouteBadgeColors(route.routeColor)
            Text(
                text = route.shortName,
                style = style,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = content,
                // Wider than the plain chip's padding: the meeting line leans through the segment edges,
                // so the extra breathing room keeps a name clear of it and of the neighbouring color.
                modifier = Modifier
                    .fillMaxHeight()
                    // drawWithCache, not drawBehind: the band's Path and the line's geometry depend only
                    // on the segment's size, so they're built once per size change, not per draw pass.
                    .drawWithCache {
                        val band = slantedBandPath(
                            extendStart = index == 0,
                            extendEnd = index == routes.lastIndex
                        )
                        val lean = leanPx()
                        val lineWidth = BADGE_LINE_WIDTH.toPx()
                        onDrawBehind {
                            drawPath(band, container)
                            // Only the leading edge, and never on the first segment: each segment draws
                            // its line after its band, so it lands on top of the neighbouring band this
                            // one just overwrote.
                            if (index > 0) {
                                drawLine(
                                    color = BADGE_LINE_COLOR,
                                    start = Offset(lean, 0f),
                                    end = Offset(-lean, size.height),
                                    strokeWidth = lineWidth
                                )
                            }
                        }
                    }
                    .padding(horizontal = 5.dp * scale, vertical = 1.dp * scale)
            )
        }
    }
}

/**
 * This segment's background as a parallelogram: vertical at the badge's outer edges
 * ([extendStart]/[extendEnd] push those past the clip so no sliver of background shows through), and
 * leaning by [SLASH_SLANT_RATIO] of the height where it meets a neighbour. The lean is symmetric about
 * mid-height, so the divider crosses the segment edge exactly where the layout puts it.
 */
private fun CacheDrawScope.slantedBandPath(extendStart: Boolean, extendEnd: Boolean): Path {
    val lean = leanPx()
    // Overhang the neighbouring segment far enough that an outer edge is always covered after clipping.
    val outer = size.height
    return Path().apply {
        moveTo(if (extendStart) -outer else lean, 0f)
        lineTo(if (extendEnd) size.width + outer else size.width + lean, 0f)
        lineTo(if (extendEnd) size.width + outer else size.width - lean, size.height)
        lineTo(if (extendStart) -outer else -lean, size.height)
        close()
    }
}

/** Half the lean, in px: how far the meeting line shifts either side of the segment edge. */
private fun CacheDrawScope.leanPx(): Float = size.height * SLASH_SLANT_RATIO / 2f
