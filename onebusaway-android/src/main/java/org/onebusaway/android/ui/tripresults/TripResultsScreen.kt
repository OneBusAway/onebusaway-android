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
package org.onebusaway.android.ui.tripresults

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.realtime.TripPlanMonitor
import org.onebusaway.android.directions.realtime.TripPlanNotifications
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.EtaDurationText
import org.onebusaway.android.ui.compose.components.EtaPartsText
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.RouteBadgeChip
import org.onebusaway.android.ui.compose.components.ScrollChevronGutter
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.parseObaHexColor

/**
 * The results header: the (1–3) itinerary option cards. Shown above the directions list, pinned at the
 * top of the results sheet. Empty until the first [TripResultsUiState.Success]. The map is revealed by
 * dragging this sheet down — it renders as the scaffold body behind it ([TripResultsMap]) — so there is
 * no list/map tab here (#1640).
 */
@Composable
fun TripResultsHeader(
    state: TripResultsUiState,
    onSelectOption: (Int) -> Unit
) {
    val success = state as? TripResultsUiState.Success ?: return
    // Side-scrollable so options never get squished: each card sizes to its own content (route/lines,
    // duration, walk distance, time) and the row scrolls horizontally when they overflow the width.
    // Flanked by the same overflow chevrons as the ETA strip (ScrollChevronGutter) so the user can see
    // — and jump to — options hanging off either edge.
    val scrollState = rememberScrollState()
    val canScrollBackward by remember { derivedStateOf { scrollState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }
    val scope = rememberCoroutineScope()

    // Jump one viewport toward an edge (or to that end, whichever is closer — animateScrollTo clamps).
    fun jump(forward: Boolean) {
        val delta = if (forward) scrollState.viewportSize else -scrollState.viewportSize
        scope.launch { scrollState.animateScrollTo(scrollState.value + delta) }
    }
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScrollChevronGutter(
            visible = canScrollBackward,
            pointsRight = false,
            contentDescriptionRes = R.string.trip_plan_options_scroll_previous,
            onClick = { jump(forward = false) }
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            success.options.forEachIndexed { index, option ->
                OptionCard(
                    option = option,
                    selected = index == success.selectedIndex,
                    onClick = { onSelectOption(index) }
                )
            }
        }
        ScrollChevronGutter(
            visible = canScrollForward,
            pointsRight = true,
            contentDescriptionRes = R.string.trip_plan_options_scroll_more,
            onClick = { jump(forward = true) }
        )
    }
}

