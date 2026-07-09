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
import android.graphics.Paint
import androidx.core.graphics.createBitmap

/**
 * The animation model for a one-shot map "ping" — a ring that radiates out from a point and fades, played
 * once to draw the eye to a just-focused vehicle (#1764). The timing/easing is pure (unit-tested); the
 * ring bitmap (for the maplibre flavor, whose classic annotation API has no circle) lives here too so both
 * flavors share the geometry. The Google flavor draws a native `Circle` from the same fractions.
 */
object MapPing {

    /** How long a single ping ripple lasts. */
    const val DURATION_MS = 700L

    /** The ring's maximum radius (dp) at the end of the ripple. */
    const val MAX_RADIUS_DP = 44f

    /** The ring stroke width (dp). */
    const val STROKE_DP = 3f

    /** Animation progress in `0..1` for [elapsedMs] since the ping started (clamped). */
    fun progress(elapsedMs: Long): Float = (elapsedMs.toFloat() / DURATION_MS).coerceIn(0f, 1f)

    /** Whether the ping has run its course (so the renderer can remove it). */
    fun isDone(elapsedMs: Long): Boolean = elapsedMs >= DURATION_MS

    /**
     * The ring radius as a fraction (`0..1`) of the max, easing out (decelerating) so it shoots out then
     * settles — the ripple's characteristic quick expansion.
     */
    fun radiusFraction(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t)
    }

    /** The ring alpha (`0..1`): full at the start, fading to 0, so the ripple dissolves as it expands. */
    fun alpha(progress: Float): Float = (1f - progress).coerceIn(0f, 1f)

    /** Applies [alpha01] (`0..1`) to the low 24 bits of [baseColor], producing an ARGB color. */
    fun withAlpha(baseColor: Int, alpha01: Float): Int =
        ((alpha01.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (baseColor and 0x00FFFFFF)

    /**
     * A stroked ring of [radiusPx] in [colorArgb], centered in a `2*[maxRadiusPx]` square — the maplibre
     * ping marker's icon. The square is a constant size (the max) so the marker stays centered on the
     * point as the ring grows inside it; the caller regenerates it each frame with a larger radius.
     */
    fun ringBitmap(maxRadiusPx: Int, radiusPx: Float, strokeWidthPx: Float, colorArgb: Int): Bitmap {
        val size = (maxRadiusPx * 2).coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val center = size / 2f
        Canvas(bitmap).drawCircle(center, center, radiusPx.coerceAtLeast(0f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = colorArgb
        })
        return bitmap
    }
}
