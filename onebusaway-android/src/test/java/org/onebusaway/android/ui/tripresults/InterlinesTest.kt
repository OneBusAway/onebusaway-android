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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode

/**
 * JVM tests for [Interlines]: the pure analysis that folds a self-interline seam away and keeps a
 * cross-route stay-aboard interline as one chain with a route-change transition (#2000).
 */
class InterlinesTest {

    private fun transit(route: String, interline: Boolean = false) = TripLeg(
        mode = TripMode.BUS,
        routeId = route,
        routeShortName = route,
        interlineWithPreviousLeg = interline
    )

    private val walk = TripLeg(mode = TripMode.WALK)

    @Test
    fun selfInterline_foldsToOneChainWithNoTransition() {
        // Inbound 12 becomes outbound 12 on the same vehicle: one continuous ride, seam hidden.
        val chains = Interlines.chains(listOf(transit("12"), transit("12", interline = true)))
        assertEquals(1, chains.size)
        assertEquals(Interlines.Chain(leaderIndex = 0, alightIndex = 1, transitionLegIndices = emptyList()), chains.single())
    }

    @Test
    fun crossRouteInterline_isOneChainWithATransitionAtTheSeam() {
        // Board the 10, the vehicle becomes the 12 mid-ride: one chain, one route-change transition.
        val chains = Interlines.chains(listOf(transit("10"), transit("12", interline = true)))
        assertEquals(
            Interlines.Chain(leaderIndex = 0, alightIndex = 1, transitionLegIndices = listOf(1)),
            chains.single()
        )
    }

    @Test
    fun chainAlightsAtTheLastLeg_mixedSelfAndCrossRoute() {
        // 10 -> 12 (change) -> 12 (self): one chain, alighting at leg 2, a single transition at leg 1.
        val chains = Interlines.chains(
            listOf(transit("10"), transit("12", interline = true), transit("12", interline = true))
        )
        assertEquals(
            Interlines.Chain(leaderIndex = 0, alightIndex = 2, transitionLegIndices = listOf(1)),
            chains.single()
        )
    }

    @Test
    fun nonInterlinedLegs_areSeparateChains() {
        val chains = Interlines.chains(listOf(transit("8"), walk, transit("48")))
        assertEquals(
            listOf(
                Interlines.Chain(0, 0, emptyList()),
                Interlines.Chain(2, 2, emptyList())
            ),
            chains
        )
    }

    @Test
    fun aChainNestedBetweenWalkLegs_isFoundWithCorrectSpan() {
        val chains = Interlines.chains(
            listOf(walk, transit("12"), transit("12", interline = true), walk)
        )
        assertEquals(Interlines.Chain(1, 2, emptyList()), chains.single())
    }

    @Test
    fun aLeadingInterlineFlagWithNoPredecessor_leadsNoChain() {
        // Malformed input (nothing precedes it to stay aboard from): it must not become a chain leader.
        assertEquals(emptyList<Interlines.Chain>(), Interlines.chains(listOf(transit("12", interline = true))))
    }

    @Test
    fun routeBadges_foldSelfInterlineButKeepCrossRoute() {
        assertEquals(
            listOf("12"),
            Interlines.routeBadges(listOf(transit("12"), transit("12", interline = true))).map { it.shortName }
        )
        assertEquals(
            listOf("10", "12"),
            Interlines.routeBadges(listOf(transit("10"), transit("12", interline = true))).map { it.shortName }
        )
        assertEquals(
            listOf("8", "48"),
            Interlines.routeBadges(listOf(transit("8"), walk, transit("48"))).map { it.shortName }
        )
    }
}