@Composable
private fun OptionCard(
    option: ItineraryOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = colorResource(
        if (selected) R.color.trip_plan_card_background_selected else R.color.trip_plan_card_background
    )
    val textColor = colorResource(
        if (selected) R.color.trip_plan_header_text_selected else R.color.trip_plan_header_text
    )
    val context = LocalContext.current
    Surface(
        color = background,
        contentColor = textColor,
        shape = MaterialTheme.shapes.small,
        // Wrap to the content width (a sensible floor so short options aren't tiny); the row scrolls.
        modifier = Modifier
            .widthIn(min = 104.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // The modes: transit route badges (no comma between), a walk glyph for a walk-only trip, or
            // a mode label for other non-transit trips.
            when (val mode = option.mode) {
                is ModeSummary.Routes -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    mode.badges.forEach { RouteBadgeChip(it.shortName, it.routeColor) }
                }
                ModeSummary.Walk -> Icon(
                    painterResource(R.drawable.ic_directions_walk),
                    contentDescription = stringResource(R.string.step_by_step_non_transit_mode_walk_action),
                    // Sized to the route-badge row height so a walk-only card's first line matches.
                    modifier = Modifier.size(20.dp)
                )
                is ModeSummary.Label -> Text(
                    text = mode.text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
            // Duration + walk distance read as one stat group, so they sit tighter together than the
            // card's other lines.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Duration — a leading hourglass + the ETA-pill-formatted trip length.
                MetricRow(R.drawable.hourglass_24, contentDescription = null) {
                    EtaDurationText(minutes = option.durationMinutes)
                }
                // Total walking for the trip — a leading walk glyph mirroring the duration row's hourglass,
                // with the distance styled like the duration (bold value + smaller unit). In the user's units
                // (miles/km, or feet/meters for short walks). Hidden when the trip has no walking.
                if (option.walkDistanceMeters > 0.0) {
                    MetricRow(
                        R.drawable.ic_directions_walk,
                        contentDescription = stringResource(R.string.step_by_step_non_transit_mode_walk_action)
                    ) {
                        EtaPartsText(ConversionUtils.getFormattedDistanceParts(option.walkDistanceMeters, context))
                    }
                }
            }
            // The device-localized departure–arrival range (unwrap the server clock only here).
            Text(
                text = "${DisplayFormat.formatTime(context, option.startTime.epochMs)} – " +
                    DisplayFormat.formatTime(context, option.endTime.epochMs),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

/**
 * One option-card stat line: a 16dp leading glyph followed by its value [content]. Shared by the
 * duration and walk-distance rows so their icon size and spacing stay in lockstep.
 */
@Composable
private fun MetricRow(iconRes: Int, contentDescription: String?, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp)
        )
        content()
    }
}

/**
 * The directions list (or the loading/error state), filling the results sheet. On [Success][
 * TripResultsUiState.Success] the option-card picker ([TripResultsHeader]) rides along as the list's
 * first item so it scrolls out of sight as the user moves down the steps, rather than staying pinned.
 * The map is the scaffold body behind the sheet ([TripResultsMap]), not a sibling tab.
 */
@Composable
fun TripResultsList(
    state: TripResultsUiState,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    onSelectOption: (Int) -> Unit = {},
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit = { _, _ -> },
    onFocusLeg: (List<GeoPoint>) -> Unit = {},
    onFocusPoint: (GeoPoint) -> Unit = {},
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit = { _, _, _ -> }
) {
    Box(
        modifier
            .fillMaxSize()
            .background(colorResource(R.color.md_theme_surfaceContainer))
    ) {
        when (state) {
            TripResultsUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))

            is TripResultsUiState.Error -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )

            // The surface reaches the bottom edge; a bottom content padding lets the final leg card
            // be scrolled clear of the nav chrome without an empty strip below the list.
            is TripResultsUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset)
            ) {
                // The picker scrolls with the steps (not pinned), so it recedes as you read down the list.
                item {
                    TripResultsHeader(state, onSelectOption)
                    HorizontalDivider()
                }
                // The whole itinerary as one continuous timeline "log" — a single item so the spine and
                // per-leg bands can be drawn across the leg boundaries (the list is small: a handful of
                // legs plus their stops/steps).
                item {
                    TripLog(
                        entries = state.directions,
                        onFocusRouteLeg = onFocusRouteLeg,
                        onFocusLeg = onFocusLeg,
                        onFocusPoint = onFocusPoint,
                        stopEtaStrip = stopEtaStrip
                    )
                }
            }
        }
    }
}

/**
 * The trip-results **sheet content**: the header (option cards) plus the directions list. Drives the
 * [TripResultsViewModel] (option cards + directions) — seeds the plan and follows option selection onto
 * the map via the [showItinerary] callback — and starts the background trip-update poller when the user
 * has trip-update notifications enabled. The caller's [showItinerary] both draws and frames the itinerary
 * (deferring the frame until the map is ready), so no separate "map ready" step is needed here.
 *
 * The map itself is deliberately **not** drawn here — the host (the home map's directions focus) owns the
 * map surface; this composable only supplies the results header + directions list, keeping an interactive
 * map out of a draggable bottom sheet where it would fight the sheet's drags (#1640).
 */
