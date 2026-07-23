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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.rememberLiveServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.compose.components.CenteredLongPressMenu
import org.onebusaway.android.ui.compose.components.MaterialSymbols
import org.onebusaway.android.ui.compose.components.ScrollChevronGutter
import org.onebusaway.android.ui.compose.components.tightLineStyle
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.DisplayFormat

// The ETA strip: a route/direction's per-trip ETA pills in a horizontally-scrollable, overflow-aware
// row (the scroll + "there's more" chevron), each pill carrying its long-press menu. Split out of
// ArrivalRows.kt so the strip is a self-contained unit; RouteArrivalRow supplies the
// badge/divider/heading scaffold around it. Scrolling is a stock LazyRow / LazyListState the user
// drives; the strip never scrolls itself (the only programmatic move is a chevron tap's one-shot
// jump), so there is no glide to contend with a fling — the #1801/#1974 main-thread cancel-storm is
// gone by construction. "Load more arrivals" is a footer button below the whole list (ArrivalsScreen),
// not a gesture on this strip.

/**
 * The horizontally-scrollable strip of per-trip ETA pills below the direction name. Pills are shown
 * in feed order from the first one; the strip never auto-scrolls, so a trip whose ETA has gone
 * negative just keeps counting down in place. When the pills overflow the row, a chevron appears at
 * that edge to signal there's more to scroll to; tapping it moves the strip one viewport that
 * direction (or to the end, whichever is closer). The chevron's own tap target is a narrow side
 * gutter separate from the pills, so it never blocks the strip's own drag-to-scroll.
 */
