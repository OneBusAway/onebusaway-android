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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import kotlin.math.ceil
import org.onebusaway.android.util.routeCasingColor

/**
 * Bitmaps for the route-continuation overlay (#1691): the pill badge naming a route, and the arrowhead
 * terminating the continuation line. The pill is also every route label on the map ([RouteBadge], drawn
 * by both flavors), which is why [badge] takes a list of routes rather than one name. Resource-free and
 * flavor-neutral (like [BikeBitmaps], so a flavor renderer just wraps the [Bitmap] as a marker icon.
 *
 * A prototype-quality first pass: legible + tappable, not a final visual design.
 */
object ContinuationBadgeBitmaps {

    // The pill at its unscaled size, which [badge]'s scale multiplies as a set. Every one of these went up
    // by half again in #2102: the label was legible but undersized against the lines it names, and moving
    // them together is what keeps a bigger pill the same drawing rather than a differently-proportioned one.
    //
    // Deliberately the *shared* base rather than the directions profile, even though #2102 is a
    // directions-map issue. Undersized was a claim about route labels generally, so the fixed-1.0x
    // consumers — focused-stop adjacency (#1827) and the route-continuation badge (#1691) — are meant to
    // grow with it. The scoped lever exists and was not pulled on purpose: ITINERARY_ROUTE_BADGE_SCALE_
    // PROFILE(distantScale = 0.75f, closeScale = 1.5f) would have reproduced today's directions labels
    // against the old constants and left those two alone. A future size change should decide between the
    // two the same way — base for "every label is wrong", profile for "this view's labels are wrong".
    //
    // One consequence worth stating: at 1.5x base against a 0.5 distant scale, a directions label at
    // overview zoom is 0.75x of its pre-#2102 self — bigger than it was even at the receded end of the
    // ramp the profile's own KDoc introduces to stop it crowding the itinerary.
    private const val TEXT_SIZE_PX = 57f
    private const val HORIZONTAL_PADDING_PX = 30f
    private const val VERTICAL_PADDING_PX = 18f
    private const val CORNER_RADIUS_PX = 33f
    private const val OUTLINE_WIDTH_DP = 3f
    private const val OUTLINE_TONE_LIGHT = 35.0
    private const val OUTLINE_TONE_DARK = 85.0

    private const val ARROW_WIDTH_PX = 56f
    private const val ARROW_HEIGHT_PX = 60f
    private const val ARROW_STROKE_PX = 4f

    /**
     * The badge bitmap naming [routes] — a rounded-rect pill sized to fit the widest name, with one row
     * per route filled in that route's own line color, stacked top to bottom in the order given. Each
     * row's text is the ink its route names ([BadgedRoute.textColor]) or, when it names none, black or
     * white — whichever contrasts better with the row it sits on, so the badge stays legible across the
     * full range of GTFS route colors.
     *
     * One pill holding several names, rather than several pills, for the reason the directions drawer's
     * joined chip gives (`RouteBadgeChip`): the routes on it are one ride the rider may board any of
     * (#2010/#2083), not separate lines that happen to meet here. The stack goes downwards rather than
     * across because a map label's neighbour is the basemap — a name-per-column pill grows into the
     * corridor it labels, while rows keep the label roughly as wide as its widest name. A label with more
     * names than a single column can hold does take a second one ([badgeGrid]) — but that is a last
     * resort, not the shape a label reaches for.
     *
     * [scale] multiplies every dimension of the pill — type, padding, corner, casing — so a scaled label
     * is the same drawing at a different size rather than a differently-proportioned one. A renderer
     * resolves it from the camera through the label's [RouteBadgeScaleProfile] (#2102).
     */
    fun badge(routes: List<BadgedRoute>, density: Float, darkMode: Boolean, scale: Float): Bitmap = badgeGrid(listOf(routes), density, darkMode, scale)

