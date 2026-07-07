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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.annotation.DrawableRes
import org.onebusaway.android.R

/**
 * Flavor-neutral generation of the three bike marker bitmaps — the small dot and the big station /
 * floating-bike pins — so the Google flavor wraps them as `BitmapDescriptor`s and maplibre as `Icon`s.
 * Lifted from the old BikeStationOverlay.
 *
 * The two big markers are composited from [pin_base][R.drawable.pin_base] tinted the bikeshare navy
 * plus a white glyph (a bike dock for a station, a cyclist for a free-floating bike), rather than
 * decoding a pre-rendered raster. The teardrop fills the bitmap edge-to-edge with the tip at the
 * bottom, matching the former rasters so the renderers' marker anchors are unchanged.
 */
object BikeBitmaps {

    /** The bikeshare navy the old rasters were drawn in. */
    private const val PIN_COLOR = 0xFF3A4677.toInt()

    /** Big markers fill a square this many dp on a side (the former raster's size). */
    private const val BIG_SIZE_DP = 32f

    /** The glyph's 24-grid box (its artwork fills ~70% of this). */
    private const val GLYPH_SIZE = 11f

    // The three icons never vary, so cache them once. The maplibre renderer clears + redraws every
    // marker on each snapshot, so without this it would re-render these per bike per render.
    private var sSmall: Bitmap? = null
    private var sBigStation: Bitmap? = null
    private var sBigFloating: Bitmap? = null

    /** The small bike-dot bitmap, drawn from the [bike_marker_small][R.drawable.bike_marker_small] vector. */
    @JvmStatic
    fun small(context: Context): Bitmap = sSmall ?: run {
        val px = context.resources.getDimensionPixelSize(R.dimen.bikeshare_small_marker_size)
        MarkerRendering.rasterize(context, R.drawable.bike_marker_small, px).also { sSmall = it }
    }

    /** The large bike-station pin (navy pin + white bike-dock glyph). */
    @JvmStatic
    fun bigStation(context: Context): Bitmap =
        sBigStation ?: bigMarker(context, R.drawable.bike_dock).also { sBigStation = it }

    /** The large floating-bike pin (navy pin + white cyclist glyph). */
    @JvmStatic
    fun bigFloating(context: Context): Bitmap =
        sBigFloating ?: bigMarker(context, R.drawable.ic_directions_bike).also { sBigFloating = it }

    /** Composites pin_base (tinted navy) with a centered white [glyphRes] — no outline, tip at the bottom. */
    private fun bigMarker(context: Context, @DrawableRes glyphRes: Int): Bitmap {
        val scale = context.resources.displayMetrics.density * BIG_SIZE_DP / MarkerRendering.GRID
        val sizePx = (MarkerRendering.GRID * scale).toInt()
        val bitmap = createBitmap(sizePx, sizePx)
        MarkerRendering.drawPinAndGlyph(
            Canvas(bitmap), context, sizePx, scale, PIN_COLOR, glyphRes, Color.WHITE, GLYPH_SIZE, outline = 0f,
        )
        return bitmap
    }
}
