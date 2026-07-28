/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

class ReminderEngineTest {
    @Test
    fun ordinaryProgression_emitsOrderedAlertsAndCompletes() {
        val plan = plan(ride())
        var state = ReminderEngineState()
        val effects = mutableListOf<ReminderEffect>()

        // Approaching the penultimate stop enters the dynamic get-ready radius.
        listOf(500.0, 20.0, 10.0, 100.0, 180.0, 990.0, 995.0).forEachIndexed { index, north ->
            val transition = ReminderEngine.reduce(plan, state, sample(north, index + 1L, speed = 8f))
            state = transition.state
            effects += transition.effects.filterNot { it is ReminderEffect.Progress }
        }

        assertEquals(1, effects.count { it is ReminderEffect.GetReady })
        assertEquals(1, effects.count { it is ReminderEffect.AlightNow })
        assertEquals(1, effects.count { it is ReminderEffect.RideCompleted })
        assertEquals(1, effects.count { it is ReminderEffect.SessionCompleted })
        assertTrue(state.completed)
        assertTrue(effects.indexOfFirst { it is ReminderEffect.GetReady } < effects.indexOfFirst { it is ReminderEffect.AlightNow })
    }

    @Test
    fun poorAccuracyAndDuplicateTimestamps_doNotAdvanceCounters() {
        val plan = plan(ride())
        val poor = ReminderEngine.reduce(plan, ReminderEngineState(), sample(10.0, 1, accuracy = 101f))
        assertEquals(ReminderEngineState(), poor.state)
        assertTrue(poor.effects.isEmpty())

        val first = ReminderEngine.reduce(plan, ReminderEngineState(), sample(10.0, 2))
        val duplicate = ReminderEngine.reduce(plan, first.state, sample(10.0, 2))
        assertEquals(first.state, duplicate.state)
        assertTrue(duplicate.effects.isEmpty())
        assertFalse(first.state.penultimateReached)
    }

    @Test
    fun noisyPenultimateSamples_requireTwoConsecutiveInside() {
        val plan = plan(ride())
        var state = ReminderEngineState()
        listOf(20.0, 80.0, 20.0).forEachIndexed { index, north ->
            state = ReminderEngine.reduce(plan, state, sample(north, index + 1L)).state
        }
        assertFalse(state.penultimateReached)
        state = ReminderEngine.reduce(plan, state, sample(15.0, 4)).state
        assertTrue(state.penultimateReached)
    }

    @Test
    fun sparseFixNearAlight_requiresEstablishedProgressThenEmitsBothAlertsOnce() {
        val plan = plan(ride())
        val first = ReminderEngine.reduce(plan, ReminderEngineState(), sample(700.0, 1))
        assertEquals(0, first.effects.count { it is ReminderEffect.GetReady })
        assertEquals(0, first.effects.count { it is ReminderEffect.AlightNow })

        val second = ReminderEngine.reduce(plan, first.state, sample(710.0, 2))
        assertEquals(0, second.effects.count { it is ReminderEffect.GetReady })
        assertEquals(0, second.effects.count { it is ReminderEffect.AlightNow })
        val third = ReminderEngine.reduce(plan, second.state, sample(720.0, 3))
        assertEquals(1, third.effects.count { it is ReminderEffect.GetReady })
        assertEquals(1, third.effects.count { it is ReminderEffect.AlightNow })

        val fourth = ReminderEngine.reduce(plan, third.state, sample(730.0, 4))
        assertEquals(0, fourth.effects.count { it is ReminderEffect.GetReady })
        assertEquals(0, fourth.effects.count { it is ReminderEffect.AlightNow })
    }

    @Test
    fun speedControlsGetReadyRadiusWithinClamp() {
        val plan = plan(ride(penultimateMeters = 0.0, alightMeters = 2_000.0))
        val slow = ReminderEngine.reduce(plan, ReminderEngineState(), sample(1_600.0, 1, speed = null))
        val fast = ReminderEngine.reduce(plan, ReminderEngineState(), sample(1_600.0, 1, speed = 20f))
        val absurd = ReminderEngine.reduce(plan, ReminderEngineState(), sample(1_600.0, 1, speed = 100f))

        assertEquals(300.0, slow.effects.filterIsInstance<ReminderEffect.Progress>().single().getReadyRadiusMeters, 0.01)
        assertEquals(1_200.0, fast.effects.filterIsInstance<ReminderEffect.Progress>().single().getReadyRadiusMeters, 0.01)
        assertEquals(1_200.0, absurd.effects.filterIsInstance<ReminderEffect.Progress>().single().getReadyRadiusMeters, 0.01)
    }

