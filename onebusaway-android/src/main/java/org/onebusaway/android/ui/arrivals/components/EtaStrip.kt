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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.onebusaway.android.R
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.rememberLiveServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.compose.components.CenteredLongPressMenu
import org.onebusaway.android.ui.compose.components.MaterialSymbols
import org.onebusaway.android.ui.compose.components.ScrollChevronGutter
import org.onebusaway.android.ui.compose.components.SlideBox
import org.onebusaway.android.ui.compose.components.tightLineStyle
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

// The ETA strip: a route/direction's per-trip ETA pills in a horizontally-scrollable, overflow-aware
// row (the scroll + "there's more" chevron), each pill carrying its long-press menu. Split out of
// ArrivalRows.kt so the strip is a self-contained unit; RouteArrivalRow supplies the
// badge/divider/heading scaffold around it. The scroll/glide gestures themselves live in SlideBox.kt
// (one scroll owner, issue #1801) — this file declares WHAT the strip rests on (the pinned pill) and
// renders the pills. "Load more arrivals" is a footer button below the whole list (ArrivalsScreen),
// not a gesture on this strip.

/**
 * Index of the soonest *upcoming* pill (first trip whose live ETA hasn't gone negative against
 * [now]), or -1 when every trip is recent-past. The single source of the "which pill leads" rule,
 * shared by the strip's first-display pin and its live-departure BOOKKEEPER so the two can't disagree.
 */
private fun List<ArrivalInfo>.firstUpcomingIndex(now: ServerTime): Int = indexOfFirst { it.liveEta(now) >= 0 }

/**
 * The horizontally-scrollable strip of per-trip ETA pills below the direction name. When the pills
 * overflow the row, a chevron appears at that edge to signal there's more to scroll to; tapping it
 * moves the strip one strip-width further that direction (or to the end, whichever is closer). The
 * chevron's own tap target is a narrow side gutter separate from the pills, so it never blocks the
 * strip's own drag-to-scroll.
 *
 * The strip also keeps its soonest *upcoming* pill pinned to the leading edge over time: it snaps
 * there instantly on first display, then as the shared live clock ticks a trip's ETA past zero
 * between polls, glides the strip left so the just-departed pill visibly slides into the left
 * overflow instead of sitting there stale until the next poll.
 */
