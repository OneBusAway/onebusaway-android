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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One route on a badge: its short name and (nullable) GTFS color as an ARGB int. */
data class RouteBadge(val shortName: String, val routeColor: Int?)

/**
 * How the routes sharing one badge relate to each other — and so what shape divides them. A badge is one
 * or the other, never a mix: the two facts arise on different legs (see `TripItinerary.substitutableRoutes`,
 * which drops the alternatives on an interlined ride), so nothing has to draw a slash and a chevron in the
 * same chip.
 */
enum class RouteBadgeJoin {

    /**
     * Interchangeable routes (#2010) — any one of them will do, so the rider takes whichever comes
     * first. Divided by a slash, the way a corridor's lines are written down: `1 Line / 2 Line`.
     */
    ANY_OF,

    /**
     * The routes one vehicle runs as in turn, ridden without alighting — a stay-aboard interline
     * (#2000). Divided by a chevron, because the order is the whole point: `5 > 12` is one ride that
     * changes its name at a seam, not a choice and not a transfer (#2049).
     */
    THEN
}

/** The badge's corner rounding — barely rounded, matching the roundels elsewhere in the app. */
private val BADGE_SHAPE = RoundedCornerShape(1.dp)

/**
 * How far the divider between two routes leans, as a fraction of the badge's height: it sits at the
 * segment edge at mid-height and shifts by half this either way, so it reads as a "/" between the names
 * rather than as a vertical seam.
 *
 * The chevron ([RouteBadgeJoin.THEN]) is the same lean folded at mid-height — one constant for both, so
 * the two joins are visibly the same badge family and only their *shape* says which relation they mean.
 */
private const val JOIN_LEAN_RATIO = 0.5f

/** The width of the badge's outline and of the line where two routes meet inside it. */
private val BADGE_LINE_WIDTH = 1.dp

/** The mode glyph's size, and its gap from the name — sized to sit level with the name's cap height
 *  rather than tower over it. Both scale with the chip. */
private val BADGE_ICON_SIZE = 11.dp

private val BADGE_ICON_GAP = 2.dp

/** The padding above and below the badge's content. Scales with the chip. */
private val BADGE_VERTICAL_PADDING = 1.dp

/**
 * The chip's height at [scale] 1 — its label's line box (`labelMedium`'s 16sp, which is taller than
 * [BADGE_ICON_SIZE] and so sets the height) plus [BADGE_VERTICAL_PADDING] above and below. Public
 * because a caller that has to line the chip up with something *else* needs it: the trip-plan option
 * cards draw glyphs and roundels in one row and size both from a single height, deriving the chip's
 * scale from this rather than hard-coding a number that would silently rot if these metrics changed.
 *
 * It is also the chip's *minimum width*, which is what makes a short name a square roundel rather than a
 * lozenge.
 *
 * Both hold at the default font scale. Under a larger accessibility font the sp line box — and so the
 * chip's height — grows while a dp-sized glyph beside it, and this dp minimum width, do not: the chip
 * comes out taller than it is wide and drifts from a neighbouring glyph, both in the direction of the
 * text being bigger, which is what the setting asked for.
 */
val ROUTE_BADGE_HEIGHT = 16.dp + BADGE_VERTICAL_PADDING * 2

/**
 * The badge's outline, and the line where two of its routes meet: black in either theme. The chips
 * themselves are pale in light mode and deep in dark mode ([rememberRouteBadgeColors]) but always
 * colored, so a black line reads against any of them — and keeps two routes that happen to share a
 * color (or have none) from running together into one name.
 */
private val BADGE_LINE_COLOR = Color.Black

/**
 * How much room a name gets either side of it inside a joined badge, before allowing for the divider.
 * Wider than the plain chip's padding, since the divider leans through the segment edges and the name has
 * to stay clear of it. Scales with the chip.
 */
private val JOINED_SEGMENT_PADDING = 5.dp

/**
 * How far a chevron's point reaches past the segment edge, at [scale][RouteBadgeChip] 1 — the drawn lean
 * ([JOIN_LEAN_RATIO] of the chip's height, halved) restated in layout units, so the padding can *allow*
 * for it instead of guessing at a number that would rot if the lean changed.
 *
 * A notched segment adds this to its leading padding and nothing to its trailing one. That's the whole
 * correction: the boundary is slanted, so a name's tightest clearance is not at mid-height (where the
 * notch is deepest) but where the edge crosses the segment's nominal edge, a quarter of the way down —
 * which for a notch is a full lean in from where the padding starts, and for a point is exactly at it.
 * Without this the name sits a half-lean left of centre in the band a rider actually sees, with the slack
 * left over as dead space after it.
 *
 * Exact at the default font scale. Above it the sp-driven chip grows while this dp allowance does not, so
 * the clearance tightens a little — the same direction of drift [ROUTE_BADGE_HEIGHT] already notes.
 */
