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

import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.map.itineraryTransitColors

/**
 * Builds the [ModeSymbol] sequence an itinerary option card shows (#2047) — the whole trip in travel
 * order, on-street legs included: `[walk] [4] [walk] [40] [walk]` rather than `[4] [40]`.
 *
 * This replaced a split vocabulary, where a transit option was a row of route roundels and a
 * non-transit one was prose ("Walk", "Bikeshare") assembled by `DirectionsGenerator`. One language
 * means a walking option and the walk *inside* a transit option are the same symbol, and a card says
 * how a rider gets to the bus rather than only which bus it is.
 *
 * Pure (no `Context`), like [Interlines] and [rideBadge], so `ModeSymbolsTest` covers the sequencing
 * directly.
 */
internal object ModeSymbols {

    /**
     * How far a rider may go on an on-street leg before the card draws its glyph at all. Under this,
     * the leg is the connective tissue of a transfer — staying put at the same stop, crossing the
     * street, or walking the length of a transit centre — and drawing it says "you walk here" about
     * something the rider would not call walking, while pushing the routes (the reason to read the
     * card) off a card only ~110dp wide.
     *
     * 500 ft (152.4 m) is the length of Lynnwood Transit Center, i.e. about a city block: the
     * agreed-on starting point from #2047, and a product judgement about drawing rather than an
     * inference about the data — the leg's distance is stated on the wire, and nothing here guesses
     * what kind of leg it is. Tune here.
     */
    const val NEGLIGIBLE_STREET_METERS = 152.4

    /**
     * The card's symbols for one itinerary, in leg order. [substitutable] is index-aligned to [legs]
     * and supplies each transit leg's interchangeable routes (#2010), exactly as
     * [TripResultsRepository][DefaultTripResultsRepository] hands them to the drawer's badges.
     *
     * A stay-aboard interline (#2000) is **one** symbol, not one per leg and not one per route: the
     * rider boards once, so the card draws one roundel, and the routes the vehicle runs as are joined
     * inside it by chevrons — `[5 > 12]` (#2049). Drawing them as separate roundels made an interline
     * read exactly like a transfer, which is the one thing it isn't. It reads the same
     * [Interlines.chains] the directions do, so the card and the drawer can't disagree about how many
     * rides a trip has. Pseudo-legs (`BOARDING`/`ALIGHTING`/`TRANSFER`) are not travel and draw
     * nothing.
     */
    fun forLegs(legs: List<TripLeg>, substitutable: List<List<InterchangeableRoute>>): List<ModeSymbol> {
        // Stated rather than defended against: a shorter list read with getOrNull would quietly badge a
        // ride with fewer routes than it can be taken on — the rider is told to wait for the 1 Line when
        // the 2 Line would also do — and nothing downstream could tell that from a leg that genuinely has
        // no alternatives. Misalignment is a caller bug, so say so where it happens.
        require(substitutable.size == legs.size) {
            "substitutable must be index-aligned to legs (${substitutable.size} vs ${legs.size})"
        }
        val mapRouteColors = itineraryTransitColors(
            legs,
            substitutable.flatten().map(InterchangeableRoute::routeId)
        )
        // One badge per ride, at the leg it's boarded at; every other transit leg is a continuation the
        // rider never acts on, and is already named inside its leader's badge.
        val rides = Interlines.chains(legs).associate { chain ->
            chain.leaderIndex to rideBadge(
                legs,
                chain,
                substitutable[chain.leaderIndex],
                mapRouteColors
            )
        }
        val symbols = legs.mapIndexedNotNull { i, leg ->
            when {
                leg.mode?.isTransit == true -> rides[i]?.let { ModeSymbol.Transit(it) }
                leg.mode?.isOnStreetNonTransit == true -> leg.streetSymbolOrNull()
                else -> null
            }
        }
        // Two street legs in a row are one act to the rider ("walk, then walk"), so they draw one glyph.
        val collapsed = symbols.filterConsecutiveDuplicateStreets()
        // "Unless it's the only icon": a trip that is nothing but a short stroll still has to say what
        // it is, so a sequence emptied by the threshold falls back to its longest street leg.
        return collapsed.ifEmpty { listOfNotNull(legs.longestStreetLeg()?.let { ModeSymbol.Street(it.streetMode()) }) }
    }

    /** The leg's symbol, or null when it's too short to be worth drawing ([NEGLIGIBLE_STREET_METERS]). */
    private fun TripLeg.streetSymbolOrNull(): ModeSymbol.Street? = if (distance >= NEGLIGIBLE_STREET_METERS) ModeSymbol.Street(streetMode()) else null

    /** The longest on-street leg, i.e. the one a street-only trip is really about; null if there is none. */
    private fun List<TripLeg>.longestStreetLeg(): TripLeg? = filter { it.mode?.isOnStreetNonTransit == true }.maxByOrNull { it.distance }

    private fun List<ModeSymbol>.filterConsecutiveDuplicateStreets(): List<ModeSymbol> = filterIndexed { i, symbol ->
        symbol !is ModeSymbol.Street || getOrNull(i - 1) != symbol
    }
}

/**
 * How the rider covers an on-street leg — shared by the option card's symbols and the directions
 * timeline's [TripLogEntry.Walk], so a leg can't be a rented bike on one and a plain one on the other.
 * Mirrors the generator's own action pick in [DirectionsGenerator.generateNonTransitDirections][
 * org.onebusaway.android.directions.util.DirectionsGenerator] — bicycle and car each get their own
 * verb, everything else walks — so the timeline's header can't disagree with the step text the same
 * leg produced.
 *
 * A `BICYCLE` leg is a *rented* bike when either of its endpoints is a vehicle-rental place: OTP
 * populates `rentalVehicle`/`vehicleRentalStation` on exactly those places, which the adapter turns
 * into [TripVertexType.BIKESHARE] (see `Otp2PlanAdapters.inferVertexType`), so reading it is a
 * structural fact rather than a guess about the leg. Either endpoint counts, not just the pick-up: a
 * dockless rental can be left at a plain street corner and a docked one starts at a station, and both
 * are the same act to the rider.
 */
internal fun TripLeg.streetMode(): StreetMode = when (mode) {
    TripMode.BICYCLE -> if (from.vertexType == TripVertexType.BIKESHARE || to.vertexType == TripVertexType.BIKESHARE) {
        StreetMode.BIKESHARE
    } else {
        StreetMode.BIKE
    }
    TripMode.CAR -> StreetMode.CAR
    else -> StreetMode.WALK
}
