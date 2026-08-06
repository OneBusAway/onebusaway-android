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
import org.onebusaway.android.directions.model.TripAlert
import org.onebusaway.android.directions.model.TripAlertSeverity
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.ui.compose.components.AlertSeverity

/**
 * JVM tests for [ModeSymbols]: an itinerary read as one left-to-right symbol sequence — the on-street
 * legs between the rides included, the negligible ones dropped (#2047) — with the ride-folding rules
 * of #2000 (stay-aboard interlines) and #2010 (interchangeable routes) intact.
 *
 * Plus the other card fact this file derives from the same legs, by the same [streetMode] split: how
 * far the trip covers on each street mode ([streetDistancesMeters], #2122).
 */
class ModeSymbolsTest {

    private fun transit(
        route: String,
        interline: Boolean = false,
        alerts: List<TripAlert> = emptyList()
    ) = TripLeg(
        mode = TripMode.BUS,
        routeId = route,
        routeShortName = route,
        interlineWithPreviousLeg = interline,
        alerts = alerts
    )

    private fun walk(meters: Double) = TripLeg(mode = TripMode.WALK, distance = meters)

    /**
     * A bicycle leg. [rented] is OTP's own `rentedBike` for it — the whole signal that separates a
     * shared bike from the rider's own (#2159). [rentalEndpoint] independently marks the leg's `to` as
     * a vehicle-rental place, so the tests can put the two in conflict; nothing but [rented] decides.
     */
    private fun bike(meters: Double, rented: Boolean = false, rentalEndpoint: Boolean = false) = TripLeg(
        mode = TripMode.BICYCLE,
        rentedVehicle = rented,
        distance = meters,
        to = TripPlace(vertexType = if (rentalEndpoint) TripVertexType.BIKESHARE else TripVertexType.NORMAL)
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

    /**
     * The flag decides, not the leg's endpoints (#2159). A ride OTP flagged is a rented bike even where
     * it starts and ends nowhere in particular — a dockless one can be picked up and left at a plain
     * street corner — and a ride it didn't flag stays the rider's own bike even when it happens to end
     * at a rental place, which is a bike parked there rather than the one being ridden.
     */
    @Test
    fun theRentalFlagDecides_notTheLegsEndpoints() {
        assertEquals(listOf(StreetMode.BIKESHARE), symbolsOf(listOf(bike(FAR, rented = true))))
        assertEquals(listOf(StreetMode.BIKE), symbolsOf(listOf(bike(FAR, rentalEndpoint = true))))
        assertEquals(
            listOf(StreetMode.BIKESHARE),
            symbolsOf(listOf(bike(FAR, rented = true, rentalEndpoint = true)))
        )
    }

    /** Two street legs in a row are one act to the rider; the card must not stutter "walk, walk". */
    @Test
    fun consecutiveIdenticalStreetLegsCollapseToOneSymbol() {
        assertEquals(listOf(StreetMode.WALK, listOf("8")), symbolsOf(listOf(longWalk, longWalk, transit("8"))))
    }

    /**
     * Straight off [Interlines.chains]: a self-interline is one ride on one route, a cross-route one is
     * one ride on two. One symbol either way — the rider boards once, so the card draws one roundel, and
     * the routes ridden are named inside it (#2049).
     */
    @Test
    fun anInterlinedRideIsOneSymbolNamingEveryRouteItRunsAs() {
        assertEquals(listOf(listOf("12")), symbolsOf(listOf(transit("12"), transit("12", interline = true))))
        // Deliberately a pair that natural name order would reverse, so this also pins that the ride
        // order survives the trip through the badge builder. What that order *means*, and that the badge
        // chevrons rather than offers a choice, is `RouteBadgesTest`'s to assert — `rideBadge` owns the
        // rule and this only checks that a card is handed the whole of it.
        assertEquals(listOf(listOf("12", "10")), symbolsOf(listOf(transit("12"), transit("10", interline = true))))
    }

    /** A transfer stays two symbols: two boardings, so two roundels with a walk (or a gap) between. */
    @Test
    fun aTransferBetweenTwoRidesStaysTwoSymbols() {
        assertEquals(
            listOf(listOf("10"), listOf("12")),
            symbolsOf(listOf(transit("10"), transit("12")))
        )
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

    /**
     * The card's metric lines (#2122): a total per street mode the trip actually uses, split the same
     * way the symbols are — so a bikeshare ride is measured as bikeshare rather than swelling the walk.
     */
    @Test
    fun aTripTotalsItsDistanceOnEachStreetModeSeparately() {
        assertEquals(
            mapOf(StreetMode.WALK to 900.0, StreetMode.BIKESHARE to 2000.0),
            listOf(
                walk(FAR),
                bike(2000.0, rented = true),
                walk(300.0),
                transit("8")
            ).streetDistancesMeters()
        )
    }

    /** A mode the trip never travels on has no line to draw, so it isn't reported as zero. */
    @Test
    fun aModeTheTripNeverUsesIsAbsentFromTheTotals() {
        assertEquals(mapOf(StreetMode.BIKE to FAR), listOf(bike(FAR)).streetDistancesMeters())
        assertEquals(emptyMap<StreetMode, Double>(), listOf(transit("8")).streetDistancesMeters())
    }

    /**
     * The totals count every on-street leg, including the ones too short to draw a symbol for: the
     * threshold says what is worth drawing as a step of the trip, not what the rider covers.
     */
    @Test
    fun aStreetLegTooShortForASymbolStillCountsTowardTheTotal() {
        assertEquals(
            mapOf(StreetMode.WALK to FAR + NEAR),
            listOf(walk(FAR), transit("8"), walk(NEAR)).streetDistancesMeters()
        )
    }

    // ---- Per-symbol alert markers (#2143) --------------------------------------------------------

    /** The alert tone marking each symbol, aligned to [symbolsOf]. */
    private fun alertsOf(legs: List<TripLeg>): List<AlertSeverity?> = ModeSymbols.forLegs(legs, legs.map { emptyList() }).map { it.alert }

    @Test
    fun onlyTheAlertedLegsSymbolIsMarked() {
        val legs = listOf(longWalk, transit("8", alerts = listOf(alert())), longWalk, transit("40"))

        assertEquals(listOf(null, AlertSeverity.WARNING, null, null), alertsOf(legs))
    }

    /** The marker is the loudest of the leg's alerts — a suspension can't be hidden by a notice. */
    @Test
    fun aLegShowsItsLoudestAlert() {
        val legs = listOf(
            transit(
                "8",
                alerts = listOf(
                    alert(TripAlertSeverity.INFO),
                    alert(TripAlertSeverity.SEVERE),
                    alert(TripAlertSeverity.WARNING)
                )
            )
        )

        assertEquals(listOf(AlertSeverity.ERROR), alertsOf(legs))
    }

    /**
     * A stay-aboard interline is one roundel (see the folding tests above), so an alert on the leg the
     * vehicle *continues* as still marks the ride the rider actually boards.
     */
    @Test
    fun anInterlinedRideIsMarkedByAnAlertOnAnyLegOfTheChain() {
        val legs = listOf(
            transit("5"),
            transit("12", interline = true, alerts = listOf(alert(TripAlertSeverity.SEVERE)))
        )

        assertEquals(listOf(listOf("5", "12")), symbolsOf(legs))
        assertEquals(listOf(AlertSeverity.ERROR), alertsOf(legs))
    }

    /**
     * Consecutive street legs collapse to one glyph, so the surviving glyph has to carry the alert the
     * collapsed leg brought — otherwise the merge silently drops it.
     */
    @Test
    fun aMergedRunOfWalksKeepsTheLoudestAlertAmongThem() {
        val legs = listOf(
            walk(FAR),
            walk(FAR).copy(alerts = listOf(alert(TripAlertSeverity.SEVERE))),
            walk(FAR).copy(alerts = listOf(alert(TripAlertSeverity.INFO)))
        )

        assertEquals(listOf(StreetMode.WALK), symbolsOf(legs))
        assertEquals(listOf(AlertSeverity.ERROR), alertsOf(legs))
    }

    /** The street-only fallback is still a leg, and still says when that leg has an alert. */
    @Test
    fun theShortStrollFallbackCarriesItsAlert() {
        val legs = listOf(walk(NEAR).copy(alerts = listOf(alert(TripAlertSeverity.SEVERE))))

        assertEquals(listOf(StreetMode.WALK), symbolsOf(legs))
        assertEquals(listOf(AlertSeverity.ERROR), alertsOf(legs))
    }

    private fun alert(severity: TripAlertSeverity = TripAlertSeverity.WARNING) = TripAlert(id = "a", header = "Heads up", severity = severity)

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