    /**
     * [badge] in several columns: the same pill, its names laid out in [columns] read top to bottom and
     * then left to right, with dividers wherever two cells meet. One column is the ordinary badge — every
     * label on a line is drawn through this — and a second only appears where a single column would grow
     * taller than the thing it labels, which today is a transit-centre stop naming more routes than fit
     * (#2107).
     *
     * The grid is **rectangular**: every column holds the same number of cells, and a caller with a
     * remainder pads it with a blank cell of its own choosing rather than leaving a hole. Stated rather
     * than handled, because a hole is a hole in the *pill* — the rounded rect and its casing enclose the
     * whole grid — so what fills it is a colour decision belonging to whoever knows what the label is,
     * not a default this drawing can pick.
     */
    fun badgeGrid(columns: List<List<BadgedRoute>>, density: Float, darkMode: Boolean, scale: Float): Bitmap {
        val rowCount = columns.firstOrNull()?.size ?: 0
        require(rowCount > 0) { "a route badge has to name a route to draw one" }
        require(columns.all { it.size == rowCount }) {
            "a route badge grid is rectangular; got columns of ${columns.map(List<BadgedRoute>::size)}"
        }
        // Stated rather than clamped: a non-positive scale has no smallest sensible pill to fall back to,
        // and would otherwise surface far from its cause as a zero-size bitmap allocation.
        require(scale > 0f) { "a route badge drawn at scale $scale would have no pixels" }
        // One paint for every cell: they share a size and weight, so only the text color changes across
        // the grid — set per cell, exactly as the band's is. That also makes the metrics below the whole
        // pill's: its rows are equal bands whatever they read.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT_SIZE_PX * scale
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val metrics = textPaint.fontMetrics
        val cornerRadius = CORNER_RADIUS_PX * scale
        // A whole number of pixels, so the bands are identical and the pill is exactly as tall as its rows
        // — the row boundaries are also where the dividers are drawn, and a fractional band would drift
        // away from them down the stack.
        val rowHeight = ceil((metrics.descent - metrics.ascent) + VERTICAL_PADDING_PX * scale * 2)
        // One width for every column, measured across the whole grid rather than per column: columns of
        // their own widths would put the dividers at ragged intervals, and a grid reads as a grid.
        val columnWidth = columns
            .flatten()
            .maxOf { route -> textPaint.measureText(route.routeShortName) + HORIZONTAL_PADDING_PX * scale * 2 }
            .coerceAtLeast(cornerRadius * 2 / columns.size)
        val width = columnWidth * columns.size
        val height = rowHeight * rowCount

        val bitmap = createBitmap(width.toInt(), height.toInt())
        val canvas = Canvas(bitmap)
        val outlineWidth = OUTLINE_WIDTH_DP * density * scale
        val badgeBounds = RectF(
            outlineWidth / 2f,
            outlineWidth / 2f,
            width - outlineWidth / 2f,
            height - outlineWidth / 2f
        )
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // The pill's shape clips the bands, so the outermost ones take its rounded corners and the
        // interior ones stay square where they meet.
        val pill = Path().apply { addRoundRect(badgeBounds, cornerRadius, cornerRadius, Path.Direction.CW) }
        canvas.withClip(pill) {
            columns.forEachIndexed { column, cells ->
                val left = columnWidth * column
                cells.forEachIndexed { row, route ->
                    val top = rowHeight * row
                    band.color = Color.rgb(Color.red(route.color), Color.green(route.color), Color.blue(route.color))
                    drawRect(RectF(left, top, left + columnWidth, top + rowHeight), band)
                    textPaint.color = route.textColor ?: MarkerRendering.legibleOn(route.color)
                    val baseline = top + rowHeight / 2f - (metrics.ascent + metrics.descent) / 2f
                    drawText(route.routeShortName, left + columnWidth / 2f, baseline, textPaint)
                }
            }
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = casingColor(columns.flatten(), darkMode)
            style = Paint.Style.STROKE
            strokeWidth = outlineWidth
        }
        // Where two cells meet, in the same color as the outline around them all — so the badge reads as
        // one bounded object holding several names, even when its routes share a color or have none.
        for (row in 1 until rowCount) {
            val y = rowHeight * row
            canvas.drawLine(badgeBounds.left, y, badgeBounds.right, y, line)
        }
        for (column in 1 until columns.size) {
            val x = columnWidth * column
            canvas.drawLine(x, badgeBounds.top, x, badgeBounds.bottom, line)
        }
        canvas.drawRoundRect(badgeBounds, cornerRadius, cornerRadius, line)
        return bitmap
    }

