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
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.PolylineDecoder

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

    fun groupByLeg(legs: List<TripLeg>, flatDirections: List<Direction>): List<DirectionItem> {
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
                val alight = flatDirections.getOrNull(cursor++)
                cards += transitCard(cardNumber, leg, board, alight, legPoints)
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
        leg: TripLeg,
        board: Direction,
        alight: Direction?,
        legPoints: List<GeoPoint>,
    ): DirectionItem {
        // Header = the board direction, composed exactly as the old top-level board row was; the alight
        // direction (previously its own top-level row) folds in below as the final sub-item.
        val subItems = subItemsOf(board.subDirections) + listOfNotNull(alight?.toAlightSubItem())
        return DirectionItem(
            iconRes = board.icon,
            text = "$cardNumber. ${board.service.str()} ${board.pickTime()}".trimEnd(),
            placeAndHeadsign = board.placeAndHeadsign.strOrNull(),
            agency = board.agency.strOrNull(),
            extra = board.extra.strOrNull(),
            isTransit = true,
            subItems = subItems,
            focusPoint = board.focusPoint(),
            legPoints = legPoints,
            routeLeg = leg.toRouteLegRef(),
        )
    }

    /** The alight ("get off") direction as the leg card's closing sub-item. */
    private fun Direction.toAlightSubItem() = DirectionItem(
        iconRes = icon,
        text = "${service.str()} ${placeAndHeadsign.str()} ${pickTime()}".trimAllSpaces(),
        focusPoint = focusPoint(),
    )

    private fun subItemsOf(subDirections: List<Direction>?): List<DirectionItem> =
        subDirections?.map {
            DirectionItem(iconRes = it.icon, text = it.directionText.str(), focusPoint = it.focusPoint())
        }.orEmpty()

    private fun TripLeg.toRouteLegRef() = RouteLegRef(
        routeId = routeId,
        boardStopId = from.stopId,
        boardStopCode = from.stopCode,
        boardStopName = from.name,
        alightStopId = to.stopId,
        boardPoint = from.point(),
        alightPoint = to.point(),
    )

    /** Decode the leg's own encoded polyline for framing; empty when the leg carries no geometry. */
    private fun TripLeg.decodedPoints(): List<GeoPoint> {
        val geometry = legGeometry ?: return emptyList()
        val encoded = geometry.points ?: return emptyList()
        if (encoded.isEmpty() || geometry.length <= 0) return emptyList()
        return PolylineDecoder.decode(encoded, geometry.length)
    }

    /** The real-time-aware display time, mirroring the old `Direction.toItem()` pick. */
    private fun Direction.pickTime(): String =
        (if (isRealTimeInfo && newTime != null) newTime else oldTime).str()

    /** The point this direction refers to, or null when the underlying place had no coordinates. */
    private fun Direction.focusPoint(): GeoPoint? {
        val lat = focusLat ?: return null
        val lon = focusLon ?: return null
        return GeoPoint(lat, lon)
    }

    private fun TripPlace.point(): GeoPoint? {
        val lat = lat ?: return null
        val lon = lon ?: return null
        return GeoPoint(lat, lon)
    }

    private fun CharSequence?.str(): String = this?.toString().orEmpty()
    private fun CharSequence?.strOrNull(): String? = this?.toString()?.takeIf { it.isNotEmpty() }
    private fun String.trimAllSpaces(): String = trim().replace(Regex("\\s{2,}"), " ")
}
