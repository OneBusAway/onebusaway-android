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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.util.GeoPoint

/**
 * JVM tests for [DirectionCardGrouping]: the generator's flat, leg-ordered [Direction] list re-groups
 * into one card per leg (walk leg = 1 direction, transit leg = board + alight = 2), with the alight
 * folded in as the last sub-item and the leg's polyline / route identity attached.
 */
class DirectionCardGroupingTest {

    // Google's canonical example polyline (decodes to 3 points); any valid encoding works here.
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    private val walkFrom = GeoPoint(47.6100, -122.3300)
    private val boardFrom = GeoPoint(47.6150, -122.3350)
    private val alightTo = GeoPoint(47.6200, -122.3400)
    private val stopMid = GeoPoint(47.6175, -122.3375)

    private val walkLeg = TripLeg(
        mode = TripMode.WALK,
        from = TripPlace(name = "Origin", lat = walkFrom.latitude, lon = walkFrom.longitude),
        to = TripPlace(name = "Pine St & 3rd Ave", lat = boardFrom.latitude, lon = boardFrom.longitude),
        legGeometry = TripLegGeometry(points = encoded, length = 3),
    )

    private val transitLeg = TripLeg(
        mode = TripMode.BUS,
        routeId = "1_100",
        routeShortName = "8",
        from = TripPlace(
            name = "Pine St & 3rd Ave", stopId = "1_500", stopCode = "500",
            lat = boardFrom.latitude, lon = boardFrom.longitude,
        ),
        to = TripPlace(
            name = "Rainier & Alaska", stopId = "1_600", stopCode = "600",
            lat = alightTo.latitude, lon = alightTo.longitude,
        ),
        legGeometry = TripLegGeometry(points = encoded, length = 3),
    )

    private val walkDir = Direction().apply {
        icon = 1
        directionText = "Walk to Pine St & 3rd Ave"
        focusLat = walkFrom.latitude
        focusLon = walkFrom.longitude
        subDirections = arrayListOf(
            Direction().apply {
                directionText = "Turn left"
                focusLat = stopMid.latitude
                focusLon = stopMid.longitude
            }
        )
    }

    private val boardDir = Direction().apply {
        icon = 2
        isTransit = true
        service = "Get on BUS Route 8"
        placeAndHeadsign = "Stop: Pine St & 3rd Ave toward Rainier Beach"
        agency = "Metro Transit"
        extra = "1 stop in between"
        oldTime = "3:52p"
        focusLat = boardFrom.latitude
        focusLon = boardFrom.longitude
        subDirections = arrayListOf(
            Direction().apply {
                directionText = "1. Capitol Hill Station"
                focusLat = stopMid.latitude
                focusLon = stopMid.longitude
            }
        )
    }

    private val alightDir = Direction().apply {
        icon = -1
        isTransit = true
        service = "Get off BUS Route 8"
        placeAndHeadsign = "Stop: Rainier & Alaska"
        oldTime = "4:15p"
        focusLat = alightTo.latitude
        focusLon = alightTo.longitude
    }

    @Test
    fun oneCardPerLeg_walkThenTransit() {
        val cards = DirectionCardGrouping.groupByLeg(
            legs = listOf(walkLeg, transitLeg),
            flatDirections = listOf(walkDir, boardDir, alightDir),
        )
        assertEquals(2, cards.size)
        assertFalse(cards[0].isTransit)
        assertTrue(cards[1].isTransit)
    }

    @Test
    fun walkCard_numberedAndCarriesLegPolylineAndTurnSteps() {
        val walk = DirectionCardGrouping.groupByLeg(listOf(walkLeg), listOf(walkDir)).single()
        assertTrue("leg number prefixed", walk.text.startsWith("1. "))
        assertEquals(3, walk.legPoints.size) // decoded from the leg geometry, for body-tap framing
        assertEquals(1, walk.subItems.size)  // its turn step
        assertEquals(stopMid, walk.subItems.single().focusPoint)
        assertNull("walk legs carry no route identity", walk.routeLeg)
    }

    @Test
    fun transitCard_foldsBoardHeaderWithStopsThenAlight() {
        val transit = DirectionCardGrouping
            .groupByLeg(listOf(transitLeg), listOf(boardDir, alightDir)).single()

        // Header = the board direction (number + service + time + detail lines).
        assertTrue("leg number prefixed", transit.text.startsWith("1. "))
        assertTrue(transit.text.contains("Get on BUS Route 8"))
        assertEquals("Metro Transit", transit.agency)
        assertEquals(boardFrom, transit.focusPoint)
        assertEquals(3, transit.legPoints.size)

        // Sub-items: intermediate stop(s) then the folded-in alight row (last).
        assertEquals(2, transit.subItems.size)
        assertEquals(stopMid, transit.subItems.first().focusPoint)
        val alight = transit.subItems.last()
        assertEquals(alightTo, alight.focusPoint)
        assertTrue(alight.text.contains("Rainier & Alaska"))
    }

    @Test
    fun transitCard_carriesRouteAndStopIdentity() {
        val transit = DirectionCardGrouping
            .groupByLeg(listOf(transitLeg), listOf(boardDir, alightDir)).single()
        val ref = transit.routeLeg
        assertNotNull(ref)
        assertEquals("1_100", ref!!.routeId)
        assertEquals("1_500", ref.boardStopId)
        assertEquals("500", ref.boardStopCode)
        assertEquals("Pine St & 3rd Ave", ref.boardStopName)
        assertEquals("1_600", ref.alightStopId)
        assertEquals(boardFrom, ref.boardPoint)
        assertEquals(alightTo, ref.alightPoint)
    }
}
