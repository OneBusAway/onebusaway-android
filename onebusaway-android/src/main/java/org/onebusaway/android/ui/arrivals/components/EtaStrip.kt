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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.onebusaway.android.R
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.rememberLiveServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.compose.components.SlideBox
import org.onebusaway.android.ui.compose.components.rememberSlideBoxState
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

// The ETA strip: a route/direction's per-trip ETA pills in a horizontally-scrollable, overflow-aware
// row (the scroll + "there's more" chevron), each pill carrying its long-press menu, plus the
// pull-past-the-end gesture that widens the arrivals window. Split out of ArrivalRows.kt so the strip
// is a self-contained unit; RouteArrivalRow supplies the badge/divider/heading scaffold around it.
// The scroll/pull/glide gestures themselves live in SlideBox.kt (one scroll owner, issue #1801) —
// this file declares WHAT the strip rests on (the pinned pill, or the trailing end during a
// load-more reveal) and renders the pills.

/**
 * The horizontally-scrollable strip of per-trip ETA pills below the direction name. When the pills
 * overflow the row, a chevron appears at that edge to signal there's more to scroll to; tapping it
 * moves the strip one strip-width further that direction (or to the end, whichever is closer). The
 * chevron's own tap target is a narrow side gutter separate from the pills, so it never blocks the
 * strip's own drag-to-scroll.
 *
 * Dragging the strip past its last pill (once there's nothing more to scroll) builds a resistive pull
 * ([SlideBox]'s gesture) that reveals a trailing load-more affordance; releasing once it's armed
 * widens the arrivals window via [ArrivalRowCallbacks.onLoadMore] — this replaced the drawer's footer
 * button (issue #1707 follow-up). A TalkBack custom action exposes the same load-more for non-drag users.
 *
 * The strip also keeps its soonest *upcoming* pill pinned to the leading edge over time: it snaps
 * there instantly on first display (using [start]), then as the shared live clock ticks a trip's ETA
 * past zero between polls, glides the strip left so the just-departed pill visibly slides into the
 * left overflow instead of sitting there stale until the next poll.
 */
