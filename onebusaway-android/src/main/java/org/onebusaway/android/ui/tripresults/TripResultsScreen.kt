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
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.app.FeatureFlags
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.realtime.TripPlanMonitor
import org.onebusaway.android.directions.realtime.TripPlanNotifications
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.LocalUnitsAreMetric
import org.onebusaway.android.ui.compose.components.AlertSeverity
import org.onebusaway.android.ui.compose.components.DirectionHeadsign
import org.onebusaway.android.ui.compose.components.EtaDurationText
import org.onebusaway.android.ui.compose.components.EtaPartsText
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.ROUTE_BADGE_HEIGHT
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeChip
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.ui.compose.components.RouteLineColors
import org.onebusaway.android.ui.compose.components.ScrollChevronGutter
import org.onebusaway.android.ui.compose.components.alertAccentColor
import org.onebusaway.android.ui.compose.components.routeLineColors
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.compose.theme.isDarkTheme
import org.onebusaway.android.ui.compose.unitsAreMetric
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.ExternalIntents
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
    onSelectOption: (Int) -> Unit,
    scheduleWinnerMode: ScheduleWinnerMode = ScheduleWinnerMode.BOTH
) {
    val success = state as? TripResultsUiState.Success ?: return
    val winners = remember(success.options, scheduleWinnerMode) {
        itineraryWinnerCategories(success.options, scheduleWinnerMode)
    }
    // Every card's summary reserves the tallest one's height, so a trip whose summary wraps doesn't push
    // its own stats a line below its neighbours' and leave the row unreadable across (#2081).
    val summaryHeights = remember(success.options) { SummaryHeights() }
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
                    winners = winners[index],
                    selected = index == success.selectedIndex,
                    summaryHeights = summaryHeights,
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

/**
 * How wide an option card's route roundel may grow before its name ellipsizes. Only bites on a route
 * badged by its long name (one publishing no short name — see [plannedBadge]); a route number plus its
 * mode glyph never comes near it. Sized to show enough of a long name to recognize it —
 * "Seattle - Brem…" — without letting one leg crowd the rest off the card, with room for the glyph the
 * badge now leads with. Tune here.
 */
private val OPTION_BADGE_MAX_WIDTH = 110.dp

/**
 * How wide an option card is allowed to grow before its summary line wraps onto another line (#2081).
 * Without it a four-leg trip drew one long card that pushed every other option off the row — the picker
 * scrolls, but the rider can only compare what is on screen at once, so a card that runs off the edge
 * costs more than a card two lines tall. Chosen to keep the *next* card in view on a narrow (320dp)
 * screen; tune here.
 *
 * It governs the **summary line**, which is what makes a card wide, and it governs where that line
 * *breaks* rather than clipping it. So it is not quite a maximum on the card: the card still exceeds it
 * for a single symbol too wide to break (see [SymbolFlow]), and it says nothing about the stats below,
 * which are short enough not to need it.
 */
private val OPTION_CARD_MAX_WIDTH = 200.dp

/**
 * The padding around each of the card's two sections. Applied per section rather than to the card, so
 * the summary's band can be filled edge to edge — the padding is inside the tint, which is what makes it
 * read as a header rather than as a stripe behind some glyphs. One value, so the two sections' content
 * cannot drift out of alignment.
 */
private val CARD_PADDING_HORIZONTAL = 12.dp
private val CARD_PADDING_VERTICAL = 8.dp
private val CARD_SECTION_PADDING =
    PaddingValues(horizontal = CARD_PADDING_HORIZONTAL, vertical = CARD_PADDING_VERTICAL)

/** Where the summary line breaks: the card's width, net of the padding it is drawn inside. */
private val SUMMARY_WRAP_WIDTH = OPTION_CARD_MAX_WIDTH - CARD_PADDING_HORIZONTAL * 2

/**
 * How far the summary's band is tinted off the card behind it, to set the trip itself on its own surface
 * above the stats (#2081).
 *
 * A veil of the **brand green** (`MaterialTheme.colorScheme.primary`) rather than a neutral step up the
 * container ramp: the band is the app's colour showing through the card, not a second grey. `primary` is
 * the token to veil with because it flips ends between the themes exactly as the ramp does — a deep green
 * over a light card darkens it, a pale green over a dark card lightens it — so one alpha serves both, and
 * a white-label brand re-tints these headers by overriding the one colour it already overrides.
 *
 * The value is chosen to land the same lightness step the neutral veil it replaced did (−7.4 on a light
 * card, +9.0 on a dark one, in CIE L*), so this changed the hue and not the weight; it holds within about
 * one unit of that across all four card states. That weight is itself set well above one step of the
 * container ramp (~0.03–0.05 here), since the band does on its own the separating a rule between the
 * sections would otherwise share — at a single step the two halves of the card barely read as two.
 *
 * Tried louder, too: a band inverted into the dark range (the card's own opposite, carrying light
 * glyphs) reads as a slab in a picker of small cards, at any strength. The header stays light and merely
 * offset; this is the knob for how far.
 *
 * Note the selected card is *already* green ([R.color.trip_plan_card_background_selected]), so on that
 * one the veil reads as darkening rather than as a hue change — which is what keeps a selected card
 * looking like the same object as its neighbours rather than a differently-tinted one.
 */
private const val CARD_HEADER_TINT_ALPHA = 0.13f

/**
 * The chevron between two of a card's mode symbols, and the gap on either side of it. Deliberately
 * small and quiet: it is punctuation saying "then", not a step of the trip, so it must not compete
 * with the glyphs and roundels it joins — hence a height well under [ModeGlyph]'s 20dp and a faded
 * tint. The gap is tighter than the 6dp the symbols used alone, since the chevron now does the
 * separating that whitespace used to. Tune all three here.
 *
 * [SYMBOL_SEPARATOR_HEIGHT] is the chevron's *drawn* height, not a box it floats inside: the drawable
 * is cropped to the glyph (see `ic_arrow_right.xml`), so growing the chevron doesn't quietly grow the
 * space around it and [SYMBOL_GAP] is the whole of that space.
 */
private val SYMBOL_SEPARATOR_HEIGHT = 8.dp

/**
 * The height every mode symbol on a card is drawn at — a bare glyph and a route roundel alike, so the
 * card's first line reads as one row of equal-weight symbols rather than glyphs standing taller (or
 * shorter) than the badges between them. Split the difference between the two forms' natural sizes:
 * the glyph came down from 20dp and the roundel up from its unscaled [ROUTE_BADGE_HEIGHT].
 */
private val SYMBOL_HEIGHT = 19.dp

/**
 * What the roundel has to be scaled by to stand [SYMBOL_HEIGHT] tall. Derived rather than written as a
 * number, so a change to the chip's own metrics re-levels the row instead of silently breaking it.
 */
private val SYMBOL_BADGE_SCALE = SYMBOL_HEIGHT / ROUTE_BADGE_HEIGHT

private const val SYMBOL_SEPARATOR_ALPHA = 0.6f

private val SYMBOL_GAP = 4.dp

/**
 * The warning triangle marking a mode symbol whose leg carries a service alert (#2143), and the gap
 * between the two. Tucked closer than [SYMBOL_GAP] separates two symbols by, so it annotates the symbol
 * beside it rather than reading as one — at equal spacing a row of `[walk] ⚠ > [1 Line] ⚠` reads as
 * six steps instead of two legs with a caveat each.
 *
 * It sits a little *below* [SYMBOL_HEIGHT] rather than distinctly under it. The first cut was small
 * enough to read as a footnote, which is the wrong register for "this train isn't running": a marker
 * the rider is meant to catch while scanning the picker has to hold its own against the roundel it
 * qualifies without displacing it as the thing being read. Tune both here.
 */
private val SYMBOL_ALERT_SIZE = 17.dp
private val SYMBOL_ALERT_GAP = 2.dp

/** The gap between two wrapped lines of the summary — [SYMBOL_GAP] itself, so the wrap reads as one
 *  evenly-spaced field of symbols rather than as two stacked rows, and stays so if that gap is retuned. */
private val SYMBOL_LINE_GAP = SYMBOL_GAP

/** A quiet keyline around a winning metric: enough separation to survive the selected card's tint. */
private val WINNER_OUTLINE_WIDTH = 1.5.dp
private val WINNER_OUTLINE_RADIUS = 4.dp
private const val WINNER_OUTLINE_ALPHA = 0.60f

/**
 * The tallest summary band across a picker row's cards, which every card in it then reserves — so the
 * stats below start at the same y on each card and can be read across the row, whether or not a
 * particular trip's summary wrapped (#2081).
 *
 * Filled in from layout rather than known up front: where a summary wraps depends on measured text, so
 * the tallest is only knowable once the cards have been measured. The first layout pass reports it and a
 * second settles every card on the result — [tallest] only ever grows within one set of options, so it
 * converges rather than oscillating, and the whole holder is re-created when the options change (a new
 * plan whose summaries all fit on one line must not inherit an old plan's taller band).
 */
@Stable
private class SummaryHeights {

    /** The tallest natural summary height reported so far, in pixels. */
    var tallest by mutableIntStateOf(0)
        private set

    fun report(height: Int) {
        if (height > tallest) tallest = height
    }
}

@Composable
private fun OptionCard(
    option: ItineraryOption,
    winners: Set<WinnerCategory>,
    selected: Boolean,
    summaryHeights: SummaryHeights,
    onClick: () -> Unit
) {
    val background = colorResource(
        if (selected) R.color.trip_plan_card_background_selected else R.color.trip_plan_card_background
    )
    val textColor = colorResource(
        if (selected) R.color.trip_plan_header_text_selected else R.color.trip_plan_header_text
    )
    // Read off the categories themselves, in their declaration order, so a category added to the enum is
    // announced without a second list here having to be kept in step with it.
    val winnerDescriptions = WinnerCategory.entries.filter { it in winners }.map { stringResource(it.labelRes) }
    Surface(
        color = background,
        contentColor = textColor,
        shape = MaterialTheme.shapes.small,
        // Wrap to the content width (a sensible floor so short options aren't tiny); the row scrolls.
        // The ceiling is the summary line's own — it wraps at [OPTION_CARD_MAX_WIDTH] rather than the
        // card being cut to it (see [SymbolFlow]).
        modifier = Modifier
            .widthIn(min = 104.dp)
            .clickable(onClick = onClick)
            .semantics {
                if (winnerDescriptions.isNotEmpty()) {
                    stateDescription = winnerDescriptions.joinToString()
                }
            }
    ) {
        // Sized to the widest thing on it, so the summary's band has a card width to fill: under the
        // picker's horizontal scroll the incoming width is unbounded, where `fillMaxWidth` measures to
        // nothing. The intrinsic pass asks the summary how wide it lands *after* wrapping — see
        // [SymbolFlow].
        Column(Modifier.width(IntrinsicSize.Max)) {
            // The trip in travel order, as one symbol sequence: a glyph per on-street leg and a roundel
            // per ride, chevron-separated (#2047). The gap between symbols is deliberately wide, so
            // "two legs" and "one leg, two interchangeable routes" (which is one seamless chip) can't
            // read as the same thing (#2010).
            //
            // Drawn from the symbols that actually render: a [StreetMode.CAR] leg has no glyph (see
            // [streetModeIcon]) and is dropped here rather than in the model, so it can't leave a
            // chevron pointing at nothing. The planner never asks OTP for car legs, so today this drops
            // nothing a rider can be shown.
            val drawn = remember(option.symbols) {
                option.symbols.filter { it !is ModeSymbol.Street || streetModeIcon(it.mode) != null }
            }
            // A trip with nothing drawable to say (see above) gets no summary at all — an empty tinted
            // strip would be worse than the card simply starting at its stats.
            if (drawn.isNotEmpty()) {
                SymbolFlow(
                    wrapAt = SUMMARY_WRAP_WIDTH,
                    minHeight = summaryHeights.tallest,
                    onNaturalHeight = summaryHeights::report,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = CARD_HEADER_TINT_ALPHA))
                        .padding(CARD_SECTION_PADDING)
                ) {
                    drawn.forEachIndexed { index, symbol ->
                        // A symbol travels with the chevron that follows it, as one unbreakable unit:
                        // the wrap then never opens a line with a chevron pointing at the symbol above
                        // it, and a broken line ends on the "and then" that carries the eye down.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(SYMBOL_GAP),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModeSymbolContent(symbol)
                            if (index < drawn.lastIndex) SymbolSeparator()
                        }
                    }
                }
            }
            StatsColumn(option, winners)
        }
    }
}