@Composable
fun TripResultsSheet(
    itineraries: List<TripItinerary>,
    params: TripPlanParams?,
    resultsViewModel: TripResultsViewModel,
    showItinerary: (TripItinerary) -> Unit,
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit,
    onFocusLeg: (List<GeoPoint>) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit,
    modifier: Modifier = Modifier,
    listBottomInset: Dp = 0.dp
) {
    val state by resultsViewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Seed from the completed plan + point the map at the first itinerary (the old bindResults).
    LaunchedEffect(itineraries) {
        resultsViewModel.setItineraries(itineraries, initialIndex = 0)
        itineraries.firstOrNull()?.let { showItinerary(it) }
        maybeStartTripUpdates(activity, params, itineraries, index = 0)
    }

    // Follow the selected option onto the map (the old observeSelection). Read [itineraries] and
    // [params] through rememberUpdatedState so the long-lived collector always sees the latest plan —
    // keying the effect on resultsViewModel alone would pin the first snapshot, so a later selection
    // could arm trip updates with a stale itinerary list *or* a stale request after new results arrive
    // (selectedItinerary is a no-replay SharedFlow, so keeping one collector — rather than restarting it
    // — also can't drop a concurrent emission).
    val currentItineraries by rememberUpdatedState(itineraries)
    val currentParams by rememberUpdatedState(params)
    LaunchedEffect(resultsViewModel) {
        resultsViewModel.selectedItinerary.collect { (index, itinerary) ->
            showItinerary(itinerary)
            maybeStartTripUpdates(activity, currentParams, currentItineraries, index)
        }
    }

    // The header (option-card picker) is folded into the list as its first item, so it scrolls away with
    // the steps instead of staying pinned above them.
    TripResultsList(
        state = state,
        modifier = modifier.fillMaxSize(),
        bottomInset = listBottomInset,
        onSelectOption = resultsViewModel::selectOption,
        onFocusRouteLeg = onFocusRouteLeg,
        onFocusLeg = onFocusLeg,
        onFocusPoint = onFocusPoint,
        stopEtaStrip = stopEtaStrip
    )
}

/**
 * Arms the trip-plan-change monitor ([TripPlanMonitor]) for the selected itinerary when trip-update
 * notifications are enabled. [params] is the request that produced [itineraries]; it's null when the
 * results were restored from a notification re-entry (the request isn't reconstructed there), in which
 * case there is nothing to re-plan, so monitoring isn't re-armed.
 */
private fun maybeStartTripUpdates(
    activity: Activity,
    params: TripPlanParams?,
    itineraries: List<TripItinerary>,
    index: Int
) {
    val itinerary = itineraries.getOrNull(index) ?: return
    if (params == null) return
    if (!TripPlanNotifications.isEnabled(activity)) return

    // The notification re-opens the activity that launched monitoring (HomeActivity, which hosts the
    // trip-plan destination) tagged with the TRIP_PLAN route — see TripPlanMonitorService.notifyChange.
    TripPlanMonitor.start(activity, params, itinerary, activity.javaClass)
}

// ---- Trip-log timeline ----------------------------------------------------------------------------
//
// The directions render as a single vertical "log": a monospaced clock-time column, a continuous spine
// with a node per event (start / walk / board / stop / exit / arrive), and the event text. Walk segments
// are a dashed neutral; a transit ride is solid in the route's colour. Each leg is united behind a faint
// tinted band and, where it has minor events (turn steps for a walk, intermediate stops for a ride),
// expands them inline on tap.

private val TIME_WIDTH = 66.dp // wide enough for a locale 12-hour time ("12:00 AM") without clipping
private val RAIL_WIDTH = 34.dp
private val RAIL_SPLIT = 22.dp // node centre, measured from the row's top — where the spine's colour flips
private val ROW_TOP = 10.dp
private val ROW_BOTTOM = 10.dp
private val BAND_LEFT = TIME_WIDTH + RAIL_WIDTH // band sits behind the content only, never over the spine

/**
 * The itinerary as one continuous timeline. Expansion is per-leg local state, keyed on [entries] so a
 * new plan resets it. The spine's per-node connector colours are derived here from the entry sequence
 * ([flattenLog]); each leg's rows are grouped so a faint band can unite them and one tap can expand the
 * leg's minor events while highlighting it on the map.
 */
@Composable
private fun TripLog(
    entries: List<TripLogEntry>,
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit,
    onFocusLeg: (List<GeoPoint>) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit
) {
    val neutral = MaterialTheme.colorScheme.outline
    val transitFallback = MaterialTheme.colorScheme.primary
    val expanded = remember(entries) { mutableStateMapOf<Int, Boolean>() }
    fun isExpanded(i: Int) = expanded[i] == true
    fun toggle(i: Int) {
        expanded[i] = !isExpanded(i)
    }

    val rows = flattenLog(entries, ::isExpanded, neutral, transitFallback)

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        var idx = 0
        while (idx < rows.size) {
            val entryIndex = rows[idx].entryIndex
            var end = idx
            while (end < rows.size && rows[end].entryIndex == entryIndex) end++
            val group = rows.subList(idx, end)
            // A leg's rows are united behind a faint band; a terminal (bandColor == null) stands alone.
            val band = group.first().bandColor
            val rowsOf: @Composable () -> Unit = {
                group.forEach { LogRow(it, ::isExpanded, ::toggle, onFocusRouteLeg, onFocusLeg, onFocusPoint, stopEtaStrip) }
            }
            if (band == null) rowsOf() else Column(Modifier.drawBehind { drawBand(band) }) { rowsOf() }
            idx = end
        }
    }
}

