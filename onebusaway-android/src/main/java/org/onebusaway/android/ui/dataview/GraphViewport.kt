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
package org.onebusaway.android.ui.dataview

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Distance-time coordinate system for the trajectory graph. Manages data bounds, zoom/pan state,
 * and pixel coordinate mapping.
 */
class GraphViewport(
        val marginLeft: Float,
        val marginTop: Float,
        val marginRight: Float,
        val marginBottom: Float
) {
    // Full data bounds (set by setDataBounds)
    var fullMinDist = 0.0
        private set
    var fullMaxDist = 0.0
        private set
    var fullMinTime = 0L
        private set
    var fullMaxTime = 0L
        private set

    // Zoom/pan state (mutated by applyScale, applyPan, resetZoom)
    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetDist = 0.0
    private var offsetTime = 0L

    // Visible window (set by setupVisibleWindow)
    var graphW = 0f
        private set
    var graphH = 0f
        private set
    var visMinDist = 0.0
        private set
    var visDistRange = 0.0
        private set
    var visMinTime = 0L
        private set
    var visTimeRange = 0L
        private set

    val graphRight
        get() = marginLeft + graphW
    val graphBottom
        get() = marginTop + graphH
    val isZoomed
        get() = scaleX > 1f || scaleY > 1f

    fun setDataBounds(minDist: Double, maxDist: Double, minTime: Long, maxTime: Long) {
        fullMinDist = minDist
        fullMaxDist = maxDist
        fullMinTime = minTime
        fullMaxTime = maxTime
        clampOffsets()
    }

    /**
     * Recomputes the visible window from view dimensions and current zoom/pan. Returns false if the
     * graph area is too small to draw.
     */
    fun setupVisibleWindow(viewWidth: Float, viewHeight: Float): Boolean {
        graphW = viewWidth - marginLeft - marginRight
        graphH = viewHeight - marginTop - marginBottom
        if (graphW <= 0 || graphH <= 0) return false

        visDistRange = (fullMaxDist - fullMinDist) / scaleX
        visTimeRange = ((fullMaxTime - fullMinTime) / scaleY).toLong()
        visMinDist = fullMinDist + offsetDist
        visMinTime = fullMinTime + offsetTime
        return true
    }

    fun resetZoom() {
        scaleX = 1f
        scaleY = 1f
        offsetDist = 0.0
        offsetTime = 0L
    }

    private fun clampOffsets() {
        val fullDistRange = fullMaxDist - fullMinDist
        val fullTimeRange = fullMaxTime - fullMinTime
        if (fullDistRange <= 0 || fullTimeRange <= 0) return

        val visDist = fullDistRange / scaleX
        val visTime = (fullTimeRange / scaleY).toLong()
        offsetDist = max(0.0, min(offsetDist, fullDistRange - visDist))
        offsetTime = max(0L, min(offsetTime, fullTimeRange - visTime))
    }

    /**
     * Applies a focal-point-preserving pinch zoom. The data-space point under [focusX],[focusY]
     * stays fixed on screen.
     */
    fun applyScale(
            factor: Float,
            focusX: Float,
            focusY: Float,
            viewWidth: Float,
            viewHeight: Float
    ) {
        val gw = viewWidth - marginLeft - marginRight
        val gh = viewHeight - marginTop - marginBottom
        if (gw <= 0 || gh <= 0) return

        val fullDistRange = fullMaxDist - fullMinDist
        val fullTimeRange = fullMaxTime - fullMinTime
        if (fullDistRange <= 0 || fullTimeRange <= 0) return

        // Data-space point under the focal point before scaling
        val visDistRange = fullDistRange / scaleX
        val visTimeRange = (fullTimeRange / scaleY).toLong()
        val focalDist = fullMinDist + offsetDist + visDistRange * ((focusX - marginLeft) / gw)
        val focalTime =
                fullMinTime + offsetTime + visTimeRange -
                        (visTimeRange * ((focusY - marginTop) / gh)).toLong()

        scaleX = max(1f, min(20f, scaleX * factor))
        scaleY = max(1f, min(20f, scaleY * factor))

        // Adjust offsets so the focal data point stays under the finger
        val newVisDistRange = fullDistRange / scaleX
        val newVisTimeRange = (fullTimeRange / scaleY).toLong()
        offsetDist = focalDist - fullMinDist - newVisDistRange * ((focusX - marginLeft) / gw)
        offsetTime =
                focalTime - fullMinTime - newVisTimeRange +
                        (newVisTimeRange * ((focusY - marginTop) / gh)).toLong()

        clampOffsets()
    }

    /** Applies a pan gesture, converting pixel deltas to data-space offsets. */
    fun applyPan(distanceX: Float, distanceY: Float, viewWidth: Float, viewHeight: Float) {
        val gw = viewWidth - marginLeft - marginRight
        val gh = viewHeight - marginTop - marginBottom
        if (gw <= 0 || gh <= 0) return

        val fullDistRange = fullMaxDist - fullMinDist
        val fullTimeRange = fullMaxTime - fullMinTime
        val visDistRange = fullDistRange / scaleX
        val visTimeRange = (fullTimeRange / scaleY).toLong()

        offsetDist += visDistRange * (distanceX / gw)
        offsetTime -= (visTimeRange * (distanceY / gh)).toLong()

        clampOffsets()
    }

    fun toPixelX(dist: Double): Float =
            marginLeft + graphW * ((dist - visMinDist) / visDistRange).toFloat()

    fun toPixelY(time: Long): Float =
            marginTop + graphH * (1f - (time - visMinTime).toFloat() / visTimeRange)

    inline fun forEachTimeTick(action: (y: Float, time: Long) -> Unit) {
        val visMaxTime = visMinTime + visTimeRange
        val step = max(10_000L, niceStep(visTimeRange / 5.0).toLong())
        var t = ((visMinTime / step) + 1) * step
        while (t < visMaxTime) {
            action(toPixelY(t), t)
            t += step
        }
    }

    inline fun forEachDistTick(action: (x: Float, dist: Double) -> Unit) {
        val visMaxDist = visMinDist + visDistRange
        val step = niceStep(visDistRange / 5.0)
        var d = ceil(visMinDist / step) * step
        while (d < visMaxDist) {
            action(toPixelX(d), d)
            d += step
        }
    }

    companion object {
        fun niceStep(raw: Double): Double {
            if (raw <= 0) return 1.0
            val magnitude = 10.0.pow(floor(log10(raw)))
            val residual = raw / magnitude
            return when {
                residual <= 1.5 -> magnitude
                residual <= 3.5 -> 2 * magnitude
                residual <= 7.5 -> 5 * magnitude
                else -> 10 * magnitude
            }
        }
    }
}
