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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.compose.components.RouteLineColors

/**
 * JVM tests for [flattenLog] — the timeline's spine logic: which rows a leg emits when collapsed vs.
 * expanded, and how the connector colour flips from node to node. The renderer resolves the actual
 * colours (which needs `android.graphics`), so the two sentinel colours here stand in for "walk" and
 * "ride" and make each segment's provenance readable in an assertion.
 */
class TripLogRowsTest {

    private val neutral = RouteLineColors(Color(0xFF888888), Color.White)
    private val ride = RouteLineColors(Color(0xFF1B6EF3), Color.White)

    private fun flatten(entries: List<TripLogEntry>, expanded: Set<Int> = emptySet()) = flattenLog(entries, expanded, neutral) { ride }

    private val start = TripLogEntry.Terminal(TerminalKind.START, ServerTime(0L), "Origin Plaza")
    private val arrive = TripLogEntry.Terminal(TerminalKind.ARRIVE, ServerTime(32 * 60_000L), "Alaska Junction")

    private val walk = TripLogEntry.Walk(
        durationMinutes = 4,
        distanceMeters = 320.0,
        isTransfer = false,
        steps = listOf(
            LogStep("Turn left onto Pike St", distanceMeters = 61.0),
            // No distance — its "travel to the next maneuver" row must be skipped, not rendered as "0".
            LogStep("Arrive at Pine St & 3rd Ave", distanceMeters = 0.0)
        )
    )

    private val seam = InterlineTransition("49", "Downtown", RouteStopRef("1_600", "600", "Seam Stop", null))

    private fun transit(events: List<RideEvent>) = TripLogEntry.Transit(
        routeShortName = "8",
        routeDisplayName = "Route 8",
        routeColorHex = "1B6EF3",
        headsign = "Rainier Beach",
        boardTime = ServerTime(4 * 60_000L),
        exitTime = ServerTime(20 * 60_000L),
        durationMinutes = 16,
        realtime = RealtimeState.OnTime,
        rideEvents = events,
        routeLeg = RouteLegRef("1_100", "Rainier Beach", null, null)
    )

    private val plainRide = transit(listOf(RideEvent.Stop(LogStop("Capitol Hill Station"))))

    private fun List<LogRowModel>.kinds() = map { it.content::class.simpleName }

    @Test
    fun collapsedLeg_emitsOnlyItsHeader_andARideAlwaysItsExit() {
        val rows = flatten(listOf(start, walk, plainRide, arrive))

        assertEquals(
            listOf("Terminal", "WalkHeader", "BoardHeader", "ExitNode", "Terminal"),
            rows.kinds()
        )
    }

    @Test
    fun expandingAWalk_revealsEachStep_withTheDistanceBetweenManeuvers() {
        val rows = flatten(listOf(start, walk, arrive), expanded = setOf(1))

        // The first step's distance is the travel to the second, so it sits between them; the last step
        // has no onward distance and so contributes no interval row.
        assertEquals(
            listOf("Terminal", "WalkHeader", "Step", "StepDistance", "Step", "Terminal"),
            rows.kinds()
        )
        assertTrue(rows.single { it.content is RowContent.WalkHeader }.expanded)
    }

    @Test
    fun expandingARide_revealsItsStops_butASeamShowsEitherWay() {
        val chain = transit(
            listOf(
                RideEvent.Stop(LogStop("Capitol Hill Station")),
                RideEvent.Transition(seam),
                RideEvent.Stop(LogStop("Rainier Beach Station"))
            )
        )

        // Collapsed: the stops hide, but the "stay on board — it becomes route 49" instruction does not.
        assertEquals(
            listOf("BoardHeader", "Transition", "ExitNode"),
            flatten(listOf(chain)).kinds()
        )
        // Expanded: the stops appear, each on the correct side of the seam it follows.
        assertEquals(
            listOf("BoardHeader", "Stop", "Transition", "Stop", "ExitNode"),
            flatten(listOf(chain), expanded = setOf(0)).kinds()
        )
    }