/** Renders one flattened [model] row — its map-focus tap wiring and body — dispatched by content kind. */
@Composable
private fun LogRow(
    model: LogRowModel,
    isExpanded: (Int) -> Boolean,
    onToggle: (Int) -> Unit,
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit,
    onFocusLeg: (List<GeoPoint>) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit
) {
    val i = model.entryIndex
    when (val content = model.content) {
        is RowContent.Terminal ->
            LogRowScaffold(model, onClick = content.entry.point?.let { { onFocusPoint(it) } }) {
                TerminalContent(content.entry)
            }

        is RowContent.WalkHeader -> {
            val walk = content.entry
            LogRowScaffold(
                model = model,
                onClick = {
                    if (walk.legPoints.isNotEmpty()) onFocusLeg(walk.legPoints) else walk.focusPoint?.let(onFocusPoint)
                    if (walk.steps.isNotEmpty()) onToggle(i)
                }
            ) { WalkHeaderContent(walk, isExpanded(i)) }
        }

        is RowContent.Step ->
            LogRowScaffold(model, onClick = content.step.point?.let { { onFocusPoint(it) } }) {
                StepContent(content.step)
            }

        is RowContent.BoardHeader -> {
            val transit = content.entry
            LogRowScaffold(model, onClick = null) {
                BoardContent(
                    entry = transit,
                    expanded = isExpanded(i),
                    onToggle = {
                        focusTransit(transit, onFocusRouteLeg, onFocusLeg, onFocusPoint)
                        if (transit.intermediateStops.isNotEmpty()) onToggle(i)
                    },
                    onFocusPoint = onFocusPoint,
                    stopEtaStrip = stopEtaStrip
                )
            }
        }

        is RowContent.Stop ->
            LogRowScaffold(model, onClick = content.stop.point?.let { { onFocusPoint(it) } }) {
                StopContent(content.stop)
            }

        is RowContent.Transition ->
            LogRowScaffold(model, onClick = content.transition.stop.point?.let { { onFocusPoint(it) } }) {
                TransitionContent(content.transition)
            }

        is RowContent.ExitNode ->
            LogRowScaffold(model, onClick = content.entry.routeLeg.alight?.point?.let { { onFocusPoint(it) } }) {
                ExitContent(content.entry)
            }
    }
}

/**
 * Tapping a transit leg: highlight its route on the map when the route id resolved (the usual case),
 * else frame the leg polyline, else recentre on the board stop. Mirrors the old leg-body behaviour.
 */
private fun focusTransit(
    entry: TripLogEntry.Transit,
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit,
    onFocusLeg: (List<GeoPoint>) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit
) {
    val routeLeg = entry.routeLeg.takeIf { it.routeId != null }
    when {
        routeLeg != null -> onFocusRouteLeg(routeLeg, entry.legPoints)
        entry.legPoints.isNotEmpty() -> onFocusLeg(entry.legPoints)
        else -> entry.routeLeg.board?.point?.let(onFocusPoint)
    }
}

/** A drawn spine segment: its [color] and whether it's dashed (a walk) or solid (a ride). */
private data class RailSeg(val color: Color, val dashed: Boolean)

/** What a single timeline row shows. */
private sealed interface RowContent {
    data class Terminal(val entry: TripLogEntry.Terminal) : RowContent
    data class WalkHeader(val entry: TripLogEntry.Walk) : RowContent
    data class Step(val step: LogStep) : RowContent
    data class BoardHeader(val entry: TripLogEntry.Transit) : RowContent
    data class Stop(val stop: LogStop) : RowContent
    data class Transition(val transition: InterlineTransition) : RowContent
    data class ExitNode(val entry: TripLogEntry.Transit) : RowContent
}

/**
 * One rendered row: its [content], the parent leg (via [entryIndex]), the spine above/below its node
 * ([top]/[bottom]), the resolved [nodeColor] the node's route-coloured parts use (parsed once per leg,
 * not re-parsed per node), and the leg's [bandColor] tint (null for a terminal, which has no band).
 */
