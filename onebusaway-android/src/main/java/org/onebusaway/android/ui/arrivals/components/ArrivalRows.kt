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
package org.onebusaway.android.ui.arrivals.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.models.Status
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.LoadMoreState
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.components.rememberRouteBadgeColors
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The per-arrival menu actions (legacy `showListItemMenu`). Implemented by the host activity,
 * which has the Context needed to launch the targets. The route-filter toggle is
 * a ViewModel action; the rest are navigation/dialogs.
 */
class ArrivalRowCallbacks(
    val onRouteFavorite: (ArrivalActions) -> Unit,
    val onShowVehiclesOnMap: (ArrivalInfo) -> Unit,
    /** The ETA pill was tapped: focus that arrival's live vehicle + its stop (whole-route tap is the row body). */
    val onEtaClick: (ArrivalInfo) -> Unit,
    val onShowTripStatus: (ArrivalInfo) -> Unit,
    val onSetReminder: (ArrivalInfo) -> Unit,
    val onShowOnlyRoute: (String) -> Unit,
    val onShowRouteSchedule: (String) -> Unit,
    val onReportArrivalProblem: (ArrivalActions) -> Unit,
    /** Opens the service-alert dialog for the given situation id (the per-row alert indicator). */
    val onShowAlert: (String) -> Unit,
    /** Fires a widen-window reload — invoked by pulling an ETA strip past its end (replaces the old
     *  "load more arrivals" footer button). Returns the request token whose lifecycle
     *  [loadMoreState] reports. */
    val onLoadMore: () -> Int,
    /** The stop's shared load-more lifecycle; the strip that fired the matching token drives its
     *  spinner/reveal from this. */
    val loadMoreState: StateFlow<LoadMoreState>
)

/**
 * The shaded rounded card that wraps each arrival row (and the report-flow picker rows),
 * matching the legacy MaterialCardView. Pass [onClick] to make the whole card a single tap target
 * (the picker); leave it null when the card holds its own buttons (the interactive list row).
 */
@Composable
internal fun ArrivalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 3.dp)
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(onClick = onClick, modifier = base, shape = shape, color = color, content = content)
    } else {
        Surface(modifier = base, shape = shape, color = color, content = content)
    }
}

/**
 * The visual content of a flat arrival row, driven by primitives so it stays previewable and
 * testable (the [ArrivalInfo] model can't be built in a @Preview). [ArrivalRowContent] adapts the
 * model onto it; the colored status pill and ETA both take the lateness [statusColor].
 */
@Composable
private fun ArrivalRowVisual(
    shortName: String,
    headsign: String,
    statusText: String,
    statusColor: Color,
    timeText: String,
    eta: Long,
    predicted: Boolean,
    canceled: Boolean,
    modifier: Modifier = Modifier,
    badgeContainer: Color = Color.Unspecified,
    badgeContent: Color = Color.Unspecified,
    onAlertClick: (() -> Unit)? = null,
    onEtaClick: (() -> Unit)? = null,
) {
    val decoration = strikeThroughIf(canceled)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LineBadge(
            text = shortName,
            maxFontSize = 36.sp,
            color = badgeContent,
            containerColor = badgeContainer,
            textDecoration = decoration
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = headsign,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textDecoration = decoration
            )
            if (statusText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                StatusPill(statusText, statusColor)
            }
            if (timeText.isNotEmpty()) {
                // The scheduled/predicted clock time: "Arriving/Departing/Arrived/Departed at 4:27 PM"
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onAlertClick != null) {
            ArrivalAlertIndicator(onClick = onAlertClick)
        }
        Spacer(Modifier.width(8.dp))
        EtaContent(eta, statusColor, predicted, canceled, onClick = onEtaClick)
    }
}

/** The tappable per-row service-alert indicator (issue #1687 Bug 2): the same warning glyph the
 *  header/banner uses, shown when this arrival is affected by an active alert; taps open its dialog. */
@Composable
internal fun ArrivalAlertIndicator(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.baseline_warning_24),
            contentDescription = stringResource(R.string.stop_info_arrival_service_alert),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/** Adapts the [ArrivalInfo] display model onto [ArrivalRowVisual]. Shared by the interactive Style
 *  A row and the report-flow picker (which wrap it with their own click + card). [onAlertClick] adds
 *  the tappable alert indicator when the arrival is affected by an active alert (null hides it). */
