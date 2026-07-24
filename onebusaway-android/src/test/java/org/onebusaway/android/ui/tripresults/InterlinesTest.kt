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
import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegAlternative
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace

/**
 * JVM tests for [Interlines]: the pure analysis that folds a self-interline seam away and keeps a
 * cross-route stay-aboard interline as one chain with a route-change transition (#2000) — and, on top
 * of it, which rides may be offered an interchangeable route ([substitutableRoutes], #2010).
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
        assertEquals(listOf("12"), badgeNames(listOf(transit("12"), transit("12", interline = true))))
        assertEquals(listOf("10", "12"), badgeNames(listOf(transit("10"), transit("12", interline = true))))
        assertEquals(listOf("8", "48"), badgeNames(listOf(transit("8"), walk, transit("48"))))
    }

    /** A ride's badge names the routes it can be taken on, not just the planned one (#2010). */
    @Test
    fun routeBadges_nameTheLegsInterchangeableRoutes() {
        val legs = listOf(transit("1 Line"))
        val badges = Interlines.routeBadges(legs, listOf(listOf(interchangeable("2 Line"))))

        assertEquals(listOf(listOf("1 Line", "2 Line")), badges.map { badge -> badge.routes.map { it.shortName } })
    }

    /**
     * A stay-aboard interline rides past its own alight stop (#2000), so another route between that
     * leg's two stops isn't a substitute for the ride — the offer is dropped for those legs (#2010).
     */
    @Test
    fun substitutableRoutes_dropsTheOfferOnAnInterlinedRide() {
        val interlined = TripItinerary(legs = listOf(withAlternative("10"), withAlternative("12", interline = true)))
        val standalone = TripItinerary(legs = listOf(withAlternative("10")))

        assertEquals(listOf(emptyList<String>(), emptyList()), interlined.substitutableRoutes().map { routes -> routes.map { it.displayName } })
        assertEquals(listOf(listOf("99")), standalone.substitutableRoutes().map { routes -> routes.map { it.displayName } })
    }

    private fun badgeNames(legs: List<TripLeg>): List<String> = Interlines.routeBadges(legs, legs.map { emptyList() }).map { it.routes.single().shortName }

    private fun interchangeable(shortName: String) = InterchangeableRoute(
        routeId = "route_$shortName",
        shortName = shortName,
        routeColor = null,
        agencyId = null,
        agencyName = null,
        headsign = null
    )

    /** A transit leg between two stops, with one same-stops, same-ride-time alternative on route 99. */
    private fun withAlternative(route: String, interline: Boolean = false) = transit(route, interline).copy(
        from = TripPlace(stopId = BOARD_STOP),
        to = TripPlace(stopId = ALIGHT_STOP),
        alternatives = listOf(
            TripLegAlternative(
                routeId = "99",
                routeShortName = "99",
                fromStopId = BOARD_STOP,
                toStopId = ALIGHT_STOP
            )
        )
    )

    private companion object {
        const val BOARD_STOP = "1_500"
        const val ALIGHT_STOP = "1_501"
    }
}
