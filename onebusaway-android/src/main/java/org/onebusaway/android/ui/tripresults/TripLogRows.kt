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

import androidx.compose.ui.graphics.Color
import org.onebusaway.android.ui.compose.components.RouteLineColors

/**
 * The trip log's **row model** — the flat, one-row-per-line shape [TripResultsList] renders, derived
 * from the [TripLogEntry] timeline by [flattenLog].
 *
 * Kept apart from the renderer, and free of Android (only Compose's pure-Kotlin [Color] value class),
 * because the spine's colour-flip logic is the fiddliest part of the timeline and is worth testing on
 * the JVM. The renderer resolves colours — which needs `android.graphics` to parse the GTFS hex — and
 * hands them in via [flattenLog]'s `neutral` / `rideColors` parameters.
 */

/** A drawn spine segment: its [color] and whether it's dashed (a walk) or solid (a ride). */
internal data class RailSeg(val color: Color, val dashed: Boolean)

/**
 * A row's slice of the faint band uniting its leg. [first]/[last] mark the leg's outermost rows — the
 * only ones whose corners are rounded, so the per-row slices read as one continuous shape.
 */
internal data class BandEdge(val color: Color, val first: Boolean, val last: Boolean)

/** What a single timeline row shows. */
internal sealed interface RowContent {
    data class Terminal(val entry: TripLogEntry.Terminal) : RowContent
    data class WalkHeader(val entry: TripLogEntry.Walk) : RowContent
    data class Step(val step: LogStep) : RowContent

    /** The distance travelled between one walk maneuver and the next — a nodeless row on the spine. */
    data class StepDistance(val distanceMeters: Double) : RowContent
    data class BoardHeader(val entry: TripLogEntry.Transit) : RowContent
    data class Stop(val stop: LogStop) : RowContent
    data class Transition(val transition: InterlineTransition) : RowContent
    data class ExitNode(val entry: TripLogEntry.Transit) : RowContent
}

/**
 * One rendered row: its [content], the parent leg (via [entryIndex]), the spine above/below its node
 * ([top]/[bottom]), the [nodeColors] the node's route-coloured parts use (resolved once per leg, not
 * per node), and the leg's [band] slice (null for a terminal, which has no band).
 *
 * [expandable] and [expanded] let a header row draw its chevron, label its tap and decide whether to
 * toggle at all without re-deriving from the entry — this is where the "does expanding reveal
 * anything?" decision is *made* (below, where the minor rows are emitted), so it is also where the
 * renderer should read it, rather than each call site guessing with its own predicate.
 *
 * [key] is stable across expand/collapse, so a lazy list can match rows by identity and keep a row's
 * subcomposition — notably a board row's live ETA session — alive when a leg above it opens.
 */
internal data class LogRowModel(
    val entryIndex: Int,
    val content: RowContent,
    val top: RailSeg?,
    val bottom: RailSeg?,
    val nodeColors: RouteLineColors,
    val band: BandEdge?,
    val key: Long,
    val expandable: Boolean = false,
    val expanded: Boolean = false
)

/** Band tints, as an alpha over the leg's own colour. Walks are the fainter of the two (no hue). */
private const val WALK_BAND_ALPHA = 0.07f
private const val RIDE_BAND_ALPHA = 0.08f

/**
 * Flattens the [entries] into rows with the spine coloured. Each node's [LogRowModel.bottom] is the
 * travel *leaving* it (a walk stays dashed-neutral through its steps; a ride stays route-coloured
 * board→stops→exit; a leg's exit hands off to the next leg's colour), and [LogRowModel.top] chains from
 * the previous node's bottom — so the colour flips exactly at each node, and a walk-to-board node reads
 * neutral above and route colour below.
 *
 * Legs in [expandedEntries] (by index into [entries]) also emit their minor events: a walk's turn steps
 * with the distance between each pair, a ride's intermediate stops. A ride's stay-aboard route changes
 * are emitted *whether or not* it is expanded — they instruct the rider ("stay on board, it becomes
 * route X") rather than merely annotating the ride — and keep their place among the stops, so a folded
 * interline chain's post-seam stops stay on the far side of their seam.
 *
 * @param neutral the colour for walk segments and their nodes
 * @param rideColors the surface-legible line + glyph colours for a ride (see `routeLineColors`)
 */