/**
 * The summary line's layout: its symbols packed left to right, wrapping onto the next line as soon as
 * the following one would carry the line past [wrapAt] (#2081).
 *
 * Not a `FlowRow`, for one reason: every child here is measured **unbounded**, so a symbol that is by
 * itself wider than [wrapAt] takes a line of its own and widens the card rather than being measured into
 * whatever width is left. That case is real — a badge joining several interchangeable routes is
 * deliberately uncapped (see [RouteBadgeChip]) — and the alternative is the failure that badge exists to
 * avoid: a `Row` handed too little width measures its later segments at zero, so the ride would quietly
 * lose a route off the end of its own badge. [wrapAt] is therefore where the line *breaks*, not a width
 * the card is cut to.
 *
 * The card takes its width from this layout's intrinsic one ([OptionCard]), which [SymbolFlowPolicy]
 * answers from the same packing the measure pass performs — so the two cannot disagree and leave the
 * card carrying a line it then refuses to fill.
 */
@Composable
private fun SymbolFlow(
    wrapAt: Dp,
    minHeight: Int,
    onNaturalHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Held through rememberUpdatedState so the reporting callback can be re-created every recomposition
    // without re-creating the policy — only [wrapAt] and [minHeight] change what this layout does.
    val report by rememberUpdatedState(onNaturalHeight)
    Layout(content, modifier, remember(wrapAt, minHeight) { SymbolFlowPolicy(wrapAt, minHeight) { report(it) } })
}

/**
 * [SymbolFlow]'s measurement. A policy object rather than a measure lambda so the layout can state its
 * own **intrinsic** width: the card is sized by asking for it ([OptionCard]), and the default intrinsic
 * a lambda inherits re-runs the whole measure block at an unbounded width and hopes the answer matches.
 * Here both paths break the lines with the same [packLines] call, so the width the card takes is the
 * width this layout then packs into, by construction rather than by argument.
 */
private class SymbolFlowPolicy(
    private val wrapAt: Dp,
    private val minHeight: Int,
    private val onNaturalHeight: (Int) -> Unit
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val gapX = SYMBOL_GAP.roundToPx()
        val gapY = SYMBOL_LINE_GAP.roundToPx()
        // Measured unbounded — the point of the whole layout, see [SymbolFlow].
        val placeables = measurables.map { it.measure(Constraints()) }
        val widths = placeables.map { it.width }
        // Whichever binds first: the line we chose, or a genuinely narrower parent.
        val lines = packLines(widths, minOf(constraints.maxWidth, wrapAt.roundToPx()), gapX)
        val lineHeights = lines.map { line -> line.maxOf { placeables[it].height } }
        val width = constraints.constrainWidth(lines.maxOfOrNull { lineWidth(widths, it, gapX) } ?: 0)
        // The height these lines want, and the height they are given: a card whose summary wraps to fewer
        // lines than its neighbours' still reserves theirs, so every card's stats start at the same y.
        // The lines are laid from the top, so the reserved room falls below them rather than centring
        // them in it.
        val natural = lineHeights.sum() + gapY * (lines.size - 1)
        val height = constraints.constrainHeight(maxOf(natural, minHeight))
        return layout(width, height) {
            // Reported from placement, not from measure: writing the row's shared state is a change to
            // someone else's layout, and placement is where that is safe to do.
            onNaturalHeight(natural)
            var y = 0
            lines.forEachIndexed { index, line ->
                var x = 0
                line.forEach {
                    val placeable = placeables[it]
                    // Centred in its line, as the symbols were in the single row they used to share: a
                    // chevron is half a glyph tall, and belongs level with what it joins.
                    placeable.placeRelative(x, y + (lineHeights[index] - placeable.height) / 2)
                    x += placeable.width + gapX
                }
                y += lineHeights[index] + gapY
            }
        }
    }

    /** The width [measure] will report when nothing narrower is imposed — the same packing, asked early. */
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val gapX = SYMBOL_GAP.roundToPx()
        val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        return packLines(widths, wrapAt.roundToPx(), gapX).maxOfOrNull { lineWidth(widths, it, gapX) } ?: 0
    }
}

/**
 * Greedy line breaking: which symbols land on each line, given their [widths] and the [gap] between two
 * of them. A symbol wider than [limit] all the same opens — and keeps — a line of its own, which is what
 * widens the card rather than cutting the symbol down (see [SymbolFlow]).
 *
 * Every line comes out no wider than the widest, so packing again at that width breaks the lines in the
 * same places: a line stopped where the *next* symbol would have passed [limit], and [limit] can only
 * have shrunk to a width this same line already fitted.
 */
private fun packLines(widths: List<Int>, limit: Int, gap: Int): List<IntRange> {
    val lines = mutableListOf<IntRange>()
    var start = 0
    var packed = 0
    widths.forEachIndexed { index, width ->
        when {
            // The first symbol on a line takes it whatever its width — nothing is ever cut to fit.
            index == start -> packed = width
            packed + gap + width <= limit -> packed += gap + width
            else -> {
                lines += start until index
                start = index
                packed = width
            }
        }
    }
    if (widths.isNotEmpty()) lines += start until widths.size
    return lines
}

/** How wide one of [packLines]' lines is: its symbols, plus a [gap] between each neighbouring pair. */
private fun lineWidth(widths: List<Int>, line: IntRange, gap: Int): Int = line.sumOf { widths[it] } + gap * (line.last - line.first)

/**
 * One symbol of a card's summary line: a bare mode glyph, or the ride's route roundel — followed by a
 * warning triangle when a service alert affects the leg(s) it stands for (#2143).
 *
 * The triangle rides *inside* the symbol, at a tighter gap than [SYMBOL_GAP] separates symbols by, so
 * it reads as an annotation on the leg to its left rather than as a step of the trip in its own right —
 * or, worse, as belonging to the symbol on the other side of the chevron.
 */
@Composable
private fun ModeSymbolContent(symbol: ModeSymbol) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SYMBOL_ALERT_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeSymbolGlyph(symbol)
        symbol.alert?.let { severity ->
            val severityLabel = stringResource(severity.labelRes)
            Icon(
                painter = painterResource(R.drawable.baseline_warning_24),
                contentDescription = stringResource(R.string.directions_leg_service_alert, severityLabel),
                tint = alertAccentColor(severity),
                modifier = Modifier.size(SYMBOL_ALERT_SIZE)
            )
        }
    }
}