@Composable
internal fun ArrivalRowContent(
    arrival: ArrivalInfo,
    modifier: Modifier = Modifier,
    badgeContainer: Color = Color.Unspecified,
    badgeContent: Color = Color.Unspecified,
    onAlertClick: (() -> Unit)? = null,
    onEtaClick: (() -> Unit)? = null,
) {
    ArrivalRowVisual(
        shortName = arrival.shortName.orEmpty(),
        headsign = arrival.headsign.orEmpty(),
        statusText = arrival.statusText.orEmpty(),
        statusColor = colorResource(arrival.color),
        timeText = arrival.timeText.orEmpty(),
        eta = arrival.eta,
        predicted = arrival.predicted,
        canceled = arrival.status == Status.CANCELED,
        modifier = modifier,
        badgeContainer = badgeContainer,
        badgeContent = badgeContent,
        onAlertClick = onAlertClick,
        onEtaClick = onEtaClick,
    )
}

/**
 * One arrivals row for a single (route, direction): the route badge on the left, and on the right
 * the direction name over a horizontally-scrollable strip of per-trip ETA pills (soonest first).
 * The unified row (issue #1707) — replaces the old per-trip Style A row and Style B card.
 *
 * - Tapping the row body frames the whole route/direction on the map ([ArrivalRowCallbacks.onShowVehiclesOnMap]).
 * - Tapping a pill focuses that specific trip's vehicle + stop ([ArrivalRowCallbacks.onEtaClick]).
 * - Long-pressing a pill opens that trip's menu (details / reminder / report).
 * - The top-right overflow ⋮ opens the route-level menu (star / show-only / schedule).
 *
 * [actionsFor] resolves each trip's [ArrivalActions] (keyed by trip id upstream); the representative
 * trip's actions drive the badge color and the route menu. [etaAnchor] is attached to the first pill
 * (the onboarding spotlight target).
 */
@Composable
fun RouteArrivalRow(
    group: RouteRowGroup,
    // The Content.dataVersion the group came from — MUST be read off the same Content object, so the
    // ETA strip's load-more reveal can pair its pills with the data generation they render.
    dataVersion: Long,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    isFavorite: Boolean,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    etaAnchor: Modifier = Modifier,
) {
    val representative = group.representative
    val routeActions = actionsFor(representative)
    var menuExpanded by remember { mutableStateOf(false) }
    val (badgeContainer, badgeContent) = rememberRouteBadgeColors(routeActions?.routeColor)
    // Fall back to the route's long name when the feed gives no headsign for this direction.
    val direction = group.headsign?.takeIf { it.isNotBlank() } ?: routeActions?.routeLongName.orEmpty()
    val onAlertClick = alertClick(routeActions, callbacks)
    ArrivalCard(modifier) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Give the row a definite height (its tallest child) so the divider can fill it.
                    .height(IntrinsicSize.Min)
                    // Tapping the row body frames the whole route on the map (the pills below focus
                    // individual trips instead).
                    .clickable { callbacks.onShowVehiclesOnMap(representative) }
                    // A little top/end room so the badge and pills clear the overlaid overflow icon.
                    .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineBadge(
                    text = representative.shortName.orEmpty(),
                    maxFontSize = 32.sp,
                    color = badgeContent,
                    containerColor = badgeContainer,
                )
                // A full-height thin divider sets the route chip apart from the ETA pills, so the two
                // similar-looking rounded colored chips don't read as the same kind of thing.
                Spacer(Modifier.width(10.dp))
                VerticalDivider()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (direction.isNotBlank()) {
                        DirectionHeader(direction)
                        Spacer(Modifier.height(6.dp))
                    }
                    EtaStrip(
                        trips = group.trips,
                        dataVersion = dataVersion,
                        actionsFor = actionsFor,
                        callbacks = callbacks,
                        firstPillModifier = etaAnchor,
                    )
                }
                if (onAlertClick != null) {
                    ArrivalAlertIndicator(onClick = onAlertClick)
                }
                // Reserve room on the right so the last pill never slides under the overflow icon.
                Spacer(Modifier.width(20.dp))
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                CornerIcon(
                    iconRes = R.drawable.more_vert,
                    contentDescription = stringResource(R.string.stop_info_item_options_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { menuExpanded = true }
                )
                RouteActionsMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    routeId = group.routeId,
                    actions = routeActions,
                    isFavorite = isFavorite,
                    filterActive = filterActive,
                    callbacks = callbacks,
                )
            }
        }
    }
}

