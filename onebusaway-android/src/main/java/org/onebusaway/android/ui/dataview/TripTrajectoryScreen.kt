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

import android.graphics.Paint
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val UI_TICK_MS = 1_000L

// Palette ported from the feature's TrajectoryGraphView.
private val ScheduleColor = Color(0xFF4488FF)
private val TrajectoryColor = Color(0xFF44CC44)
private val EstimateColor = Color(0xFFBBBBBB)
private val ConfidenceColor = Color(0x66BBBBBB)
private val PdfColor = Color(0x40BBBBBB)
private val GridColor = Color(0xFF333333)
private val NowColor = Color(0xFFFF4444)
private val DeviationColor = Color(0xFFFFAA00)
private val AxisColor = Color(0xFF888888)

// Dash patterns are constant, so build the PathEffects once instead of per Canvas draw.
private val CiDashes = PathEffect.dashPathEffect(floatArrayOf(24f, 16f))
private val DeviationDashes = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
private val NowDashes = PathEffect.dashPathEffect(floatArrayOf(16f, 8f))

/** A text Paint for canvas labels; [color] is an android.graphics.Color int. */
private fun textPaint(density: Density, color: Int, textSizeDp: Float, bold: Boolean = false) =
    Paint().apply {
        this.color = color
        textSize = with(density) { textSizeDp.dp.toPx() }
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

/**
 * Trip trajectory debug screen — a distance (X) vs server-clock-time (Y, newest on top) plot of a
 * vehicle's reported positions against the schedule and the live extrapolation. Ticks [refresh]
 * ~1×/sec while visible. (The full raw-sample table is a follow-up.)
 */
@Composable
fun TripTrajectoryRoute(viewModel: TripTrajectoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.refresh()
            delay(UI_TICK_MS)
        }
    }
    TripTrajectoryScreen(state, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTrajectoryScreen(state: TripTrajectoryUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trajectory") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val ended = if (state.tripEnded) " · trip ended" else ""
            Text(
                text = "Trip ${state.tripId} · vehicle ${state.vehicleId ?: "—"} · ${state.sampleCount} samples$ended",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            TrajectoryGraph(state.trajectory, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun TrajectoryGraph(trajectory: TripTrajectory, modifier: Modifier) {
    val density = LocalDensity.current
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val viewport = remember {
        with(density) {
            GraphViewport(
                marginLeft = 64.dp.toPx(),
                marginTop = 12.dp.toPx(),
                marginRight = 12.dp.toPx(),
                marginBottom = 28.dp.toPx(),
            )
        }
    }
    val labelPaint = remember(density) { textPaint(density, android.graphics.Color.LTGRAY, 15f) }
    val nowLabelPaint = remember(density) { textPaint(density, android.graphics.Color.parseColor("#FF4444"), 15f) }
    val deviationLabelPaint = remember(density) {
        textPaint(density, android.graphics.Color.parseColor("#FFAA00"), 16.5f, bold = true)
    }
    val timeFormat = remember(is24Hour) { if (is24Hour) "HH:mm:ss" else "h:mm:ss" }

    // Gestures mutate the (non-Compose) viewport; bump this to force the Canvas to redraw.
    var invalidations by remember { mutableIntStateOf(0) }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (zoom != 1f) viewport.applyScale(zoom, centroid.x, centroid.y, w, h)
                    if (pan != Offset.Zero) viewport.applyPan(-pan.x, -pan.y, w, h)
                    invalidations++
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    viewport.resetZoom()
                    invalidations++
                })
            }
    ) {
        invalidations // read so gesture mutations recompose this draw
        viewport.setDataBounds(
            trajectory.bounds.minDist, trajectory.bounds.maxDist,
            trajectory.bounds.minTime, trajectory.bounds.maxTime,
        )
        if (!viewport.setupVisibleWindow(size.width, size.height)) return@Canvas

        drawGrid(viewport, labelPaint, timeFormat)
        drawSchedule(viewport, trajectory.schedule)
        drawObservations(viewport, trajectory.observations)
        trajectory.extrapolation?.let { drawExtrapolation(viewport, it, labelPaint, deviationLabelPaint) }
        drawNowLine(viewport, trajectory.nowMs, nowLabelPaint, timeFormat)
    }
}

private fun DrawScope.drawGrid(viewport: GraphViewport, labelPaint: Paint, timeFormat: String) {
    val native = drawContext.canvas.nativeCanvas
    viewport.forEachDistTick { x, dist ->
        drawLine(GridColor, Offset(x, viewport.marginTop), Offset(x, viewport.graphBottom), 2f)
        native.drawText("${dist.toInt()}m", x + 2f, viewport.graphBottom + labelPaint.textSize, labelPaint)
    }
    viewport.forEachTimeTick { y, time ->
        drawLine(GridColor, Offset(viewport.marginLeft, y), Offset(viewport.graphRight, y), 2f)
        native.drawText(DateFormat.format(timeFormat, time).toString(), 4f, y - 2f, labelPaint)
    }

    // Axis baselines: solid Y (left) and X (bottom) edges of the plot area.
    drawLine(AxisColor, Offset(viewport.marginLeft, viewport.marginTop), Offset(viewport.marginLeft, viewport.graphBottom), 2f)
    drawLine(AxisColor, Offset(viewport.marginLeft, viewport.graphBottom), Offset(viewport.graphRight, viewport.graphBottom), 2f)
}

