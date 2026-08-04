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

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * [rideCoveringLegs] — how a route label tapped on the map (#2101) finds the drawer entry for the ride it
 * names. The map knows an itinerary only by leg index; the drawer knows a ride, which may be several legs
 * (a folded interline, #2000). This is the join between them, and getting it wrong focuses the wrong ride
 * rather than failing visibly, so each way the two can disagree is pinned here.
 */
class RideCoveringLegsTest {

    private val start = TripLogEntry.Terminal(TerminalKind.START, ServerTime(0L), "Origin")
    private val arrive = TripLogEntry.Terminal(TerminalKind.ARRIVE, ServerTime(40 * 60_000L), "Destination")

    private val walk = TripLogEntry.Walk(
        mode = StreetMode.WALK,
        durationMinutes = 4,
        distanceMeters = 320.0,
        isTransfer = false,
        steps = emptyList(),
        legIndices = setOf(0)
    )

    private fun ride(shortName: String, legIndices: Set<Int>) = TripLogEntry.Transit(
        routeShortName = shortName,
        routeDisplayName = "Route $shortName",
        mode = TransitMode.BUS,
        routeColorHex = null,
        headsign = "Downtown",
        reachStopTime = ServerTime(3 * 60_000L),
        boardTime = ServerTime(4 * 60_000L),
        exitTime = ServerTime(20 * 60_000L),
        durationMinutes = 16,
        rideEvents = emptyList(),
        routeLeg = RouteLegRef("1_$shortName", "Downtown", null, null),
        legIndices = legIndices
    )

    @Test
    fun `a label names the ride whose legs it carries`() {
        val eight = ride("8", setOf(1))
        val fortyNine = ride("49", setOf(3))
        val directions = listOf(start, walk, eight, walk, fortyNine, arrive)

        assertSame(eight, directions.rideCoveringLegs(setOf(1)))
        assertSame(fortyNine, directions.rideCoveringLegs(setOf(3)))
    }

    @Test
    fun `every leg of a folded interline finds the one ride it is part of`() {
        // The rider never gets off, so legs 1 and 2 are a single drawer entry — but the vehicle runs as a
        // different route after the seam, so the map labels each leg separately ("5", then "12"). Either
        // label, naming only its own leg, has to reach the whole ride: that is the tap the rider means.
        val chain = ride("5", setOf(1, 2))
        val directions = listOf(start, chain, arrive)

        assertSame(chain, directions.rideCoveringLegs(setOf(1)))
        assertSame(chain, directions.rideCoveringLegs(setOf(2)))
    }

    @Test
    fun `a label shared by two rides of one route resolves to the earlier`() {
        // One label is one tap target, so the itinerary that boards the 40 twice can only lead somewhere
        // once: the ride the rider reaches first.
        val first = ride("40", setOf(1))
        val second = ride("40", setOf(5))
        val directions = listOf(start, first, walk, second, arrive)

        assertSame(first, directions.rideCoveringLegs(setOf(1, 5)))
    }

    @Test
    fun `legs this itinerary does not have name no ride`() {
        // Nothing to focus, and nothing is focused — the caller has a null to act on rather than a
        // nearest-match ride the rider never tapped.
        val directions = listOf(start, ride("8", setOf(1)), arrive)

        assertNull(directions.rideCoveringLegs(setOf(7)))
        assertNull(emptyList<TripLogEntry>().rideCoveringLegs(setOf(1)))
    }

    @Test
    fun `an on-street leg is not a ride`() {
        // Walks carry leg indices too, but the map draws no label on one, so a tap can never name one —
        // and if it somehow did, it is not a ride to focus.
        val directions = listOf(start, walk, arrive)

        assertNull(directions.rideCoveringLegs(setOf(0)))
    }
}