private data class LogRowModel(
    val entryIndex: Int,
    val content: RowContent,
    val top: RailSeg?,
    val bottom: RailSeg?,
    val nodeColor: Color,
    val bandColor: Color?
)

/**
 * Flattens the [entries] into rows with the spine coloured. Each node's `bottom` is the travel *leaving*
 * it (a walk stays dashed-neutral through its steps; a ride stays route-coloured board→stops→exit; a
 * node's exit hands off to the next leg's colour), and `top` chains from the previous node's `bottom`, so
 * the colour flips exactly at each node — a walk-to-board node reads neutral above, route colour below.
 */
private fun flattenLog(
    entries: List<TripLogEntry>,
    isExpanded: (Int) -> Boolean,
    neutral: Color,
    transitFallback: Color
): List<LogRowModel> {
    val rows = ArrayList<LogRowModel>()
    var prevBottom: RailSeg? = null
    fun push(i: Int, content: RowContent, bottom: RailSeg?, nodeColor: Color, bandColor: Color?) {
        rows += LogRowModel(i, content, top = prevBottom, bottom = bottom, nodeColor = nodeColor, bandColor = bandColor)
        prevBottom = bottom
    }

    // The segment travelled when leaving [entry] toward the next node (null for a terminal/no travel):
    // a walk is dashed neutral, a ride is the solid route colour.
    fun leading(entry: TripLogEntry?): RailSeg? = when (entry) {
        is TripLogEntry.Walk -> RailSeg(neutral, dashed = true)
        is TripLogEntry.Transit -> RailSeg(routeColor(entry.routeColorHex, transitFallback), dashed = false)
        else -> null
    }
    entries.forEachIndexed { i, entry ->
        when (entry) {
            is TripLogEntry.Terminal -> {
                val bottom = if (entry.kind == TerminalKind.START) leading(entries.getOrNull(i + 1)) else null
                push(i, RowContent.Terminal(entry), bottom, nodeColor = transitFallback, bandColor = null)
            }
            is TripLogEntry.Walk -> {
                val seg = leading(entry) // dashed neutral
                val band = neutral.copy(alpha = 0.07f)
                push(i, RowContent.WalkHeader(entry), seg, nodeColor = neutral, bandColor = band)
                if (isExpanded(i)) entry.steps.forEach { push(i, RowContent.Step(it), seg, neutral, band) }
            }
            is TripLogEntry.Transit -> {
                val ride = leading(entry) // solid route colour
                val color = ride?.color ?: transitFallback // the leg's colour, parsed once for band + nodes
                val band = color.copy(alpha = 0.08f)
                push(i, RowContent.BoardHeader(entry), ride, color, band)
                if (isExpanded(i)) entry.intermediateStops.forEach { push(i, RowContent.Stop(it), ride, color, band) }
                // Stay-aboard interline changes (#2000) are always shown — they're an instruction to the
                // rider ("stay on board, it becomes route X"), not a minor stop hidden behind expansion.
                entry.routeLeg.interlineTransitions.forEach { push(i, RowContent.Transition(it), ride, color, band) }
                push(i, RowContent.ExitNode(entry), leading(entries.getOrNull(i + 1)), color, band)
            }
        }
    }
    return rows
}

/** Parse a GTFS colour hex to a Compose [Color], falling back to a neutral transit colour. */
private fun routeColor(hex: String?, fallback: Color): Color = parseObaHexColor(hex?.removePrefix("#"))?.let { Color(it) } ?: fallback

/** The GTFS colour as the nullable ARGB int a [RouteBadgeChip] wants (null → the chip's own default). */
private fun routeColorInt(hex: String?): Int? = parseObaHexColor(hex?.removePrefix("#"))

/**
 * One timeline row: the [time] column, the spine cell (drawn from [LogRowModel.top]/[bottom] with the
 * node on top), and the [content]. The whole row is the tap target when [onClick] is set.
 */
