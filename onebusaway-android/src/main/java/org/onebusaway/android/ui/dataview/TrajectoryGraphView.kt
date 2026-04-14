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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.io.elements.ObaTripSchedule
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.util.PreferenceUtils

private const val TICK_INTERVAL_MS = 1000L
private val BG_COLOR = Color.parseColor("#1A1A1A")
private const val PDF_NUM_BINS = 160
private const val METERS_PER_FOOT = 0.3048
private const val FEET_PER_MILE = 5280.0

/**
 * Custom View that draws a distance-time graph comparing scheduled vs actual vehicle trajectory.
 */
class TrajectoryGraphView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    // Data state
    private var history: List<ObaTripStatus> = emptyList()
    private var extrapolationAnchor: ObaTripStatus? = null
    private var anchorTimeMs: Long = 0L
    private var schedule: ObaTripSchedule? = null
    private var serviceDate = 0L
    // TODO: clock-domain mismatch. currentTime is local clock (System.currentTimeMillis),
    //  but the time axis is laid out against server-clock values — history[].lastUpdateTime
    //  and serviceDate + stopTime*1000. Any client/server clock skew shifts the red "now"
    //  line away from the latest trajectory dot. Fix by translating currentTime into server
    //  clock via Trip's fetchTimes/localFetchTimes offset.
    private var currentTime = System.currentTimeMillis()
    private var distribution: ProbDistribution? = null
    private var cachedCiLoDist = 0.0
    private var cachedCiHiDist = 0.0
    private var highlightedStopId: String? = null

    /** Index of the selected history point, or null. */
    var selectedIndex: Int? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Called when the user taps a data point (index) or empty space (null). */
    var onDataPointSelected: ((Int?) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val useImperial = !PreferenceUtils.getUnitsAreMetricFromPreferences(context)
    private var tickingActive = false

    private val viewport =
            GraphViewport(
                    marginLeft = 65 * density,
                    marginTop = 15 * density,
                    marginRight = 15 * density,
                    marginBottom = 35 * density
            )

    // --- Paints ---

    private fun strokePaint(colorStr: String, widthDp: Float, dashDp: FloatArray? = null) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(colorStr)
                style = Paint.Style.STROKE
                strokeWidth = widthDp * density
                dashDp?.let {
                    pathEffect = DashPathEffect(FloatArray(it.size) { i -> it[i] * density }, 0f)
                }
            }

    private fun fillPaint(colorStr: String) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(colorStr)
                style = Paint.Style.FILL
            }

    private fun textPaint(colorStr: String, sizeDp: Float, bold: Boolean = false) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(colorStr)
                textSize = sizeDp * density
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }

    private val schedulePaint = strokePaint("#4488FF", 2f)
    private val scheduleDotPaint = fillPaint("#4488FF")
    private val scheduleDwellPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4488FF")
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
    private val trajectoryPaint = strokePaint("#44CC44", 3f)
    private val trajectoryDotPaint = fillPaint("#44CC44")
    private val selectedDotPaint = fillPaint("#FFFFFF")
    private val nowLinePaint = strokePaint("#FF4444", 1f, dashDp = floatArrayOf(8f, 4f))
    private val nowLabelPaint = textPaint("#FF4444", 10f)
    private val axisPaint = strokePaint("#888888", 1f)
    private val labelPaint = textPaint("#AAAAAA", 10f)
    private val gridPaint = strokePaint("#333333", 0.5f)
    private val noDataPaint = textPaint("#FF4444", 14f)
    private val extrapolatePaint = strokePaint("#BBBBBB", 1.5f)
    private val extrapolateDashPaint = strokePaint("#BBBBBB", 1.5f, dashDp = floatArrayOf(6f, 4f))
    private val extrapolateLabelPaint = textPaint("#BBBBBB", 10f)
    private val deviationDotPaint = fillPaint("#FFAA00")
    private val deviationLabelPaint = textPaint("#FFAA00", 11f, bold = true)
    private val deviationLinePaint = strokePaint("#FFAA00", 1.5f, dashDp = floatArrayOf(4f, 3f))
    private val confidenceBandPaint = strokePaint("#66BBBBBB", 1f, dashDp = floatArrayOf(4f, 4f))
    private val pdfFillPaint = fillPaint("#40BBBBBB")

    // Reusable drawing objects
    private val schedulePath = Path()
    private val trajectoryPath = Path()
    private val pdfPath = Path()
    private val pdfValues = DoubleArray(PDF_NUM_BINS)
    private val reusableDate = Date()

    // Gesture detectors
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, PanListener())

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable =
            object : Runnable {
                override fun run() {
                    currentTime = System.currentTimeMillis()
                    invalidate()
                    tickHandler.postDelayed(this, TICK_INTERVAL_MS)
                }
            }

    // --- Public API ---

    fun setHighlightedStopId(stopId: String?) {
        highlightedStopId = stopId
        invalidate()
    }

    fun setData(
            history: List<ObaTripStatus>?,
            schedule: ObaTripSchedule?,
            serviceDate: Long,
            distribution: ProbDistribution?,
            anchor: ObaTripStatus? = null,
            anchorTime: Long = 0L
    ) {
        this.history = history?.toList() ?: emptyList()
        extrapolationAnchor = anchor ?: findExtrapolationAnchor(this.history)
        this.anchorTimeMs =
                if (anchorTime > 0) anchorTime else extrapolationAnchor?.lastUpdateTime ?: 0L
        this.schedule = schedule
        this.serviceDate = serviceDate
        this.distribution = distribution
        if (distribution != null) {
            cachedCiLoDist = distribution.quantile(0.10)
            cachedCiHiDist = distribution.quantile(0.90)
        } else {
            cachedCiLoDist = 0.0
            cachedCiHiDist = 0.0
        }
        currentTime = System.currentTimeMillis()
        updateTicking()
        invalidate()
    }

    // --- Lifecycle ---

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTicking()
    }

    override fun onDetachedFromWindow() {
        stopTicking()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateTicking()
    }

    private fun updateTicking() {
        if (isAttachedToWindow && visibility == VISIBLE && distribution != null) startTicking()
        else stopTicking()
    }

    private fun startTicking() {
        if (!tickingActive) {
            tickingActive = true
            tickHandler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
        }
    }

    private fun stopTicking() {
        if (tickingActive) {
            tickingActive = false
            tickHandler.removeCallbacks(tickRunnable)
        }
    }

    // --- Touch handling ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (viewport.isZoomed || scaleDetector.isInProgress) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }

    // --- Drawing ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(BG_COLOR)

        if (!computeAndSetDataBounds()) {
            drawNoData(canvas)
            return
        }
        if (!viewport.setupVisibleWindow(width.toFloat(), height.toFloat())) return

        drawAxesAndLabels(canvas)

        canvas.save()
        canvas.clipRect(
                viewport.marginLeft,
                viewport.marginTop,
                viewport.graphRight,
                viewport.graphBottom
        )

        drawGridLines(canvas)
        drawScheduleLine(canvas)
        drawTrajectoryDots(canvas)

        val lastDist = extrapolationAnchor?.distanceAlongTrip
        val lastTime = anchorTimeMs

        drawExtrapolationAndDeviation(canvas, lastDist, lastTime)
        drawGammaPdfAndBands(canvas, lastDist, lastTime)
        drawNowLine(canvas)

        canvas.restore()
        drawLegend(canvas)
    }

    private fun computeAndSetDataBounds(): Boolean {
        var minDist = 0.0
        var maxDist = 0.0
        var minTime = Long.MAX_VALUE
        var maxTime = Long.MIN_VALUE
        var hasData = false

        val sched = schedule
        if (sched?.stopTimes != null && serviceDate > 0) {
            for (st in sched.stopTimes) {
                val d = st.distanceAlongTrip
                val t = serviceDate + st.arrivalTime * 1000L
                if (d > maxDist) maxDist = d
                if (t < minTime) minTime = t
                if (t > maxTime) maxTime = t
                hasData = true
            }
        }

        for (e in history) {
            e.distanceAlongTrip?.let { d ->
                if (d > maxDist) maxDist = d
                if (d < minDist) minDist = d
            }
            val t = e.lastUpdateTime
            if (t > 0) {
                if (t < minTime) minTime = t
                if (t > maxTime) maxTime = t
                hasData = true
            }
        }

        if (!hasData) return false

        if (currentTime < minTime) minTime = currentTime
        if (currentTime + 60_000 > maxTime) maxTime = currentTime + 60_000

        var distRange = maxDist - minDist
        if (distRange < 100) distRange = 100.0
        maxDist = minDist + distRange * 1.05
        minDist = max(0.0, minDist - distRange * 0.02)

        var timeRange = maxTime - minTime
        if (timeRange < 60_000) timeRange = 60_000
        minTime -= timeRange / 20
        maxTime += timeRange / 20

        viewport.setDataBounds(minDist, maxDist, minTime, maxTime)
        return true
    }

    private fun drawNoData(canvas: Canvas) {
        canvas.drawText(
                "No data available",
                viewport.marginLeft + 10 * density,
                height / 2f,
                noDataPaint
        )
    }

    private fun drawAxesAndLabels(canvas: Canvas) {
        canvas.drawLine(
                viewport.marginLeft,
                viewport.marginTop,
                viewport.marginLeft,
                viewport.graphBottom,
                axisPaint
        )
        canvas.drawLine(
                viewport.marginLeft,
                viewport.graphBottom,
                viewport.graphRight,
                viewport.graphBottom,
                axisPaint
        )

        viewport.forEachTimeTick { y, time ->
            if (y in viewport.marginTop..viewport.graphBottom) {
                reusableDate.time = time
                canvas.drawText(
                        timeFmt.format(reusableDate),
                        4 * density,
                        y + 4 * density,
                        labelPaint
                )
            }
        }

        viewport.forEachDistTick { x, dist ->
            if (x in viewport.marginLeft..viewport.graphRight) {
                canvas.drawText(
                        formatDist(dist),
                        x - 10 * density,
                        viewport.graphBottom + 15 * density,
                        labelPaint
                )
            }
        }
    }

    private fun drawGridLines(canvas: Canvas) {
        viewport.forEachTimeTick { y, _ ->
            canvas.drawLine(viewport.marginLeft, y, viewport.graphRight, y, gridPaint)
        }
        viewport.forEachDistTick { x, _ ->
            canvas.drawLine(x, viewport.marginTop, x, viewport.graphBottom, gridPaint)
        }
    }

    private fun drawScheduleLine(canvas: Canvas) {
        val sched = schedule ?: return
        val stops = sched.stopTimes ?: return
        if (stops.isEmpty() || serviceDate <= 0) return

        schedulePath.reset()
        var first = true
        for (st in stops) {
            val x = viewport.toPixelX(st.distanceAlongTrip)
            val yArrive = viewport.toPixelY(serviceDate + st.arrivalTime * 1000L)
            val yDepart = viewport.toPixelY(serviceDate + st.departureTime * 1000L)
            if (first) {
                schedulePath.moveTo(x, yArrive)
                first = false
            } else schedulePath.lineTo(x, yArrive)
            val dotRadius = if (highlightedStopId == st.stopId) 8 * density else 4 * density
            if (st.departureTime != st.arrivalTime) {
                schedulePath.lineTo(x, yDepart)
                scheduleDwellPaint.strokeWidth = dotRadius * 2
                canvas.drawLine(x, yArrive, x, yDepart, scheduleDwellPaint)
            } else {
                canvas.drawCircle(x, yArrive, dotRadius, scheduleDotPaint)
            }
        }
        canvas.drawPath(schedulePath, schedulePaint)
    }

    private fun drawTrajectoryDots(canvas: Canvas) {
        if (history.isEmpty()) return
        trajectoryPath.reset()
        var first = true
        for ((i, e) in history.withIndex()) {
            val d = e.distanceAlongTrip ?: continue
            val t = e.lastUpdateTime
            if (t <= 0) continue
            val x = viewport.toPixelX(d)
            val y = viewport.toPixelY(t)
            if (first) {
                trajectoryPath.moveTo(x, y)
                first = false
            } else trajectoryPath.lineTo(x, y)
            val selected = i == selectedIndex
            val radius = if (selected) 6 * density else 3 * density
            val paint = if (selected) selectedDotPaint else trajectoryDotPaint
            canvas.drawCircle(x, y, radius, paint)
        }
        canvas.drawPath(trajectoryPath, trajectoryPaint)
    }

    private fun drawExtrapolationAndDeviation(canvas: Canvas, lastDist: Double?, lastTime: Long) {
        val dist = distribution ?: return
        if (lastDist == null || currentTime <= lastTime) return

        val extrapolatedDist = dist.median()

        val x1 = viewport.toPixelX(lastDist)
        val y1 = viewport.toPixelY(lastTime)
        val x2 = viewport.toPixelX(extrapolatedDist)
        val y2 = viewport.toPixelY(currentTime)
        canvas.drawLine(x1, y1, x2, y2, extrapolatePaint)

        canvas.drawLine(x2, y2, x2, viewport.graphBottom, extrapolateDashPaint)
        canvas.drawText(
                "~${formatDistPrecise(extrapolatedDist)}",
                x2 - 10 * density,
                viewport.graphBottom - 5 * density,
                extrapolateLabelPaint
        )

        val scheduledTime = interpolateScheduleTime(extrapolatedDist)
        if (scheduledTime > 0) {
            val schedY = viewport.toPixelY(scheduledTime)
            canvas.drawCircle(x2, schedY, 5 * density, deviationDotPaint)
            canvas.drawLine(x2, schedY, x2, y2, deviationLinePaint)
            val devLabel = formatDeviationLabel((currentTime - scheduledTime) / 1000)
            canvas.drawText(
                    devLabel,
                    x2 + 5 * density,
                    (schedY + y2) / 2 + 4 * density,
                    deviationLabelPaint
            )
        }
    }

    private fun drawGammaPdfAndBands(canvas: Canvas, lastDist: Double?, lastTime: Long) {
        val dist = distribution ?: return
        if (lastDist == null || currentTime <= lastTime) return

        val maxHeightPx = 105 * density
        val posMin = dist.quantile(0.0)
        val posMax = dist.quantile(0.95)
        if (posMax <= posMin) return

        val binWidth = (posMax - posMin) / PDF_NUM_BINS

        var maxVal = 0.0
        for (i in 0 until PDF_NUM_BINS) {
            val pos = posMin + (i + 0.5) * binWidth
            val v = dist.pdf(pos)
            pdfValues[i] = v
            if (v > maxVal) maxVal = v
        }

        if (maxVal > 0) {
            pdfPath.reset()
            pdfPath.moveTo(viewport.toPixelX(posMin), viewport.graphBottom)
            for (i in 0 until PDF_NUM_BINS) {
                val pos = posMin + (i + 0.5) * binWidth
                val h = (pdfValues[i] / maxVal * maxHeightPx).toFloat()
                pdfPath.lineTo(viewport.toPixelX(pos), viewport.graphBottom - h)
            }
            pdfPath.lineTo(viewport.toPixelX(posMax), viewport.graphBottom)
            pdfPath.close()
            canvas.drawPath(pdfPath, pdfFillPaint)
        }

        // 80% CI bands
        val xStart = viewport.toPixelX(lastDist)
        val yStart = viewport.toPixelY(lastTime)
        val yNow = viewport.toPixelY(currentTime)
        canvas.drawLine(
                xStart,
                yStart,
                viewport.toPixelX(cachedCiLoDist),
                yNow,
                confidenceBandPaint
        )
        canvas.drawLine(
                xStart,
                yStart,
                viewport.toPixelX(cachedCiHiDist),
                yNow,
                confidenceBandPaint
        )
    }

    private fun drawNowLine(canvas: Canvas) {
        val nowY = viewport.toPixelY(currentTime)
        if (nowY in viewport.marginTop..viewport.graphBottom) {
            canvas.drawLine(viewport.marginLeft, nowY, viewport.graphRight, nowY, nowLinePaint)
            reusableDate.time = currentTime
            canvas.drawText(
                    "now ${timeFmt.format(reusableDate)}",
                    viewport.marginLeft + 5 * density,
                    nowY - 4 * density,
                    nowLabelPaint
            )
        }
    }

    private fun drawLegend(canvas: Canvas) {
        val lx = viewport.marginLeft + 10 * density
        var ly = viewport.marginTop + 15 * density
        val swatchW = 20 * density
        val textX = lx + 25 * density
        val textDy = 4 * density
        val rowH = 18 * density

        canvas.drawLine(lx, ly, lx + swatchW, ly, schedulePaint)
        canvas.drawText("Schedule", textX, ly + textDy, labelPaint)

        ly += rowH
        canvas.drawLine(lx, ly, lx + swatchW, ly, trajectoryPaint)
        canvas.drawCircle(lx + swatchW / 2, ly, 3 * density, trajectoryDotPaint)
        canvas.drawText("Actual (GPS)", textX, ly + textDy, labelPaint)

        ly += rowH
        canvas.drawLine(lx, ly, lx + swatchW, ly, extrapolateDashPaint)
        canvas.drawText("Estimated", textX, ly + textDy, labelPaint)

        ly += rowH
        canvas.drawLine(lx, ly, lx + swatchW, ly, confidenceBandPaint)
        canvas.drawText("80% CI", textX, ly + textDy, labelPaint)

        ly += rowH
        canvas.drawRect(lx, ly - 5 * density, lx + swatchW, ly + 5 * density, pdfFillPaint)
        canvas.drawText("Position PDF", textX, ly + textDy, labelPaint)
    }

    // --- Schedule interpolation ---

    private fun interpolateScheduleTime(distanceMeters: Double): Long {
        val stops = schedule?.stopTimes ?: return 0
        if (stops.size < 2) return 0

        for (i in 1 until stops.size) {
            val d0 = stops[i - 1].distanceAlongTrip
            val d1 = stops[i].distanceAlongTrip
            if (distanceMeters in d0..d1 && d1 > d0) {
                val fraction = (distanceMeters - d0) / (d1 - d0)
                val t0 = serviceDate + stops[i - 1].departureTime * 1000L
                val t1 = serviceDate + stops[i].arrivalTime * 1000L
                return t0 + (fraction * (t1 - t0)).toLong()
            }
        }
        return 0
    }

    // --- Formatting ---

    private fun formatDist(meters: Double): String =
            if (useImperial) {
                val feet = meters / METERS_PER_FOOT
                if (feet >= FEET_PER_MILE) "%.1fmi".format(Locale.US, feet / FEET_PER_MILE)
                else "%.0fft".format(Locale.US, feet)
            } else {
                if (meters >= 1000) "%.1fkm".format(Locale.US, meters / 1000.0)
                else "%.0fm".format(Locale.US, meters)
            }

    private fun formatDistPrecise(meters: Double): String =
            if (useImperial) {
                "%.0fft".format(Locale.US, meters / METERS_PER_FOOT)
            } else {
                "%.0fm".format(Locale.US, meters)
            }

    // --- Gesture listeners ---

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.applyScale(
                    detector.scaleFactor,
                    detector.focusX,
                    detector.focusY,
                    width.toFloat(),
                    height.toFloat()
            )
            invalidate()
            return true
        }
    }

    private inner class PanListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
        ): Boolean {
            viewport.applyPan(distanceX, distanceY, width.toFloat(), height.toFloat())
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val hitRadius = 20 * density
            var bestIndex: Int? = null
            var bestDistSq = hitRadius * hitRadius
            for ((i, entry) in history.withIndex()) {
                val d = entry.distanceAlongTrip ?: continue
                val t = entry.lastUpdateTime
                if (t <= 0) continue
                val dx = e.x - viewport.toPixelX(d)
                val dy = e.y - viewport.toPixelY(t)
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestIndex = i
                }
            }
            selectedIndex = bestIndex
            onDataPointSelected?.invoke(bestIndex)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            viewport.resetZoom()
            invalidate()
            return true
        }
    }

    companion object {
        private fun formatDeviationLabel(devSeconds: Long): String {
            if (devSeconds == 0L) return "on time"
            val absSeconds = abs(devSeconds)
            val magnitude =
                    if (absSeconds >= 60) {
                        val mins = absSeconds / 60
                        val secs = absSeconds % 60
                        "${mins}m" + if (secs > 0) "${secs}s" else ""
                    } else {
                        "${absSeconds}s"
                    }
            return magnitude + if (devSeconds > 0) " late" else " early"
        }

        private fun findExtrapolationAnchor(history: List<ObaTripStatus>): ObaTripStatus? {
            for (i in history.indices.reversed()) {
                val s = history[i]
                if (s.distanceAlongTrip != null && s.lastUpdateTime > 0) return s
            }
            return null
        }
    }
}