/** [ModeSymbolContent] without its alert marker: the symbol proper. */
@Composable
private fun ModeSymbolGlyph(symbol: ModeSymbol) {
    when (symbol) {
        // A glyph alone — there is nothing to name about a walk.
        is ModeSymbol.Street -> StreetMetric.of(symbol.mode)?.let { metric ->
            ModeGlyph(metric.glyph.iconRes, stringResource(metric.labelRes))
        }
        // Every badge leads with the mode it's ridden on, which a route number never says on its own. A
        // route publishing no short name badges its long name, capped to [OPTION_BADGE_MAX_WIDTH] and
        // ellipsized so one wordy name can't crowd the other legs off the card; only a route that names
        // itself in no way at all is left with the bare glyph.
        is ModeSymbol.Transit -> {
            val badge = symbol.badge
            val glyph = transitModeIcon(badge.mode)
            val glyphLabel = stringResource(transitModeLabel(badge.mode))
            if (badge.isUnnamed) {
                ModeGlyph(glyph, glyphLabel)
            } else {
                RouteBadgeChip(
                    badge.routes,
                    scale = SYMBOL_BADGE_SCALE,
                    maxWidth = OPTION_BADGE_MAX_WIDTH,
                    join = badge.join,
                    leadingIcon = glyph,
                    leadingIconDescription = glyphLabel
                )
            }
        }
    }
}

/**
 * The lower half of an option card: what the trip costs (duration, and the ground the rider covers under
 * their own power) and when it runs, each metric outlined where it wins its category. Takes the [winners]
 * set itself rather than a flag per category, so a new [WinnerCategory] is one line here and nothing at
 * the call site.
 */
@Composable
private fun StatsColumn(option: ItineraryOption, winners: Set<WinnerCategory>) {
    val context = LocalContext.current
    // Named for the units, not `metric`, to stay clear of the StreetMetric the distance rows destructure.
    val metricUnits = unitsAreMetric()
    val winnerOutlineColor = MaterialTheme.colorScheme.outline.copy(alpha = WINNER_OUTLINE_ALPHA)
    Column(
        modifier = Modifier.padding(CARD_SECTION_PADDING),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Duration + the street distances read as one stat group, so they sit tighter together than the
        // card's other lines.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Duration — a leading hourglass + the ETA-pill-formatted trip length.
            MetricRow(
                MetricGlyph.DURATION,
                contentDescription = null,
                winner = WinnerCategory.SHORTEST_TRAVEL_TIME in winners,
                outlineColor = winnerOutlineColor
            ) {
                EtaDurationText(
                    minutes = option.durationMinutes,
                    modifier = Modifier.alignByBaseline(),
                    numberSize = METRIC_NUMBER_SIZE,
                    unitSize = METRIC_UNIT_SIZE
                )
            }
            // How far the trip goes on each street mode, one line each (#2122) — a leading mode glyph
            // mirroring the duration row's hourglass, with the distance styled like the duration (bold
            // value + smaller unit) and in the user's units (miles/km, or feet/meters for short hops).
            // A card used to say only how far it walked, which left a bikeshare trip's ride unmeasured
            // and a bike-only one claiming "0 ft" as its whole street distance.
            streetDistanceLines(option.streetDistanceMeters, winners).forEach { (metric, meters) ->
                MetricRow(
                    metric.glyph,
                    contentDescription = stringResource(metric.labelRes),
                    winner = metric.winner in winners,
                    outlineColor = winnerOutlineColor
                ) {
                    EtaPartsText(
                        ConversionUtils.getFormattedDistanceParts(meters, context, metricUnits),
                        modifier = Modifier.alignByBaseline(),
                        numberSize = METRIC_NUMBER_SIZE,
                        unitSize = METRIC_UNIT_SIZE
                    )
                }
            }
        }
        // The device-localized departure–arrival range (unwrap the server clock only here).
        val startText = DisplayFormat.formatTime(context, option.startTime.epochMs)
        val endText = DisplayFormat.formatTime(context, option.endTime.epochMs)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScheduleMetric(startText, winner = WinnerCategory.LATEST_DEPARTURE in winners, outlineColor = winnerOutlineColor)
            Text(" – ", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            ScheduleMetric(endText, winner = WinnerCategory.EARLIEST_ARRIVAL in winners, outlineColor = winnerOutlineColor)
        }
    }
}

/**
 * Which street-distance lines a card draws, and how far each says — its per-mode totals read in
 * [StreetMetric] order rather than in travel order, so cards measuring the same modes list them in the
 * same order and the picker row reads across. Cards measuring *different* modes do stagger below their
 * first line — a bikeshare option carries a line its walk-only neighbour hasn't got — but that
 * difference is itself what the rider is choosing between.
 *
 * A mode the trip doesn't use draws no line, unless this option **won** that mode's category by not
 * using it: then it shows its "0 ft", since the outline needs a value to sit on and "needs none of it"
 * is worth saying.
 *
 * Only the modes the cards can present are considered at all — [StreetMetric] is that list, so a mode
 * with no glyph can't reach a line here and a mode with no line can't be announced as a winner.
 */
private fun streetDistanceLines(
    distances: Map<StreetMode, Double>,
    winners: Set<WinnerCategory>
): List<Pair<StreetMetric, Double>> = StreetMetric.entries.mapNotNull { metric ->
    val meters = distances[metric.mode] ?: 0.0
    if (meters > 0.0 || metric.winner in winners) metric to meters else null
}

/**
 * One option-card stat line: a leading glyph followed by its value [content], drawn so the glyph and
 * the value occupy exactly the same band — same top edge, same bottom edge, both sitting on the value's
 * baseline (#2076). Shared by every row of the stat group — the duration and each street distance — so
 * they all stay in lockstep.
 *
 * The glyph is sized and placed by its *ink*, not by its box: the box is inflated from the wanted ink
 * height by the asset's own ink fraction ([MetricGlyph]), and the row's alignment line is the ink's
 * bottom rather than the box's, so the transparent margin baked into the vector doesn't push the glyph
 * below the text (which is what it used to do — a centred 16dp box put 1.5dp of hourglass under the
 * digits).
 *
 * [content] holds up its half of that: it must be a value sized [METRIC_NUMBER_SIZE]/[METRIC_UNIT_SIZE]
 * and hung on `Modifier.alignByBaseline()` — the glyph is cut to those digits, and a row that opts out
 * of the alignment line simply isn't levelled by anything.
 */
@Composable
private fun MetricRow(
    glyph: MetricGlyph,
    contentDescription: String?,
    winner: Boolean,
    outlineColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val inkHeight = digitCapHeight(density, METRIC_NUMBER_SIZE)
    val box = glyph.boxFor(inkHeight)
    // Levelling by ink leaves each glyph a different box width (no two assets ink the same fraction of
    // their viewport), which would step the rows' values apart horizontally. Reserve the widest
    // row's box for every row: Icon fits-and-centres the vector, so the extra width only centres the
    // glyph — the height still drives its scale.
    val column = inkHeight * WIDEST_BOX_FACTOR
    Row(
        modifier = Modifier.winnerOutline(winner, outlineColor),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painterResource(glyph.iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(width = column, height = box)
                .alignBy { with(density) { glyph.inkBaselineFor(inkHeight).roundToPx() } }
        )
        content()
    }
}

/**
 * A metric row's leading glyph, together with where the glyph's ink actually sits inside its 24-unit
 * vector viewport — read off the asset's `pathData` bounds, and no two of them the same: the hourglass
 * inks y[2, 22], the walker y[1.5, 23], the bicycle y[1.5, 22], the rental bike y[1, 22.497]. Carrying
 * the bounds is what lets [MetricRow] work in ink.
 *
 * These numbers are transcribed by hand from the asset, so re-read them whenever you swap a drawable,
 * edit its path, or re-import it from Material — nothing checks them for you, and a stale reading
 * un-levels its row quietly rather than loudly.
 *
 * The assets stay uncropped — cropping would only delete the ink fraction, not the levelling — and for
 * the street glyphs it can't be done at all: each is also a mode glyph ([streetModeIcon]) and a step
 * icon, where it has to sit at the same visual weight as the uncropped bus/rail glyphs beside it, so a
 * crop would mean a second copy of its path.
 */
private enum class MetricGlyph(@DrawableRes val iconRes: Int, val inkTop: Float, val inkBottom: Float) {
    DURATION(R.drawable.hourglass_24, inkTop = 2f, inkBottom = 22f),
    WALK(R.drawable.ic_directions_walk, inkTop = 1.5f, inkBottom = 23f),
    BIKE(R.drawable.ic_directions_bike, inkTop = 1.5f, inkBottom = 22f),
    BIKESHARE(R.drawable.ic_bike_rental, inkTop = 1f, inkBottom = 22.497f);

    /** What the icon box has to be scaled by for this glyph's ink alone to stand a wanted height. */
    val boxFactor: Float = GLYPH_VIEWPORT / (inkBottom - inkTop)

    /** The icon box that draws this glyph's ink exactly [inkHeight] tall. */
    fun boxFor(inkHeight: Dp): Dp = inkHeight * boxFactor

    /** Where the ink's bottom edge falls inside that box — the row's alignment line, i.e. the baseline. */
    fun inkBaselineFor(inkHeight: Dp): Dp = boxFor(inkHeight) * inkBottom / GLYPH_VIEWPORT
}

/** The viewport every glyph in [MetricGlyph] is authored in (Material's 24dp grid). */
private const val GLYPH_VIEWPORT = 24f

/**
 * The widest row's box, as a multiple of the ink height every row is cut to — derived from the glyphs
 * rather than pinned as a gutter width, so adding a metric re-levels the column instead of silently
 * leaving one row's value indented from the others.
 */
private val WIDEST_BOX_FACTOR = MetricGlyph.entries.maxOf { it.boxFactor }

/**
 * The metric value's type sizes. Bigger than the ETA pill's own 15/12sp: #2076 levelled the glyph and
 * the value on each other, and the honest meeting point is between the two — the glyph came down from
 * a 13.3dp ink height and the digits up from ~10.7dp, to ~12dp each. The unit keeps roughly the pill's
 * value:unit size ratio so the pair still reads as one "32 min", not two words.
 */
private val METRIC_NUMBER_SIZE = 17.sp
private val METRIC_UNIT_SIZE = 13.sp