private val CHEVRON_LEAN_ALLOWANCE = ROUTE_BADGE_HEIGHT * JOIN_LEAN_RATIO / 2

/**
 * A small route roundel — the route's short name on a chip tinted from its GTFS color (via
 * [rememberRouteBadgeColors]), or a neutral chip when the route has no color. The compact form used
 * where several routes sit in a row (the stop-focus header's subordinate routes, the trip-plan option
 * cards), as opposed to the large square [LineBadge]. [scale] enlarges the whole chip (text + padding)
 * proportionally — e.g. the directions board badge uses 1.5×.
 *
 * The chip is never narrower than it is tall ([ROUTE_BADGE_HEIGHT] at the default font scale), so a
 * one- or two-character route reads as a roundel of the same family as a longer one rather than as a
 * sliver next to it. The name centres itself in whatever width that leaves.
 *
 * [maxWidth] caps the chip and ellipsizes the name past it. Unconstrained by default, since a route
 * short name is a handful of characters; it's for callers that badge a route by its *long* name because
 * it publishes no short one ("Seattle - Bremerton"), which would otherwise blow out a row of roundels.
 *
 * [leadingIcon] puts a glyph inside the chip, ahead of the name — the mode the route is ridden on, which
 * a route number alone never says. [leadingIconDescription] labels it for TalkBack; the mode is real
 * information, not decoration, so it is worth announcing.
 */
@Composable
fun RouteBadgeChip(
    shortName: String,
    routeColor: Int?,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    maxWidth: Dp = Dp.Unspecified,
    leadingIcon: Int? = null,
    leadingIconDescription: String? = null
) {
    val (container, content) = rememberRouteBadgeColors(routeColor)
    Surface(
        // A short name still gets a proper roundel rather than a narrow lozenge: at the default font
        // scale the chip is at least as wide as it is tall, so "8" and "45" read as the same object at
        // different widths instead of as a sliver beside a badge. [Surface] propagates the minimum to
        // its content, which centres itself in the room that gives it (see [BadgeContent]).
        modifier = modifier.widthIn(min = ROUTE_BADGE_HEIGHT * scale, max = maxWidth),
        color = container,
        contentColor = content,
        shape = BADGE_SHAPE
    ) {
        BadgeContent(
            name = shortName,
            contentColor = content,
            scale = scale,
            horizontalPadding = 3.dp * scale,
            leadingIcon = leadingIcon,
            leadingIconDescription = leadingIconDescription
        )
    }
}

/** The badge's inner row — an optional mode glyph, then the route's name. Shared by the plain chip and
 *  by each segment of the joined one, so the two can't drift on metrics or truncation. */
@Composable
private fun BadgeContent(
    name: String,
    contentColor: Color,
    scale: Float,
    horizontalPadding: Dp,
    leadingIcon: Int?,
    leadingIconDescription: String?,
    modifier: Modifier = Modifier
) {
    val base = MaterialTheme.typography.labelMedium
    // Scale every text metric together — the line box with the glyphs (labelMedium's 16sp line height
    // would clip a 1.5x-scaled name) and the tracking with both. TextUnit's own `* Float` keeps each
    // value's unit (and leaves an unspecified one unspecified), so no metric needs a special case.
    val style = base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = base.lineHeight * scale,
        letterSpacing = base.letterSpacing * scale
    )
    Row(
        modifier = modifier.padding(horizontal = horizontalPadding, vertical = BADGE_VERTICAL_PADDING * scale),
        verticalAlignment = Alignment.CenterVertically,
        // Centred, not packed at the start: a name narrower than the chip's square minimum has room left
        // over, and it belongs either side of the name rather than all after it.
        horizontalArrangement = Arrangement.spacedBy(
            space = BADGE_ICON_GAP * scale,
            alignment = Alignment.CenterHorizontally
        )
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = leadingIconDescription,
                tint = contentColor,
                modifier = Modifier.size(BADGE_ICON_SIZE * scale)
            )
        }
        Text(
            text = name,
            style = style,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor
        )
    }
}

