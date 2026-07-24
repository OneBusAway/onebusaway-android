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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
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
import org.onebusaway.android.ui.compose.components.RouteLineColors
import org.onebusaway.android.ui.compose.components.ScrollChevronGutter
import org.onebusaway.android.ui.compose.components.routeLineColors
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.compose.theme.isDarkTheme
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

            is TripResultsUiState.Success -> TripLogList(
                state = state,
                bottomInset = bottomInset,
                onSelectOption = onSelectOption,
                onFocusRouteLeg = onFocusRouteLeg,
                onFocusLeg = onFocusLeg,
                onFocusPoint = onFocusPoint,
                stopEtaStrip = stopEtaStrip
            )
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

private val TIME_WIDTH = 66.dp // fits a locale 12-hour time ("12:00 AM") at the default font scale
private val RAIL_WIDTH = 34.dp
private val RAIL_SPLIT = 22.dp // node centre, measured from the row's top — where the spine's colour flips
private val ROW_TOP = 10.dp
private val ROW_BOTTOM = 10.dp
private val RAIL_STROKE = 3.dp
private val BAND_RADIUS = 13.dp
private val BAND_INSET = 2.dp
private val BAND_END = 4.dp

/** The minimum height of a row's content — the platform's 48dp target when the row is a tap target. */
private val ROW_MIN_HEIGHT = 36.dp
private val ROW_MIN_TOUCH_HEIGHT = 48.dp

/** The gap between the option-card header and the log, and below the log's last row. */
private val LOG_EDGE_GAP = 4.dp

/**
 * How far the timeline's fixed metrics stretch with the user's font scale, capped at the platform's 2×
 * ceiling so a large text size can't crowd the content off a narrow screen. [TIME_WIDTH] is sized for
 * the default scale, so the ledger's clock time — its primary information — can't clip at an
 * accessibility text size, and [RAIL_SPLIT] tracks it so each node stays centred on its row's first
 * text line. One shared scale for every row, so the spine still lines up.
 */
@Composable
private fun timelineScale(): Float = LocalDensity.current.fontScale.coerceIn(1f, 2f)

/** Where the spine's colour flips and each node centres, measured from the row's top. */
@Composable
private fun railSplit(): Dp = RAIL_SPLIT * timelineScale()

/**
 * The itinerary as one continuous timeline, one lazy list row per event. Expansion is per-leg state,
 * keyed on the entries so a new plan resets it. The spine's per-node connector colours and each leg's
 * band are derived up front by [flattenLog] from the entry sequence; the rows themselves compose lazily,
 * so expanding a long walk leg doesn't compose every one of its steps at once.
 */
@Composable
private fun TripLogList(
    state: TripResultsUiState.Success,
    bottomInset: Dp,
    onSelectOption: (Int) -> Unit,
    onFocusRouteLeg: (RouteLegRef, List<GeoPoint>) -> Unit,
    onFocusLeg: (List<GeoPoint>) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit
) {
    val entries = state.directions
    val dark = MaterialTheme.colorScheme.isDarkTheme()
    // A walk's spine and any route whose colour we can't use: an outline-toned line, with the surface
    // showing through as the glyph so a filled neutral node still reads.
    val neutral = RouteLineColors(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surface)
    val expanded = remember(entries) { mutableStateSetOf<Int>() }
    val onToggle: (Int) -> Unit = remember(expanded) { { i -> if (!expanded.add(i)) expanded.remove(i) } }
    // Snapshotted to a plain Set so it can key the memo below — reading it here is also what makes a
    // toggle recompose this list.
    val expandedEntries = expanded.toSet()
    val rows = remember(entries, expandedEntries, neutral, dark) {
        flattenLog(
            entries = entries,
            expandedEntries = expandedEntries,
            neutral = neutral,
            // The agency's GTFS colour re-toned to stay legible on this theme's surface, so a near-black
            // or near-white route can't hand us an invisible spine. Same system as the leg's route badge.
            rideColors = { routeLineColors(routeColorInt(it.routeColorHex), dark, neutral) }
        )
    }

    // The surface reaches the bottom edge; a bottom content padding lets the final leg row be scrolled
    // clear of the nav chrome without an empty strip below the list.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + LOG_EDGE_GAP)
    ) {
        // The picker scrolls with the steps (not pinned), so it recedes as you read down the list.
        item {
            TripResultsHeader(state, onSelectOption)
            HorizontalDivider()
            Spacer(Modifier.height(LOG_EDGE_GAP))
        }
        // Keyed by row identity, not position, so opening a leg doesn't discard the subcompositions of
        // every row below it — a board row's live ETA session survives the insert.
        items(rows, key = { it.key }) { row ->
            LogRow(row, onToggle, onFocusRouteLeg, onFocusLeg, onFocusPoint, stopEtaStrip)
        }
    }
}