/**
 * The ink height of a digit set at [size] — what a rider sees as the height of "32", cap to baseline,
 * which is what a glyph beside it has to match. Asked of the platform paint rather than taken as a
 * fraction of the em size: the em box reserves ascent/descent space no digit fills (a 15sp digit inks
 * about 10.7dp), and the ratio is the font's to state, not ours to assume. Measured through the row's
 * [Density], so it tracks the user's font-scale setting along with the text.
 */
@Composable
private fun digitCapHeight(density: Density, size: TextUnit): Dp = remember(density, size) {
    val bounds = Rect()
    Paint()
        .apply {
            // Matches how [EtaPartsText] sets the value: the theme's default family, bold.
            typeface = Typeface.DEFAULT_BOLD
            textSize = with(density) { size.toPx() }
        }
        .getTextBounds(CAP_SAMPLE, 0, CAP_SAMPLE.length, bounds)
    with(density) { bounds.height().toDp() }
}

/** A flat-topped, flat-bottomed digit: no round-glyph overshoot, no descender, so its ink is the cap. */
private const val CAP_SAMPLE = "7"

/** One endpoint of the option's time range, outlined independently when it wins its schedule category. */
@Composable
private fun ScheduleMetric(text: String, winner: Boolean, outlineColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier = Modifier.winnerOutline(winner, outlineColor)
    )
}

