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
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripVertexType

/**
 * JVM tests for [ModeSymbols]: an itinerary read as one left-to-right symbol sequence — the on-street
 * legs between the rides included, the negligible ones dropped (#2047) — with the ride-folding rules
 * of #2000 (stay-aboard interlines) and #2010 (interchangeable routes) intact.
 */
class ModeSymbolsTest {

    private fun transit(route: String, interline: Boolean = false) = TripLeg(
        mode = TripMode.BUS,
        routeId = route,
        routeShortName = route,
        interlineWithPreviousLeg = interline
    )

    private fun walk(meters: Double) = TripLeg(mode = TripMode.WALK, distance = meters)

    private fun bike(meters: Double, rented: Boolean = false) = TripLeg(
        mode = TripMode.BICYCLE,
        distance = meters,
        from = TripPlace(vertexType = if (rented) TripVertexType.BIKESHARE else TripVertexType.NORMAL)
    )

    /** The symbols as a readable sequence: a street glyph's mode, or a ride's route names. */
    private fun symbolsOf(legs: List<TripLeg>): List<Any> = ModeSymbols.forLegs(legs, legs.map { emptyList() }).map { it.describe() }

    private fun ModeSymbol.describe(): Any = when (this) {
        is ModeSymbol.Street -> mode
        is ModeSymbol.Transit -> badge.routes.map { it.shortName }
    }

    /** A walk over the threshold, so the sequencing tests aren't quietly testing the omission rule. */
    private val longWalk get() = walk(FAR)

    @Test
    fun aTransitTripReadsAsItsWalksAndItsRides_inTravelOrder() {
        assertEquals(
            listOf(StreetMode.WALK, listOf("8"), StreetMode.WALK, listOf("40"), StreetMode.WALK),
            symbolsOf(listOf(longWalk, transit("8"), longWalk, transit("40"), longWalk))
        )
    }

    /**
     * The point of the threshold: a transfer that is really "stay where you are" or "cross the street"
     * draws nothing, so the card keeps saying what a rider reads it for — which routes to ride.
     */
    @Test
    fun aNegligibleStreetLegDrawsNoSymbol() {
        assertEquals(
            listOf(listOf("8"), listOf("40")),
            symbolsOf(listOf(walk(NEAR), transit("8"), walk(NEAR), transit("40")))
        )
    }

    /** The threshold is inclusive: exactly 500 ft is a block's walk, and a block is worth drawing. */
    @Test
    fun aStreetLegExactlyAtTheThresholdDrawsItsSymbol() {
        assertEquals(
            listOf(StreetMode.WALK, listOf("8")),
            symbolsOf(listOf(walk(ModeSymbols.NEGLIGIBLE_STREET_METERS), transit("8")))
        )
    }

    /** "…unless it's the only icon": a trip that is nothing but a short stroll still has to say so. */
    @Test
    fun aStreetOnlyTripKeepsOneSymbolEvenBelowTheThreshold() {
        assertEquals(listOf(StreetMode.WALK), symbolsOf(listOf(walk(NEAR))))
        // The longest leg names the trip, so a stroll to a rented bike doesn't relabel the ride a walk.
        assertEquals(listOf(StreetMode.BIKESHARE), symbolsOf(listOf(walk(NEAR), bike(NEAR * 2, rented = true), walk(NEAR))))
    }

    @Test
    fun aWalkOnlyTripIsOneWalkGlyph() {
        assertEquals(listOf(StreetMode.WALK), symbolsOf(listOf(longWalk)))
    }

    /** A rented bike and the rider's own bike are different acts, so they get different symbols. */
    @Test
    fun aBikeLegSaysWhetherTheBikeIsRented() {
        assertEquals(
            listOf(StreetMode.WALK, StreetMode.BIKESHARE, StreetMode.WALK),
            symbolsOf(listOf(longWalk, bike(FAR, rented = true), longWalk))
        )
        assertEquals(listOf(StreetMode.BIKE), symbolsOf(listOf(bike(FAR))))
    }

    /** Two street legs in a row are one act to the rider; the card must not stutter "walk, walk". */
    @Test
    fun consecutiveIdenticalStreetLegsCollapseToOneSymbol() {
        assertEquals(listOf(StreetMode.WALK, listOf("8")), symbolsOf(listOf(longWalk, longWalk, transit("8"))))
    }

    /** Straight off [Interlines.chains]: a self-interline is one ride, a cross-route one is two. */
    @Test
    fun interlinedRidesBadgeTheRoutesActuallyRidden() {
        assertEquals(listOf(listOf("12")), symbolsOf(listOf(transit("12"), transit("12", interline = true))))
        assertEquals(listOf(listOf("10"), listOf("12")), symbolsOf(listOf(transit("10"), transit("12", interline = true))))
    }

    /** A ride's badge names the routes it can be taken on, not just the planned one (#2010). */
    @Test
    fun aRidesBadgeNamesTheLegsInterchangeableRoutes() {
        val legs = listOf(transit("1 Line"))
        val symbols = ModeSymbols.forLegs(legs, listOf(listOf(interchangeable("2 Line"))))

        assertEquals(
            listOf(listOf("1 Line", "2 Line")),
            symbols.filterIsInstance<ModeSymbol.Transit>().map { symbol -> symbol.badge.routes.map { it.shortName } }
        )
    }

    /** OTP's non-travel pseudo-legs are neither ridden nor walked, so they draw nothing. */
    @Test
    fun pseudoLegsDrawNothing() {
        assertEquals(
            listOf(listOf("8")),
            symbolsOf(listOf(TripLeg(mode = TripMode.BOARDING), transit("8"), TripLeg(mode = TripMode.ALIGHTING)))
        )
    }

    private fun interchangeable(shortName: String) = InterchangeableRoute(
        routeId = "route_$shortName",
        displayName = shortName,
        routeColor = null,
        agencyId = null,
        agencyName = null,
        headsign = null
    )

    private companion object {
        /** Comfortably under the threshold — a stop-to-stop shuffle. */
        const val NEAR = 40.0

        /** Comfortably over it — a walk a rider would call a walk. */
        const val FAR = 600.0
    }
}