    @Test
    fun theSpineFlipsColourExactlyAtEachNode() {
        val rows = flatten(listOf(start, walk, plainRide, arrive))
        val (startRow, walkRow, boardRow, exitRow, arriveRow) = rows

        // The trip starts at the origin dot: nothing above it, the walk's dashed neutral below.
        assertNull(startRow.top)
        assertEquals(RailSeg(neutral.line, dashed = true), startRow.bottom)

        // Each node's top chains from the previous node's bottom, so the colour changes only at nodes.
        assertEquals(startRow.bottom, walkRow.top)
        assertEquals(RailSeg(neutral.line, dashed = true), walkRow.bottom)

        // The board node is the flip: dashed neutral above, solid route colour below.
        assertEquals(RailSeg(neutral.line, dashed = true), boardRow.top)
        assertEquals(RailSeg(ride.line, dashed = false), boardRow.bottom)

        // The ride stays route-coloured to its exit, which then hands off to the *next* leg — here the
        // Arrive terminal, so nothing continues past it.
        assertEquals(RailSeg(ride.line, dashed = false), exitRow.top)
        assertNull(exitRow.bottom)
        assertNull(arriveRow.bottom)
    }

    @Test
    fun aRidesNodesAllShareOneResolvedColour() {
        val rows = flatten(listOf(plainRide), expanded = setOf(0))

        assertTrue(rows.all { it.nodeColors == ride })
    }

    @Test
    fun onlyALegsOuterRowsRoundTheirBandCorners() {
        val rows = flatten(listOf(start, walk, arrive), expanded = setOf(1))
        val banded = rows.filter { it.band != null }

        // A terminal stands alone with no band at all.
        assertTrue(rows.filter { it.content is RowContent.Terminal }.all { it.band == null })
        // The walk's rows are one continuous band: rounded at the run's two ends, square in between.
        assertEquals(4, banded.size)
        assertTrue(banded.first().band!!.first)
        assertTrue(banded.last().band!!.last)
        assertTrue(banded.drop(1).none { it.band!!.first })
        assertTrue(banded.dropLast(1).none { it.band!!.last })
    }

    @Test
    fun adjacentLegsGetTheirOwnBands() {
        val rows = flatten(listOf(walk, plainRide))
        val walkBand = rows.first { it.content is RowContent.WalkHeader }.band!!
        val boardBand = rows.first { it.content is RowContent.BoardHeader }.band!!

        // Back-to-back legs must not merge into one band — each run closes at its own boundary.
        assertTrue(walkBand.first && walkBand.last)
        assertTrue(boardBand.first)
        assertFalse(boardBand.last) // the ride's band continues to its exit row
    }

    @Test
    fun aLegIsExpandableOnlyWhenExpandingWouldRevealSomething() {
        // A seam always shows, so a ride carrying only a seam has nothing to unfold; steps and stops do.
        val seamOnly = transit(listOf(RideEvent.Transition(seam)))
        val bareWalk = walk.copy(steps = emptyList())
        val rows = flatten(listOf(bareWalk, seamOnly, plainRide, walk))

        assertFalse(rows.single { it.content is RowContent.WalkHeader && it.entryIndex == 0 }.expandable)
        assertFalse(rows.single { it.entryIndex == 1 && it.content is RowContent.BoardHeader }.expandable)
        assertTrue(rows.single { it.entryIndex == 2 && it.content is RowContent.BoardHeader }.expandable)
        assertTrue(rows.single { it.entryIndex == 3 && it.content is RowContent.WalkHeader }.expandable)
    }

    @Test
    fun rowKeysAreUnique_andSurviveALegAboveExpanding() {
        val entries = listOf(start, walk, plainRide, arrive)
        val collapsed = flatten(entries)
        val walkOpen = flatten(entries, expanded = setOf(1))

        assertEquals("keys must be unique", walkOpen.size, walkOpen.map { it.key }.toSet().size)
        // Opening the walk inserts rows above the ride, but the ride's rows keep their identity — so the
        // lazy list reuses their subcompositions (the board row's live ETA session) instead of rebuilding.
        val keysOf = { rows: List<LogRowModel> -> rows.filter { it.entryIndex >= 2 }.map { it.key } }
        assertEquals(keysOf(collapsed), keysOf(walkOpen))
    }

    @Test
    fun anEmptyLogHasNoRows() {
        assertTrue(flatten(emptyList()).isEmpty())
    }
}
