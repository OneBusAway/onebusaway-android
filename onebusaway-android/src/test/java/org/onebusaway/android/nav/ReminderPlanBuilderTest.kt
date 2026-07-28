/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.time.ServerTime

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
        assertEquals("a-board", result.plan.rides.single().board.id)
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