    @Test
    fun completingRide_activatesTransferThenCompletesSession() {
        val plan = plan(ride(), ride(penultimateMeters = 2_000.0, alightMeters = 3_000.0))
        var state = ReminderEngineState(rideProgressEstablished = true)
        state = ReminderEngine.reduce(plan, state, sample(995.0, 1)).state
        val firstCompletion = ReminderEngine.reduce(plan, state, sample(999.0, 2))
        assertEquals(1, firstCompletion.state.activeRideIndex)
        assertFalse(firstCompletion.state.completed)
        assertTrue(firstCompletion.effects.any { it is ReminderEffect.RideCompleted })
        assertFalse(firstCompletion.effects.any { it is ReminderEffect.SessionCompleted })

        state = firstCompletion.state.copy(rideProgressEstablished = true)
        state = ReminderEngine.reduce(plan, state, sample(2_995.0, 3)).state
        val finalCompletion = ReminderEngine.reduce(plan, state, sample(2_999.0, 4))
        assertTrue(finalCompletion.state.completed)
        assertTrue(finalCompletion.effects.any { it is ReminderEffect.SessionCompleted })
    }

    @Test
    fun alightEffectCarriesModeAwareWordingAndTransferDestination() {
        val plan = plan(ride(mode = ReminderMode.BUS), ride(mode = ReminderMode.RAIL))
        var state = ReminderEngineState(penultimateReached = true, previousAlightDistanceMeters = 1_000.0)
        state = ReminderEngine.reduce(plan, state, sample(100.0, 1)).state
        val bus = ReminderEngine.reduce(plan, state, sample(200.0, 2)).effects.filterIsInstance<ReminderEffect.AlightNow>().single()
        assertTrue(bus.usesRequestStopWording)
        assertTrue(bus.isTransfer)

        val railPlan = plan(ride(mode = ReminderMode.RAIL))
        state = ReminderEngineState(penultimateReached = true, previousAlightDistanceMeters = 1_000.0)
        state = ReminderEngine.reduce(railPlan, state, sample(100.0, 1)).state
        val rail = ReminderEngine.reduce(railPlan, state, sample(200.0, 2)).effects.filterIsInstance<ReminderEffect.AlightNow>().single()
        assertFalse(rail.usesRequestStopWording)
        assertFalse(rail.isTransfer)
    }

    @Test
    fun stateAndPlan_roundTripForProcessRestoration() {
        val plan = plan(ride())
        val state = ReminderEngineState(activeRideIndex = 0, getReadyEmitted = true, lastSampleTimestamp = WallTime(42))
        assertEquals(plan, ReminderPlanJson.decode(ReminderPlanJson.encode(plan)))
        assertEquals(state, ReminderPlanJson.decodeState(ReminderPlanJson.encodeState(state)))
        assertEquals(null, ReminderPlanJson.decode("{\"version\":99}"))
    }

    private fun plan(vararg rides: ReminderRide) = ReminderPlan(sessionId = "session", rides = rides.toList())

    private fun ride(
        mode: ReminderMode = ReminderMode.BUS,
        penultimateMeters: Double = 0.0,
        alightMeters: Double = 1_000.0
    ) = ReminderRide(
        mode = mode,
        routeLabel = "10",
        tripId = "trip",
        board = stop("board", -1_000.0),
        penultimate = stop("before", penultimateMeters),
        alight = stop("destination", alightMeters),
        scheduledStart = ServerTime(1),
        scheduledEnd = ServerTime(2)
    )

    private fun stop(id: String, northMeters: Double) = ReminderStop(id, id, point(northMeters))

    private fun point(northMeters: Double) = ReminderPoint(northMeters / METERS_PER_DEGREE, 0.0)

    private fun sample(
        northMeters: Double,
        timestamp: Long,
        accuracy: Float = 5f,
        speed: Float? = null
    ) = ReminderLocationSample(point(northMeters), accuracy, speed, WallTime(timestamp))

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
    }
}