@Composable
internal fun EtaStrip(
    trips: List<ArrivalInfo>,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    firstPillModifier: Modifier = Modifier,
    // Hoisted so callers (and previews) can control/observe the scroll — e.g. a preview starts it
    // mid-scroll to show both edge chevrons.
    scrollState: ScrollState = rememberScrollState()
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
    // the left, reachable via the left chevron. Initialized to the first-upcoming index so the strip
    // justifies there on first display (0 — already the strip's start — when the first pill is itself
    // upcoming, or when none is; `firstUpcomingIndex` returns -1 there, floored to 0). Uses the SAME
    // `firstUpcomingIndex` helper the BOOKKEEPER effect uses below, so first-display and steady-state
    // agree and no caller can desync them by forgetting to pass it (#1973). Thereafter only ever
    // advances forward, from the BOOKKEEPER — never yanked backward by an ordinary poll data reshuffle.
    var pinnedIndex by remember {
        mutableIntStateOf(trips.firstUpcomingIndex(liveNow).coerceAtLeast(0))
    }

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
        snapshotFlow { tripsState.value.firstUpcomingIndex(liveNowState.value) }
            .collect { current ->
                if (current > pinnedIndex) {
                    pinnedIndex = current
                    arrowOverridePx = null
                }
            }
    }

    // Jumps the strip one viewport toward the given direction (or to the end, whichever is
    // closer) by setting the one-shot arrow override above; SlideBox's own glide clamps the
    // result to [0, maxValue], so no clamping is needed here.
    fun jumpArrow(forward: Boolean) {
        val delta = if (forward) scrollState.viewportSize else -scrollState.viewportSize
        arrowOverridePx = scrollState.value + delta
    }

    Row(modifier, verticalAlignment = Alignment.Bottom) {
        // Left gutter: a chevron back toward earlier arrivals, shown once the strip is scrolled off its
        // start. Reserved (like the right gutter) so toggling it never reflows the pills.
        ScrollChevronGutter(
            visible = canScrollBackward,
            pointsRight = false,
            contentDescriptionRes = R.string.stop_info_eta_strip_scroll_earlier,
            onClick = { jumpArrow(forward = false) }
        )

        // The scrollable pill content, inside the gesture-owning SlideBox: the strip DECLARES what it
        // rests on (the pinned pill) and the box does all scrolling/gliding itself with a single
        // scroll owner (issue #1801).
        SlideBox(
            scroll = scrollState,
            anchorPx = { arrowOverridePx ?: pinnedOffsetPx.takeIf { it >= 0 } },
            // Keep the pinned pill's offset scroll-reachable so the strip justifies to it even when
            // the pills fit the viewport (horizontalScroll would otherwise cap maxValue below it and
            // the justify would silently no-op, leaving the recent-past pills on screen). The
            // persistent pinned offset, deliberately NOT the arrow override — a chevron jump should
            // stop at the real content end, not stretch it.
            minReachablePx = { pinnedOffsetPx.takeIf { it >= 0 } },
            // height(IntrinsicSize.Max) fixes the scrolling row to its tallest pill, so a shorter
            // pill — the single-line "NOW" pill, which has no clock subline — can fillMaxHeight up to
            // match its neighbours. The layout does the leveling; no pill guesses another's height.
            modifier = Modifier.weight(1f).height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            // Bottom-align so a smaller recent-past pill sits on the same baseline as the full-size ones.
            verticalAlignment = Alignment.Bottom
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
                    modifier = pillModifier
                )
            }
        }

        // Right gutter: a chevron forward toward later arrivals.
        ScrollChevronGutter(
            visible = canScrollForward,
            pointsRight = true,
            contentDescriptionRes = R.string.stop_info_eta_strip_scroll_later,
            onClick = { jumpArrow(forward = true) }
        )
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // trip.displayTime only changes on a fresh poll, but liveNow (and so this composable) recomposes
    // every second (issue #1781's ticker) — memoize so the locale-aware format call doesn't re-run on
    // every tick.
    val context = LocalContext.current
    val clockTime = remember(trip.displayTime, context) {
        DisplayFormat.formatTime(context, trip.displayTime.epochMs)
    }
    // fillMaxHeight here and on the pill so the colored Surface stretches to the strip's tallest pill
    // (fixed by the SlideBox's IntrinsicSize.Max modifier — see EtaStrip), levelling the shorter
    // single-line NOW pill up to its neighbours.
    Box(modifier.fillMaxHeight()) {
        EtaPill(
            modifier = Modifier.fillMaxHeight(),
            eta = trip.liveEta(liveNow),
            color = colorResource(trip.color),
            predicted = trip.predicted,
            canceled = trip.status == Status.CANCELED,
            clockTime = clockTime,
            onClick = { callbacks.onEtaClick(trip) },
            onLongClick = { expanded = true }
        )
        TripActionsMenu(expanded, { expanded = false }, trip, actions, callbacks)
    }
}

/** The per-trip menu opened by long-pressing a pill: trip details, a reminder, or a problem report
 *  for that specific trip. Route-wide actions live on the row's long-press menu ([RouteActionsMenu]). */
@Composable
internal fun TripActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    arrival: ArrivalInfo,
    actions: ArrivalActions?,
    callbacks: ArrivalRowCallbacks
) {
    CenteredLongPressMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuRow(R.string.bus_options_menu_show_trip_details, MaterialSymbols.TripStatus) {
            onDismiss()
            callbacks.onShowTripStatus(arrival)
        }
        MenuRow(R.string.bus_options_menu_set_reminder, MaterialSymbols.AddReminder) {
            onDismiss()
            callbacks.onSetReminder(arrival)
        }
        if (actions != null) {
            MenuRow(R.string.bus_options_menu_report_trip_problem, MaterialSymbols.Report) {
                onDismiss()
                callbacks.onReportArrivalProblem(actions)
            }
        }
    }
}

