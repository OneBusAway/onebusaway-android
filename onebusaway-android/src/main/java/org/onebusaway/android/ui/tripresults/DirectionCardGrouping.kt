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

import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.geoPointOrNull

/**
 * Re-groups the legacy [DirectionsGenerator][org.onebusaway.android.directions.util.DirectionsGenerator]'s
 * flat, leg-ordered [Direction] list into **one [DirectionItem] card per itinerary leg**, so the drawer
 * reads as a leg at a time rather than a flat step list.
 *
 * The generator emits a predictable count per leg — 1 [Direction] for a non-transit (walk/bike/car)
 * leg, 2 for a transit leg (a "get on" board direction followed by a "get off" alight direction). This
 * mirrors that exact split leg-by-leg, walking the flat list with a cursor, so:
 * - a non-transit leg becomes one walk card (its turn steps as [DirectionItem.subItems]);
 * - a transit leg folds both directions into one card: the board direction supplies the card header,
 *   and the alight direction becomes the last sub-item after the intermediate stops.
 *
 * Each card carries its leg's decoded polyline in [DirectionItem.legPoints] (so a body tap can frame
 * the whole leg) and, for a transit leg, a [RouteLegRef] with the route/stop identity a later route
 * focus will consume.
 *
 * Pure (no `Context`) and so JVM-unit-testable — the generator, which needs resources for the step
 * text, has already run by the time this is called. The predicate that decides walk-vs-transit
 * ([TripMode.isOnStreetNonTransit][org.onebusaway.android.directions.model.TripMode]) is identical to
 * the generator's, so the cursor never drifts out of step with the flat list.
 */
object DirectionCardGrouping {

    /**
     * [routeLegRefs] is aligned to [legs] — the pre-resolved (OBA-id) route/stop identity for each
     * transit leg, or null for a non-transit leg. The repository resolves these (a suspend, network-
     * backed step) so this stays pure; a transit card just carries its ref for the UI.
     */
    fun groupByLeg(
        legs: List<TripLeg>,
        flatDirections: List<Direction>,
        routeLegRefs: List<RouteLegRef?>,
    ): List<DirectionItem> {
        val cards = ArrayList<DirectionItem>(legs.size)
        var cursor = 0
        legs.forEachIndexed { legIndex, leg ->
            val cardNumber = legIndex + 1
            val legPoints = leg.decodedPoints()
            if (leg.mode?.isOnStreetNonTransit == true) {
                val walk = flatDirections.getOrNull(cursor++) ?: return@forEachIndexed
                cards += walkCard(cardNumber, walk, legPoints)
            } else {
                val board = flatDirections.getOrNull(cursor++) ?: return@forEachIndexed
                // Consume the generator's "get off" direction to keep the cursor aligned; the Board and
                // Alight stops ride on the pre-resolved routeLeg and are rendered as the card's sub-items.
                flatDirections.getOrNull(cursor++)
                cards += transitCard(cardNumber, board, legPoints, routeLegRefs.getOrNull(legIndex))
            }
        }
        return cards
    }

    private fun walkCard(cardNumber: Int, walk: Direction, legPoints: List<GeoPoint>) = DirectionItem(
        iconRes = walk.icon,
        text = "$cardNumber. ${walk.directionText.str()}",
        isTransit = false,
        subItems = subItemsOf(walk.subDirections),
        focusPoint = walk.focusPoint(),
        legPoints = legPoints,
    )

    private fun transitCard(
        cardNumber: Int,
        board: Direction,
        legPoints: List<GeoPoint>,
        routeLeg: RouteLegRef?,
    ): DirectionItem {
        // Header = the board direction (route + time + detail lines). The leg's Board and Alight stops
        // ride on [routeLeg] and are rendered as the card's two sub-items by the UI; the leg row itself
        // focuses the route, and each sub-item shows its stop's inline ETA strip.
        return DirectionItem(
            iconRes = board.icon,
            text = "$cardNumber. ${board.service.str()} ${board.pickTime()}".trimEnd(),
            placeAndHeadsign = board.placeAndHeadsign.strOrNull(),
            agency = board.agency.strOrNull(),
            extra = board.extra.strOrNull(),
            isTransit = true,
            focusPoint = board.focusPoint(),
            legPoints = legPoints,
            routeLeg = routeLeg,
        )
    }

    private fun subItemsOf(subDirections: List<Direction>?): List<DirectionItem> =
        subDirections?.map {
            DirectionItem(iconRes = it.icon, text = it.directionText.str(), focusPoint = it.focusPoint())
        }.orEmpty()

    /** Decode the leg's own encoded polyline for framing; empty when the leg carries no geometry. */
    private fun TripLeg.decodedPoints(): List<GeoPoint> = legGeometry?.decodedPoints().orEmpty()

    /** The real-time-aware display time, mirroring the old `Direction.toItem()` pick. */
    private fun Direction.pickTime(): String =
        (if (isRealTimeInfo && newTime != null) newTime else oldTime).str()

    /** The point this direction refers to, or null when the underlying place had no coordinates. */
    private fun Direction.focusPoint(): GeoPoint? = geoPointOrNull(focusLat, focusLon)

    private fun CharSequence?.str(): String = this?.toString().orEmpty()
    private fun CharSequence?.strOrNull(): String? = this?.toString()?.takeIf { it.isNotEmpty() }
}