/**
 * The same roundel for a set of routes ridden as one leg — "1 Line/2 Line" for a pair of lines sharing
 * the same track between two stops ([RouteBadgeJoin.ANY_OF], #2010), or "5 > 12" for a bus that becomes
 * another route with the rider still aboard ([RouteBadgeJoin.THEN], #2000/#2049). One chip, not several:
 * the names sit side by side on a single background, each in its own route color, divided by the shape
 * [join] calls for — so the badge reads as *one* ride however many routes it names, rather than as
 * separate legs the rider gets off and on between.
 *
 * Each segment paints its own band, overhanging its neighbours by half the lean; siblings paint left to
 * right, so each band's left edge cleanly overwrites the previous band's overhang and the colors meet
 * exactly on the divider. A [BADGE_LINE_COLOR] hairline is then drawn along that meeting line, and the
 * whole chip is outlined in the same color — so the badge reads as one bounded object holding two
 * names, even when its routes share a color or have none. The chip is clipped to [BADGE_SHAPE], which
 * trims the outermost bands' overhang back to the badge's own edges.
 *
 * [routes] is in the order the badge reads. For [RouteBadgeJoin.THEN] that order *is* the information —
 * `5 > 12` and `12 > 5` are different rides — so the caller sorts an [RouteBadgeJoin.ANY_OF] badge into
 * natural name order and leaves a [RouteBadgeJoin.THEN] one in ride order (see `RouteBadges`).
 *
 * A single-route list is the plain chip above, outlined to match: these badges sit side by side in the
 * trip planner (`[2] [1 Line/2 Line]`), so they have to be bounded the same way. It has no divider, so
 * [join] doesn't reach it. The plain chip's other callers keep their un-outlined roundel. [scale]
 * enlarges everything proportionally, as on the plain chip.
 *
 * [maxWidth] applies only to that single-route case — see the note at the joined `Row` below.
 * [leadingIcon] heads the whole badge rather than each name in it: however its routes are joined, a badge
 * is one ride on one mode, so a glyph per segment would read as several legs.
 *
 * The bands are laid out by a `Row` (which an RTL layout would reverse) but painted in raw draw-space
 * offsets (which it would not), so a joined badge assumes an LTR reading order — as it has since #2010,
 * and as the app does throughout, shipping no RTL locale.
 */
@Composable
fun RouteBadgeChip(
    routes: List<RouteBadge>,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    maxWidth: Dp = Dp.Unspecified,
    join: RouteBadgeJoin = RouteBadgeJoin.ANY_OF,
    leadingIcon: Int? = null,
    leadingIconDescription: String? = null
) {
    val outlined = modifier.border(BADGE_LINE_WIDTH, BADGE_LINE_COLOR, BADGE_SHAPE)
    if (routes.size == 1) {
        val route = routes.first()
        RouteBadgeChip(route.shortName, route.routeColor, outlined, scale, maxWidth, leadingIcon, leadingIconDescription)
        return
    }
    // Deliberately uncapped, unlike the plain chip: this Row's children are unweighted, so a bounded max
    // would be consumed by the first segment and leave the later ones measured at zero width — a route
    // silently missing from a badge whose whole point is naming every route on the ride is far worse than
    // a wide chip, and the option-card row it sits in already scrolls horizontally. [maxWidth] therefore
    // only reaches the single-route delegation above, which is the case it exists for.
    Row(outlined.clip(BADGE_SHAPE).height(IntrinsicSize.Min)) {
        routes.forEachIndexed { index, route ->
            val (container, content) = rememberRouteBadgeColors(route.routeColor)
            BadgeContent(
                name = route.shortName,
                contentColor = content,
                scale = scale,
                // Wider than the plain chip's, since the divider leans through the segment edges.
                horizontalPadding = JOINED_SEGMENT_PADDING * scale,
                // The glyph heads the badge, not each name in it: these routes are one ride on one mode,
                // and a glyph per segment would read as several.
                leadingIcon = leadingIcon.takeIf { index == 0 },
                leadingIconDescription = leadingIconDescription,
                modifier = Modifier
                    .fillMaxHeight()
                    // Each segment is squared off the same way the plain chip is, so a one-character
                    // route inside a joined badge doesn't come out as a sliver beside a wider sibling.
                    .widthIn(min = ROUTE_BADGE_HEIGHT * scale)
                    // drawWithCache, not drawBehind: the band's Path and the line's geometry depend only
                    // on the segment's size, so they're built once per size change, not per draw pass.
                    .drawWithCache {
                        // One edge per segment, shared by the band it cuts and the line drawn along it —
                        // the geometry is defined once (see [joinEdge]) and computed once too.
                        val edge = joinEdge(join)
                        val band = bandPath(
                            edge = edge,
                            extendStart = index == 0,
                            extendEnd = index == routes.lastIndex
                        )
                        // Only the leading edge, and never on the first segment: each segment draws its
                        // line after its band, so it lands on top of the neighbouring band this one just
                        // overwrote. The first segment has no neighbour, so it builds no line at all.
                        val divider = if (index > 0) edge.toPath() else null
                        val line = Stroke(BADGE_LINE_WIDTH.toPx())
                        onDrawBehind {
                            drawPath(band, container)
                            divider?.let { drawPath(it, BADGE_LINE_COLOR, style = line) }
                        }
                    }
                    // Room for the notch the previous segment's divider cuts into this one, so the name
                    // stays centred in the band the rider sees. Applied after the draw modifier, which
                    // therefore still sizes the band to the whole segment.
                    .padding(start = join.leadingInset(index) * scale)
            )
        }
    }
}