internal fun flattenLog(
    entries: List<TripLogEntry>,
    expandedEntries: Set<Int>,
    neutral: RouteLineColors,
    rideColors: (TripLogEntry.Transit) -> RouteLineColors
): List<LogRowModel> {
    val rows = ArrayList<LogRowModel>()
    var prevBottom: RailSeg? = null
    // Distinguishes rows within one leg, so a row's key stays put as legs above it expand.
    var ordinalInLeg = 0
    fun push(
        i: Int,
        content: RowContent,
        bottom: RailSeg?,
        nodeColors: RouteLineColors,
        band: Color?,
        expandable: Boolean = false,
        expanded: Boolean = false
    ) {
        if (rows.lastOrNull()?.entryIndex != i) ordinalInLeg = 0
        rows += LogRowModel(
            entryIndex = i,
            content = content,
            top = prevBottom,
            bottom = bottom,
            nodeColors = nodeColors,
            // Edges are filled in below, once the whole run of a leg's rows is known.
            band = band?.let { BandEdge(it, first = false, last = false) },
            key = i.toLong() shl 32 or (ordinalInLeg++).toLong(),
            expandable = expandable,
            expanded = expanded
        )
        prevBottom = bottom
    }

    // The segment travelled when leaving the node *after* this one — null when there is no next leg to
    // travel on (the Arrive terminal), so the spine stops there.
    fun leadingToNext(next: TripLogEntry?): RailSeg? = when (next) {
        is TripLogEntry.Walk -> RailSeg(neutral.line, dashed = true)
        is TripLogEntry.Transit -> RailSeg(rideColors(next).line, dashed = false)
        else -> null
    }

    entries.forEachIndexed { i, entry ->
        val expanded = i in expandedEntries
        when (entry) {
            is TripLogEntry.Terminal -> {
                // Only the Start terminal leads anywhere; Arrive ends the trip, so its spine stops.
                val bottom = if (entry.kind == TerminalKind.START) leadingToNext(entries.getOrNull(i + 1)) else null
                push(i, RowContent.Terminal(entry), bottom, nodeColors = neutral, band = null)
            }

            is TripLogEntry.Walk -> {
                val seg = RailSeg(neutral.line, dashed = true)
                val band = neutral.line.copy(alpha = WALK_BAND_ALPHA)
                push(i, RowContent.WalkHeader(entry), seg, neutral, band, entry.steps.isNotEmpty(), expanded)
                if (expanded) {
                    entry.steps.forEach { step ->
                        push(i, RowContent.Step(step), seg, neutral, band)
                        // A step's distance is the travel from *this* maneuver to the next, so it reads as
                        // a delta row sitting between the two instructions.
                        if (step.distanceMeters > 0.0) {
                            push(i, RowContent.StepDistance(step.distanceMeters), seg, neutral, band)
                        }
                    }
                }
            }

            is TripLogEntry.Transit -> {
                val colors = rideColors(entry) // the leg's colours, resolved once for band + every node
                val ride = RailSeg(colors.line, dashed = false)
                val band = colors.line.copy(alpha = RIDE_BAND_ALPHA)
                // Stops are what expansion reveals; a seam shows either way, so it alone doesn't make
                // the leg expandable.
                val hasStops = entry.rideEvents.any { it is RideEvent.Stop }
                push(i, RowContent.BoardHeader(entry), ride, colors, band, hasStops, expanded)
                entry.rideEvents.forEach { event ->
                    when (event) {
                        is RideEvent.Stop -> if (expanded) push(i, RowContent.Stop(event.stop), ride, colors, band)
                        is RideEvent.Transition -> push(i, RowContent.Transition(event.transition), ride, colors, band)
                    }
                }
                push(i, RowContent.ExitNode(entry), leadingToNext(entries.getOrNull(i + 1)), colors, band)
            }
        }
    }
    return rows.markBandEdges()
}

/**
 * Rounds only each leg's outermost banded rows, so the per-row slices read as one continuous shape.
 * Patches the rows already built rather than rebuilding the list — interior rows are pushed with the
 * flags they keep, so only the two boundary rows per leg change.
 */
private fun MutableList<LogRowModel>.markBandEdges(): List<LogRowModel> {
    for (i in indices) {
        val band = this[i].band ?: continue
        val first = getOrNull(i - 1)?.entryIndex != this[i].entryIndex
        val last = getOrNull(i + 1)?.entryIndex != this[i].entryIndex
        if (first || last) this[i] = this[i].copy(band = band.copy(first = first, last = last))
    }
    return this
}
