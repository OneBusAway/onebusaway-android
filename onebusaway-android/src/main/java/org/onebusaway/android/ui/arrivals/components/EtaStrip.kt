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

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
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
import org.onebusaway.android.ui.compose.components.MenuRow
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeChip
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
 * A moment ruled across the strip's timeline: a vertical rule standing where [at] falls among the
 * departures, with the pills before it dimmed as ones the moment has already passed. The directions
 * drawer rules when the rider reaches the stop (#2125), so a plan whose walk lands after the next two
 * departures reads as such instead of as if the rider were standing at the stop already.
 *
 * The marker names the *moment*, not a pill position: [EtaStrip] places it against the very list it
 * draws, so the rule cannot come to disagree with the pills it is drawn between. It reads them in
 * order, so the strip's trips must be chronological for the rule to divide them into a before and an
 * after — which is what the pill order means anyway.
 *
 * [contentDescription] is what a screen reader reads for the rule itself; [passedStateDescription] is
 * what it reads on each pill the rule has passed, since a pill's dimming is otherwise invisible to it
 * and a rule further down the row comes too late to explain a departure already announced.
 */
internal data class EtaStripMarker(
    val at: ServerTime,
    val contentDescription: String,
    val passedStateDescription: String
)

/**
 * How many of [items] fall before [moment] — i.e. the index the strip's marker rule stands at, given
 * chronologically-ordered items. Kept as a pure function (and JVM-tested) because the boundary rule is
 * the whole meaning of the marker: an item exactly *at* [moment] counts as not-yet-passed and stays
 * after the rule, so a trip plan whose walk is timed to the vehicle — OTP hands back the identical
 * instant for both — cannot rule out the very departure it boards.
 *
 * Generic over the item, taking its time as [timeOf], so the caller needn't build a throwaway list of
 * times to count over (the same shape [interleaveRouteItems][
 * org.onebusaway.android.ui.home.directions.interleaveRouteItems] uses).
 */
internal fun <T> countBefore(items: List<T>, moment: ServerTime, timeOf: (T) -> ServerTime): Int = items.count { timeOf(it) < moment }

/**
 * The horizontally-scrollable strip of per-trip ETA pills below the direction name. Pills are shown
 * in feed order from the first one; the strip never auto-scrolls, so a trip whose ETA has gone
 * negative just keeps counting down in place. When the pills overflow the row, a chevron appears at
 * that edge to signal there's more to scroll to; tapping it moves the strip one viewport that
 * direction (or to the end, whichever is closer). The chevron's own tap target is a narrow side
 * gutter separate from the pills, so it never blocks the strip's own drag-to-scroll.
 *
 * [marker] optionally rules one moment across the strip — see [EtaStripMarker].
 */
@Composable
internal fun EtaStrip(
    trips: List<ArrivalInfo>,
    actionsFor: (ArrivalInfo) -> ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    firstPillModifier: Modifier = Modifier,
    routeBadgeFor: (ArrivalInfo) -> RouteBadge? = { null },
    marker: EtaStripMarker? = null,
    /** The trip this strip's row is drilled into, if any — see [EtaPillFocus]. */
    focus: EtaPillFocus? = null,
    // Hoisted for previews/tests ONLY (both real call sites use the default) so a caller can start
    // the strip mid-scroll.
    state: LazyListState = rememberLazyListState()
) {
    // All of this strip's trips share one poll (one route/direction group from a single
    // ConvertArrivals pass), so their serverNow is identical — tick ONE shared clock here rather than
    // a redundant per-pill ticker/coroutine (issue #1781). ServerTime(0) is an inert placeholder for
    // the (pill-less) empty-trips case; nothing reads it since the pill loop below never runs.
    val liveNow = rememberLiveServerTime(trips.firstOrNull()?.serverNow ?: ServerTime(0L))
    // Remembered because reading the live clock above recomposes this whole body once a second, and
    // this would otherwise re-run the caller's lambda over every trip on each of those ticks. It only
    // changes when the trips or the badge source do.
    val hasRouteBadges = remember(trips, routeBadgeFor) { trips.any { routeBadgeFor(it) != null } }
    // Every pill's clock time, formatted once here for the whole strip rather than per pill: the
    // reference pill below needs the same answer the pills do (see referenceClock), and formatting is a
    // locale-aware DateUtils round trip. Remembered for the same reason as above — the live clock
    // recomposes this body every second, but these only move when a poll brings new trips.
    val context = LocalContext.current
    val clocks = remember(trips, context) { trips.map { it.arrivalClock(context) } }
    // The tallest pill variant this strip needs. A pill carrying a struck-through timetable time
    // (#2167) is one clock line taller than its neighbours, and the reference pill has to match the
    // strip's tallest or the taller pill would be measured short and clipped. Per-strip, so a strip
    // where nothing was corrected keeps exactly the height it had.
    val referenceClock = remember(clocks) {
        // Measured for height only, so the times themselves are arbitrary; all that matters is whether
        // there are one or two clock lines.
        ArrivalClock(expected = "0:00", corrects = "0:01".takeIf { clocks.any { clock -> clock.corrects != null } })
    }
    // Where the marker's moment falls among these departures. Remembered for the same reason: the live
    // clock recomposes this body every second, but the answer only moves when a poll brings new trips.
    val markerIndex = remember(trips, marker) { marker?.let { countBefore(trips, it.at) { trip -> trip.displayTime } } }

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
                // An invisible tallest-variant pill (ETA + clock subline, plus the struck timetable
                // line when any pill here has one), measured to size the row and never placed — so
                // it's never drawn, takes no input, adds no semantics. Constant params, so it never
                // recomposes on the live clock tick.
                EtaPill(
                    eta = 10,
                    color = Color.Transparent,
                    predicted = false,
                    clock = referenceClock,
                    routeBadge = if (hasRouteBadges) RouteBadge("00", null) else null
                )
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
                    // Route + trip-instance identity, so a poll that drops an aged-out leading trip keeps the
                    // viewport on the surviving pills instead of shifting by an index. It's the SAME
                    // (tripId, serviceDate, stopSequence) triple the arrivals dedup collapses to one
                    // entry within one route (see collapseDuplicateTripInstances); routeId keeps that
                    // invariant valid when the directions drawer interleaves several routes (#2099).
                    // tripId alone is NOT unique (a loop route's two genuine visits to one stop share
                    // it), and a duplicate LazyRow key is a fatal throw at measure time. The NUL
                    // separator is the same can't-occur-in-an-id joiner routeRowKey uses, written as an
                    // escape rather than pasted raw, which had made this file binary to grep (#2012).
                    key = { _, trip -> "${trip.routeId}\u0000${trip.tripId}\u0000${trip.serviceDate}\u0000${trip.stopSequence}" }
                ) { index, trip ->
                    // The first pill carries the caller's anchor modifier (e.g. the tutorial spotlight).
                    val pillModifier = if (index == 0) firstPillModifier else Modifier
                    // The rule rides inside the item it precedes: a LazyListScope can't emit a lone item
                    // partway through an itemsIndexed block without splitting the trips in two and
                    // duplicating the pill below. Null on every other item, so only one carries it.
                    MarkedItem(marker?.takeIf { markerIndex == index }) {
                        EtaPillWithMenu(
                            trip = trip,
                            clock = clocks[index],
                            liveNow = liveNow,
                            actions = actionsFor(trip),
                            callbacks = callbacks,
                            routeBadge = routeBadgeFor(trip),
                            outline = focus?.takeIf { it.tripId == trip.tripId }?.outline,
                            modifier = pillModifier.passedByMarker(marker, isPassed = index < (markerIndex ?: 0))
                        )
                    }
                }
                // A marker past the last departure — the rider gets to the stop after everything the feed
                // knows about — closes the strip instead of vanishing.
                if (marker != null && markerIndex != null && markerIndex >= trips.size) {
                    item(key = ETA_STRIP_MARKER_TAG) { EtaStripMarkerRule(marker) }
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

/**
 * The trip a row is drilled into (the stop→route→trip focus, #2205) and the stroke to outline its pill
 * with. One value rather than two loose parameters, so a pill can never be told which trip is focused
 * without also being told what to draw for it. [outline] is literally the row card's own selection
 * border — the same object, built once by [RouteArrivalRow] — so the pill reads as belonging to the
 * outlined row and the two can't drift in width or colour.
 */
internal data class EtaPillFocus(val tripId: String, val outline: BorderStroke)

/**
 * Announces the focused pill as selected, so the outline reaches accessibility services (and UI tests)
 * as more than a colour. Applied only to that pill — marking every other one "not selected" would have
 * TalkBack narrate a selection state on strips that have none. Hoisted because it captures nothing and
 * a pill recomposes every second off the strip's live clock.
 */
private val FOCUSED_PILL_SEMANTICS = Modifier.semantics { selected = true }

/** The gap between adjacent ETA pills, for the LazyRow's [Arrangement.spacedBy]. */
private val PILL_SPACING = 6.dp

/** How faint a pill the strip's [EtaStripMarker] has already passed goes. Still legible — it's a real
 *  departure — but clearly behind the ones after the rule. */
private const val PASSED_PILL_ALPHA = 0.38f

/** The marker rule's width. */
private val MARKER_WIDTH = 3.dp

/** A stable handle on the marker rule, so a render test can sample it without matching on its colour.
 *  Doubles as its LazyRow key on the one path where the rule is an item of its own — a trip-identity key
 *  is a NUL-joined tuple of OBA ids, so a bare word can't collide with one. */
internal const val ETA_STRIP_MARKER_TAG = "etaStripMarker"

/** One LazyRow item: [pill], preceded by [marker]'s rule when this is the pill it stands before. Spaced
 *  as two adjacent pills would be, so the rule doesn't crowd the strip's rhythm. A [marker] of null —
 *  every other pill, and every pill on the unmarked arrivals path — emits the pill alone, adding no
 *  layout node of its own. */
@Composable
private fun MarkedItem(marker: EtaStripMarker?, pill: @Composable () -> Unit) {
    if (marker == null) {
        pill()
        return
    }
    Row(
        Modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(PILL_SPACING),
        verticalAlignment = Alignment.Bottom
    ) {
        EtaStripMarkerRule(marker)
        pill()
    }
}

/**
 * The marker itself: a full-height rounded rule in the theme's primary colour, standing between the
 * departures on either side of the moment it marks. It names that moment for a screen reader, since a
 * rule says nothing on its own; what the dimming beside it means is said on the dimmed pills
 * ([passedByMarker]), where a screen reader actually meets it.
 */
@Composable
private fun EtaStripMarkerRule(marker: EtaStripMarker) {
    VerticalDivider(
        modifier = Modifier
            .testTag(ETA_STRIP_MARKER_TAG)
            .clip(RoundedCornerShape(MARKER_WIDTH / 2))
            .semantics { contentDescription = marker.contentDescription },
        thickness = MARKER_WIDTH,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Marks this pill as one the strip's [marker] has already passed: dimmed, and — because the dimming is
 * invisible to a screen reader — carrying the marker's own words for what being on that side of the rule
 * means. Still fully readable and tappable: it's a real departure, just not one this plan can use.
 *
 * A no-op when [isPassed] is false or there is no marker, and free on that path — `Modifier.alpha`
 * returns the receiver unchanged at 1f, so the unmarked arrivals strip grows no graphics layer.
 */
private fun Modifier.passedByMarker(marker: EtaStripMarker?, isPassed: Boolean): Modifier = if (marker == null || !isPassed) {
    this
} else {
    alpha(PASSED_PILL_ALPHA).semantics { stateDescription = marker.passedStateDescription }
}

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
 *  (issue #1781) — counts this pill down between polls rather than freezing at the poll-time eta.
 *  [clock] is this trip's entry in the strip's once-per-poll formatted clock times (see EtaStrip),
 *  passed in rather than derived here because this composable recomposes every second. */
@Composable
private fun EtaPillWithMenu(
    trip: ArrivalInfo,
    clock: ArrivalClock,
    liveNow: ServerTime,
    actions: ArrivalActions?,
    callbacks: ArrivalRowCallbacks,
    modifier: Modifier = Modifier,
    routeBadge: RouteBadge? = null,
    outline: BorderStroke? = null
) {
    var expanded by remember { mutableStateOf(false) }
    // fillMaxHeight here and on the pill so the colored Surface stretches to the strip's tallest pill
    // (the strip fixes its row height to the tallest pill via ReferencePillHeightFrame — see
    // EtaStrip), levelling the shorter single-line NOW pill up to its neighbours.
    Box(modifier.fillMaxHeight()) {
        EtaPill(
            modifier = Modifier.fillMaxHeight(),
            eta = trip.liveEta(liveNow),
            // The on-fill tier, not `trip.color`: the pill paints this as a Surface with white text
            // on top, so it needs the darkened variant to clear WCAG AA (#2043).
            color = colorResource(trip.fillColor),
            predicted = trip.predicted,
            onMap = trip.vehicleOnMap,
            canceled = trip.status == Status.CANCELED,
            clock = clock,
            routeBadge = routeBadge,
            outline = outline,
            onClick = { callbacks.onEtaClick(trip) },
            onLongClick = { expanded = true }
        )
        TripActionsMenu(expanded, { expanded = false }, trip, actions, callbacks)
    }
}

/** The per-trip menu opened by long-pressing a pill: trip details, a reminder, or a problem report
 *  for that specific trip. Route-wide actions — including tracking, which follows the whole row
 *  rather than one vehicle — live on the row's long-press menu ([RouteActionsMenu]). */
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
 * How wide a pill's route roundel may grow before its name ellipsizes — the option cards' own
 * OPTION_BADGE_MAX_WIDTH rule (TripResultsScreen), at pill scale. Only bites on a route badged by its
 * long name (one publishing no short name — both badge sources feeding a pill fall back to the long
 * name), which would otherwise stretch one pill far past its neighbours and turn the strip's even
 * rhythm into a single wide outlier. A route number never comes near it. Tune here.
 */
private val PILL_BADGE_MAX_WIDTH = 72.dp

/**
 * The prominent white-on-lateness ETA pill — one per trip in a route row's strip (and the Home legend
 * dialog, which passes no clicks). [onClick] taps focus that trip's vehicle + stop; [onLongClick]
 * opens the trip menu; [canceled] strikes the text through. [clock] is the small "1:10pm"-style
 * clock time shown below the ETA (issue #1786) — with the timetable time it corrects struck through
 * above it when there is one (#2167); null omits those lines entirely (e.g. the Home legend's
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
    routeBadge: RouteBadge? = null,
    // The trip's live vehicle is drawn on the map right now (#1992): show the "on the map" pin instead of
    // the rss glyph, cueing that a tap on this pill reframes the map to that vehicle. Implies [predicted]
    // (a drawn vehicle is always real-time), so it wins when both would apply.
    onMap: Boolean = false,
    canceled: Boolean = false,
    clock: ArrivalClock? = null,
    // The row is drilled into this pill's trip (#2205): its card's selection border, drawn on the pill
    // too. Null is the ordinary unfocused pill.
    outline: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val decoration = strikeThroughIf(canceled)
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
    Surface(
        modifier = modifier.then(if (outline == null) Modifier else FOCUSED_PILL_SEMANTICS).then(interaction),
        shape = shape,
        color = color,
        border = outline
    ) {
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
                    // A badged direction pill puts another element at its top edge. Reserve the live
                    // indicator's overlaid corner on both sides so it cannot cover the roundel and the
                    // roundel remains visually centered.
                    start = if (routeBadge == null) 6.dp else indicatorSize,
                    end = if (routeBadge == null) 6.dp else indicatorSize,
                    top = topPadding,
                    bottom = bottomPadding
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(clockTimeGap)
            ) {
                if (routeBadge != null) {
                    RouteBadgeChip(
                        shortName = routeBadge.shortName,
                        routeColor = routeBadge.routeColor,
                        scale = 0.8f,
                        maxWidth = PILL_BADGE_MAX_WIDTH
                    )
                }
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
                if (clock != null && etaParts != null) {
                    CorrectedClockTime(
                        clock = clock,
                        fontSize = clockTimeSize,
                        color = Color.White.copy(alpha = 0.8f),
                        // Same trim the ETA line gets, so a corrected pill's two clock lines stack as
                        // tightly as the single line did rather than gaining a line box each.
                        style = remember(baseTextStyle, clockTimeSize) { tightLineStyle(baseTextStyle, clockTimeSize) },
                        canceled = canceled
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
    marker: EtaStripMarker? = null,
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
                    marker = marker,
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

@Preview(showBackground = true, widthDp = 240, name = "EtaStrip · marked (rider arrives)")
@Composable
private fun EtaStripMarkedPreview() {
    // The directions case: the rider's walk lands after the first two departures (northgatePills run
    // 3, 11, 19, 27 minutes out against a serverNow of 0), so the rule stands before the third and the
    // two it has passed are dimmed.
    EtaStripPreviewFrame(
        trips = northgatePills(4),
        marker = EtaStripMarker(
            at = ServerTime(16 * 60_000L),
            contentDescription = "You get to this stop at 3:19pm",
            passedStateDescription = "Leaves before you get here"
        )
    )
}

@Preview(showBackground = true, widthDp = 240, name = "EtaStrip · corrected clock time")
@Composable
private fun EtaStripCorrectedPreview() {
    // The first bus is running 4 minutes behind its timetable, so its pill strikes the scheduled time
    // through above the one it's now expected at (#2167). The second is on its timetable time and
    // shows one clock line — levelled to its neighbour's height by the strip, as the NOW pill is.
    EtaStripPreviewFrame(
        trips = listOf(
            previewArrival("8", "Rainier Beach", etaMinutes = 6, scheduleDeviationMinutes = 4, tripId = "trip_1"),
            previewArrival("8", "Rainier Beach", etaMinutes = 14, tripId = "trip_2")
        )
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
 *  colors, a canceled pill, the corrected clock (#2167), and the past-an-hour "Xhr Ymin" form. */
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
                EtaPill(-3, colorResource(R.color.stop_info_delayed_fill), predicted = true, clock = ArrivalClock("2:57pm"))
                EtaPill(0, colorResource(R.color.stop_info_ontime_fill), predicted = true, clock = ArrivalClock("3:00pm"))
                // Late, and so showing the timetable time it corrects struck through above (#2167).
                EtaPill(
                    5,
                    colorResource(R.color.stop_info_delayed_fill),
                    predicted = true,
                    clock = ArrivalClock(expected = "3:05pm", corrects = "3:00pm")
                )
                EtaPill(12, colorResource(R.color.stop_info_early_fill), predicted = true, clock = ArrivalClock("3:12pm"))
                EtaPill(22, colorResource(R.color.stop_info_scheduled_fill), predicted = false, clock = ArrivalClock("3:22pm"))
                EtaPill(
                    8,
                    colorResource(R.color.stop_info_scheduled_fill),
                    predicted = false,
                    canceled = true,
                    clock = ArrivalClock("3:08pm")
                )
                // Past an hour: the number switches to hours, the leftover minutes fold into the label (#1777).
                EtaPill(83, colorResource(R.color.stop_info_scheduled_fill), predicted = true, clock = ArrivalClock("4:23pm"))
                EtaPill(125, colorResource(R.color.stop_info_early_fill), predicted = false, clock = ArrivalClock("5:05pm"))
            }
        }
    }
}
