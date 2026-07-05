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
package org.onebusaway.android.ui.tripplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.onebusaway.android.R

/** The two rest positions of the form sheet: the full form (maximized) or the From/To summary bar. */
enum class FormAnchor { Collapsed, Expanded }

/**
 * The trip-plan form as a **top sheet** over the directions map: a top-anchored surface that slides down
 * from the top on first appearance (mirroring `MoreStopsBanner`'s slide-in in
 * [org.onebusaway.android.ui.home.map.MapFeature]).
 *
 * It drags like the directions sheet — the sheet's **height** is driven by [dragState] (its `offset` is
 * the visible body height), so dragging the bottom handle grows/shrinks it continuously and snaps between
 * two anchors: **expanded** (the full [TripPlanForm]) and **collapsed** (a compact `From → To` bar). The
 * two layouts **cross-fade** with the drag: the full form fades out and the bar fades in as it shrinks,
 * so the collapsed state reads as a distinct minimized summary rather than a clipped form. Both are
 * measured (via [onSizeChanged]) so the anchors land exactly on each layout's height.
 *
 * All form/date/contacts callbacks pass straight through to [TripPlanForm]; [onBack] and
 * [onReportProblem] are the former [org.onebusaway.android.ui.tripplan] toolbar actions, now hosted here.
 */
@Composable
fun TripPlanFormSheet(
    dragState: AnchoredDraggableState<FormAnchor>,
    state: TripPlanFormState,
    onFromQueryChange: (String) -> Unit,
    onToQueryChange: (String) -> Unit,
    onSelectFrom: (TripEndpoint.Geocoded) -> Unit,
    onSelectTo: (TripEndpoint.Geocoded) -> Unit,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
    onFromCurrentLocation: () -> Unit,
    onToCurrentLocation: () -> Unit,
    onFromContacts: () -> Unit,
    onToContacts: () -> Unit,
    onFromPickOnMap: () -> Unit,
    onToPickOnMap: () -> Unit,
    onSetArriving: (Boolean) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onReverse: () -> Unit,
    onAdvancedSettings: () -> Unit,
    onBack: () -> Unit,
    onReportProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Play the slide-in once, on first composition of the destination.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val density = LocalDensity.current
    // Cap the expanded form so it can't cover the whole map; the natural height is used when smaller.
    val maxFormPx = with(density) { (LocalConfiguration.current.screenHeightDp * 0.6f).dp.toPx() }
    // The two layouts' natural heights, reported by the content below; the anchors sit on these.
    var formPx by remember { mutableStateOf(0f) }
    var barPx by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    val expandedPx = min(formPx, maxFormPx)

    // Place the anchors once both layouts are measured: collapsed = the bar's height, expanded = the form
    // (capped). The offset between them is used directly as the sheet's body height and cross-fade point.
    LaunchedEffect(barPx, expandedPx) {
        if (barPx > 0f && expandedPx > barPx) {
            dragState.updateAnchors(
                DraggableAnchors {
                    FormAnchor.Collapsed at barPx
                    FormAnchor.Expanded at expandedPx
                }
            )
        }
    }

    // 0 at the collapsed (bar) anchor, 1 at the expanded (form) anchor — read at draw time so the
    // cross-fade follows the drag without recomposing. Defaults to fully expanded until anchored.
    val formAlpha = {
        val o = dragState.offset
        val range = expandedPx - barPx
        if (o.isNaN() || range <= 0f) 1f else ((o - barPx) / range).coerceIn(0f, 1f)
    }

    val toggle: () -> Unit = {
        scope.launch {
            runCatching {
                dragState.animateTo(
                    if (dragState.targetValue == FormAnchor.Expanded) FormAnchor.Collapsed else FormAnchor.Expanded
                )
            }
        }
    }
    // While collapsed, the bar (on top) is tappable to expand; while expanded it's non-interactive so
    // taps fall through to the real form fields beneath it.
    val collapsed = dragState.targetValue == FormAnchor.Collapsed

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            // Rounded bottom corners so it reads as a sheet hanging from the top edge; the top edge
            // tucks flush under the status bar (the content Column carries the inset).
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.statusBarsPadding()) {
                SheetHeader(onBack = onBack, onReportProblem = onReportProblem)
                // The body, clipped to the drag-controlled height. The full form and the compact bar are
                // stacked and cross-faded by [formAlpha]; the layout node reports `offset` as its height so
                // the sheet grows/shrinks with the drag.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val visible = dragState.offset
                                .let { if (it.isNaN()) placeable.height.toFloat() else it }
                                .roundToInt()
                                .coerceIn(0, placeable.height)
                            layout(placeable.width, visible) { placeable.place(0, 0) }
                        }
                ) {
                    // Full form (fades in as the sheet grows).
                    Box(
                        Modifier
                            .graphicsLayer { alpha = formAlpha() }
                            .onSizeChanged { formPx = it.height.toFloat() }
                    ) {
                        TripPlanForm(
                            state = state,
                            onFromQueryChange = onFromQueryChange,
                            onToQueryChange = onToQueryChange,
                            onSelectFrom = onSelectFrom,
                            onSelectTo = onSelectTo,
                            onClearFrom = onClearFrom,
                            onClearTo = onClearTo,
                            onFromCurrentLocation = onFromCurrentLocation,
                            onToCurrentLocation = onToCurrentLocation,
                            onFromContacts = onFromContacts,
                            onToContacts = onToContacts,
                            onFromPickOnMap = onFromPickOnMap,
                            onToPickOnMap = onToPickOnMap,
                            onSetArriving = onSetArriving,
                            onPickDate = onPickDate,
                            onPickTime = onPickTime,
                            onReverse = onReverse,
                            onAdvancedSettings = onAdvancedSettings,
                        )
                    }
                    // Compact From → To bar (fades in as the sheet shrinks), on top and tappable-to-expand
                    // only while collapsed so it doesn't shadow the form fields when open.
                    Box(
                        Modifier
                            .graphicsLayer { alpha = 1f - formAlpha() }
                            .onSizeChanged { barPx = it.height.toFloat() }
                            .then(if (collapsed) Modifier.clickable(onClick = toggle) else Modifier)
                    ) {
                        CompactRouteBar(from = state.from, to = state.to)
                    }
                }
                // The pull tab at the sheet's bottom edge: drag to resize, tap to toggle.
                FormDragHandle(dragState = dragState, onToggle = toggle)
            }
        }
    }
}

