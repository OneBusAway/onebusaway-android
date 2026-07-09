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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The pure animation model for a one-shot map "ping" — a ring that radiates out from a point and fades,
 * played once to draw the eye to a just-focused vehicle (#1764). Just timing/easing/color (unit-tested);
 * each flavor's renderer draws with its own SDK primitive from these fractions (Google a native `Circle`,
 * maplibre a stroked-ring bitmap marker). Timing is expressed as a [Duration] elapsed since the ping
 * started (the renderer subtracts two `WallTime`s), so no raw-millis time math leaks in.
 */
object MapPing {

    /** How long a single ping ripple lasts. */
    val DURATION: Duration = 700.milliseconds

    /**
     * The most the ping waits for the framing pan to settle (the next camera idle) before playing anyway.
     * A fallback for a fit that doesn't actually move the camera (already framed); a real pan settles first.
     */
    val SETTLE_TIMEOUT: Duration = 1500.milliseconds

    /** The ring's maximum radius (dp) at the end of the ripple. */
    const val MAX_RADIUS_DP = 44f

    /** The ring stroke width (dp). */
    const val STROKE_DP = 3f

    /** Animation progress in `0..1` for [elapsed] since the ping started (clamped). */
    fun progress(elapsed: Duration): Float = (elapsed / DURATION).toFloat().coerceIn(0f, 1f)

    /** Whether the ping has run its course (so the renderer can remove it). */
    fun isDone(elapsed: Duration): Boolean = elapsed >= DURATION

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
}