/**
 * The prominent white-on-lateness ETA pill — one per trip in a route row's strip (and the Home legend
 * dialog, which passes no clicks). [onClick] taps focus that trip's vehicle + stop; [onLongClick]
 * opens the trip menu; [canceled] strikes the text through. [clockTime] is the small "1:10pm"-style
 * clock time shown below the ETA (issue #1786); null omits that line (e.g. the Home legend's
 * illustrative pills, which aren't tied to a real arrival time). The "NOW" pill ([eta] == 0) always
 * omits it too — it's a single centered label — so it's shorter by content; the strip levels it back
 * to its neighbours' height with fillMaxHeight (see EtaStrip's SlideBox modifier / EtaPillWithMenu).
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
    onLongClick: (() -> Unit)? = null
) {
    val decoration = if (canceled) TextDecoration.LineThrough else null
    val shape = RoundedCornerShape(8.dp)
    val numberSize = 28.sp
    // "NOW" reads a touch too urgent at the full number size, so its glyph is dialed back slightly —
    // still clearly dominant, just not shouting (#1805 unified it to numberSize; this softens it). The
    // pill has no clock subline, so it's shorter than its neighbours by content — the strip stretches
    // it back to their height via fillMaxHeight (see EtaStrip), and the label is centered within it.
    val nowSize = 26.sp
    val labelSize = 14.sp
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
    // Numbers stay bold-sized, only the unit letters shrink (see formatEtaParts for the part shape;
    // etaAnnotatedString for why they render as a single AnnotatedString).
    val etaParts = if (eta != 0L) DisplayFormat.formatEtaParts(LocalContext.current, eta) else null
    // See tightLineStyle's doc: keyed to each Text's own (dominant) size, so the padding/gap values
    // below are the actual on-screen spacing rather than a guess fighting Android's hidden font padding.
    val baseTextStyle = LocalTextStyle.current
    Surface(modifier = modifier.then(interaction), shape = shape, color = color) {
        // A Box so the live indicator can overlay the pill (below) instead of reserving layout width:
        // live and scheduled pills stay identical widths, and the glyph is free to overlap the ETA
        // text at the top-trailing corner rather than widening the pill.
        // fillMaxHeight so the content box fills the (possibly stretched) Surface; Center so the
        // single-line NOW label sits mid-pill when the Surface is taller than its own text. For a
        // numeric pill the Surface already hugs its content, so centering is a no-op there.
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            // Sized to its own content (no fixed height) so the optional clock-time line simply adds to
            // the pill's height rather than being clipped by — or leaving a gap below it in — a height
            // guessed independently of the actual text metrics.
            Column(
                modifier = Modifier.padding(
                    start = 6.dp,
                    end = 6.dp,
                    top = topPadding,
                    bottom = bottomPadding
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
                    Text(
                        text = etaAnnotatedString(
                            etaParts,
                            emphasizedSpan = SpanStyle(
                                fontSize = numberSize,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            unemphasizedSpan = SpanStyle(
                                fontSize = labelSize,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                        ),
                        textDecoration = decoration,
                        // Keyed to numberSize (the line's dominant glyph) — the smaller labelSize span
                        // rides the same trimmed line box rather than getting one of its own.
                        style = remember(baseTextStyle, numberSize) { tightLineStyle(baseTextStyle, numberSize) }
                    )
                }
                // The NOW pill (etaParts == null) drops the clock subline — it's a single centered
                // label, stretched to its neighbours' height by fillMaxHeight rather than by a second
                // line of its own.
                if (clockTime != null && etaParts != null) {
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
                        .size(indicatorSize)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews.

/** [count] "40 Northgate" pills with increasing upcoming ETAs, for the strip previews. */
private fun northgatePills(count: Int) = List(count) { previewArrival("40", "Northgate", etaMinutes = 3L + it * 8) }

/**
 * Shared strip-preview scaffold. height(IntrinsicSize.Min) bounds the row to the pill height — as
 * RouteArrivalRow's IntrinsicSize.Min row does in production — so the chevrons' fillMaxHeight resolves
 * to the pills instead of filling the whole preview surface.
 */
@Composable
private fun EtaStripPreviewFrame(
    trips: List<ArrivalInfo>,
    scrollState: ScrollState = rememberScrollState()
) {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.height(IntrinsicSize.Min).padding(8.dp)) {
                EtaStrip(
                    trips = trips,
                    actionsFor = { null },
                    callbacks = previewRowCallbacks(),
                    scrollState = scrollState
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
            previewArrival("8", "Rainier Beach", etaMinutes = 12)
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
                    8,
                    colorResource(R.color.stop_info_scheduled_time),
                    predicted = false,
                    canceled = true,
                    clockTime = "3:08pm"
                )
                // Past an hour: the number switches to hours, the leftover minutes fold into the label (#1777).
                EtaPill(83, colorResource(R.color.stop_info_scheduled_time), predicted = true, clockTime = "4:23pm")
                EtaPill(125, colorResource(R.color.stop_info_early), predicted = false, clockTime = "5:05pm")
            }
        }
    }
}