/**
 * How much room this join's divider needs on the leading edge of the segment at [index] — nothing on the
 * first segment, which has no neighbour to be cut by. Stated as a `when` over the join rather than as a
 * test for one of them, so a third join shape has to answer the question instead of silently reserving
 * nothing and drawing its name into its own divider.
 */
private fun RouteBadgeJoin.leadingInset(index: Int): Dp = when {
    index == 0 -> 0.dp
    // A slash sits at the segment edge at mid-height, where the name's ink is, and leans away above and
    // below it — so [JOINED_SEGMENT_PADDING] already clears it. Only a chevron's notch reaches inwards.
    this == RouteBadgeJoin.ANY_OF -> 0.dp
    else -> CHEVRON_LEAN_ALLOWANCE
}

/**
 * Where two of the badge's routes meet, as a top-to-bottom polyline in offsets from the segment edge: a
 * "/" for [RouteBadgeJoin.ANY_OF], a ">" for [RouteBadgeJoin.THEN]. Both are symmetric about the edge, so
 * whichever shape divides them, the names sit where the layout put them and neither is pushed off centre.
 *
 * The single definition of the join's geometry: the hairline is drawn along it and the bands either side
 * are cut by it ([bandPath]), so a band cannot drift away from the line dividing it.
 */
private fun CacheDrawScope.joinEdge(join: RouteBadgeJoin): List<Offset> {
    val lean = leanPx()
    return when (join) {
        RouteBadgeJoin.ANY_OF -> listOf(Offset(lean, 0f), Offset(-lean, size.height))
        // A chevron pointing the way the row is read: the same lean, folded at mid-height.
        RouteBadgeJoin.THEN -> listOf(Offset(-lean, 0f), Offset(lean, size.height / 2f), Offset(-lean, size.height))
    }
}

/** This polyline as an open `Path` — the one walk from a list of points to something drawable, shared by
 *  the divider hairline and by the band edges it cuts. */
private fun List<Offset>.toPath(): Path = Path().apply {
    moveTo(first().x, first().y)
    for (i in 1..lastIndex) lineTo(this@toPath[i].x, this@toPath[i].y)
}

/**
 * This segment's background: cut by [edge] where it meets a neighbour, and vertical at the badge's outer
 * edges — [extendStart]/[extendEnd] push those well past the clip so no sliver of background shows
 * through. Since a segment's trailing edge and its neighbour's leading edge are that same [edge],
 * consecutive bands meet exactly along it with no gap and no double-painted overlap.
 */
private fun CacheDrawScope.bandPath(edge: List<Offset>, extendStart: Boolean, extendEnd: Boolean): Path {
    // Overhang the neighbouring segment far enough that an outer edge is always covered after clipping.
    val outer = size.height
    fun straightEdge(x: Float) = listOf(Offset(x, 0f), Offset(x, size.height))
    val leading = if (extendStart) straightEdge(-outer) else edge
    val trailing = if (extendEnd) straightEdge(size.width + outer) else edge.map { Offset(it.x + size.width, it.y) }
    // Around the band: down the leading edge, across the bottom, back up the trailing edge, and closed
    // across the top.
    return leading.toPath().apply {
        for (i in trailing.lastIndex downTo 0) lineTo(trailing[i].x, trailing[i].y)
        close()
    }
}

/** Half the lean, in px: how far the meeting line shifts either side of the segment edge. */
private fun CacheDrawScope.leanPx(): Float = size.height * JOIN_LEAN_RATIO / 2f