@Composable
internal fun EtaStrip(
    trips: List<ArrivalInfo>,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    firstPillModifier: Modifier = Modifier,
    // Hoisted for previews/tests ONLY (both real call sites use the default) so a caller can start
    // the strip mid-scroll.
    state: LazyListState = rememberLazyListState()
) {
    // All of this strip's trips share one poll (one route/direction group from a single
    // ConvertArrivals pass), so their serverNow is identical — tick ONE shared clock here rather than
    // a redundant per-pill ticker/coroutine (issue #1781). ServerTime(0) is an inert placeholder for
    // the (pill-less) empty-trips case; nothing reads it since the pill loop below never runs.
    val liveNow = rememberLiveServerTime(trips.firstOrNull()?.serverNow ?: ServerTime(0L))

    // The strip viewport width in px, for the one-viewport chevron jump below.
    var viewportPx by remember { mutableIntStateOf(0) }

    // Read directly — LazyListState.canScroll* are already snapshot-backed and only flip at the
    // scrollable/not boundary.
    val canScrollForward = state.canScrollForward
    val canScrollBackward = state.canScrollBackward

    // Jumps the strip one viewport toward the given direction; animateScrollBy clamps at the content
    // ends, giving "or to the end, whichever is closer" for free.
    val scope = rememberCoroutineScope()
    fun jumpArrow(forward: Boolean) {
        val delta = if (forward) viewportPx.toFloat() else -viewportPx.toFloat()
        scope.launch { state.animateScrollBy(delta) }
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

        // The scrollable pill content. The reference frame fixes the LazyRow's height to the tallest
        // pill variant so the shorter single-line "NOW" pill levels up to its neighbours (see
        // ReferencePillHeightFrame); it also shields the intrinsic passes the hosts run (a LazyRow is
        // a SubcomposeLayout, whose intrinsics throw).
        ReferencePillHeightFrame(
            modifier = Modifier.weight(1f),
            reference = {
                // An invisible tallest-variant pill (two-line ETA + clock subline), measured to size
                // the row and never placed — so it's never drawn, takes no input, adds no semantics.
                // Constant params, so it never recomposes on the live clock tick.
                EtaPill(eta = 10, color = Color.Transparent, predicted = false, clockTime = "0:00")
            }
        ) {
            LazyRow(
                state = state,
                modifier = Modifier.onSizeChanged { viewportPx = it.width },
                horizontalArrangement = Arrangement.spacedBy(PILL_SPACING),
                // Bottom-align so a smaller pill sits on the same baseline as the full-size ones.
                verticalAlignment = Alignment.Bottom
            ) {
                itemsIndexed(
                    trips,
                    // Trip-instance identity, so a poll that drops an aged-out leading trip keeps the
                    // viewport on the surviving pills instead of shifting by an index. It's the SAME
                    // (tripId, serviceDate, stopSequence) triple the arrivals dedup treats as one
                    // instance (see collapseBlockIdPhantoms) — tripId alone is NOT unique (a loop
                    // route's two genuine visits to one stop share it), and a duplicate LazyRow key
                    // throws.
                    key = { _, trip -> "${trip.tripId} ${trip.serviceDate} ${trip.stopSequence}" }
                ) { index, trip ->
                    // The first pill carries the caller's anchor modifier (e.g. the tutorial spotlight).
                    val pillModifier = if (index == 0) firstPillModifier else Modifier
                    EtaPillWithMenu(
                        trip = trip,
                        liveNow = liveNow,
                        actions = actionsFor(trip),
                        callbacks = callbacks,
                        modifier = pillModifier
                    )
                }
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

/** The gap between adjacent ETA pills, for the LazyRow's [Arrangement.spacedBy]. */
private val PILL_SPACING = 6.dp

/**
 * Wraps [content] (the strip's LazyRow) in a layout whose height is fixed to a measured [reference]
 * pill — the tallest pill variant — so the shorter single-line "NOW" pill (via its own fillMaxHeight)
 * levels up to its neighbours without any pill guessing another's height.
 *
 * It also shields the strip from the intrinsic-measurement passes its hosts run (RouteArrivalRow's
 * `height(IntrinsicSize.Min)` row; the preview frame): a LazyRow is a SubcomposeLayout, whose
 * intrinsic queries throw. This policy answers every intrinsic from the reference alone and never
 * touches the LazyRow measurable off the measure path, so the throw can't happen. The reference is
 * measured but never placed — so it's never drawn and contributes only its height.
 */
@Composable
private fun ReferencePillHeightFrame(
    reference: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        contents = listOf(reference, content),
        modifier = modifier,
        measurePolicy = remember {
            object : MultiContentMeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<List<Measurable>>,
                    constraints: Constraints
                ): MeasureResult {
                    val ghost = measurables[0].first().measure(
                        constraints.copy(minWidth = 0, minHeight = 0)
                    )
                    val height = ghost.height.coerceIn(constraints.minHeight, constraints.maxHeight)
                    val row = measurables[1].first().measure(
                        constraints.copy(minHeight = height, maxHeight = height)
                    )
                    return layout(row.width, height) { row.place(0, 0) }
                }

                // All four intrinsics answer from the reference (slot 0) only — never the LazyRow.
                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<List<IntrinsicMeasurable>>,
                    width: Int
                ): Int = measurables[0].first().minIntrinsicHeight(width)

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<List<IntrinsicMeasurable>>,
                    width: Int
                ): Int = measurables[0].first().maxIntrinsicHeight(width)

                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<List<IntrinsicMeasurable>>,
                    height: Int
                ): Int = measurables[0].first().minIntrinsicWidth(height)

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<List<IntrinsicMeasurable>>,
                    height: Int
                ): Int = measurables[0].first().maxIntrinsicWidth(height)
            }
        }
    )
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
    // (the strip fixes its row height to the tallest pill via ReferencePillHeightFrame — see
    // EtaStrip), levelling the shorter single-line NOW pill up to its neighbours.
    Box(modifier.fillMaxHeight()) {
        EtaPill(
            modifier = Modifier.fillMaxHeight(),
            eta = trip.liveEta(liveNow),
            color = colorResource(trip.color),
            predicted = trip.predicted,
            onMap = trip.vehicleOnMap,
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
 * to its neighbours' height with fillMaxHeight (see EtaStrip's ReferencePillHeightFrame / EtaPillWithMenu).
 *
 * Every pill renders at the same size regardless of [eta] — a recent-past (negative-ETA) trip shows
 * its negative countdown in place at the same size as the upcoming ones, not a smaller pill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EtaPill(
    eta: Long,
    color: Color,
    predicted: Boolean,
    modifier: Modifier = Modifier,
    // The trip's live vehicle is drawn on the map right now (#1992): show the "on the map" pin instead of
    // the rss glyph, cueing that a tap on this pill reframes the map to that vehicle. Implies [predicted]
    // (a drawn vehicle is always real-time), so it wins when both would apply.
    onMap: Boolean = false,
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
    val indicatorSize = 13.8.dp // 1.5× the base accent, then +15%; overlaid, so the extra size overlaps, not widens
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
            // ETA text a little instead of widening the pill (drawn last = on top). A vehicle that's drawn
            // on the map now shows the "on the map" pin (a tap reframes to it); an AVL-tracked trip whose
            // vehicle isn't drawn shows the rss glyph (#1992).
            val indicatorModifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 1.dp, end = 1.dp)
                .size(indicatorSize)
            if (onMap) {
                OnMapIndicator(color = Color.White, modifier = indicatorModifier)
            } else if (predicted) {
                RealtimeIndicator(color = Color.White, modifier = indicatorModifier)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews.

/** [count] "40 Northgate" pills with increasing upcoming ETAs, for the strip previews. Each gets a
 *  distinct trip id so the strip's LazyRow key is unique across the row (see EtaStrip's itemsIndexed). */
private fun northgatePills(count: Int) = List(count) { previewArrival("40", "Northgate", etaMinutes = 3L + it * 8, tripId = "trip_$it") }

/**
 * Shared strip-preview scaffold. height(IntrinsicSize.Min) bounds the row to the pill height — as
 * RouteArrivalRow's IntrinsicSize.Min row does in production — so the chevrons' fillMaxHeight resolves
 * to the pills instead of filling the whole preview surface.
 */
@Composable
private fun EtaStripPreviewFrame(
    trips: List<ArrivalInfo>,
    state: LazyListState = rememberLazyListState()
) {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            // height(IntrinsicSize.Min) bounds the row to the pill height AND doubles as the
            // intrinsics canary: the strip's LazyRow is a SubcomposeLayout (its intrinsics throw), so
            // a render here fails loudly if ReferencePillHeightFrame ever stops answering them.
            Box(Modifier.height(IntrinsicSize.Min).padding(8.dp)) {
                EtaStrip(
                    trips = trips,
                    actionsFor = { null },
                    callbacks = previewRowCallbacks(),
                    state = state
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
    // Started part-way scrolled (content hanging off BOTH ends) via a hoisted list state, so both the
    // left- and right-edge chevrons show.
    EtaStripPreviewFrame(
        trips = northgatePills(7),
        state = remember { LazyListState(firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 30) }
    )
}

@Preview(showBackground = true, widthDp = 240, name = "EtaStrip · fits (no chevron)")
@Composable
private fun EtaStripFitsPreview() {
    // Two pills fit inside 240dp, so no scroll and no chevron.
    EtaStripPreviewFrame(
        trips = listOf(
            previewArrival("8", "Rainier Beach", etaMinutes = 4),
            previewArrival("8", "Rainier Beach", etaMinutes = 12, tripId = "trip_2")
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
                // A recent-past arrival: same size as the upcoming ones — negative ETAs aren't shrunk.
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