@Composable
internal fun EtaStrip(
    trips: List<ArrivalInfo>,
    // The Content.dataVersion this strip's trips came from. MUST come from the same Content object as
    // the trips themselves, so the strip can never see a version ahead of its rendered pills.
    dataVersion: Long,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    // The trip index to justify to the strip's leading (left) edge on first display; earlier trips
    // (e.g. the recent-past, negative-ETA pills) then overflow off the left, reachable via the left
    // chevron. Null (or index 0) leaves the strip at its start.
    start: Int? = null,
    firstPillModifier: Modifier = Modifier,
    // Hoisted so callers (and previews) can control/observe the scroll — e.g. a preview starts it
    // mid-scroll to show both edge chevrons.
    scrollState: ScrollState = rememberScrollState(),
) {
    val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }
    val canScrollBackward by remember { derivedStateOf { scrollState.canScrollBackward } }

    // All of this strip's trips share one poll (one route/direction group from a single
    // ConvertArrivals pass), so their serverNow is identical — tick ONE shared clock here rather than
    // a redundant per-pill ticker/coroutine (issue #1781). ServerTime(0) is an inert placeholder for
    // the (pill-less) empty-trips case; nothing reads it since the pill loop below never runs.
    val liveNow = rememberLiveServerTime(trips.firstOrNull()?.serverNow ?: ServerTime(0L))

    // The pinned pill's own content-x (positionInParent is scroll-independent, so it's the offset from
    // the content's start), -1 until measured. Only the currently-pinned pill is ever measured — see
    // the pill loop below — so this holds one live value rather than a map of every pill's offset.
    var pinnedOffsetPx by remember { mutableIntStateOf(-1) }

    // The pill currently pinned to the strip's leading edge — earlier (recent-past) pills overflow off
    // the left, reachable via the left chevron. Starts at the poll-time first-upcoming index (or 0,
    // already the strip's start, when every trip is upcoming); only ever advances forward, from the
    // BOOKKEEPER effect below — never yanked backward by an ordinary poll data reshuffle.
    var pinnedIndex by remember { mutableIntStateOf(start?.takeIf { it in 1..trips.lastIndex } ?: 0) }

    // A one-shot scroll target (absolute, same units as pinnedOffsetPx) set by tapping an overflow
    // chevron; takes priority over the pinned-pill anchor below. Left in place once reached — like an
    // ordinary drag, an arrow tap should stick rather than snap back — and cleared only when the pin
    // itself next advances (see the BOOKKEEPER effect), so live departure-tracking still wins over a
    // stale manual position exactly as it already does against a plain drag.
    var arrowOverridePx by remember { mutableStateOf<Int?>(null) }

    // A later poll can SHRINK `trips` (recent-past trips aging out of the feed), which the
    // forward-only ratchet above can't fix on its own — clamp back onto the new list's bounds so the
    // pin always names a real pill instead of freezing `pinnedOffsetPx` on one that no longer exists.
    pinnedIndex = pinnedIndex.coerceAtMost(trips.lastIndex.coerceAtLeast(0))

    // Bridges values that change across recompositions into the long-lived effect below, which
    // otherwise would close over a stale snapshot the moment it first suspends — the same idiom as
    // `versionState` a few lines down.
    val tripsState = rememberUpdatedState(trips)
    val liveNowState = rememberUpdatedState(liveNow)

    // BOOKKEEPER: advances `pinnedIndex` as `liveNow` ticks a trip's countdown past zero between
    // polls — a single long-lived collector (not re-launched per tick), so a departure is observed
    // exactly once as a level change, not by polling a recomposition-derived key. Never backward, so
    // an ordinary poll data reshuffle can't yank the pin; only a live departure moves it.
    LaunchedEffect(Unit) {
        snapshotFlow { tripsState.value.indexOfFirst { it.liveEta(liveNowState.value) >= 0 } }
            .collect { current ->
                if (current > pinnedIndex) {
                    pinnedIndex = current
                    arrowOverridePx = null
                }
            }
    }

    val loadMoreLabel = stringResource(R.string.stop_info_load_more_arrivals)

    // Jumps the strip one viewport toward the given direction (or to the end, whichever is
    // closer) by setting the one-shot arrow override above; SlideBox's own glide clamps the
    // result to [0, maxValue], so no clamping is needed here.
    fun jumpArrow(forward: Boolean) {
        val delta = if (forward) scrollState.viewportSize else -scrollState.viewportSize
        arrowOverridePx = scrollState.value + delta
    }

    // The strip's active load-more request: the token returned at fire time, NO_LOAD_REQUEST at rest.
    // Saveable so a list eviction / recreation mid-load resumes (or cleanly ends) the transaction.
    val request = rememberSaveable { mutableIntStateOf(NO_LOAD_REQUEST) }
    val loadMore by callbacks.loadMoreState.collectAsStateWithLifecycle()
    // The spinner shows from fire until the composition that carries the completing data's version, so
    // the spinner and the new pills swap in the SAME composition — never a frame where both/neither
    // show. At rest, request is NO_LOAD_REQUEST, which reads as Superseded → no spinner.
    val loadingMore = spinnerVisible(loadMoreOutcome(loadMore, request.intValue), dataVersion)

    val slideBox = rememberSlideBoxState(scrollState)

    // Layout acknowledgement: the highest dataVersion whose strip content has been MEASURED. Written in
    // the measure pass that wraps horizontalScroll — the same pass (and snapshot batch) in which
    // scrollState.maxValue is brought up to date — so `measuredVersion >= V` GUARANTEES maxValue is
    // consistent with data version V. Read only from snapshotFlow (never composition), so writing it
    // per layout pass can't cause recomposition loops.
    val measuredVersion = remember { mutableLongStateOf(0L) }
    val versionState = rememberUpdatedState(dataVersion)

    // The reveal transaction for this strip's request: while `request` is live, the SlideBox's
    // followEnd regime (declared below) keeps the strip pinned to its (moving) right end — first the
    // spinner slot, then the reloaded pills. This effect decides when that transaction ENDS: once the
    // layout provably reflects the data that completed the request and the strip is at rest at the
    // true end. Every wait is a level-triggered predicate over monotonic snapshot/StateFlow state, so
    // a signal that fires before we start listening is still observed.
    LaunchedEffect(request.intValue) {
        val token = request.intValue
        if (token == NO_LOAD_REQUEST) return@LaunchedEffect
        try {
            // AWAIT RESULT: level-triggered on the VM's StateFlow — a Finished that landed before
            // we started collecting is still observed (StateFlow replays its value).
            val landed = callbacks.loadMoreState
                .map { loadMoreOutcome(it, token) }
                .first { it !is LoadMoreOutcome.Pending }
            if (landed is LoadMoreOutcome.Landed) {
                // SETTLE: wait until (a) layout reflects the completing data's version — which,
                // via the layout-ack modifier, also means maxValue is current for that data and
                // the spinner slot is gone (spinner and new pills swap in the same composition,
                // both keyed on dataVersion) — and (b) the box reports itself at rest at the true
                // end. Success with new pills, success with none for this row, and failure all take
                // this one path: the end is wherever layout says it is.
                snapshotFlow {
                    measuredVersion.longValue >= landed.dataVersion && slideBox.isSettledAtEnd
                }.first { it }
            }
        } finally {
            // Idempotent teardown; guarded so a user-takeover or a re-fire (which already moved
            // `request`) isn't clobbered.
            if (request.intValue == token) request.intValue = NO_LOAD_REQUEST
        }
    }

    Row(
        modifier.semantics {
            customActions = listOf(
                // The full transaction, so TalkBack users get the same spinner + reveal.
                CustomAccessibilityAction(loadMoreLabel) {
                    request.intValue = callbacks.onLoadMore(); true
                }
            )
        },
        verticalAlignment = Alignment.Bottom
    ) {
        // Left gutter: a chevron back toward earlier arrivals, shown once the strip is scrolled off its
        // start. Reserved (like the right gutter) so toggling it never reflows the pills.
        ScrollChevronGutter(
            visible = canScrollBackward && !loadingMore,
            pointsRight = false,
            contentDescriptionRes = R.string.stop_info_eta_strip_scroll_earlier,
            onClick = { jumpArrow(forward = false) },
        )

        // The scrollable pill content, inside the gesture-owning SlideBox: the strip DECLARES what it
        // rests on — the pinned pill, or the trailing end while a load-more reveal is live — and the
        // box does all scrolling/gliding itself with a single scroll owner (issue #1801). The pull
        // gesture lives in the box too (the gutters don't scroll).
        SlideBox(
            state = slideBox,
            anchorPx = { arrowOverridePx ?: pinnedOffsetPx.takeIf { it >= 0 } },
            followEnd = request.intValue != NO_LOAD_REQUEST,
            onPullFired = {
                // The VM sets Loading(token) synchronously inside onLoadMore, so the spinner is
                // already visible in the composition this write lands in — no gap.
                request.intValue = callbacks.onLoadMore()
            },
            onUserScroll = {
                // A real user scroll on this strip while a reveal transaction is active ends it: the
                // user wins — the SlideBox hands the scroll back and the spinner is dropped. The load
                // itself continues in the ViewModel; its pills land unanimated. (Unconditional: an
                // equal-value write is a snapshot no-op under structural equality.)
                request.intValue = NO_LOAD_REQUEST
            },
            modifier = Modifier.weight(1f),
            // The composition↔layout bridge: measure the scroll content, then record the data
            // version this layout reflects (see acknowledgeVersion).
            contentModifier = Modifier.acknowledgeVersion(versionState, measuredVersion),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            // Bottom-align so a smaller recent-past pill sits on the same baseline as the full-size ones.
            verticalAlignment = Alignment.Bottom,
            overlay = {
                // The pull-revealed load-more chip (invisible at rest, armed as the pull crosses the
                // threshold). Hidden while loading — the inline spinner takes over then.
                if (!loadingMore) {
                    LoadMorePullChip(
                        progress = { slideBox.pullProgress },
                        armed = { slideBox.armed },
                        contentDescription = loadMoreLabel,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            },
        ) {
            trips.forEachIndexed { index, trip ->
                // Only the currently-pinned pill measures its content-space offset — it's the one
                // pill the SlideBox anchor ever reads, and re-measuring continuously (not just once)
                // catches its own width shifting as its digit count changes. As `pinnedIndex`
                // advances, this modifier simply moves to the new pill on the next recomposition.
                val measureOffset = if (index == pinnedIndex) {
                    Modifier.onGloballyPositioned { coords ->
                        // Offset within the scroll content Row (its direct parent), which is
                        // content-space and so scroll-independent.
                        coords.parentLayoutCoordinates?.let { parent ->
                            pinnedOffsetPx = parent.localPositionOf(coords, Offset.Zero).x.roundToInt()
                        }
                    }
                } else {
                    Modifier
                }
                val pillModifier = if (index == 0) firstPillModifier.then(measureOffset) else measureOffset
                EtaPillWithMenu(
                    trip = trip,
                    liveNow = liveNow,
                    actions = actionsFor(trip),
                    callbacks = callbacks,
                    modifier = pillModifier,
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

        // Right gutter: a chevron forward toward later arrivals (the pull-past-the-end load affordance
        // takes over inside the content box once you reach the end).
        ScrollChevronGutter(
            visible = canScrollForward && !loadingMore,
            pointsRight = true,
            contentDescriptionRes = R.string.stop_info_eta_strip_scroll_later,
            onClick = { jumpArrow(forward = true) },
        )
    }
}

/**
 * A fixed-width trailing/leading gutter holding the "more to scroll" chevron, [visible] when that
 * direction has more content. [pointsRight] picks the direction; the left reuses the right chevron
 * drawable rotated 180°. The slot is reserved even when hidden so toggling it never reflows the
 * pills. Tapping it fires [onClick] (a one-page scroll toward that edge); only wired up while
 * [visible], so a hidden gutter is never an invisible tap target.
 */
@Composable
private fun ScrollChevronGutter(
    visible: Boolean,
    pointsRight: Boolean,
    contentDescriptionRes: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(20.dp)
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            Icon(
                painter = painterResource(R.drawable.ic_navigation_chevron_right),
                // Resolved only while shown, since this composable recomposes on every liveNow tick.
                contentDescription = stringResource(contentDescriptionRes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (pointsRight) 0f else 180f)
            )
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
    Surface(
        modifier = modifier.graphicsLayer {
            val p = progress()
            alpha = p
            // Slide in from the right edge as the pull grows (fully seated at p == 1).
            translationX = (1f - p) * slidePx
        },
        shape = CircleShape,
        color = container,
        // Sets LocalContentColor, so the Icon below inherits it instead of an explicit tint.
        contentColor = content,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** A single ETA pill with its long-press per-trip menu. Tap focuses the vehicle; long-press opens
 *  the menu (trip details / reminder / report). [liveNow] is the strip's one shared ticking clock
 *  (issue #1781) — counts this pill down between polls rather than freezing at the poll-time eta. */
@Composable
private fun EtaPillWithMenu(
    trip: ArrivalInfo,
    liveNow: ServerTime,
    actions: ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // trip.displayTime only changes on a fresh poll, but liveNow (and so this composable) recomposes
    // every second (issue #1781's ticker) — memoize so the locale-aware format call doesn't re-run on
    // every tick.
    val context = LocalContext.current
    val clockTime = remember(trip.displayTime, context) {
        DisplayFormat.formatTime(context, trip.displayTime.epochMs)
    }
    Box(modifier) {
        EtaPill(
            eta = trip.liveEta(liveNow),
            color = colorResource(trip.color),
            predicted = trip.predicted,
            canceled = trip.status == Status.CANCELED,
            clockTime = clockTime,
            onClick = { callbacks.onEtaClick(trip) },
            onLongClick = { expanded = true },
        )
        TripActionsMenu(expanded, { expanded = false }, trip, actions, callbacks)
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

/**
 * [base] with Android's default font-metrics padding (extra ascent/descent space reserved beyond a
 * glyph's visible ink) trimmed to [size]'s true line height. Without this, every gap in a small
 * pill/badge — row-to-row, row-to-edge — is a function of opaque per-font-size padding instead of the
 * caller's own explicit spacing.
 */
private fun tightLineStyle(base: TextStyle, size: TextUnit) = base.copy(
    lineHeight = size,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

/**
 * The prominent white-on-lateness ETA pill — one per trip in a route row's strip (and the Home legend
 * dialog, which passes no clicks). [onClick] taps focus that trip's vehicle + stop; [onLongClick]
 * opens the trip menu; [canceled] strikes the text through. [clockTime] is the small "1:10pm"-style
 * clock time shown below the ETA (issue #1786); null omits that line (e.g. the Home legend's
 * illustrative pills, which aren't tied to a real arrival time).
 *
 * Every pill renders at the same size regardless of [eta] — a recent-past (negative-ETA) trip is
 * distinguished from upcoming ones by the strip's own scroll position (it's justified off the leading
 * edge, reachable via the left chevron) rather than a smaller pill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EtaPill(
    eta: Long,
    color: Color,
    predicted: Boolean,
    modifier: Modifier = Modifier,
    canceled: Boolean = false,
    clockTime: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val decoration = if (canceled) TextDecoration.LineThrough else null
    val shape = RoundedCornerShape(8.dp)
    val numberSize = 28.sp
    val labelSize = 14.sp
    val nowSize = 22.sp
    val indicatorSize = 12.dp // 1.5× the base accent; overlaid, so the extra size overlaps, not widens
    val clockTimeSize = 10.sp
    val topPadding = 3.dp
    val bottomPadding = 3.5.dp
    // Negative: tightLineStyle's trim gets the ETA row and clock-time line close but not flush (some
    // residual line-box slack survives it), so this pulls them the rest of the way — tuned by eye
    // against a device screenshot, not derived from the other constants above.
    val clockTimeGap = (-2).dp
    // A single combinedClickable serves both tap (focus vehicle) and long-press (trip menu). Placed on
    // the modifier the Surface clips, so the ripple stays inside the pill.
    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else {
        Modifier
    }
    // One consistent "Xhr Ymin" shape: ["23", "min"] under an hour (the "0hr" is omitted), or
    // ["1", "hr", " 30", "min"] past it — every number stays bold-sized, only the unit letters
    // shrink, so the leftover minutes stay as legible as the hour count (#1777).
    val etaParts = if (eta != 0L) DisplayFormat.formatEtaParts(LocalContext.current, eta) else null
    // See tightLineStyle's doc: keyed to each Text's own (dominant) size, so the padding/gap values
    // below are the actual on-screen spacing rather than a guess fighting Android's hidden font padding.
    val baseTextStyle = LocalTextStyle.current
    Surface(modifier = modifier.then(interaction), shape = shape, color = color) {
        // A Box so the live indicator can overlay the pill (below) instead of reserving layout width:
        // live and scheduled pills stay identical widths, and the glyph is free to overlap the ETA
        // text at the top-trailing corner rather than widening the pill.
        Box {
            // Sized to its own content (no fixed height) so the optional clock-time line simply adds to
            // the pill's height rather than being clipped by — or leaving a gap below it in — a height
            // guessed independently of the actual text metrics. Pills of different heights (with vs.
            // without a clock line) still share a bottom edge via the strip's own
            // `verticalAlignment = Alignment.Bottom` (EtaStrip's Row).
            Column(
                modifier = Modifier.padding(
                    start = 6.dp, end = 6.dp, top = topPadding, bottom = bottomPadding
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(clockTimeGap)
            ) {
                if (etaParts == null) {
                    Text(
                        text = stringResource(R.string.stop_info_eta_now),
                        fontSize = nowSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textDecoration = decoration,
                        style = remember(baseTextStyle, nowSize) { tightLineStyle(baseTextStyle, nowSize) }
                    )
                } else {
                    // A single AnnotatedString (not separate Text composables) so the text shaper kerns
                    // across the number/unit boundary instead of gluing together independently-measured
                    // boxes — splitting them produced inconsistent gaps (e.g. "1hr" vs "4hr").
                    Text(
                        text = buildAnnotatedString {
                            etaParts.forEach { part ->
                                withStyle(
                                    SpanStyle(
                                        fontSize = if (part.emphasized) numberSize else labelSize,
                                        fontWeight = if (part.emphasized) FontWeight.Bold else FontWeight.Normal,
                                        color = Color.White
                                    )
                                ) {
                                    append(part.text)
                                }
                            }
                        },
                        textDecoration = decoration,
                        // Keyed to numberSize (the line's dominant glyph) — the smaller labelSize span
                        // rides the same trimmed line box rather than getting one of its own.
                        style = remember(baseTextStyle, numberSize) { tightLineStyle(baseTextStyle, numberSize) }
                    )
                }
                if (clockTime != null) {
                    Text(
                        text = clockTime,
                        fontSize = clockTimeSize,
                        color = Color.White.copy(alpha = 0.8f),
                        textDecoration = decoration,
                        style = remember(baseTextStyle, clockTimeSize) { tightLineStyle(baseTextStyle, clockTimeSize) }
                    )
                }
            }
            // The live indicator, overlaid on the top-trailing corner so its 1.5× size overlaps the
            // ETA text a little instead of widening the pill (drawn last = on top).
            if (predicted) {
                RealtimeIndicator(
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 1.dp)
                        .size(indicatorSize),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews.

/** [count] "40 Northgate" pills with increasing upcoming ETAs, for the strip previews. */
private fun northgatePills(count: Int) =
    List(count) { previewArrival("40", "Northgate", etaMinutes = 3L + it * 8) }

/**
 * Shared strip-preview scaffold. height(IntrinsicSize.Min) bounds the row to the pill height — as
 * RouteArrivalRow's IntrinsicSize.Min row does in production — so the chevrons' fillMaxHeight resolves
 * to the pills instead of filling the whole preview surface.
 */
@Composable
private fun EtaStripPreviewFrame(
    trips: List<ArrivalInfo>,
    scrollState: ScrollState = rememberScrollState(),
) {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.height(IntrinsicSize.Min).padding(8.dp)) {
                EtaStrip(
                    trips = trips,
                    dataVersion = 1L,
                    actionsFor = { null },
                    callbacks = previewRowCallbacks(),
                    scrollState = scrollState,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 240, name = "EtaStrip · overflowing (chevron)")
@Composable
private fun EtaStripOverflowPreview() {
    // Enough pills to exceed the 240dp width, so the right-edge chevron hint appears.
    EtaStripPreviewFrame(trips = northgatePills(6))
}

@Preview(showBackground = true, widthDp = 240, name = "EtaStrip · scrolled (both chevrons)")
@Composable
private fun EtaStripScrolledPreview() {
    // Started part-way scrolled (content hanging off BOTH ends), so both the left- and right-edge
    // chevrons show. The initial offset clamps to the range after layout.
    EtaStripPreviewFrame(trips = northgatePills(7), scrollState = rememberScrollState(initial = 300))
}

@Preview(showBackground = true, widthDp = 240, name = "EtaStrip · fits (no chevron)")
@Composable
private fun EtaStripFitsPreview() {
    // Two pills fit inside 240dp, so no scroll and no chevron.
    EtaStripPreviewFrame(
        trips = listOf(
            previewArrival("8", "Rainier Beach", etaMinutes = 4),
            previewArrival("8", "Rainier Beach", etaMinutes = 12),
        )
    )
}

/** A gallery of individual [EtaPill] states (not the strip): recent-past, "Now", the lateness
 *  colors, a canceled pill, and the past-an-hour "Xhr Ymin" form. */
@Preview(showBackground = true)
@Composable
private fun EtaPillVariantsPreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // A recent-past arrival: same size as the upcoming ones — negative ETAs are
                // distinguished by strip scroll position, not pill size.
                EtaPill(-3, colorResource(R.color.stop_info_delayed), predicted = true, clockTime = "2:57pm")
                EtaPill(0, colorResource(R.color.stop_info_ontime), predicted = true, clockTime = "3:00pm")
                EtaPill(5, colorResource(R.color.stop_info_delayed), predicted = true, clockTime = "3:05pm")
                EtaPill(12, colorResource(R.color.stop_info_early), predicted = true, clockTime = "3:12pm")
                EtaPill(22, colorResource(R.color.stop_info_scheduled_time), predicted = false, clockTime = "3:22pm")
                EtaPill(
                    8, colorResource(R.color.stop_info_scheduled_time), predicted = false, canceled = true,
                    clockTime = "3:08pm"
                )
                // Past an hour: the number switches to hours, the leftover minutes fold into the label (#1777).
                EtaPill(83, colorResource(R.color.stop_info_scheduled_time), predicted = true, clockTime = "4:23pm")
                EtaPill(125, colorResource(R.color.stop_info_early), predicted = false, clockTime = "5:05pm")
            }
        }
    }
}