@Composable
private fun LogRowScaffold(
    model: LogRowModel,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // The time column shows the node's clock time and, in the gap below it, the leg's elapsed "delta".
    val (time, delta) = when (val c = model.content) {
        is RowContent.Terminal -> DisplayFormat.formatTime(context, c.entry.time.epochMs) to null
        is RowContent.BoardHeader ->
            DisplayFormat.formatTime(context, c.entry.boardTime.epochMs) to deltaText(c.entry.durationMinutes, context)
        is RowContent.ExitNode -> DisplayFormat.formatTime(context, c.entry.exitTime.epochMs) to null
        is RowContent.WalkHeader -> null to deltaText(c.entry.durationMinutes, context)
        else -> null to null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.Top
    ) {
        // Centered in the time column — halfway between the screen edge and the spine.
        Column(
            modifier = Modifier
                .width(TIME_WIDTH)
                .padding(top = ROW_TOP),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            time?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            delta?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
        Box(Modifier.width(RAIL_WIDTH).fillMaxHeight()) {
            Canvas(Modifier.matchParentSize()) {
                val cx = size.width / 2f
                val split = with(density) { RAIL_SPLIT.toPx() }
                val stroke = with(density) { 3.dp.toPx() }
                model.top?.let { drawSegment(it, cx, 0f, split, stroke, density) }
                model.bottom?.let { drawSegment(it, cx, split, size.height, stroke, density) }
            }
            LogNode(model.content, model.nodeColor)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 36.dp)
                .padding(start = 8.dp, top = ROW_TOP, bottom = ROW_BOTTOM, end = 10.dp),
            content = content
        )
    }
}

private fun DrawScope.drawSegment(seg: RailSeg, cx: Float, y0: Float, y1: Float, stroke: Float, density: Density) {
    val effect = if (seg.dashed) {
        val on = with(density) { 1.dp.toPx() }
        val off = with(density) { 7.dp.toPx() }
        PathEffect.dashPathEffect(floatArrayOf(on, off))
    } else {
        null
    }
    drawLine(seg.color, Offset(cx, y0), Offset(cx, y1), stroke, cap = StrokeCap.Round, pathEffect = effect)
}

/** The faint rounded band uniting a leg — drawn behind the content column only, so the spine stays clean. */
private fun DrawScope.drawBand(color: Color) {
    val left = BAND_LEFT.toPx()
    val insetY = 2.dp.toPx()
    val right = 4.dp.toPx()
    val radius = 13.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(left, insetY),
        size = Size(size.width - left - right, (size.height - insetY * 2).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius, radius)
    )
}

/**
 * The node graphic for a row, positioned so its centre sits on the spine's colour-flip point. Route-
 * coloured nodes use [nodeColor] (the leg's colour, already parsed once in [flattenLog]).
 */
@Composable
private fun BoxScope.LogNode(content: RowContent, nodeColor: Color) {
    val surface = MaterialTheme.colorScheme.surface
    val muted = MaterialTheme.colorScheme.outline
    when (content) {
        is RowContent.Terminal -> when (content.entry.kind) {
            TerminalKind.START -> NodeSlot(14.dp) {
                Box(Modifier.matchParentSize().clip(CircleShape).background(surface))
                Box(Modifier.size(10.dp).clip(CircleShape).background(muted))
            }
            TerminalKind.ARRIVE -> FilledNode(
                26.dp,
                MaterialTheme.colorScheme.primary,
                R.drawable.ic_map_pin,
                MaterialTheme.colorScheme.onPrimary,
                15.dp
            )
        }
        is RowContent.WalkHeader ->
            RingNode(24.dp, 1.5.dp, muted.copy(alpha = 0.6f), iconRes = R.drawable.ic_directions_walk)
        is RowContent.BoardHeader ->
            FilledNode(26.dp, nodeColor, R.drawable.ic_bus, Color.White, 16.dp, shape = RoundedCornerShape(8.dp))
        is RowContent.ExitNode -> RingNode(22.dp, 3.dp, nodeColor)
        is RowContent.Stop -> RingNode(11.dp, 2.dp, nodeColor)
        is RowContent.Transition ->
            FilledNode(22.dp, nodeColor, R.drawable.ic_continue, Color.White, 14.dp)
        is RowContent.Step -> RingNode(8.dp, 2.dp, muted.copy(alpha = 0.7f))
    }
}

/** A hollow node: a surface-filled circle with a [color] border, optionally with a muted centre [iconRes]. */
@Composable
private fun BoxScope.RingNode(size: Dp, border: Dp, color: Color, iconRes: Int? = null, iconSize: Dp = 14.dp) {
    NodeSlot(size) {
        Box(
            Modifier.matchParentSize().clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(border, color, CircleShape)
        )
        iconRes?.let {
            Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(iconSize))
        }
    }
}