/** The form sheet's header: a back arrow and the "report trip problem" overflow menu. */
@Composable
private fun SheetHeader(onBack: () -> Unit, onReportProblem: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_up))
        }
        Text(
            text = stringResource(R.string.trip_plan_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        ReportProblemMenu(onReportProblem)
    }
}

/** The compact `From → To` summary shown when the sheet is minimized. */
@Composable
private fun CompactRouteBar(from: TripEndpoint, to: TripEndpoint) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EndpointSummary(endpoint = from)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EndpointSummary(endpoint = to)
        }
    }
}

/** A one-line label for an endpoint, resolving the fixed-label kinds to their string resources. */
@Composable
private fun EndpointSummary(endpoint: TripEndpoint) {
    val label = endpoint.displayText ?: when (endpoint) {
        is TripEndpoint.MapPoint -> stringResource(R.string.trip_plan_map_location)
        else -> stringResource(R.string.tripplanner_current_location)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The form sheet's bottom grab handle — its minimize/maximize affordance, symmetric to the directions
 * sheet's top handle. It carries the sheet's vertical drag ([dragState], driving the sheet height), and
 * a tap toggles the two modes via [onToggle]. Sits at the sheet's bottom edge as its pull tab.
 */
@Composable
private fun FormDragHandle(
    dragState: AnchoredDraggableState<FormAnchor>,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .anchoredDraggable(dragState, Orientation.Vertical)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colorResource(R.color.navdrawer_icon_tint),
            shape = RoundedCornerShape(percent = 50),
        ) {
            Box(Modifier.size(width = 32.dp, height = 4.dp))
        }
    }
}

/** The "report trip problem" overflow menu (ported from the old toolbar). */
@Composable
private fun ReportProblemMenu(onReportProblem: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    IconButton(onClick = { menuExpanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tripplanner_report_trip_problem)) },
            onClick = {
                menuExpanded = false
                onReportProblem()
            },
        )
    }
}