/** Adds no layout or drawing when [winner] is false, preserving the existing ordinary metric rows. */
private fun Modifier.winnerOutline(winner: Boolean, color: Color): Modifier = if (winner) {
    this
        .border(WINNER_OUTLINE_WIDTH, color, RoundedCornerShape(WINNER_OUTLINE_RADIUS))
        .padding(horizontal = 3.dp, vertical = 1.dp)
} else {
    this
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
    scheduleWinnerMode: ScheduleWinnerMode = ScheduleWinnerMode.BOTH,
    onSelectOption: (Int) -> Unit = {},
    onFocusRouteLeg: (RouteLegRef, FocusedLeg) -> Unit = { _, _ -> },
    onFocusLeg: (FocusedLeg) -> Unit = {},
    onFocusPoint: (GeoPoint) -> Unit = {},
    stopEtaStrip: @Composable (TripLogEntry.Transit, RouteStopRef) -> Unit = { _, _ -> },
    reminderControl: @Composable () -> Unit = {}
) {
    // Resolved once for the whole drawer rather than by each distance row: the rows below run one per
    // walk step, and a leaf resolve is a Hilt entry-point hop plus a locale query every time one enters
    // composition. A preview (or any other host) that has already pinned the units keeps its value.
    CompositionLocalProvider(LocalUnitsAreMetric provides unitsAreMetric()) {
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
                    scheduleWinnerMode = scheduleWinnerMode,
                    onSelectOption = onSelectOption,
                    onFocusRouteLeg = onFocusRouteLeg,
                    onFocusLeg = onFocusLeg,
                    onFocusPoint = onFocusPoint,
                    stopEtaStrip = stopEtaStrip,
                    reminderControl = reminderControl
                )
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
    onFocusRouteLeg: (RouteLegRef, FocusedLeg) -> Unit,
    onFocusLeg: (FocusedLeg) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (TripLogEntry.Transit, RouteStopRef) -> Unit,
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
        scheduleWinnerMode = when (params?.arriving) {
            true -> ScheduleWinnerMode.LATEST_DEPARTURE
            false -> ScheduleWinnerMode.EARLIEST_ARRIVAL
            null -> ScheduleWinnerMode.BOTH
        },
        onSelectOption = resultsViewModel::selectOption,
        onFocusRouteLeg = onFocusRouteLeg,
        onFocusLeg = onFocusLeg,
        onFocusPoint = onFocusPoint,
        stopEtaStrip = stopEtaStrip,
        reminderControl = {
            // Destination reminders are off pending the navigation-mode rework; leaving the slot
            // empty removes the affordance rather than offering one that starts nothing.
            if (FeatureFlags.DESTINATION_REMINDERS) {
                ItineraryReminderControl(
                    itineraries.getOrNull((state as? TripResultsUiState.Success)?.selectedIndex ?: 0),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
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

/** How much the drawer enlarges a route roundel over its default size — see [SegmentIdentity]. */
private const val BADGE_SCALE = 1.5f

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
    scheduleWinnerMode: ScheduleWinnerMode,
    onSelectOption: (Int) -> Unit,
    onFocusRouteLeg: (RouteLegRef, FocusedLeg) -> Unit,
    onFocusLeg: (FocusedLeg) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (TripLogEntry.Transit, RouteStopRef) -> Unit,
    reminderControl: @Composable () -> Unit
) {
    val entries = state.directions
    val expanded = remember(entries) { mutableStateSetOf<Int>() }
    val onToggle: (Int) -> Unit = remember(expanded) { { i -> if (!expanded.add(i)) expanded.remove(i) } }
    // Snapshotted to a plain Set so it can key the memo in rememberLogRows — reading it here is also
    // what makes a toggle recompose this list.
    val rows = rememberLogRows(entries, expanded.toSet())

    // The surface reaches the bottom edge; a bottom content padding lets the final leg row be scrolled
    // clear of the nav chrome without an empty strip below the list.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + LOG_EDGE_GAP)
    ) {
        // The picker scrolls with the steps (not pinned), so it recedes as you read down the list.
        item {
            TripResultsHeader(state, onSelectOption, scheduleWinnerMode)
            reminderControl()
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

/**
 * The [entries] flattened into timeline rows, with the spine and band colours this theme resolves.
 *
 * Held apart from [TripLogList] so a single leg can be rendered through the very same pipeline (see the
 * leg previews at the foot of this file) — the colour resolution is the part a second call site would
 * otherwise copy and let drift.
 */
@Composable
private fun rememberLogRows(entries: List<TripLogEntry>, expandedEntries: Set<Int>): List<LogRowModel> {
    val dark = MaterialTheme.colorScheme.isDarkTheme()
    // A walk's spine and any route whose colour we can't use: an outline-toned line, with the surface
    // showing through as the glyph so a filled neutral node still reads.
    val neutral = RouteLineColors(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.surface)
    return remember(entries, expandedEntries, neutral, dark) {
        flattenLog(
            entries = entries,
            expandedEntries = expandedEntries,
            neutral = neutral,
            // The agency's GTFS colour re-toned to stay legible on this theme's surface, so a near-black
            // or near-white route can't hand us an invisible spine. Same system as the leg's route badge —
            // including its answer for a route that publishes no colour at all
            // ([ridePresentationColor]), so a ferry's spine, its roundel and its line on the map are one
            // hue rather than three fallbacks.
            rideColors = { routeLineColors(ridePresentationColor(it.routeColorHex), dark, neutral) }
        )
    }
}

/** Renders one flattened [model] row — its map-focus tap wiring and body — dispatched by content kind. */
@Composable
private fun LogRow(
    model: LogRowModel,
    onToggle: (Int) -> Unit,
    onFocusRouteLeg: (RouteLegRef, FocusedLeg) -> Unit,
    onFocusLeg: (FocusedLeg) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (TripLogEntry.Transit, RouteStopRef) -> Unit
) {
    val i = model.entryIndex
    when (val content = model.content) {
        is RowContent.Terminal ->
            LogRowScaffold(model, onClick = content.entry.point?.let { { onFocusPoint(it) } }) {
                TerminalContent(content.entry)
            }

        is RowContent.WalkHeader -> {
            val walk = content.entry
            // The row's tap only frames the leg on the map; expanding its steps is the chevron's own
            // tap target (#2040), not a side effect of this one.
            LogRowScaffold(
                model = model,
                onClick = {
                    if (walk.legPoints.isNotEmpty()) onFocusLeg(walk.focus) else walk.focusPoint?.let(onFocusPoint)
                },
                onToggleExpand = { onToggle(i) }
            ) { WalkHeaderContent(walk) }
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
            LogRowScaffold(model, onClick = null, onToggleExpand = { onToggle(i) }) {
                BoardContent(
                    entry = transit,
                    onFocus = { focusTransit(transit, onFocusRouteLeg, onFocusLeg, onFocusPoint) },
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
 *
 * Not private, because the drawer's row is no longer the only way to tap a ride: the route label drawn on
 * that ride's line on the map is one too (#2101), and it spends this same function so the two can't
 * drift into meaning different things.
 */
internal fun focusTransit(
    entry: TripLogEntry.Transit,
    onFocusRouteLeg: (RouteLegRef, FocusedLeg) -> Unit,
    onFocusLeg: (FocusedLeg) -> Unit,
    onFocusPoint: (GeoPoint) -> Unit
) {
    val routeLeg = entry.routeLeg.takeIf { it.routeId != null }
    when {
        routeLeg != null -> onFocusRouteLeg(routeLeg, entry.focus)
        entry.legPoints.isNotEmpty() -> onFocusLeg(entry.focus)
        else -> entry.routeLeg.board?.point?.let(onFocusPoint)
    }
}

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
    onToggleExpand: (() -> Unit)? = null,
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
        // Its own segment at the row's right edge — centred on the row's full height, not just the
        // header line's — rather than sharing the content column's Row and bumping that line's height
        // out to the chevron's touch target (#2040).
        if (model.expandable && onToggleExpand != null) {
            ExpandChevron(
                expanded = model.expanded,
                onToggle = onToggleExpand,
                label = expandLabel(model),
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
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
 * The glyph for an on-street leg — inside its node on the spine, as its symbol on an option card, and
 * leading that mode's distance line. All three read the same [StreetMetric.glyph], so a mode can't be
 * drawn one way as a symbol and another as a metric.
 *
 * A rented bike takes a rental glyph (`ic_bike_rental`: Material Symbols' `car_rental` key over a
 * bicycle instead of a car) rather than the plain bicycle, so a shared bike doesn't read as the one the
 * rider brought — the same distinction the map draws between a bikeshare dock and a bike.
 *
 * Null for [StreetMode.CAR], which [StreetMetric] doesn't list: the app ships no car drawable because
 * its planner never asks OTP for car modes (the mode picker offers none — see
 * `org.onebusaway.android.ui.tripplan.TripModeSelection`), and a bare ring is honest where a walking
 * figure would be wrong. Offering car planning means a [StreetMetric] entry and a [MetricGlyph] with its
 * ink bounds.
 */
private fun streetModeIcon(mode: StreetMode): Int? = StreetMetric.of(mode)?.glyph?.iconRes

/**
 * The glyph a presentable street mode is drawn with — total, since [StreetMetric] holds exactly the
 * modes the cards can show, so the compiler asks for the art whenever one is added.
 */
private val StreetMetric.glyph: MetricGlyph get() = when (this) {
    StreetMetric.WALK -> MetricGlyph.WALK
    StreetMetric.BIKE -> MetricGlyph.BIKE
    StreetMetric.BIKESHARE -> MetricGlyph.BIKESHARE
}

/**
 * An option card's bare mode glyph — an on-street leg (which has no route to badge), or the vehicle a
 * leg with no route name is ridden on. Drawn at [SYMBOL_HEIGHT], the same height the roundels beside it
 * are scaled to, so a card's first line lines up whichever form its symbols take. Always labelled: with
 * no badge beside it, this glyph is the only thing naming that leg.
 */
@Composable
private fun ModeGlyph(iconRes: Int, contentDescription: String) {
    Icon(painterResource(iconRes), contentDescription = contentDescription, modifier = Modifier.size(SYMBOL_HEIGHT))
}

/**
 * The chevron joining two mode symbols. Quietened by fading the card's *own* content colour rather
 * than reaching for a theme grey: the card is tinted (and differently again when selected), so an
 * unrelated `onSurfaceVariant` lands as an off-hue smudge on it.
 *
 * Unlabelled: the order it marks is already the order TalkBack reads the symbols in, so announcing it
 * between every pair would only pad the card.
 */
@Composable
private fun SymbolSeparator() {
    Icon(
        painterResource(R.drawable.ic_arrow_right),
        contentDescription = null,
        tint = LocalContentColor.current.copy(alpha = SYMBOL_SEPARATOR_ALPHA),
        // The cropped glyph is 1:2, so the box must be too — a square one would pad it back out.
        modifier = Modifier.size(SYMBOL_SEPARATOR_HEIGHT / 2, SYMBOL_SEPARATOR_HEIGHT)
    )
}

/**
 * The glyph for a transit leg — its Board node on the spine, and the option card's stand-in for a leg
 * whose route publishes no name. Total over [TransitMode] (which is already narrowed to the art the app
 * ships), so unlike [streetModeIcon] there is no null case: a ride always draws something.
 */
private fun transitModeIcon(mode: TransitMode): Int = when (mode) {
    TransitMode.BUS -> R.drawable.ic_bus
    TransitMode.RAIL -> R.drawable.ic_directions_railway
    TransitMode.SUBWAY -> R.drawable.ic_directions_subway
    TransitMode.TRAM -> R.drawable.ic_tram
    // ic_directions_boat, not the byte-identical ic_ferry: it's drawn to match the weight of the
    // walk/railway/subway glyphs the spine already uses.
    TransitMode.FERRY -> R.drawable.ic_directions_boat
}

/** What to call [mode] aloud, for a glyph standing in for a route name. */
private fun transitModeLabel(mode: TransitMode): Int = when (mode) {
    TransitMode.BUS -> R.string.step_by_step_transit_mode_bus
    TransitMode.RAIL -> R.string.step_by_step_transit_mode_rail
    TransitMode.SUBWAY -> R.string.step_by_step_transit_mode_subway
    TransitMode.TRAM -> R.string.step_by_step_transit_mode_tram
    TransitMode.FERRY -> R.string.step_by_step_transit_mode_ferry
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
            FilledNode(26.dp, nodeColor, transitModeIcon(content.entry.mode), onNode, 16.dp, shape = RoundedCornerShape(8.dp))
        is RowContent.ExitNode -> RingNode(22.dp, 3.dp, nodeColor)
        is RowContent.Stop -> RingNode(11.dp, 2.dp, nodeColor)
        // A seam's arrow points *down* the rail, the way the ride carries on. It used to borrow the walk
        // maneuver's ic_continue, whose arrow points up — that glyph means "keep going straight ahead"
        // on a compass-oriented turn list, and read as travel back up the timeline on a vertical one.
        is RowContent.Transition ->
            FilledNode(22.dp, nodeColor, R.drawable.ic_arrow_downward, onNode, 14.dp)
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
        // dotted/dotless I wrong on a string we just localized. Read through LocalLocale rather than
        // Locale.getDefault(): the latter isn't observable state, so this Text would keep the old
        // casing after the rider changes their locale until something else recomposed it.
        text = stringResource(
            if (entry.kind == TerminalKind.START) R.string.trip_plan_leaving else R.string.trip_plan_arriving
        ).uppercase(LocalLocale.current.platformLocale),
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
    // A shared bike is a different act from riding your own — you have to find it and rent it first —
    // so the leg is titled by what the rider is doing rather than by the vehicle they end up on. It's
    // the same word the option card's glyph is announced with ([StreetMetric.BIKESHARE]), so the card
    // and the row that expands from it name the leg alike.
    StreetMode.BIKESHARE ->
        if (isTransfer) R.string.trip_plan_bikeshare_transfer else R.string.transit_directions_bikeshare_label
    StreetMode.CAR ->
        if (isTransfer) R.string.trip_plan_car_transfer else R.string.step_by_step_non_transit_mode_car_action
}

@Composable
private fun ColumnScope.WalkHeaderContent(entry: TripLogEntry.Walk) {
    val context = LocalContext.current
    Text(
        text = stringResource(streetActionRes(entry.mode, entry.isTransfer)),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    val meta = walkMeta(entry, context, unitsAreMetric())
    if (meta.isNotEmpty()) {
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    entry.rental?.let { RentalContent(it) }
    // A walk gets its alerts under its header for the same reason a ride does — a closed dock or a
    // blocked path is about this stretch of the trip, not the trip as a whole.
    LegAlerts(entry.alerts)
}

/**
 * What the rider needs to know about the shared bike they're being sent to (#2150): whose it is, what
 * it is, where to find it, and how to unlock it. Before this the leg said only "Bike", which told a
 * rider standing over an unfamiliar scooter nothing about which app opens it.
 *
 * The operator's chip is also the way over to them: it wears an open-in-new arrow and is the row's
 * only external affordance, so the brand the rider is looking for and the thing they tap are one
 * object rather than a chip with a button repeating its name underneath. A network with neither a
 * rental URI nor a catalog entry has nowhere to send anyone, and its chip is a plain label — no arrow,
 * no tap — rather than one going somewhere approximate.
 *
 * Everything else is drawn from what the feed actually said, and omitted rather than guessed when it
 * didn't: an operator the app has no catalog entry for wears its raw network id (see
 * [RentalOperators]), and a pickup with no named dock draws no pickup line.
 *
 * The vehicle id is deliberately not shown. The ids the live networks publish are UUIDs — nothing a
 * rider can match against the bike in front of them — so drawing one would be noise that reads like an
 * instruction.
 */
@Composable
private fun ColumnScope.RentalContent(rental: RentalPickup) {
    val context = LocalContext.current
    val metric = unitsAreMetric()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OperatorChip(rental) { link -> openRental(context, link, rental.fallback) }
        val details = listOfNotNull(
            rental.vehicle?.let { stringResource(rentalVehicleRes(it)) },
            rental.rangeMeters?.let {
                stringResource(
                    R.string.trip_plan_rental_range,
                    ConversionUtils.getFormattedDistance(it.toDouble(), context, metric)
                )
            }
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    rental.stationName?.let {
        Text(
            text = stringResource(R.string.trip_plan_rental_pick_up_at, it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The operator on its brand colour — and, when there is somewhere to send the rider, the tap that
 * takes them there.
 *
 * The tap area is the chip plus [RENTAL_OPERATOR_CHIP_TAP_PADDING], which lands short of the 48dp
 * interactive minimum — deliberately: reserving the full minimum around a chip this size padded the
 * row by a dozen dp above and below and made the leg read as three loosely-spaced blocks rather than
 * one. The chip is drawn at [RENTAL_OPERATOR_CHIP_SCALE] partly to keep what is left a comfortable
 * mark, and it is never the only way on — the row it sits in is itself a tap target.
 *
 * Only [RentalLink.Deep] names the exact vehicle the rider was routed onto, so only it is announced as
 * opening it; the rest merely open the operator, and none of it claims a reservation was made (#2138).
 */
@Composable
private fun OperatorChip(rental: RentalPickup, onOpen: (RentalLink) -> Unit) {
    val name = rental.operator.displayName
    val described = stringResource(R.string.trip_plan_rental_operator_description, name)
    val link = rental.link
    val openLabel = link?.let {
        stringResource(
            if (it is RentalLink.Deep) R.string.trip_plan_rental_open_in else R.string.trip_plan_rental_rent_with,
            name
        )
    }
    Box(
        // Padding *after* clickable, so the breathing room around the chip is part of what the rider
        // can hit rather than a dead margin outside it.
        modifier = when (link) {
            null -> Modifier
            else -> Modifier.clickable(onClickLabel = openLabel) { onOpen(link) }
        }.padding(vertical = RENTAL_OPERATOR_CHIP_TAP_PADDING),
        contentAlignment = Alignment.Center
    ) {
        RouteBadgeChip(
            shortName = name,
            routeColor = rental.operator.brandColor,
            scale = RENTAL_OPERATOR_CHIP_SCALE,
            // A brand name is a word, not a route number: capped so a long one ellipsizes inside the
            // chip instead of pushing the vehicle beside it off the row. Scaled with the chip, or the
            // enlargement would buy the arrow room by taking it from the name.
            maxWidth = RENTAL_OPERATOR_CHIP_MAX_WIDTH * RENTAL_OPERATOR_CHIP_SCALE,
            trailingIcon = R.drawable.ic_open_in_new.takeIf { link != null },
            // Merged, so the chip is one node reading "Lime rental" rather than that description and
            // the bare brand name inside it announced as two.
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = described }
        )
    }
}

/**
 * How much bigger the operator chip is than a route roundel. It is the leg's brand *and* its only tap
 * target, and it carries a glyph beside the name, so it is drawn above roundel size — the same 1.5×
 * the directions board badge uses, rather than a size of its own.
 */
private const val RENTAL_OPERATOR_CHIP_SCALE = 1.5f

/** The breathing room above and below the operator chip, which is also part of its tap area. */
private val RENTAL_OPERATOR_CHIP_TAP_PADDING = 3.dp

/** How wide the operator chip may get before its name ellipsizes, at scale 1 — a dozen characters. */
private val RENTAL_OPERATOR_CHIP_MAX_WIDTH = 96.dp

/**
 * Hands the rider over: the operator's own link, and — when the device has nothing that handles a
 * custom-scheme deep link — whatever [fallback] the pickup kept for exactly that (see
 * [RentalPickup.fallback]).
 */
private fun openRental(context: Context, link: RentalLink, fallback: RentalLink?) {
    when (link) {
        is RentalLink.Deep -> if (!ExternalIntents.openFeedUri(context, link.uri)) {
            fallback?.let { openRental(context, it, fallback = null) }
        }
        is RentalLink.OperatorApp -> ExternalIntents.openAppOrStoreListing(context, link.packageName)
        is RentalLink.Web -> ExternalIntents.goToUrl(context, link.url)
    }
}

/** What to call the rented vehicle — total over [RentalVehicleKind], so a new kind needs its word. */
private fun rentalVehicleRes(kind: RentalVehicleKind): Int = when (kind) {
    RentalVehicleKind.BIKE -> R.string.trip_plan_rental_bike
    RentalVehicleKind.EBIKE -> R.string.trip_plan_rental_ebike
    RentalVehicleKind.CARGO_BIKE -> R.string.trip_plan_rental_cargo_bike
    RentalVehicleKind.ELECTRIC_CARGO_BIKE -> R.string.trip_plan_rental_electric_cargo_bike
    RentalVehicleKind.SCOOTER -> R.string.trip_plan_rental_scooter
    RentalVehicleKind.ESCOOTER -> R.string.trip_plan_rental_escooter
    RentalVehicleKind.MOPED -> R.string.trip_plan_rental_moped
    RentalVehicleKind.CAR -> R.string.trip_plan_rental_car
}

/**
 * A leg's service alerts, under its header (#2143), loudest first.
 *
 * Drawn inline in the leg's row rather than as lazy items of their own: an alert belongs to the leg the
 * way its headsign does, and hanging it off the row keeps it inside the leg's band and spine, so it
 * cannot drift away from the ride it qualifies as the list scrolls or a leg above it expands.
 */
@Composable
private fun LegAlerts(alerts: List<TripAlertItem>) {
    alerts.forEach { alert ->
        key(alert.contentId) {
            TripAlertBanner(alert)
        }
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
        text = ConversionUtils.getFormattedDistance(distanceMeters, LocalContext.current, unitsAreMetric()),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun ColumnScope.BoardContent(
    entry: TripLogEntry.Transit,
    onFocus: () -> Unit,
    onFocusPoint: (GeoPoint) -> Unit,
    stopEtaStrip: @Composable (TripLogEntry.Transit, RouteStopRef) -> Unit
) {
    // The route/headsign block highlights the leg on the map; expanding its steps is the scaffold's
    // own chevron segment (#2040), not a side effect of this tap. The board stop + ETA strip below is a
    // third, separate tap target that zooms to the stop. Because this control is this inner block rather
    // than the whole row, the scaffold's touch-target floor doesn't reach it — so it carries its own. (Its
    // content clears 48dp on its own in practice; this is the guarantee, not the usual case.)
    Column(
        Modifier
            .defaultMinSize(minHeight = ROW_MIN_TOUCH_HEIGHT)
            .clickable(onClick = onFocus)
    ) {
        val boardable = entry.boardableRoutes()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            boardable.forEach { route ->
                SegmentIdentity(badge = route.badge, name = route.name, headsign = route.headsign)
            }
        }
        if (boardable.size > 1) {
            Text(
                text = stringResource(R.string.directions_whichever_comes_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
    // Directly under the route header and above the boarding instruction (#2143): the alert is about
    // *this* ride, so it belongs against the ride's name, and the rider reads that service is disrupted
    // before being told which stop to wait at. Outside the identity block above, whose tap frames the
    // leg on the map — an alert owns its own expand tap and must not spend that one.
    LegAlerts(entry.alerts)
    entry.routeLeg.board?.let { stop ->
        Spacer(Modifier.height(6.dp))
        StopActionLabel(
            actionRes = R.string.step_by_step_transit_get_on,
            stopName = stop.name,
            onClick = { stop.point?.let(onFocusPoint) }
        )
        // The whole ride, not just its route/stop: the strip also rules the plan's own arrival at this
        // stop across the live ETAs (#2125), and a pill tap frames the ride's geometry on the map.
        stopEtaStrip(entry, stop)
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
 * to stay on board — never to get off and reboard.
 *
 * Laid out as the board row of the ride's *next segment* (#2071) — roundel and headsign, by the same
 * rules a board row uses — since that is what the seam is: the header above named the route boarded, and
 * this names the route the rider goes on to ride, at the point they reach it. The instruction and the
 * seam stop follow, so a route reads
 * `[12] → Interlaken Park / Stay on board / At Mount Baker Transit Center`.
 *
 * With nothing at all to name the new route by — no badge, no name, no headsign — the instruction says
 * so itself rather than standing over a blank.
 */
@Composable
private fun ColumnScope.TransitionContent(transition: InterlineTransition) {
    val headsign = transition.headsign?.takeIf { it.isNotEmpty() }
    SegmentIdentity(
        badge = transition.badge,
        // Only ever the stand-in for a missing roundel, as on a board row: a route with a roundel is
        // named by it alone (#2151).
        name = transition.routeDisplayName.takeIf { transition.badge == null },
        headsign = headsign
    )
    val named = transition.badge != null || transition.routeDisplayName != null || headsign != null
    Text(
        text = stringResource(
            if (named) R.string.step_by_step_transit_stay_on_board else R.string.step_by_step_transit_interline_unknown_route
        ),
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

/**
 * How a row names one route the rider is about to be on: its roundel and, on the same line, where that
 * route is headed.
 *
 * One composable rather than two so the ride's segments can't drift apart (#2071). The rider boards a
 * segment at the board row and reaches every later one at a seam row, and those are the same act — a
 * padding or type tweak landing on one of them and not the other would make one ride read as two
 * different kinds of thing. It names exactly one route, so a board row offering several draws it once
 * per route (#2151).
 *
 * The roundel and the headsign together already say what to board, so the route's long name isn't
 * printed beside them (#2151); [name] is drawn only in the roundel's place, for a route publishing no
 * short name — see [BoardableRoute]. The headsign takes the app's shared "heading toward X" treatment
 * ([DirectionHeadsign], #1823) rather than a setting of its own, so the sign on the bus reads the same
 * here as on an arrivals row.
 */
@Composable
private fun SegmentIdentity(badge: RouteBadge?, name: String?, headsign: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (badge != null) {
            RouteBadgeChip(badge.shortName, badge.routeColor, scale = BADGE_SCALE)
            Spacer(Modifier.width(8.dp))
        } else if (name != null) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
        }
        // Weighted so a long headsign ellipsizes against the row rather than pushing the roundel off it.
        headsign?.let { DirectionHeadsign(it, Modifier.weight(1f)) }
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

/**
 * The expand/collapse control for a leg with minor events, shown as the up/down chevron glyph. This is
 * its own tap target — separate from the row's tap, which frames the leg on the map — so opening the
 * steps is never a side effect of a map-focus tap (#2040). [label] carries the expand/collapse wording
 * since the glyph swap alone isn't announced to a screen reader.
 */
@Composable
private fun ExpandChevron(expanded: Boolean, onToggle: () -> Unit, label: String?, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (expanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.outline
        )
    }
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
private fun walkMeta(entry: TripLogEntry.Walk, context: Context, metric: Boolean): String = if (entry.distanceMeters > 0.0) ConversionUtils.getFormattedDistance(entry.distanceMeters, context, metric) else ""

// ---- previews ------------------------------------------------------------------------------------
//
// The itinerary drawer, driven through [TripResultsList] — the seam where the sheet becomes pure state:
// everything above it ([TripResultsSheet], [DirectionsResultsSheet]) needs the host Activity, a Hilt
// ViewModel, and a running TripPlanMonitor, none of which exist under layoutlib.
//
// Every preview pins [LocalUnitsAreMetric]. Distances are formatted from it rather than from the
// rider's preference precisely so these can render: the preference read reaches Application.get(),
// which throws here. See ConversionUtils.getFormattedDistanceParts.
//
// The fixtures build one trip, shared by the whole-drawer previews and the single-leg ones below, so a
// leg cannot read one way in the itinerary and another on its own.

// ---- shared fixtures -----------------------------------------------------------------------------

/** The ride boarded as the 8 that becomes the 12 underneath the rider (#2000/#2049), chevron-joined. */
private val PREVIEW_BUS_CHAIN_BADGE = LegBadge(
    listOf(RouteBadge("8", 0xFF1B6EF3.toInt()), RouteBadge("12", 0xFFD62828.toInt())),
    TransitMode.BUS,
    RouteBadgeJoin.THEN
)

/** Two interchangeable rail lines (#2010), slash-joined — board whichever comes first. */
private val PREVIEW_RAIL_PAIR_BADGE = LegBadge(
    listOf(RouteBadge("1 Line", 0xFF00A651.toInt()), RouteBadge("2 Line", 0xFF0075C4.toInt())),
    TransitMode.RAIL,
    RouteBadgeJoin.ANY_OF
)

private val PREVIEW_RIDE_STOPS = listOf(
    RideEvent.Stop(LogStop("Capitol Hill Station")),
    RideEvent.Stop(LogStop("23rd Ave & E Union St")),
    RideEvent.Stop(LogStop("Rainier Ave S & S McClellan St"))
)

/** The stay-aboard seam the chevroned badge stands for, reached partway through the ride (#2071). */
private val PREVIEW_INTERLINE_SEAM = RideEvent.Transition(
    InterlineTransition(
        badge = RouteBadge("12", 0xFFD62828.toInt()),
        routeDisplayName = "Route 12",
        headsign = "Interlaken Park",
        stop = RouteStopRef("1_550", "550", "Mount Baker Transit Center", null)
    )
)

// One alert per travelling leg, one per severity — enough to show each tone *and* that the placement is
// per-leg: the walk's notice, the bus's reroute and the boat's cancellation each sit under their own
// header rather than pooling at the top of the itinerary.

private val PREVIEW_WALK_ALERT = TripAlertItem(
    contentId = "alert_sidewalk",
    summary = "Sidewalk closed on the east side of 5th Ave",
    description = null,
    url = null,
    severity = AlertSeverity.INFO
)

private val PREVIEW_BUS_ALERT = TripAlertItem(
    contentId = "alert_reroute",
    summary = "Route 8 is rerouted around Denny Way construction",
    description = "Buses are using Olive Way in both directions. Stops between Denny & Stewart are " +
        "closed; use the temporary stop at Olive & Boren.",
    url = "https://example.transit/alerts/route-8",
    severity = AlertSeverity.WARNING
)

private val PREVIEW_FERRY_ALERT = TripAlertItem(
    contentId = "alert_ferry_cancelled",
    summary = "The 4:10 PM Bremerton sailing is cancelled",
    description = "One vessel is out of service. The next sailing is at 5:30 PM.",
    url = null,
    severity = AlertSeverity.ERROR
)

/** One transit leg, defaulted to an ordinary bus ride so each caller states only what it varies. */
private fun previewTransitLeg(
    routeShortName: String? = "8",
    routeDisplayName: String? = "Route 8",
    mode: TransitMode = TransitMode.BUS,
    routeColorHex: String? = "1B6EF3",
    headsign: String? = "Rainier Beach",
    rideEvents: List<RideEvent> = PREVIEW_RIDE_STOPS,
    alternatives: List<AlternativeRouteRef> = emptyList(),
    alerts: List<TripAlertItem> = emptyList()
) = TripLogEntry.Transit(
    routeShortName = routeShortName,
    routeDisplayName = routeDisplayName,
    mode = mode,
    routeColorHex = routeColorHex,
    headsign = headsign,
    reachStopTime = ServerTime(3 * 60_000L),
    boardTime = ServerTime(4 * 60_000L),
    exitTime = ServerTime(20 * 60_000L),
    durationMinutes = 16,
    rideEvents = rideEvents,
    routeLeg = RouteLegRef(
        routeId = "1_100",
        headsign = headsign,
        board = RouteStopRef("1_500", "500", "Pine St & 3rd Ave", null),
        alight = RouteStopRef("1_600", "600", "Rainier Ave S & S Alaska St", null),
        alternatives = alternatives
    ),
    alerts = alerts
)

/**
 * A Washington State Ferries run: no route short name, so no badge — the long name stands in the
 * roundel's place and the boat glyph rides the spine. Built off [previewTransitLeg] and then re-timed,
 * since an hour on a boat is the one thing about it that isn't a bus ride's shape.
 */
private fun previewFerryLeg(alerts: List<TripAlertItem> = emptyList()) = previewTransitLeg(
    routeShortName = null,
    routeDisplayName = "Seattle - Bremerton",
    mode = TransitMode.FERRY,
    routeColorHex = null,
    headsign = "Bremerton",
    rideEvents = emptyList(),
    alerts = alerts
).copy(
    reachStopTime = ServerTime(20 * 60_000L),
    boardTime = ServerTime(24 * 60_000L),
    exitTime = ServerTime(84 * 60_000L),
    durationMinutes = 60,
    routeLeg = RouteLegRef(
        routeId = "95_74",
        headsign = "Bremerton",
        board = RouteStopRef("95_1", null, "Seattle Ferry Terminal", null),
        alight = RouteStopRef("95_2", null, "Bremerton Ferry Terminal", null)
    )
)

// ---- itinerary fixtures --------------------------------------------------------------------------

/**
 * The three option cards of the picker. With [alerted], the symbols standing for the legs that carry an
 * alert below are marked — the walk and the bus, at the tones their own alerts have, so the card and the
 * directions name the same two rides rather than pointing at different ones.
 */
private fun previewOptions(alerted: Boolean = false) = listOf(
    ItineraryOption(
        // Both joined badges side by side: a bus that becomes the 12 with the rider aboard,
        // chevroned (#2049), then an interchangeable rail pair, slashed (#2010) — the
        // transfer between them being too short to draw a glyph for (#2047).
        symbols = listOf(
            ModeSymbol.Street(StreetMode.WALK, alert = PREVIEW_WALK_ALERT.severity.takeIf { alerted }),
            ModeSymbol.Transit(PREVIEW_BUS_CHAIN_BADGE, alert = PREVIEW_BUS_ALERT.severity.takeIf { alerted }),
            ModeSymbol.Transit(PREVIEW_RAIL_PAIR_BADGE),
            ModeSymbol.Street(StreetMode.WALK)
        ),
        durationMinutes = 32,
        startTime = ServerTime(0L),
        endTime = ServerTime(32 * 60_000L),
        streetDistanceMeters = mapOf(StreetMode.WALK to 800.0)
    ),
    ItineraryOption(
        // The second leg is a ferry, which publishes no route short name — so it badges its
        // long name, capped and ellipsized.
        symbols = listOf(
            ModeSymbol.Transit(LegBadge(listOf(RouteBadge("48", null)), TransitMode.BUS, RouteBadgeJoin.ANY_OF)),
            ModeSymbol.Street(StreetMode.WALK),
            ModeSymbol.Transit(LegBadge(listOf(RouteBadge("Seattle - Bremerton", null)), TransitMode.FERRY, RouteBadgeJoin.ANY_OF))
        ),
        durationMinutes = 41,
        startTime = ServerTime(0L),
        endTime = ServerTime(41 * 60_000L),
        streetDistanceMeters = mapOf(StreetMode.WALK to 400.0)
    ),
    ItineraryOption(
        // A bikeshare trip: walk to the dock, ride, walk from it (#2047).
        symbols = listOf(
            ModeSymbol.Street(StreetMode.WALK),
            ModeSymbol.Street(StreetMode.BIKESHARE),
            ModeSymbol.Street(StreetMode.WALK)
        ),
        durationMinutes = 18,
        startTime = ServerTime(0L),
        endTime = ServerTime(18 * 60_000L),
        // Both of its street modes measured, the ride as well as the walk (#2122).
        streetDistanceMeters = mapOf(StreetMode.WALK to 500.0, StreetMode.BIKESHARE to 2300.0)
    )
)

/**
 * The selected option's directions: a walk, the interlined bus ride its chevroned badge stands for, a
 * ferry, and both terminals. With [alerted], each travelling leg also carries its own alert (#2143).
 */
private fun previewDirections(alerted: Boolean = false) = listOf(
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
        steps = listOf(LogStep("Head north on 5th Ave", distanceMeters = 107.0)),
        alerts = listOfNotNull(PREVIEW_WALK_ALERT.takeIf { alerted })
    ),
    // The ride the first option's chevron badge stands for: boarded once as the 8, becoming the 12 at
    // Mount Baker without the rider getting off. The header badges only the 8; the 12 arrives at its own
    // seam row, which is why the seam sits among the stops rather than before them.
    previewTransitLeg(
        rideEvents = PREVIEW_RIDE_STOPS.take(2) + PREVIEW_INTERLINE_SEAM + PREVIEW_RIDE_STOPS.drop(2),
        alerts = listOfNotNull(PREVIEW_BUS_ALERT.takeIf { alerted })
    ),
    previewFerryLeg(alerts = listOfNotNull(PREVIEW_FERRY_ALERT.takeIf { alerted })),
    TripLogEntry.Terminal(
        kind = TerminalKind.ARRIVE,
        time = ServerTime(90 * 60_000L),
        place = "Bremerton"
    )
)

private fun previewSuccess(alerted: Boolean = false) = TripResultsUiState.Success(
    options = previewOptions(alerted),
    selectedIndex = 0,
    directions = previewDirections(alerted)
)

/**
 * Renders [state] as the drawer draws it. [metric] pins the unit system, which is both what makes these
 * render at all and what lets one fixture be checked in either unit system.
 */
@Composable
private fun TripDrawerPreviewFrame(state: TripResultsUiState, metric: Boolean = false) {
    CompositionLocalProvider(LocalUnitsAreMetric provides metric) {
        ObaTheme {
            TripResultsList(state)
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900, name = "Itinerary · full trip")
@Composable
private fun TripResultsPreview() {
    TripDrawerPreviewFrame(previewSuccess())
}

@Preview(showBackground = true, widthDp = 400, heightDp = 900, name = "Itinerary · metric units")
@Composable
private fun TripResultsMetricPreview() {
    // The same trip in km/m. Worth its own preview because the unit swap changes how wide the option
    // cards' distance lines run, which is where the summary symbols get squeezed.
    TripDrawerPreviewFrame(previewSuccess(), metric = true)
}

/**
 * Alerts on every travelling leg (#2143): a banner under each leg's own header, and the triangles on the
 * option card marking the same two legs. Rendered in both themes — the severity container tints are the
 * part most likely to go wrong in dark.
 */
@Preview(showBackground = true, widthDp = 400, heightDp = 900, name = "Itinerary · service alerts")
@Preview(
    showBackground = true,
    widthDp = 400,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Itinerary · service alerts, dark"
)
@Composable
private fun TripResultsAlertsPreview() {
    TripDrawerPreviewFrame(previewSuccess(alerted = true))
}

@Preview(showBackground = true, widthDp = 400, heightDp = 240, name = "Itinerary · loading")
@Composable
private fun TripResultsLoadingPreview() {
    TripDrawerPreviewFrame(TripResultsUiState.Loading)
}

@Preview(showBackground = true, widthDp = 400, heightDp = 240, name = "Itinerary · error")
@Composable
private fun TripResultsErrorPreview() {
    TripDrawerPreviewFrame(TripResultsUiState.Error("No itineraries found between these places."))
}

// ---- transit-leg previews ------------------------------------------------------------------------
//
// One leg on its own, run through the same [rememberLogRows] pipeline the drawer uses, so what these
// show is what the list draws — spine, band, nodes and all. A lone leg has no neighbouring leg to hand
// its spine to, so the rail stops at the exit node rather than running on; that truncation is the
// isolation, not a defect.

/**
 * Renders [entry] — a ride or an on-street leg — alone, [expanded] or not. The row callbacks are
 * no-ops (nothing is tappable in a static preview) and the ETA strip is empty — it needs a live
 * arrivals session, which is exactly the host dependency previewing one leg is meant to escape.
 */
@Composable
private fun LegPreviewFrame(entry: TripLogEntry, expanded: Boolean = false) {
    LegsPreviewFrame(listOf(entry), if (expanded) setOf(0) else emptySet())
}

/**
 * The same, for a short run of consecutive legs — where what's being looked at is how a leg sits
 * against its neighbours (spacing, the spine's colour flip at each node) rather than the leg alone.
 */
@Composable
private fun LegsPreviewFrame(entries: List<TripLogEntry>, expanded: Set<Int> = emptySet()) {
    CompositionLocalProvider(LocalUnitsAreMetric provides false) {
        ObaTheme {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    rememberLogRows(entries, expanded).forEach { row ->
                        key(row.key) {
                            LogRow(
                                model = row,
                                onToggle = {},
                                onFocusRouteLeg = { _, _ -> },
                                onFocusLeg = {},
                                onFocusPoint = {},
                                stopEtaStrip = { _, _ -> }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, name = "Transit leg · collapsed")
@Composable
private fun TransitLegPreview() {
    // Board header (roundel + headsign) and board stop — the ride's stops stay folded away.
    LegPreviewFrame(previewTransitLeg())
}

@Preview(showBackground = true, widthDp = 400, heightDp = 340, name = "Transit leg · expanded")
@Composable
private fun TransitLegExpandedPreview() {
    // What the chevron reveals: every intermediate stop, on the ride's own coloured spine.
    LegPreviewFrame(previewTransitLeg(), expanded = true)
}

@Preview(showBackground = true, widthDp = 400, heightDp = 300, name = "Transit leg · service alert")
@Composable
private fun TransitLegAlertPreview() {
    // The #2143 placement at leg scale: the banner sits under the route header and above "Get on at",
    // which is the adjacency the whole change is about.
    LegPreviewFrame(previewTransitLeg(alerts = listOf(PREVIEW_BUS_ALERT)))
}

@Preview(showBackground = true, widthDp = 400, heightDp = 360, name = "Transit leg · two alerts")
@Composable
private fun TransitLegTwoAlertsPreview() {
    // Loudest first, and two tinted cards stacked — the case where the leg header risks being crowded
    // off the top of the row.
    LegPreviewFrame(previewTransitLeg(alerts = listOf(PREVIEW_FERRY_ALERT, PREVIEW_BUS_ALERT)))
}

@Preview(showBackground = true, widthDp = 400, name = "Transit leg · whichever comes first")
@Composable
private fun TransitLegInterchangeablePreview() {
    // An interchangeable pair (#2010/#2151): a line per route, each with its own headsign — the two
    // share the track but not where they end up — plus the caption that says to board either.
    LegPreviewFrame(
        previewTransitLeg(
            routeShortName = "1 Line",
            routeDisplayName = null,
            mode = TransitMode.RAIL,
            routeColorHex = "00A651",
            headsign = "Angle Lake",
            alternatives = listOf(
                AlternativeRouteRef(
                    routeId = "40_2LINE",
                    headsign = "Downtown Redmond",
                    shortName = "2 Line",
                    routeColor = 0xFF0075C4.toInt()
                )
            )
        )
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 300, name = "Transit leg · interline seam")
@Composable
private fun TransitLegInterlinePreview() {
    // A stay-aboard route change (#2000/#2071). The seam row shows whether or not the leg is expanded —
    // it instructs the rider rather than merely annotating the ride — so this one stays collapsed.
    LegPreviewFrame(
        previewTransitLeg(rideEvents = listOf(PREVIEW_RIDE_STOPS.first(), PREVIEW_INTERLINE_SEAM))
    )
}

@Preview(showBackground = true, widthDp = 400, name = "Transit leg · unnamed route (ferry)")
@Composable
private fun TransitLegNoShortNamePreview() {
    // No short name to badge, so the long name stands in its place and the boat glyph rides the spine —
    // and with no GTFS colour, the whole leg falls back to one neutral hue rather than three.
    LegPreviewFrame(previewFerryLeg())
}

// ---- bikeshare-leg previews ----------------------------------------------------------------------

/** The pickup a Puget Sound rider actually gets today: a catalogued operator, an e-bike, no dock. */
private fun previewLimePickup() = RentalPickup(
    operator = RentalOperators.of("lime_seattle"),
    vehicle = RentalVehicleKind.EBIKE,
    stationName = null,
    rangeMeters = 43356,
    // No live network publishes a rental URI, so the chip opens the operator rather than this vehicle.
    link = RentalLink.OperatorApp("com.limebike"),
    fallback = RentalLink.Web("https://www.li.me/")
)

/** A rented-vehicle leg, ridden on [rental] — the shape a bikeshare itinerary's middle leg takes. */
private fun previewRentalLeg(rental: RentalPickup) = TripLogEntry.Walk(
    mode = StreetMode.BIKESHARE,
    durationMinutes = 12,
    distanceMeters = 3380.0,
    isTransfer = false,
    steps = emptyList(),
    rental = rental
)

@Preview(showBackground = true, widthDp = 400, name = "Bikeshare leg · known operator")
@Preview(
    showBackground = true,
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Bikeshare leg · known operator, dark"
)
@Composable
private fun BikeshareLegPreview() {
    // What a Puget Sound rider actually gets today: a catalogued operator on its brand colour, an
    // electric-assist bicycle with its remaining range, no dock to look for, and — the feed publishing
    // no rental URI — a chip that opens the operator rather than claiming to unlock this bike. Drawn in
    // both themes because a saturated brand colour is exactly what the chip's tinting has to tame.
    LegPreviewFrame(previewRentalLeg(previewLimePickup()))
}

/** The walk that fetches the bike, the ride, and the walk from where it's left. */
private fun previewBikeshareItinerary() = listOf(
    TripLogEntry.Terminal(kind = TerminalKind.START, time = ServerTime(0L), place = "3rd Ave & Pike St"),
    TripLogEntry.Walk(
        mode = StreetMode.WALK,
        durationMinutes = 3,
        distanceMeters = 210.0,
        isTransfer = false,
        steps = listOf(LogStep("Head north on 3rd Ave", distanceMeters = 210.0))
    ),
    previewRentalLeg(previewLimePickup()),
    TripLogEntry.Walk(
        mode = StreetMode.WALK,
        durationMinutes = 2,
        distanceMeters = 140.0,
        isTransfer = false,
        steps = listOf(LogStep("Head west on Denny Way", distanceMeters = 140.0))
    ),
    TripLogEntry.Terminal(kind = TerminalKind.ARRIVE, time = ServerTime(17 * 60_000L), place = "Seattle Center")
)

@Preview(showBackground = true, widthDp = 400, heightDp = 420, name = "Bikeshare itinerary · walk, ride, walk")
@Preview(
    showBackground = true,
    widthDp = 400,
    heightDp = 420,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Bikeshare itinerary · walk, ride, walk, dark"
)
@Composable
private fun BikeshareItineraryPreview() {
    // The ride between its neighbours — where the operator chip has to hold its own against the walk
    // rows above and below it without swamping them.
    LegsPreviewFrame(previewBikeshareItinerary())
}

@Preview(showBackground = true, widthDp = 400, name = "Bikeshare leg · docked, unknown operator")
@Composable
private fun BikeshareLegDockedPreview() {
    // The other end of the range: a network the catalog has never heard of, wearing its raw id on a
    // neutral chip, with a dock to name and nothing to link to. Nothing here is invented for it.
    LegPreviewFrame(
        previewRentalLeg(
            RentalPickup(
                operator = RentalOperators.of("some_city_bikes"),
                vehicle = null,
                stationName = "Pine St & 3rd Ave",
                rangeMeters = null,
                link = null
            )
        )
    )
}