private fun DrawScope.drawSchedule(viewport: GraphViewport, schedule: List<ScheduleStop>) {
    if (schedule.isEmpty()) return
    var prev: Offset? = null
    schedule.forEach { stop ->
        val arrival = Offset(viewport.toPixelX(stop.distanceMeters), viewport.toPixelY(stop.arrivalMs))
        val departure = Offset(viewport.toPixelX(stop.distanceMeters), viewport.toPixelY(stop.departureMs))
        prev?.let { drawLine(ScheduleColor, it, arrival, 4f) }
        if (stop.departureMs != stop.arrivalMs) {
            drawLine(ScheduleColor, arrival, departure, 8f, cap = StrokeCap.Round) // dwell
        }
        drawCircle(ScheduleColor, radius = 8f, center = arrival)
        prev = departure
    }
}

private fun DrawScope.drawObservations(viewport: GraphViewport, observations: List<TrajectoryPoint>) {
    var prev: Offset? = null
    observations.forEach { point ->
        val p = Offset(viewport.toPixelX(point.distanceMeters), viewport.toPixelY(point.timeMs))
        prev?.let { drawLine(TrajectoryColor, it, p, 6f) }
        drawCircle(TrajectoryColor, radius = 6f, center = p)
        prev = p
    }
}

private fun DrawScope.drawExtrapolation(
    viewport: GraphViewport,
    series: ExtrapolationSeries,
    labelPaint: Paint,
    deviationLabelPaint: Paint,
) {
    val anchor = Offset(viewport.toPixelX(series.anchor.distanceMeters), viewport.toPixelY(series.anchor.timeMs))
    val median = Offset(viewport.toPixelX(series.medianMeters), viewport.toPixelY(series.nowMs))

    // 80% CI: dashed lines from the anchor to the low/high bounds at "now".
    val low = Offset(viewport.toPixelX(series.lowMeters), viewport.toPixelY(series.nowMs))
    val high = Offset(viewport.toPixelX(series.highMeters), viewport.toPixelY(series.nowMs))
    drawLine(ConfidenceColor, anchor, low, 3f, pathEffect = CiDashes)
    drawLine(ConfidenceColor, anchor, high, 3f, pathEffect = CiDashes)

    // Position PDF histogram along distance, anchored on the X axis (graph bottom), growing upward.
    if (series.pdf.size >= 2) {
        val baseY = viewport.graphBottom
        val maxPx = 96.dp.toPx()
        val path = Path().apply {
            moveTo(viewport.toPixelX(series.pdf.first().distanceMeters), baseY)
            series.pdf.forEach { bin ->
                lineTo(viewport.toPixelX(bin.distanceMeters), baseY - (bin.normalizedHeight.toFloat() * maxPx))
            }
            lineTo(viewport.toPixelX(series.pdf.last().distanceMeters), baseY)
            close()
        }
        drawPath(path, PdfColor)
    }

    // Median estimate, solid: anchor -> projected point where it meets the "now" line.
    drawLine(EstimateColor, anchor, median, 3f)

    // Projected-point drop: vertical line from the now-line intersection down to the X axis,
    // landing at the median distance — the centerline of the position PDF below.
    drawLine(EstimateColor, median, Offset(median.x, viewport.graphBottom), 3f, pathEffect = CiDashes)
    drawContext.canvas.nativeCanvas.drawText(
        "~${series.medianMeters.toInt()}m",
        median.x + 4f,
        viewport.graphBottom - 6f,
        labelPaint,
    )

    // Schedule deviation: an orange marker at the time the schedule says the vehicle reaches the
    // median distance, linked up to "now" by a dashed line; the gap is how early/late it's running.
    if (series.scheduleAtMedianMs > 0L) {
        val schedY = viewport.toPixelY(series.scheduleAtMedianMs)
        drawLine(DeviationColor, Offset(median.x, schedY), median, 3f, pathEffect = DeviationDashes)
        drawCircle(DeviationColor, radius = 10f, center = Offset(median.x, schedY))
        val devLabel = formatDeviationLabel((series.nowMs - series.scheduleAtMedianMs) / 1000)
        drawContext.canvas.nativeCanvas.drawText(
            devLabel,
            median.x + 5f,
            (schedY + median.y) / 2 + 4f,
            deviationLabelPaint,
        )
    }
}

private fun DrawScope.drawNowLine(viewport: GraphViewport, nowMs: Long, nowLabelPaint: Paint, timeFormat: String) {
    if (nowMs <= 0L) return
    val y = viewport.toPixelY(nowMs)
    if (y < viewport.marginTop || y > viewport.graphBottom) return
    drawLine(NowColor, Offset(viewport.marginLeft, y), Offset(viewport.graphRight, y), 2f, pathEffect = NowDashes)
    val label = "now ${DateFormat.format(timeFormat, nowMs)}"
    drawContext.canvas.nativeCanvas.drawText(label, viewport.marginLeft + 5f, y - 4f, nowLabelPaint)
}