/** A filled node: a [color] [shape] with a centred [iconRes] tinted [iconTint]. */
@Composable
private fun BoxScope.FilledNode(
    size: Dp,
    color: Color,
    iconRes: Int,
    iconTint: Color,
    iconSize: Dp,
    shape: Shape = CircleShape
) {
    NodeSlot(size) {
        Box(Modifier.matchParentSize().clip(shape).background(color))
        Icon(painterResource(iconRes), null, tint = iconTint, modifier = Modifier.size(iconSize))
    }
}

/** Places a [size]-square node so its centre lands on [RAIL_SPLIT] (the spine's colour-flip point). */
@Composable
private fun BoxScope.NodeSlot(size: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = RAIL_SPLIT - size / 2)
            .size(size),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ColumnScope.TerminalContent(entry: TripLogEntry.Terminal) {
    Text(
        text = stringResource(
            if (entry.kind == TerminalKind.START) R.string.trip_plan_leaving else R.string.trip_plan_arriving
        ).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = entry.place,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ColumnScope.WalkHeaderContent(entry: TripLogEntry.Walk, expanded: Boolean) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (entry.isTransfer) R.string.trip_plan_walk_transfer else R.string.step_by_step_non_transit_mode_walk_action
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (entry.steps.isNotEmpty()) Chevron(expanded)
    }
    val meta = walkMeta(entry, context)
    if (meta.isNotEmpty()) {
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.StepContent(step: LogStep) {
    Text(
        text = step.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ColumnScope.BoardContent(
    entry: TripLogEntry.Transit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit
) {
    val context = LocalContext.current
    // The route/headsign/meta block toggles the leg (and highlights it on the map); the board stop + ETA
    // strip below is its own tap target that zooms to the stop.
    Column(Modifier.clickable(onClick = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteBadgeChip(entry.routeShortName, routeColorInt(entry.routeColorHex))
            if (entry.routeDisplayName.isNotEmpty() && entry.routeDisplayName != entry.routeShortName) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.routeDisplayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (entry.intermediateStops.isNotEmpty()) Chevron(expanded)
        }
        entry.headsign?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = transitMeta(entry, context)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RealtimeChip(entry.realtime)
        }
    }
    entry.routeLeg.board?.let { stop ->
        Spacer(Modifier.height(6.dp))
        StopActionLabel(
            actionRes = R.string.step_by_step_transit_get_on,
            stopName = stop.name,
            onClick = { stop.point?.let(onFocusPoint) }
        )
        stopEtaStrip(entry.routeLeg, stop, entry.legPoints)
    }
}

@Composable
private fun ColumnScope.StopContent(stop: LogStop) {
    Text(
        text = stop.name,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * A stay-aboard interline (#2000): the vehicle keeps going but its route changes, so the rider is told
 * to stay on board — never to get off and reboard. Shows the new route label (short name + "to headsign")
 * and the seam stop where the change happens.
 */
@Composable
private fun ColumnScope.TransitionContent(transition: InterlineTransition) {
    val routeLabel = buildString {
        append(transition.routeShortName.orEmpty())
        if (!transition.headsign.isNullOrEmpty()) {
            append(" ")
            append(stringResource(R.string.step_by_step_transit_connector_headsign))
            append(" ")
            append(transition.headsign)
        }
    }.trim()
    Text(
        text = stringResource(R.string.step_by_step_transit_interline, routeLabel),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    transition.stop.name?.let { name ->
        Text(
            text = "${stringResource(R.string.step_by_step_transit_connector_stop_name)} $name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.ExitContent(entry: TripLogEntry.Transit) {
    StopActionLabel(
        actionRes = R.string.step_by_step_transit_get_off,
        stopName = entry.routeLeg.alight?.name,
        onClick = null
    )
}

/** A "Get on / Get off <stop>" line — the boarding verb plus the stop name, optionally tappable. */
@Composable
private fun StopActionLabel(actionRes: Int, stopName: String?, onClick: (() -> Unit)?) {
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(actionRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stopName.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The on-time / delayed real-time chip; [RealtimeState.Unknown] renders nothing. */
@Composable
private fun RealtimeChip(state: RealtimeState) {
    val (color, text) = when (state) {
        RealtimeState.Unknown -> return
        RealtimeState.OnTime -> colorResource(R.color.trip_realtime_on_time) to
            stringResource(R.string.trip_plan_realtime_on_time)
        is RealtimeState.Late -> colorResource(R.color.trip_realtime_delayed) to
            stringResource(R.string.trip_plan_realtime_late, state.minutes.toInt())
        is RealtimeState.Early -> colorResource(R.color.trip_realtime_on_time) to
            stringResource(R.string.trip_plan_realtime_early, state.minutes.toInt())
    }
    Spacer(Modifier.width(8.dp))
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** The expand/collapse chevron shown on a leg with minor events; rotates via the up/down glyph. */
@Composable
private fun Chevron(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
        contentDescription = stringResource(
            if (expanded) R.string.trip_plan_collapse_leg else R.string.trip_plan_expand_leg
        ),
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(24.dp)
    )
}

/**
 * The leg's elapsed duration as a compact delta ("14 min") for the narrow time column — always the
 * abbreviated unit, unlike [ConversionUtils.getFormattedDurationTextNoSeconds] which spells out
 * "minutes" for plural values (too wide here). Hours fold into its "1h 30min" form.
 */
private fun deltaText(minutes: Long, context: Context): String = if (minutes >= 60) {
    ConversionUtils.getFormattedDurationTextNoSeconds(minutes * 60, false, context)
} else {
    "$minutes ${context.getString(R.string.minutes_abbreviation)}"
}

/** The walk leg's distance ("0.2 mi"); blank when the leg carries no distance. Its duration is the delta. */
private fun walkMeta(entry: TripLogEntry.Walk, context: Context): String = if (entry.distanceMeters > 0.0) ConversionUtils.getFormattedDistance(entry.distanceMeters, context) else ""

/** The transit leg's stop count ("5 stops"); blank when unknown (e.g. the OTP2 path). Duration is the delta. */
private fun transitMeta(entry: TripLogEntry.Transit, context: Context): String = if (entry.stopCount > 0) {
    context.resources.getQuantityString(R.plurals.trip_plan_intermediate_stops, entry.stopCount, entry.stopCount)
} else {
    ""
}

@Preview(showBackground = true)
@Composable
private fun TripResultsPreview() {
    ObaTheme {
        val state = TripResultsUiState.Success(
            options = listOf(
                ItineraryOption(
                    mode = ModeSummary.Routes(listOf(RouteBadge("8", 0xFF1B6EF3.toInt()))),
                    durationMinutes = 32,
                    startTime = ServerTime(0L),
                    endTime = ServerTime(32 * 60_000L),
                    walkDistanceMeters = 800.0
                ),
                ItineraryOption(
                    mode = ModeSummary.Routes(listOf(RouteBadge("48", null), RouteBadge("11", null))),
                    durationMinutes = 41,
                    startTime = ServerTime(0L),
                    endTime = ServerTime(41 * 60_000L),
                    walkDistanceMeters = 400.0
                )
            ),
            selectedIndex = 0,
            directions = listOf(
                TripLogEntry.Terminal(
                    kind = TerminalKind.START,
                    time = ServerTime(0L),
                    place = "5th Ave & Pine St"
                ),
                TripLogEntry.Walk(
                    durationMinutes = 4,
                    distanceMeters = 320.0,
                    isTransfer = false,
                    steps = listOf(LogStep("Head north on 5th Ave (350 ft)"))
                ),
                TripLogEntry.Transit(
                    routeShortName = "8",
                    routeDisplayName = "Route 8",
                    routeColorHex = "1B6EF3",
                    headsign = "Rainier Beach",
                    boardTime = ServerTime(4 * 60_000L),
                    exitTime = ServerTime(20 * 60_000L),
                    stopCount = 3,
                    durationMinutes = 16,
                    realtime = RealtimeState.OnTime,
                    intermediateStops = listOf(
                        LogStop("Capitol Hill Station"),
                        LogStop("23rd Ave & E Union St"),
                        LogStop("Mount Baker Transit Center")
                    ),
                    routeLeg = RouteLegRef(
                        routeId = "1_100",
                        headsign = "Rainier Beach",
                        board = RouteStopRef("1_500", "500", "Pine St & 3rd Ave", null),
                        alight = RouteStopRef("1_600", "600", "Rainier & Alaska", null)
                    )
                ),
                TripLogEntry.Terminal(
                    kind = TerminalKind.ARRIVE,
                    time = ServerTime(32 * 60_000L),
                    place = "Rainier & Alaska"
                )
            )
        )
        TripResultsList(state)
    }
}
