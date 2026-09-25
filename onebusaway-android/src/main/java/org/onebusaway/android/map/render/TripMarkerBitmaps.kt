/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap

/**
 * Circular trip-marker bitmaps: a ringed, opaque-filled disc with a drawable centered inside (ported
 * from the feature's Google-only MapIconUtils.createCircleIcon, lifted to `src/main` so both flavors
 * share it — the Google adapter wraps the Bitmap in a `BitmapDescriptor`, maplibre in an `Icon`). Used
 * for the trip map's extrapolated-vehicle / fast-estimate / last-fix markers. The fill is fully opaque:
 * these markers overlap and move every frame, so a translucent fill makes the blended overlap shimmer.
 * Cached per (drawable, fill).
 *
 * The **fill is the caller's** — the selected trip's markers take the uncertainty band's own colour, so
 * a rider reads the band, the fix it starts at and the fast estimate it ends at as one data object
 * rather than three unrelated map decorations (#1990). Everything drawn *on* the disc (the ring and the
 * glyph) is then whichever of black/white reads against that fill ([MarkerRendering.legibleOn]), the
 * same call every other route-coloured map element makes — so no fill can render a marker's own ring or
 * glyph invisible, as a fixed gray pair chosen for a white disc could.
 */
object TripMarkerBitmaps {

    /** The default disc fill, for a marker with no data colour of its own to take. */
    const val DEFAULT_FILL_COLOR = 0xFFFFFFFF.toInt()

    private const val OPAQUE_ALPHA = 0xFF000000.toInt()

    private const val ICON_SIZE_DP = 28

    @VisibleForTesting
    internal const val ICON_PADDING_DP = 4

    /**
     * The shared map-marker rim width (#2055) — the vehicle badge that these markers bracket converts
     * the same dp through its own geometry, so the two read as one family rather than a 2 dp ring beside
     * a hairline.
     */
    @VisibleForTesting
    internal const val STROKE_WIDTH_DP = MarkerRendering.MARKER_STROKE_DP

    /**
     * Bounded in **bytes**, not entries, for the reason [VehicleBitmaps]' cache is: a disc is ~28 KiB at
     * xxhdpi and ~50 KiB at xxxhdpi, so an entry count would mean wildly different memory on different
     * devices for the same nominal size.
     *
     * An LRU rather than the plain map this was, because taking the caller's fill turned a fixed key
     * space into an open one: it used to be two drawables x two tints — four bitmaps, for the life of the
     * process — and is now two drawables x however many band colours a session ever shows, which is a
     * fresh hue per route and per stop-focus adjacency slot. This is an `object`, so unbounded it would
     * outlive every renderer that filled it. 512 KiB holds ~18 discs at xxhdpi, against a working set of
     * exactly two (one selection's dot + fast estimate); the slack is what keeps flipping between
     * recently-visited selections off the render path. Overflow only costs a re-render.
     */
    private val cache = object : LruCache<Long, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }

    private const val MAX_CACHE_BYTES = 512 * 1024

    /** A circular marker for [drawableRes] centered on a disc filled [fillColor] (forced opaque). */
    fun circle(context: Context, drawableRes: Int, fillColor: Int = DEFAULT_FILL_COLOR): Bitmap {
        val opaqueFill = fillColor or OPAQUE_ALPHA
        val key = (drawableRes.toLong() shl 32) or (opaqueFill.toLong() and 0xFFFFFFFFL)
        return cache.get(key) ?: draw(context, drawableRes, opaqueFill).also { cache.put(key, it) }
    }

    private fun draw(context: Context, drawableRes: Int, fillColor: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (ICON_SIZE_DP * density).toInt()
        val padding = (ICON_PADDING_DP * density).toInt()
        val strokeWidth = STROKE_WIDTH_DP * density
        val center = sizePx / 2f
        val onFill = MarkerRendering.legibleOn(fillColor)

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        canvas.drawCircle(
            center,
            center,
            center - strokeWidth / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onFill
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }
        )
        canvas.drawCircle(
            center,
            center,
            center - strokeWidth,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style = Paint.Style.FILL
            }
        )

        // mutate(), because the glyph is now tinted per fill colour: without it the tint would be written
        // into the shared ConstantState and recolour every other use of the same drawable.
        ContextCompat.getDrawable(context, drawableRes)?.mutate()?.apply {
            setTint(onFill)
            setBounds(padding, padding, sizePx - padding, sizePx - padding)
            draw(canvas)
        }
        return bitmap
    }
}