    /**
     * A stable key identifying the bitmap [badge] draws for these inputs, beside the function itself so the
     * two can't disagree about which of them the key names (as [VehicleBitmaps.iconKey] is). A renderer
     * caches one wrapper (a Google `BitmapDescriptor`) per key.
     *
     * Every name and color on the badge takes part — including a row's own ink, since two rows can share a
     * fill and read in different colors — so two labels differing only in a stacked route can't
     * share a bitmap. So does [darkMode], which the casing reads: the same routes case differently
     * either side of a light/dark switch, and a key blind to it would serve the pre-switch pill. [scale]
     * takes part for the same reason it does in [VehicleBitmaps.iconKey]: a zoom-scheduled label (#2102)
     * draws the same routes at several sizes over one camera session, and a key blind to the size would
     * hand back whichever it drew first. Density is fixed for the lifetime of a renderer's cache, so it
     * distinguishes nothing within one.
     */
    fun badgeKey(routes: List<BadgedRoute>, darkMode: Boolean, scale: Float): String = badgeGridKey(listOf(routes), darkMode, scale)

    /**
     * [badgeKey] for a [badgeGrid]. The column boundaries take part too, since the same names in one
     * column and in two are different pills.
     */
    fun badgeGridKey(columns: List<List<BadgedRoute>>, darkMode: Boolean, scale: Float): String = columns.joinToString(
        separator = "/",
        prefix = "route-badge:$darkMode:$scale:"
    ) { cells ->
        cells.joinToString(separator = "|") { "${it.routeShortName}:${it.color}:${it.textColor}" }
    }

    /**
     * The color of the badge's outline and of the lines dividing its rows. A badge with one color between
     * its routes cases itself in that color's own hue ([routeBadgeOutlineColor]) — which is every
     * single-route label, and equally a pair of colorless routes drawn in the shared transit fallback. Only
     * a badge that really does hold several colors has no hue to case with — picking one row's would say
     * that row is the badge — so it takes the same tone with the hue dropped, a neutral that reads against
     * every band it encloses.
     */
    internal fun casingColor(routes: List<BadgedRoute>, darkMode: Boolean): Int = routes
        .map(BadgedRoute::color)
        .distinct()
        .singleOrNull()
        ?.let { routeBadgeOutlineColor(it, darkMode) }
        ?: neutralBadgeOutlineColor(darkMode)

    /**
     * The arrowhead bitmap terminating a continuation line, filled with [color] (the line's own color)
     * and outlined in white for contrast against any basemap. Drawn tip-up (bearing 0°); the renderer
     * anchors it bottom-center at the line's end point and rotates it to the line's travel bearing, so
     * the tip points onward from that point.
     */
    fun arrow(color: Int): Bitmap {
        val bitmap = createBitmap(ARROW_WIDTH_PX.toInt(), ARROW_HEIGHT_PX.toInt())
        val canvas = Canvas(bitmap)
        val path = Path().apply {
            moveTo(ARROW_WIDTH_PX / 2f, 0f)
            lineTo(ARROW_WIDTH_PX, ARROW_HEIGHT_PX)
            lineTo(0f, ARROW_HEIGHT_PX)
            close()
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
        )
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = ARROW_STROKE_PX
                strokeJoin = Paint.Join.ROUND
            }
        )
        return bitmap
    }

    /**
     * Theme-aware casing that retains the badge color's HCT hue/chroma and shifts only its tone. Shares
     * [routeCasingColor] with a selected route line's case (#2082); these tones are gentler, since a badge
     * outline sits against the badge's own fill rather than having to hold a hairline off the basemap.
     */
    internal fun routeBadgeOutlineColor(color: Int, darkMode: Boolean): Int = routeCasingColor(color, outlineTone(darkMode))

    /**
     * [routeBadgeOutlineColor] with no hue to keep — the casing a badge naming several routes takes (#2083).
     * Grey through that same shared re-tone, which deliberately doesn't decline a hueless source: it stays
     * hueless and lands on the theme's tone, which is exactly the answer wanted here.
     */
    internal fun neutralBadgeOutlineColor(darkMode: Boolean): Int = routeCasingColor(Color.GRAY, outlineTone(darkMode))

    private fun outlineTone(darkMode: Boolean): Double = if (darkMode) OUTLINE_TONE_DARK else OUTLINE_TONE_LIGHT
}