/** Renders one flattened [model] row — its map-focus tap wiring and body — dispatched by content kind. */
@Composable
private fun LogRow(
    model: LogRowModel,
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
                    if (model.expandable) onToggle(i)
                },
                // The row is one control that both frames the leg and unfolds its steps, so the expand
                // wording rides on the row's own click label rather than on a decorative chevron.
                onClickLabel = expandLabel(model)
            ) { WalkHeaderContent(walk, model) }
        }

        is RowContent.Step ->
            LogRowScaffold(model, onClick = content.step.point?.let { { onFocusPoint(it) } }) {
                StepContent(content.step)
            }

        is RowContent.StepDistance ->
            LogRowScaffold(model, onClick = null, compact = true) {
                StepDistanceContent(content.distanceMeters)
            }

        is RowContent.BoardHeader -> {
            val transit = content.entry
            LogRowScaffold(model, onClick = null) {
                BoardContent(
                    entry = transit,
                    model = model,
                    onToggle = {
                        focusTransit(transit, onFocusRouteLeg, onFocusLeg, onFocusPoint)
                        if (model.expandable) onToggle(i)
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
 * The accessibility label for a leg header's tap: what the tap will *do* to the steps. Null when the leg
 * has nothing to reveal, leaving the row's plain "activate" affordance.
 */
@Composable
private fun expandLabel(model: LogRowModel): String? = when {
    !model.expandable -> null
    model.expanded -> stringResource(R.string.trip_plan_collapse_leg)
    else -> stringResource(R.string.trip_plan_expand_leg)
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

/** The GTFS colour as the nullable ARGB int the route-colour helpers want (null → their fallback). */
private fun routeColorInt(hex: String?): Int? = parseObaHexColor(hex?.removePrefix("#"))

/**
 * One timeline row: the time column, the spine cell (drawn from [LogRowModel.top]/[bottom] with the node
 * on top), and the [content]. The whole row is the tap target when [onClick] is set, labelled for
 * accessibility by [onClickLabel]. [compact] tightens the row for a nodeless annotation (the
 * between-steps distance) so it reads as an interval, not an event.
 *
 * The spine and the leg's band are drawn by the row itself ([drawRowChrome]) rather than by a
 * full-height child, so the row needs no intrinsic measurement and each one can stand alone as a lazy
 * list item.
 */
@Composable
private fun LogRowScaffold(
    model: LogRowModel,
    onClick: (() -> Unit)?,
    onClickLabel: String? = null,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val scale = timelineScale()
    val timeWidth = TIME_WIDTH * scale
    val rowTop = ROW_TOP * scale
    val railSplit = RAIL_SPLIT * scale
    // The time column shows a node's clock time and, in the gap below it, the leg's elapsed "delta".
    // (A walk step's distance is not shown here — it rides between the steps in the content column.)
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
            // drawWithCache, not drawBehind: the dash effect and every Dp→px conversion are resolved
            // once per size/metric change instead of on every frame this row is drawn.
            .drawWithCache {
                val chrome = RowChrome(this, model, timeWidth, railSplit)
                onDrawBehind { chrome.draw(this) }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.Top
    ) {
        // Centered in the time column — halfway between the screen edge and the spine.
        Column(
            modifier = Modifier
                .width(timeWidth)
                .padding(top = rowTop),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            time?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // The column is sized for the common short time; a locale with a wide am/pm marker
                    // ("12:00 nachm.") wraps rather than losing the clock time to an ellipsis.
                    maxLines = 2
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
        Box(Modifier.width(RAIL_WIDTH)) {
            LogNode(model.content, model.nodeColors)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(
                    minHeight = when {
                        compact -> 0.dp
                        // A tappable row keeps the platform's minimum touch target.
                        onClick != null -> ROW_MIN_TOUCH_HEIGHT
                        else -> ROW_MIN_HEIGHT
                    }
                )
                .padding(
                    start = 8.dp,
                    top = if (compact) 0.dp else rowTop,
                    bottom = if (compact) 0.dp else ROW_BOTTOM,
                    end = 10.dp
                ),
            content = content
        )
    }
}

/**
 * A row's background — its leg band, then the spine above and below the row's node — with every
 * measurement resolved up front. Built once per size/metric change by `drawWithCache` and replayed on
 * each frame, so the draw phase does no unit conversion and allocates no [PathEffect].
 *
 * **LTR only.** These x offsets are measured from the row's left edge, while the [Row] laying the
 * columns out would mirror under RTL — so the spine would part company with the nodes it threads. That
 * is unreachable today (the app declares no `android:supportsRtl` and ships no RTL locale), and is left
 * unhandled rather than written blind: enabling RTL means mirroring every x here against `size.width`
 * and verifying it on a real RTL locale, not just flipping a sign.
 */
private class RowChrome(density: Density, private val model: LogRowModel, timeWidth: Dp, railSplit: Dp) {
    private val railLeft = with(density) { timeWidth.toPx() }
    private val railWidth = with(density) { RAIL_WIDTH.toPx() }
    private val centreX = railLeft + railWidth / 2f
    private val split = with(density) { railSplit.toPx() }
    private val stroke = with(density) { RAIL_STROKE.toPx() }
    private val bandRadius = with(density) { BAND_RADIUS.toPx() }
    private val bandInset = with(density) { BAND_INSET.toPx() }
    private val bandEnd = with(density) { BAND_END.toPx() }
    private val dash = with(density) {
        PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx()))
    }

    fun draw(scope: DrawScope) = with(scope) {
        model.band?.let { drawBand(it) }
        model.top?.let { drawSegment(it, 0f, split) }
        model.bottom?.let { drawSegment(it, split, size.height) }
    }

    private fun DrawScope.drawSegment(seg: RailSeg, y0: Float, y1: Float) = drawLine(
        color = seg.color,
        start = Offset(centreX, y0),
        end = Offset(centreX, y1),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
        pathEffect = if (seg.dashed) dash else null
    )

    /**
     * This row's slice of the faint band uniting its leg — drawn behind the content column only, so the
     * spine stays clean. An interior row's rect is extended a corner radius past the row edge and
     * clipped back, so only the leg's outermost rows show rounded corners and the slices read as one.
     */
    private fun DrawScope.drawBand(band: BandEdge) {
        val left = railLeft + railWidth
        val top = if (band.first) bandInset else -bandRadius
        val bottom = if (band.last) size.height - bandInset else size.height + bandRadius
        clipRect {
            drawRoundRect(
                color = band.color,
                topLeft = Offset(left, top),
                size = Size(
                    width = (size.width - left - bandEnd).coerceAtLeast(0f),
                    height = (bottom - top).coerceAtLeast(0f)
                ),
                cornerRadius = CornerRadius(bandRadius, bandRadius)
            )
        }
    }
}

/**
 * The glyph inside an on-street leg's node. Null for [StreetMode.CAR]: the app ships no car drawable
 * because its planner never asks OTP for car modes (see `TripModes`), and a bare ring is honest where
 * a walking figure would be wrong. Add `ic_directions_car` here if car planning is ever offered.
 */
private fun streetModeIcon(mode: StreetMode): Int? = when (mode) {
    StreetMode.WALK -> R.drawable.ic_directions_walk
    StreetMode.BIKE -> R.drawable.ic_directions_bike
    StreetMode.CAR -> null
}

/**
 * The node graphic for a row, positioned so its centre sits on the spine's colour-flip point. Route-
 * coloured nodes use [nodeColor] (the leg's colour, already parsed once in [flattenLog]).
 */
@Composable
private fun BoxScope.LogNode(content: RowContent, nodeColors: RouteLineColors) {
    val muted = MaterialTheme.colorScheme.outline
    val (nodeColor, onNode) = nodeColors
    when (content) {
        // The trip endpoints are plain dots: a green origin and a red destination, no inset icon.
        is RowContent.Terminal -> DotNode(
            colorResource(
                if (content.entry.kind == TerminalKind.START) {
                    R.color.trip_origin_marker
                } else {
                    R.color.trip_destination_marker
                }
            )
        )
        is RowContent.WalkHeader ->
            RingNode(24.dp, 1.5.dp, muted.copy(alpha = 0.6f), iconRes = streetModeIcon(content.entry.mode))
        is RowContent.BoardHeader ->
            FilledNode(26.dp, nodeColor, R.drawable.ic_bus, onNode, 16.dp, shape = RoundedCornerShape(8.dp))
        is RowContent.ExitNode -> RingNode(22.dp, 3.dp, nodeColor)
        is RowContent.Stop -> RingNode(11.dp, 2.dp, nodeColor)
        is RowContent.Transition ->
            FilledNode(22.dp, nodeColor, R.drawable.ic_continue, onNode, 14.dp)
        is RowContent.Step -> RingNode(8.dp, 2.dp, muted.copy(alpha = 0.7f))
        // The between-steps distance is an interval, not an event — the spine runs through unbroken.
        is RowContent.StepDistance -> Unit
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

/** A solid [color] dot with a surface halo separating it from the spine — the timeline's trip endpoints. */
@Composable
private fun BoxScope.DotNode(color: Color) {
    // ~1.75x the ordinary node dot so the trip endpoints read as the timeline's anchors.
    NodeSlot(24.dp) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface))
        Box(Modifier.size(18.dp).clip(CircleShape).background(color))
    }
}

/** Places a [size]-square node so its centre lands on [railSplit] (the spine's colour-flip point). */
@Composable
private fun BoxScope.NodeSlot(size: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = (railSplit() - size / 2).coerceAtLeast(0.dp))
            .size(size),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ColumnScope.TerminalContent(entry: TripLogEntry.Terminal) {
    Text(
        // Locale-aware uppercasing: the bare uppercase() overload is Locale.ROOT, which gets Turkish
        // dotted/dotless I wrong on a string we just localized.
        text = stringResource(
            if (entry.kind == TerminalKind.START) R.string.trip_plan_leaving else R.string.trip_plan_arriving
        ).uppercase(Locale.getDefault()),
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

/**
 * The header verb for an on-street leg: its travel mode, and whether the leg merely connects two rides.
 * Each combination is its own string rather than an assembled "<verb> to transfer" — how the two ideas
 * combine is a translator's call, not a concatenation.
 */
private fun streetActionRes(mode: StreetMode, isTransfer: Boolean): Int = when (mode) {
    StreetMode.WALK ->
        if (isTransfer) R.string.trip_plan_walk_transfer else R.string.step_by_step_non_transit_mode_walk_action
    StreetMode.BIKE ->
        if (isTransfer) R.string.trip_plan_bike_transfer else R.string.step_by_step_non_transit_mode_bicycle_action
    StreetMode.CAR ->
        if (isTransfer) R.string.trip_plan_car_transfer else R.string.step_by_step_non_transit_mode_car_action
}

@Composable
private fun ColumnScope.WalkHeaderContent(entry: TripLogEntry.Walk, model: LogRowModel) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(streetActionRes(entry.mode, entry.isTransfer)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (model.expandable) Chevron(model.expanded)
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

/**
 * The distance walked between one maneuver and the next — a quiet monospaced annotation sitting between
 * the two step rows, in the same column as the instructions.
 */
@Composable
private fun ColumnScope.StepDistanceContent(distanceMeters: Double) {
    Text(
        text = ConversionUtils.getFormattedDistance(distanceMeters, LocalContext.current),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun ColumnScope.BoardContent(
    entry: TripLogEntry.Transit,
    model: LogRowModel,
    onToggle: () -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (RouteLegRef, RouteStopRef, List<GeoPoint>) -> Unit
) {
    val context = LocalContext.current
    // The route/headsign/meta block toggles the leg (and highlights it on the map); the board stop + ETA
    // strip below is its own tap target that zooms to the stop. Because the control is this inner block
    // rather than the whole row, the scaffold's touch-target floor doesn't reach it — so it carries its
    // own. (Its content clears 48dp on its own in practice; this is the guarantee, not the usual case.)
    Column(
        Modifier
            .defaultMinSize(minHeight = ROW_MIN_TOUCH_HEIGHT)
            .clickable(onClickLabel = expandLabel(model), onClick = onToggle)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteBadgeChip(entry.routeShortName, routeColorInt(entry.routeColorHex), scale = 1.5f)
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
            if (model.expandable) Chevron(model.expanded)
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
            pluralStringResource(R.plurals.trip_plan_realtime_late, state.minutes.toInt(), state.minutes.toInt())
        // Early gets its own colour, not the on-time green: a vehicle running ahead of schedule is a
        // risk to the rider (they can miss it), not a reassurance.
        is RealtimeState.Early -> colorResource(R.color.trip_realtime_early) to
            pluralStringResource(R.plurals.trip_plan_realtime_early, state.minutes.toInt(), state.minutes.toInt())
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

/**
 * The expand/collapse chevron shown on a leg with minor events; rotates via the up/down glyph. Purely
 * decorative (no content description) — it isn't its own control, it just pictures what the row's tap
 * will do, which the row announces through its own click label (see [expandLabel]).
 */
@Composable
private fun Chevron(expanded: Boolean) {
    Icon(
        imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(24.dp)
    )
}

/**
 * The leg's elapsed duration as a compact delta ("14min", "1h 30min") for the narrow time column.
 * Always the abbreviated unit, unlike [ConversionUtils.getFormattedDurationTextNoSeconds], which
 * spells out "minutes" below the hour (too wide here) — and which is also why the two forms are
 * assembled from their own format resources rather than by delegating the hours case to that helper:
 * it puts no space before its "min", so borrowing it left one column printing both "45 min" and
 * "1h 30min". The unit rides inside each resource, so spacing and order stay a translator's call.
 */
private fun deltaText(minutes: Long, context: Context): String {
    // Int args, matching the plural call sites below — a leg is never long enough to overflow, and %d
    // format args are checked against Integer.
    val hours = (minutes / 60).toInt()
    return if (hours > 0) {
        context.getString(R.string.trip_plan_delta_hours_minutes, hours, (minutes % 60).toInt())
    } else {
        context.getString(R.string.trip_plan_delta_minutes, minutes.toInt())
    }
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
                    mode = StreetMode.WALK,
                    durationMinutes = 4,
                    distanceMeters = 320.0,
                    isTransfer = false,
                    steps = listOf(LogStep("Head north on 5th Ave", distanceMeters = 107.0))
                ),
                TripLogEntry.Transit(
                    routeShortName = "8",
                    routeDisplayName = "Route 8",
                    routeColorHex = "1B6EF3",
                    headsign = "Rainier Beach",
                    boardTime = ServerTime(4 * 60_000L),
                    exitTime = ServerTime(20 * 60_000L),
                    durationMinutes = 16,
                    realtime = RealtimeState.OnTime,
                    rideEvents = listOf(
                        RideEvent.Stop(LogStop("Capitol Hill Station")),
                        RideEvent.Stop(LogStop("23rd Ave & E Union St")),
                        RideEvent.Stop(LogStop("Mount Baker Transit Center"))
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
