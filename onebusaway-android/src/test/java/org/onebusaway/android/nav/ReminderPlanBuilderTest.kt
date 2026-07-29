/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.encodePolyline

class ReminderPlanBuilderTest {
    @Test
    fun ignoresStreetLegsAndBuildsEveryTransitRide() {
        val itinerary = TripItinerary(
            legs = listOf(
                leg(TripMode.WALK),
                leg(TripMode.BUS, prefix = "a"),
                leg(TripMode.BICYCLE),
                leg(TripMode.RAIL, prefix = "b")
            )
        )
        val result = ReminderPlanBuilder.build(itinerary, "id") as ReminderPlanResult.Success
        assertEquals(listOf(ReminderMode.BUS, ReminderMode.RAIL), result.plan.rides.map { it.mode })
        assertEquals("a-middle", result.plan.rides.first().penultimate.id)
    }

    @Test
    fun stayAboardInterlineBecomesOneRideEndingAtLastLeg() {
        val first = leg(TripMode.BUS, prefix = "a")
        val second = leg(TripMode.BUS, prefix = "b").copy(interlineWithPreviousLeg = true, routeShortName = "20")
        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(first, second)), "id") as ReminderPlanResult.Success

        assertEquals(1, result.plan.rides.size)
        assertEquals("a-board", result.plan.rides.single().board?.id)
        assertEquals("b-alight", result.plan.rides.single().alight.id)
        assertEquals("10 / 20", result.plan.rides.single().routeLabel)
    }

    @Test
    fun oneStopRideUsesBoardAsPenultimate() {
        val direct = leg(TripMode.BUS).copy(stop = emptyList())
        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(direct)), "id") as ReminderPlanResult.Success
        assertEquals(result.plan.rides.single().board, result.plan.rides.single().penultimate)
    }

    @Test
    fun legacySingleRideCarriesNoBoardingStop() {
        val stop = ReminderStop("before", "Before", ReminderPoint(47.6, -122.3))
        val destination = ReminderStop("destination", "Destination", ReminderPoint(47.61, -122.3))
        val result = ReminderPlanBuilder.buildSingleRide(
            sessionId = "legacy",
            tripId = "trip",
            board = null,
            penultimate = stop,
            alight = destination
        ) as ReminderPlanResult.Success

        // The legacy schema stores only the destination and the stop before it; inventing a
        // boarding stop would misreport it to anything that later reads the field.
        assertNull(result.plan.rides.single().board)
    }

    @Test
    fun legGeometryBecomesTheRidesShapeWithItsStopOffsets() {
        val path = (0..20).map { GeoPoint(it * 0.001, 0.0) }
        val withGeometry = leg(TripMode.BUS).copy(
            legGeometry = TripLegGeometry(points = encodePolyline(path), length = path.size)
        )

        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(withGeometry)), "id") as ReminderPlanResult.Success

        val shape = result.plan.rides.single().shape!!
        val boardOffset = shape.boardOffsetMeters!!
        // Stops sit at latitude 0.0 / 0.01 / 0.02, so their offsets rise across the leg's length.
        assertEquals(0.0, boardOffset, 1.0)
        assertTrue(shape.penultimateOffsetMeters > boardOffset)
        assertTrue(shape.alightOffsetMeters > shape.penultimateOffsetMeters)
        assertNotNull("the encoded path must survive for the engine to decode", shape.polyline)
    }

    @Test
    fun geometryThatDoesNotPassTheStopsIsRejectedRatherThanTrusted() {
        // A path a long way from this leg's stops: the wrong pattern, or the wrong leg. Placing the
        // stops on it would produce confident nonsense, so the ride keeps straight-line distances.
        val elsewhere = (0..20).map { GeoPoint(it * 0.001, 5.0) }
        val mismatched = leg(TripMode.BUS).copy(
            legGeometry = TripLegGeometry(points = encodePolyline(elsewhere), length = elsewhere.size)
        )

        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(mismatched)), "id") as ReminderPlanResult.Success

        assertNull(result.plan.rides.single().shape)
    }

    @Test
    fun aLegWithoutGeometryHasNoShape() {
        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(leg(TripMode.BUS))), "id") as ReminderPlanResult.Success

        assertNull(result.plan.rides.single().shape)
    }

    @Test
    fun incompleteTransitLegRejectsWholePlan() {
        val invalid = leg(TripMode.BUS, prefix = "bad").copy(to = place("bad-alight", latitude = null))
        val result = ReminderPlanBuilder.build(
            TripItinerary(legs = listOf(leg(TripMode.BUS, prefix = "good"), invalid)),
            "id"
        )
        assertTrue(result is ReminderPlanResult.Error)
        result as ReminderPlanResult.Error
        assertEquals(ReminderPlanError.INCOMPLETE_STOP_INFORMATION, result.reason)
        assertEquals(2, result.legNumber)
    }

    @Test
    fun invalidIntermediateStopRejectsWholePlan() {
        val invalid = leg(TripMode.BUS).copy(
            stop = listOf(
                place("x-board"),
                place("x-invalid", latitude = null),
                place("x-alight", latitude = 0.02)
            )
        )

        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(invalid)), "id")

        assertEquals(ReminderPlanError.INCOMPLETE_STOP_INFORMATION, (result as ReminderPlanResult.Error).reason)
    }

    @Test
    fun coLocatedStopsWithDistinctIdsRemainDistinct() {
        val coLocated = leg(TripMode.BUS).copy(
            stop = listOf(
                place("x-board"),
                place("x-middle"),
                place("x-alight", latitude = 0.02)
            )
        )

        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(coLocated)), "id") as ReminderPlanResult.Success

        assertEquals("x-middle", result.plan.rides.single().penultimate.id)
    }

    @Test
    fun itineraryWithoutTransitHasUserVisibleError() {
        val result = ReminderPlanBuilder.build(TripItinerary(legs = listOf(leg(TripMode.WALK))), "id")
        assertEquals(ReminderPlanError.NO_TRANSIT_RIDES, (result as ReminderPlanResult.Error).reason)
    }

    private fun leg(mode: TripMode, prefix: String = "x") = TripLeg(
        mode = mode,
        routeShortName = "10",
        tripId = if (mode.isTransit) "$prefix-trip" else null,
        duration = 5.minutes,
        startTime = ServerTime(1_000),
        endTime = ServerTime(2_000),
        from = place("$prefix-board"),
        to = place("$prefix-alight", latitude = 0.02),
        stop = if (mode.isTransit) listOf(place("$prefix-board"), place("$prefix-middle", latitude = 0.01), place("$prefix-alight", latitude = 0.02)) else null
    )

    private fun place(id: String, latitude: Double? = 0.0) = TripPlace(
        name = id,
        stopId = id,
        lat = latitude,
        lon = 0.0
    )
}