/**
 * The "heading toward X" line above a route row's pill strip: a muted forward-arrow (reads as
 * "toward" without asserting a destination — a headsign can be a direction or loop name) followed by
 * the destination in a slightly-tightened monospace so it reads like the sign on the bus.
 */
@Composable
private fun DirectionHeader(direction: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = direction,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Pull-past-end-to-load tuning. The strip captures a drag that continues past its last pill and, once
// it crosses [PULL_TO_LOAD_THRESHOLD], loads more arrivals on release (the old footer button's job).
/** Finger travel maps to pull distance at this ratio, so the pull lags the finger for a rubber-band feel. */
private const val PULL_RESISTANCE = 0.5f
/** Pull distance (post-resistance) that arms the load-more trigger. */
private val PULL_TO_LOAD_THRESHOLD = 48.dp
/** Pull distance is clamped here so the strip can't be dragged arbitrarily far off its end. */
private val PULL_TO_LOAD_MAX = 72.dp
/**
 * The horizontally-scrollable strip of per-trip ETA pills below the direction name. When the pills
 * overflow the row, a right-edge fade + chevron appears to signal there's more to scroll to; it's a
 * pure visual hint (no pointer handling) so it never blocks the strip's own drag-to-scroll.
 *
 * Dragging the strip past its last pill (once there's nothing more to scroll) builds a resistive pull
 * that reveals a trailing load-more affordance; releasing past [PULL_TO_LOAD_THRESHOLD] widens the
 * arrivals window via [ArrivalRowCallbacks.onLoadMore] — this replaced the drawer's footer button
 * (issue #1707 follow-up). A TalkBack custom action exposes the same load-more for non-drag users.
 */
@Composable
private fun EtaStrip(
    trips: List<ArrivalInfo>,
    // The Content.dataVersion this strip's trips came from. MUST come from the same Content object as
    // the trips themselves, so the strip can never see a version ahead of its rendered pills.
    dataVersion: Long,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    firstPillModifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }
    val density = LocalDensity.current
    val triggerPx = remember(density) { with(density) { PULL_TO_LOAD_THRESHOLD.toPx() } }
    val maxPullPx = remember(density) { with(density) { PULL_TO_LOAD_MAX.toPx() } }
    // Current pull distance in px (post-resistance). Written from the nested-scroll callbacks and read
    // in the layout/graphics phase, so growing it during a drag doesn't recompose the whole strip.
    val pull = remember { mutableFloatStateOf(0f) }
    // Whether the pull has crossed the arm threshold. A derivedState kept out of this composable's own
    // read set so only its reader (the chip) recomposes when it flips — never the whole strip per frame.
    val armed = remember(triggerPx) { derivedStateOf { pull.floatValue >= triggerPx } }
    val loadMoreLabel = stringResource(R.string.stop_info_load_more_arrivals)
    val haptic = LocalHapticFeedback.current

    // The strip's active load-more request: the token returned at fire time, NO_LOAD_REQUEST at rest.
    // Saveable so a list eviction / recreation mid-load resumes (or cleanly ends) the transaction.
    val request = rememberSaveable { mutableIntStateOf(NO_LOAD_REQUEST) }
    val loadMore by callbacks.loadMoreState.collectAsStateWithLifecycle()
    // The spinner shows from fire until the composition that carries the completing data's version, so
    // the spinner and the new pills swap in the SAME composition — never a frame where both/neither
    // show. At rest, request is NO_LOAD_REQUEST, which reads as Superseded → no spinner.
    val loadingMore = spinnerVisible(loadMoreOutcome(loadMore, request.intValue), dataVersion)

    // Layout acknowledgement: the highest dataVersion whose strip content has been MEASURED. Written in
    // the measure pass that wraps horizontalScroll — the same pass (and snapshot batch) in which
    // scrollState.maxValue is brought up to date — so `measuredVersion >= V` GUARANTEES maxValue is
    // consistent with data version V. Read only from snapshotFlow (never composition), so writing it
    // per layout pass can't cause recomposition loops.
    val measuredVersion = remember { mutableLongStateOf(0L) }
    val versionState = rememberUpdatedState(dataVersion)

    // The reveal transaction for this strip's request: keep the strip pinned to its (moving) right end
    // — first the spinner slot, then the reloaded pills — and settle once the layout provably reflects
    // the data that completed the request. Every wait is a level-triggered predicate over monotonic
    // snapshot/StateFlow state, so a signal that fires before we start listening is still observed.
    LaunchedEffect(request.intValue) {
        val token = request.intValue
        if (token == NO_LOAD_REQUEST) return@LaunchedEffect
        try {
            coroutineScope {
                // FOLLOWER: glides to the current end whenever there is one to reach. Each glide runs
                // TO COMPLETION and the loop then re-evaluates against current state (never
                // collectLatest: a change mid-glide is caught by the fresh level-triggered re-await,
                // so there is no lost-update window).
                val follower = launch {
                    while (true) {
                        snapshotFlow { scrollState.maxValue > scrollState.value }.first { it }
                        try {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        } catch (cause: CancellationException) {
                            currentCoroutineContext().ensureActive() // real teardown propagates
                            // Lost the scrollable mutex to a touch. Never contest user input: resume
                            // the chase only once the strip is fully at rest. (A drag that actually
                            // travels ends the whole transaction via the nested-scroll hook.)
                            snapshotFlow { scrollState.isScrollInProgress }.first { !it }
                        }
                    }
                }
                // AWAIT RESULT: level-triggered on the VM's StateFlow — a Finished that landed before
                // we started collecting is still observed (StateFlow replays its value).
                val landed = callbacks.loadMoreState
                    .map { loadMoreOutcome(it, token) }
                    .first { it !is LoadMoreOutcome.Pending }
                if (landed is LoadMoreOutcome.Landed) {
                    // SETTLE: wait until (a) layout reflects the completing data's version — which,
                    // via the layout-ack modifier, also means maxValue is current for that data and
                    // the spinner slot is gone (spinner and new pills swap in the same composition,
                    // both keyed on dataVersion) — and (b) we are at rest at the true end. Success
                    // with new pills, success with none for this row, and failure all take this one
                    // path: the end is wherever layout says it is.
                    snapshotFlow {
                        measuredVersion.longValue >= landed.dataVersion &&
                            scrollState.value == scrollState.maxValue &&
                            !scrollState.isScrollInProgress
                    }.first { it }
                }
                follower.cancel()
            }
        } finally {
            // Idempotent teardown; guarded so a user-takeover or a re-fire (which already moved
            // `request`) isn't clobbered.
            if (request.intValue == token) request.intValue = NO_LOAD_REQUEST
        }
    }

    val pullConnection = remember(scrollState, callbacks, triggerPx, maxPullPx, haptic) {
        object : NestedScrollConnection {
            // Dragging back toward the pills unwinds an active pull before the strip itself scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Programmatic glides dispatch as SideEffect; only real user input drives the pull and
                // the take-over below.
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // A real user scroll on this strip while a reveal transaction is active ends it: the
                // user wins, the transaction effect is cancelled (killing any in-flight glide via the
                // scrollable mutex), and the spinner is dropped. The load itself continues in the
                // ViewModel; its pills land unanimated.
                if (request.intValue != NO_LOAD_REQUEST) request.intValue = NO_LOAD_REQUEST
                // Dragging back toward the pills unwinds an active pull before the strip itself scrolls.
                if (available.x > 0f && pull.floatValue > 0f) {
                    val consumedFinger = (pull.floatValue / PULL_RESISTANCE).coerceAtMost(available.x)
                    pull.floatValue -= consumedFinger * PULL_RESISTANCE
                    return Offset(consumedFinger, 0f)
                }
                return Offset.Zero
            }

            // Past the strip's end, a further left-drag (negative x) builds the resistive pull; a light
            // tick fires the instant it crosses the arm threshold (rising edge only).
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.x < 0f && !scrollState.canScrollForward) {
                    val before = pull.floatValue
                    pull.floatValue = (before - available.x * PULL_RESISTANCE).coerceAtMost(maxPullPx)
                    if (before < triggerPx && pull.floatValue >= triggerPx) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            // Drag released: fire load-more if armed (the reveal transaction, keyed on the request
            // token, then pins the strip to its end), then spring the pull back.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.floatValue <= 0f) return Velocity.Zero
                if (pull.floatValue >= triggerPx) {
                    // The VM sets Loading(token) synchronously inside onLoadMore, so the spinner is
                    // already visible in the composition this write lands in — no gap.
                    request.intValue = callbacks.onLoadMore()
                }
                animate(pull.floatValue, 0f) { value, _ -> pull.floatValue = value }
                // Swallow the fling — there's nothing left to scroll to at the strip's end.
                return available
            }
        }
    }

    Box(
        modifier
            .clipToBounds()
            .nestedScroll(pullConnection)
            .semantics {
                customActions = listOf(
                    // The full transaction, so TalkBack users get the same spinner + reveal.
                    CustomAccessibilityAction(loadMoreLabel) {
                        request.intValue = callbacks.onLoadMore(); true
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                // Follow the pull so the pills slide aside, opening the gap the load-more chip fills.
                .offset { IntOffset(-pull.floatValue.roundToInt(), 0) }
                // The composition↔layout bridge: measure the scroll content, then record the data
                // version this layout reflects (see acknowledgeVersion).
                .acknowledgeVersion(versionState, measuredVersion)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            // Bottom-align so a smaller recent-past pill sits on the same baseline as the full-size ones.
            verticalAlignment = Alignment.Bottom
        ) {
            trips.forEachIndexed { index, trip ->
                EtaPillWithMenu(
                    trip = trip,
                    actions = actionsFor(trip),
                    callbacks = callbacks,
                    modifier = if (index == 0) firstPillModifier else Modifier,
                )
            }
            // While the reload is in flight, the spinner rides as a real tail item so it reserves its
            // own slot to the right of the last pill (rather than overlaying it); when the completing
            // data's composition lands it's dropped in the same frame the new pills appear, and the
            // strip glides left to the new end.
            if (loadingMore) {
                Box(
                    modifier = Modifier.size(width = 28.dp, height = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // The pull-revealed load-more chip (invisible at rest, armed as the pull crosses the threshold).
        // Hidden while loading — the inline spinner above takes over then.
        if (!loadingMore) {
            LoadMorePullChip(
                progress = { (pull.floatValue / triggerPx).coerceIn(0f, 1f) },
                armed = { armed.value },
                contentDescription = loadMoreLabel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        if (canScrollForward && !loadingMore) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(24.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer)
                            )
                        )
                )
                Icon(
                    painter = painterResource(R.drawable.ic_navigation_chevron_right),
                    // Decorative: a pure "there's more to scroll" hint over the scrollable pill strip,
                    // not an actionable control — so TalkBack skips it.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .size(20.dp)
                )
            }
        }
    }
}

/**
 * The circular load-more affordance uncovered as the ETA strip is pulled past its end. [progress]
 * (0..1, read in the graphics phase to avoid recomposing on every drag frame) fades and slides it in;
 * [armed] (progress has reached 1) flips it to the primary color so "release to load" reads at a glance.
 */
@Composable
private fun LoadMorePullChip(
    progress: () -> Float,
    armed: () -> Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val slidePx = with(LocalDensity.current) { 20.dp.toPx() }
    // Reading the arm lambda here scopes threshold-crossing recomposition to just this small chip.
    val isArmed = armed()
    val container = if (isArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isArmed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .graphicsLayer {
                val p = progress()
                alpha = p
                // Slide in from the right edge as the pull grows (fully seated at p == 1).
                translationX = (1f - p) * slidePx
            }
            .size(30.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** A single ETA pill with its long-press per-trip menu. Tap focuses the vehicle; long-press opens
 *  the menu (trip details / reminder / report). */
@Composable
private fun EtaPillWithMenu(
    trip: ArrivalInfo,
    actions: ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        EtaPill(
            eta = trip.eta,
            color = colorResource(trip.color),
            predicted = trip.predicted,
            modifier = modifier,
            canceled = trip.status == Status.CANCELED,
            onClick = { callbacks.onEtaClick(trip) },
            onLongClick = { expanded = true },
        )
        TripActionsMenu(expanded, { expanded = false }, trip, actions, callbacks)
    }
}

/** The alert-indicator tap for a row: opens the arrival's active alert, or null when it has none. */
internal fun alertClick(actions: ArrivalActions?, callbacks: ArrivalRowCallbacks): (() -> Unit)? =
    actions?.alertSituationId?.let { id -> { callbacks.onShowAlert(id) } }

/** A small tap target tucked into a card corner — the legacy overlaid star / overflow icons. */
@Composable
private fun CornerIcon(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .size(18.dp)
    )
}

/** The route-level overflow menu (the row's ⋮): star the route, narrow the stop to this route, and
 *  open its schedule. Per-trip actions live on each pill's long-press menu ([TripActionsMenu]). */
@Composable
internal fun RouteActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    routeId: String,
    actions: ArrivalActions?,
    isFavorite: Boolean,
    filterActive: Boolean,
    callbacks: ArrivalRowCallbacks
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (actions != null) {
            val favLabel = if (isFavorite) {
                R.string.bus_options_menu_remove_star
            } else {
                R.string.bus_options_menu_add_star
            }
            MenuRow(favLabel) { onDismiss(); callbacks.onRouteFavorite(actions) }
        }
        val filterLabel = if (filterActive) {
            R.string.bus_options_menu_show_all_routes
        } else {
            R.string.bus_options_menu_show_only_this_route
        }
        MenuRow(filterLabel) { onDismiss(); callbacks.onShowOnlyRoute(routeId) }
        val url = actions?.scheduleUrl
        if (!url.isNullOrBlank()) {
            MenuRow(R.string.bus_options_menu_show_route_schedule) {
                onDismiss(); callbacks.onShowRouteSchedule(url)
            }
        }
    }
}

/** The per-trip menu opened by long-pressing a pill: trip details, a reminder, or a problem report
 *  for that specific trip. Route-wide actions live on the row's ⋮ menu ([RouteActionsMenu]). */
@Composable
internal fun TripActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    arrival: ArrivalInfo,
    actions: ArrivalActions?,
    callbacks: ArrivalRowCallbacks
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuRow(R.string.bus_options_menu_show_trip_details) {
            onDismiss(); callbacks.onShowTripStatus(arrival)
        }
        MenuRow(R.string.bus_options_menu_set_reminder) {
            onDismiss(); callbacks.onSetReminder(arrival)
        }
        if (actions != null) {
            MenuRow(R.string.bus_options_menu_report_trip_problem) {
                onDismiss(); callbacks.onReportArrivalProblem(actions)
            }
        }
    }
}

