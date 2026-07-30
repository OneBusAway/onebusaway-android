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

    private const val TEXT_SIZE_PX = 38f
    private const val HORIZONTAL_PADDING_PX = 20f
    private const val VERTICAL_PADDING_PX = 12f
    private const val CORNER_RADIUS_PX = 22f
    private const val OUTLINE_WIDTH_DP = 2f
    private const val OUTLINE_TONE_LIGHT = 35.0
    private const val OUTLINE_TONE_DARK = 85.0

    private const val ARROW_WIDTH_PX = 56f
    private const val ARROW_HEIGHT_PX = 60f
    private const val ARROW_STROKE_PX = 4f

    /**
     * The badge bitmap naming [routes] — a rounded-rect pill sized to fit the widest name, with one row
     * per route filled in that route's own line color, stacked top to bottom in the order given. Each
     * row's text is black or white, whichever contrasts better with the row it sits on, so the badge
     * stays legible across the full range of GTFS route colors.
     *
     * One pill holding several names, rather than several pills, for the reason the directions drawer's
     * joined chip gives (`RouteBadgeChip`): the routes on it are one ride the rider may board any of
     * (#2010/#2083), not separate lines that happen to meet here. The stack goes downwards rather than
     * across because a map label's neighbour is the basemap — a name-per-column pill grows into the
     * corridor it labels, while rows keep the label roughly as wide as its widest name.
     */
    fun badge(routes: List<BadgedRoute>, density: Float, darkMode: Boolean): Bitmap {
        require(routes.isNotEmpty()) { "a route badge has to name a route to draw one" }
        // One paint for every row: they share a size and weight, so only the text color changes down the
        // stack — set per row, exactly as the band's is. That also makes the metrics below the whole pill's:
        // its rows are equal bands whatever they read.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT_SIZE_PX
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val metrics = textPaint.fontMetrics
        // A whole number of pixels, so the bands are identical and the pill is exactly as tall as its rows
        // — the row boundaries are also where the dividers are drawn, and a fractional band would drift
        // away from them down the stack.
        val rowHeight = ceil((metrics.descent - metrics.ascent) + VERTICAL_PADDING_PX * 2)
        val width = routes
            .maxOf { route -> textPaint.measureText(route.routeShortName) + HORIZONTAL_PADDING_PX * 2 }
            .coerceAtLeast(CORNER_RADIUS_PX * 2)
        val height = rowHeight * routes.size

        val bitmap = createBitmap(width.toInt(), height.toInt())
        val canvas = Canvas(bitmap)
        val outlineWidth = OUTLINE_WIDTH_DP * density
        val badgeBounds = RectF(
            outlineWidth / 2f,
            outlineWidth / 2f,
            width - outlineWidth / 2f,
            height - outlineWidth / 2f
        )
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // The pill's shape clips the bands, so the outermost ones take its rounded corners and the
        // interior ones stay square where they meet.
        val pill = Path().apply { addRoundRect(badgeBounds, CORNER_RADIUS_PX, CORNER_RADIUS_PX, Path.Direction.CW) }
        canvas.withClip(pill) {
            routes.forEachIndexed { index, route ->
                val top = rowHeight * index
                band.color = Color.rgb(Color.red(route.color), Color.green(route.color), Color.blue(route.color))
                drawRect(RectF(0f, top, width, top + rowHeight), band)
                textPaint.color = MarkerRendering.legibleOn(route.color)
                val baseline = top + rowHeight / 2f - (metrics.ascent + metrics.descent) / 2f
                drawText(route.routeShortName, width / 2f, baseline, textPaint)
            }
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = casingColor(routes, darkMode)
            style = Paint.Style.STROKE
            strokeWidth = outlineWidth
        }
        // Where two rows meet, in the same color as the outline around them all — so the badge reads as
        // one bounded object holding several names, even when its routes share a color or have none.
        for (index in 1..routes.lastIndex) {
            val y = rowHeight * index
            canvas.drawLine(badgeBounds.left, y, badgeBounds.right, y, line)
        }
        canvas.drawRoundRect(badgeBounds, CORNER_RADIUS_PX, CORNER_RADIUS_PX, line)
        return bitmap
    }

    /**
     * A stable key identifying the bitmap [badge] draws for these inputs, beside the function itself so the
     * two can't disagree about which of them the key names (as [VehicleBitmaps.iconKey] is). A renderer
     * caches one wrapper (a Google `BitmapDescriptor`) per key.
     *
     * Every name and color on the badge takes part, so two labels differing only in a stacked route can't
     * share a bitmap — and so does [darkMode], which the casing reads: the same routes case differently
     * either side of a light/dark switch, and a key blind to it would serve the pre-switch pill. Density is
     * fixed for the lifetime of a renderer's cache, so it distinguishes nothing within one.
     */
    fun badgeKey(routes: List<BadgedRoute>, darkMode: Boolean): String = routes.joinToString(
        separator = "|",
        prefix = "route-badge:$darkMode:"
    ) { "${it.routeShortName}:${it.color}" }

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