/** A dropdown item that just shows a string resource; shared by the per-arrival and overflow menus. */
@Composable
internal fun MenuRow(textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}

private val PillShape = RoundedCornerShape(6.dp)

/** The lateness-colored status pill (white text on the deviation color), the legacy status badge. */
@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = PillShape, color = color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** The prominent ETA, color-coded by lateness, with the real-time indicator as a superscript on
 *  the "min" label (matching the drawer pill); the legacy `eta`/`eta_min`. [onClick], when non-null,
 *  makes the ETA its own tap target (focus this trip's vehicle + stop, distinct from the row-body tap). */
@Composable
private fun EtaContent(
    eta: Long,
    color: Color,
    predicted: Boolean,
    canceled: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val decoration = strikeThroughIf(canceled)
    // When clickable, clip to the pill shape so the tap ripple stays inside the ETA. No padding/size
    // change, so the ETA looks identical at rest; the clickable is a solid target on the number itself.
    val rowModifier = if (onClick != null) {
        Modifier.clip(PillShape).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(modifier = rowModifier) {
        if (eta == 0L) {
            Text(
                text = stringResource(R.string.stop_info_eta_now),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textDecoration = decoration
            )
            EtaRealtimeIndicator(predicted, color)
        } else {
            Text(
                text = eta.toString(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textDecoration = decoration,
                modifier = Modifier.alignByBaseline()
            )
            // "min" shares the number's baseline; the indicator rides at its upper-right.
            Row(modifier = Modifier.alignByBaseline(), verticalAlignment = Alignment.Top) {
                Text(
                    text = " " + stringResource(R.string.minutes_abbreviation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    textDecoration = decoration,
                    modifier = Modifier.alignByBaseline()
                )
                EtaRealtimeIndicator(predicted, color)
            }
        }
    }
}

/** The real-time indicator's reserved column (so ETAs right-justify alike); animates when live. */
@Composable
private fun EtaRealtimeIndicator(predicted: Boolean, color: Color) {
    Box(Modifier.padding(start = 2.dp).size(8.dp)) {
        if (predicted) RealtimeIndicator(color = color, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The pulsing real-time "connectedness" indicator: concentric stroked circles expanding and
 * contracting at staggered durations (transparent fill, stroked outline, FastOutLinearIn, REVERSE
 * repeat). Shared by the standalone/list ETA and the map drawer's ETA pill.
 */
@Composable
internal fun RealtimeIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "realtime")
    // Staggered durations make the rings radiate out of phase, matching the legacy 1500/1800/2000.
    val rings = listOf(1500, 1800, 2000).map { duration ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "realtime-$duration"
        )
    }
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2f
        val stroke = Stroke(width = 1.2.dp.toPx())
        rings.forEach { ring ->
            drawCircle(color = color, radius = maxRadius * ring.value, style = stroke)
        }
    }
}

private fun strikeThroughIf(canceled: Boolean): TextDecoration =
    if (canceled) TextDecoration.LineThrough else TextDecoration.None

/**
 * The prominent white-on-lateness ETA pill — one per trip in a route row's strip (and the Home legend
 * dialog, which passes no clicks). [onClick] taps focus that trip's vehicle + stop; [onLongClick]
 * opens the trip menu; [canceled] strikes the text through.
 *
 * A recent-past (negative-ETA) trip renders **compact** — 75% of the size — to de-emphasize it against
 * the upcoming arrivals, while the strip bottom-aligns so its digits still sit on the shared baseline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EtaPill(
    eta: Long,
    color: Color,
    predicted: Boolean,
    modifier: Modifier = Modifier,
    canceled: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val decoration = if (canceled) TextDecoration.LineThrough else null
    val shape = RoundedCornerShape(8.dp)
    // Recent-past arrivals (negative ETA) shrink to 75% so the upcoming "big numbers" dominate the strip.
    val compact = eta < 0
    val pillHeight = if (compact) 24.dp else 32.dp
    val numberSize = if (compact) 21.sp else 28.sp
    val labelSize = if (compact) 11.sp else 14.sp
    val nowSize = if (compact) 17.sp else 22.sp
    val indicatorSize = if (compact) 6.dp else 8.dp
    // A single combinedClickable serves both tap (focus vehicle) and long-press (trip menu). Placed on
    // the modifier the Surface clips, so the ripple stays inside the pill.
    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Surface(modifier = modifier.then(interaction), shape = shape, color = color) {
        // Content bottom-aligned so that, with the strip bottom-aligning pills of different sizes, every
        // pill's digits land on the same baseline (digits have no descender, so bottom ≈ baseline).
        Box(
            modifier = Modifier
                .height(pillHeight)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (eta != 0L) {
                    Text(
                        text = eta.toString(),
                        fontSize = numberSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textDecoration = decoration
                    )
                }
                // The trailing label ("min" / "Now") with the radiating real-time indicator at its
                // upper-right: top-aligning this inner row floats the small indicator to the label's
                // top. The Box is always present so the pill width is stable whether or not it's live.
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (eta == 0L) {
                            stringResource(R.string.stop_info_eta_now)
                        } else {
                            " " + stringResource(R.string.minutes_abbreviation)
                        },
                        fontSize = if (eta == 0L) nowSize else labelSize,
                        fontWeight = if (eta == 0L) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                        textDecoration = decoration
                    )
                    Box(Modifier.padding(start = 2.dp).size(indicatorSize)) {
                        if (predicted) {
                            RealtimeIndicator(color = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — the ETA pills from primitives (the ArrivalInfo model isn't previewable).

@Preview(showBackground = true)
@Composable
private fun EtaPillStripPreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            // Bottom-aligned like the real strip, so the compact past pill shares the baseline.
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // A recent-past arrival: renders at 75% size.
                EtaPill(-3, colorResource(R.color.stop_info_delayed), predicted = true)
                EtaPill(0, colorResource(R.color.stop_info_ontime), predicted = true)
                EtaPill(5, colorResource(R.color.stop_info_delayed), predicted = true)
                EtaPill(12, colorResource(R.color.stop_info_early), predicted = true)
                EtaPill(22, colorResource(R.color.stop_info_scheduled_time), predicted = false)
                EtaPill(8, colorResource(R.color.stop_info_scheduled_time), predicted = false, canceled = true)
            }
        }
    }
}
