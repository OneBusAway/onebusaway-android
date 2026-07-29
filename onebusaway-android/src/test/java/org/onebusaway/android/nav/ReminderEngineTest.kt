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
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.encodePolyline

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
        var state = ReminderEngineState(penultimateReached = true, previousProgressMeters = -1_000.0)
        state = ReminderEngine.reduce(plan, state, sample(100.0, 1)).state
        val bus = ReminderEngine.reduce(plan, state, sample(200.0, 2)).effects.filterIsInstance<ReminderEffect.AlightNow>().single()
        assertTrue(bus.usesRequestStopWording)
        assertTrue(bus.isTransfer)

        val railPlan = plan(ride(mode = ReminderMode.RAIL))
        state = ReminderEngineState(penultimateReached = true, previousProgressMeters = -1_000.0)
        state = ReminderEngine.reduce(railPlan, state, sample(100.0, 1)).state
        val rail = ReminderEngine.reduce(railPlan, state, sample(200.0, 2)).effects.filterIsInstance<ReminderEffect.AlightNow>().single()
        assertFalse(rail.usesRequestStopWording)
        assertFalse(rail.isTransfer)
    }

    @Test
    fun waitingAtTransferStop_doesNotAlertUntilTheConnectingVehicleDeparts() {
        // Ride 2 boards where ride 1 alighted and runs only a short distance, so every proximity
        // signal is already satisfied while the rider is still standing on the platform.
        val plan = plan(
            ride(),
            ride(boardMeters = 1_000.0, penultimateMeters = 1_200.0, alightMeters = 1_300.0)
        )
        var state = ReminderEngineState(activeRideIndex = 1, awaitingBoarding = true)
        val whileWaiting = mutableListOf<ReminderEffect>()

        // Drifting along the platform toward the destination end: enough to establish "progress".
        listOf(950.0, 960.0, 970.0).forEachIndexed { index, north ->
            val transition = ReminderEngine.reduce(plan, state, sample(north, index + 1L))
            state = transition.state
            whileWaiting += transition.effects.filterNot { it is ReminderEffect.Progress }
        }
        assertTrue("no alert may fire before boarding", whileWaiting.isEmpty())
        assertTrue(state.awaitingBoarding)

        // The connecting vehicle pulls out: the rider leaves the boarding stop toward the alight stop.
        val departing = ReminderEngine.reduce(plan, state, sample(1_100.0, 4))
        assertFalse(departing.state.awaitingBoarding)
        assertTrue(departing.effects.any { it is ReminderEffect.GetReady })
    }

    @Test
    fun sparseFixesDuringTheWait_stillConfirmBoardingOnceUnderWay() {
        // No fix while the rider waits, then one well past the boarding stop: progress alone proves
        // they are under way, so the ride is not stranded waiting for a departure it never saw.
        val plan = plan(ride(), ride(boardMeters = 0.0, penultimateMeters = 2_000.0, alightMeters = 3_000.0))
        var state = ReminderEngineState(activeRideIndex = 1, awaitingBoarding = true)
        listOf(1_000.0, 1_500.0, 2_000.0).forEachIndexed { index, north ->
            state = ReminderEngine.reduce(plan, state, sample(north, index + 1L)).state
        }
        assertFalse(state.awaitingBoarding)
    }

    @Test
    fun silenceSurvivesARideTransition() {
        val plan = plan(ride(), ride(penultimateMeters = 2_000.0, alightMeters = 3_000.0))
        var state = ReminderEngineState(rideProgressEstablished = true, speechMuted = true)
        state = ReminderEngine.reduce(plan, state, sample(995.0, 1)).state
        val afterTransfer = ReminderEngine.reduce(plan, state, sample(999.0, 2)).state

        assertEquals(1, afterTransfer.activeRideIndex)
        assertTrue("the rider's silence choice belongs to the session", afterTransfer.speechMuted)
        assertEquals(WallTime(2), afterTransfer.lastSampleTimestamp)
    }

    @Test
    fun strictlyOlderSampleIsIgnored() {
        val plan = plan(ride())
        val first = ReminderEngine.reduce(plan, ReminderEngineState(), sample(500.0, 5))
        val stale = ReminderEngine.reduce(plan, first.state, sample(10.0, 3))

        assertEquals(first.state, stale.state)
        assertTrue(stale.effects.isEmpty())
    }

    @Test
    fun restoredMidRideState_completesWithoutRefiringLatchedAlerts() {
        val plan = plan(ride())
        val restored = ReminderPlanJson.decodeState(
            ReminderPlanJson.encodeState(
                ReminderEngineState(
                    getReadyEmitted = true,
                    alightNowEmitted = true,
                    penultimateReached = true,
                    rideProgressEstablished = true
                )
            )
        )!!

        var state = restored
        val effects = mutableListOf<ReminderEffect>()
        listOf(995.0, 999.0).forEachIndexed { index, north ->
            val transition = ReminderEngine.reduce(plan, state, sample(north, index + 1L))
            state = transition.state
            effects += transition.effects.filterNot { it is ReminderEffect.Progress }
        }

        assertEquals(0, effects.count { it is ReminderEffect.GetReady })
        assertEquals(0, effects.count { it is ReminderEffect.AlightNow })
        assertTrue(state.completed)
        assertTrue(effects.any { it is ReminderEffect.SessionCompleted })
    }

    @Test
    fun restorationKeyIgnoresPerSampleScratchButTracksLatches() {
        val base = ReminderEngineState()

        // Filtering scratch that re-converges within a couple of fixes must not force a write.
        assertEquals(
            base.restorationKey(),
            base.copy(
                previousProgressMeters = -120.0,
                advancedSamples = 2,
                penultimateInsideSamples = 1,
                lastSampleTimestamp = WallTime(9)
            ).restorationKey()
        )

        listOf(
            base.copy(getReadyEmitted = true),
            base.copy(alightNowEmitted = true),
            base.copy(activeRideIndex = 1),
            base.copy(awaitingBoarding = true),
            base.copy(speechMuted = true),
            base.copy(completed = true)
        ).forEach { assertFalse(base.restorationKey() == it.restorationKey()) }
    }

    @Test
    fun curvingRoute_establishesProgressOnlyWithAShape() {
        // A hooked route: east, then north, then back west to a destination that sits due north of
        // the boarding stop. While the vehicle runs the outbound leg it is getting *further* from
        // the destination in a straight line, even though it is making real progress along the route.
        val shaped = plan(hookedRide(withShape = true))
        val unshaped = plan(hookedRide(withShape = false))
        val outbound = listOf(hookPoint(0.004), hookPoint(0.008), hookPoint(0.012))

        var shapedState = ReminderEngineState()
        var unshapedState = ReminderEngineState()
        outbound.forEachIndexed { index, point ->
            val fix = ReminderLocationSample(point, 5f, null, WallTime(index + 1L))
            shapedState = ReminderEngine.reduce(shaped, shapedState, fix).state
            unshapedState = ReminderEngine.reduce(unshaped, unshapedState, fix).state
        }

        assertTrue(
            "along the route the rider is unambiguously making progress",
            shapedState.rideProgressEstablished
        )
        assertFalse(
            "straight-line distance to the destination grows on the outbound leg, hiding the progress",
            unshapedState.rideProgressEstablished
        )
    }

    @Test
    fun stopOffsetFromTheVehiclePathIsStillDetected() {
        // A stop placed 60 m off the centreline — a platform beside the track. Its offset along the
        // route is what matters, not how close a fix gets to the stop's own coordinate.
        val ride = shapedRide(penultimateMeters = 500.0, alightMeters = 1_000.0, stopOffsetDegrees = 0.00054)
        val plan = plan(ride)

        var state = ReminderEngineState()
        val effects = mutableListOf<ReminderEffect>()
        listOf(300.0, 480.0, 500.0, 620.0, 700.0, 980.0, 1_000.0).forEachIndexed { index, north ->
            val transition = ReminderEngine.reduce(plan, state, sample(north, index + 1L, speed = 8f))
            state = transition.state
            effects += transition.effects.filterNot { it is ReminderEffect.Progress }
        }

        assertEquals(1, effects.count { it is ReminderEffect.GetReady })
        assertEquals(1, effects.count { it is ReminderEffect.AlightNow })
        assertTrue(state.completed)
    }

    @Test
    fun alongARouteAFastModeKeepsItsFullEarlyWarning() {
        // 100 km/h needs 2.5 km to get the intended 90 seconds. The straight-line cap of 1,200 m
        // would cut that to 43 s — least warning exactly where most is needed.
        val shaped = plan(shapedRide(penultimateMeters = 4_000.0, alightMeters = 5_000.0))
        val unshaped = plan(ride(penultimateMeters = 4_000.0, alightMeters = 5_000.0))
        val fast = 27.78f

        val alongRoute = ReminderEngine.reduce(shaped, ReminderEngineState(), sample(100.0, 1, speed = fast))
        val straightLine = ReminderEngine.reduce(unshaped, ReminderEngineState(), sample(100.0, 1, speed = fast))

        assertEquals(
            (fast * DestinationReminderPolicy.GET_READY_SECONDS),
            alongRoute.effects.filterIsInstance<ReminderEffect.Progress>().single().getReadyRadiusMeters,
            1.0
        )
        assertEquals(
            DestinationReminderPolicy.MAX_GET_READY_STRAIGHT_LINE_METERS,
            straightLine.effects.filterIsInstance<ReminderEffect.Progress>().single().getReadyRadiusMeters,
            0.01
        )
    }

    @Test
    fun aFixFarOffTheRouteFallsBackToStraightLineDistances() {
        // Well beyond MAX_OFF_ROUTE_METERS of the path: the shape cannot place this fix, so the
        // reading must degrade rather than report a confidently wrong position along the route.
        val plan = plan(shapedRide())
        val farEast = ReminderPoint(500.0 / METERS_PER_DEGREE, 5_000.0 / METERS_PER_DEGREE)

        val transition = ReminderEngine.reduce(
            plan,
            ReminderEngineState(),
            ReminderLocationSample(farEast, 5f, null, WallTime(1))
        )

        // The straight-line reading of that position is dominated by the 5 km eastward offset.
        val progress = transition.effects.filterIsInstance<ReminderEffect.Progress>().single()
        assertTrue("expected a straight-line reading", progress.alightDistanceMeters > 4_000.0)
    }

    @Test
    fun aPlanWithoutAShapeStillDecodes() {
        // What a session persisted before shapes existed looks like on disk.
        val legacyJson = """
            {"version":1,"sessionId":"s","rides":[{"mode":"BUS","routeLabel":"10","tripId":"t",
            "board":null,"penultimate":{"id":"p","name":"p","point":{"latitude":0.0,"longitude":0.0}},
            "alight":{"id":"a","name":"a","point":{"latitude":0.01,"longitude":0.0}},
            "scheduledStart":null,"scheduledEnd":null}]}
        """.trimIndent().replace("\n", "")

        val decoded = ReminderPlanJson.decode(legacyJson)

        assertEquals("t", decoded?.rides?.single()?.tripId)
        assertEquals(null, decoded?.rides?.single()?.shape)
    }

    @Test
    fun stateAndPlan_roundTripForProcessRestoration() {
        val plan = plan(ride())
        val state = ReminderEngineState(activeRideIndex = 0, getReadyEmitted = true, lastSampleTimestamp = WallTime(42))
        assertEquals(plan, ReminderPlanJson.decode(ReminderPlanJson.encode(plan)))
        assertEquals(state, ReminderPlanJson.decodeState(ReminderPlanJson.encodeState(state)))
        assertEquals(null, ReminderPlanJson.decode("{\"version\":99}"))
    }

    @Test
    fun unscheduledPlan_roundTripsForLegacyRestoration() {
        val plan = plan(ride().copy(scheduledStart = null, scheduledEnd = null))

        assertEquals(plan, ReminderPlanJson.decode(ReminderPlanJson.encode(plan)))
    }

    private fun plan(vararg rides: ReminderRide) = ReminderPlan(sessionId = "session", rides = rides.toList())

    private fun ride(
        mode: ReminderMode = ReminderMode.BUS,
        penultimateMeters: Double = 0.0,
        alightMeters: Double = 1_000.0,
        boardMeters: Double = -1_000.0
    ) = ReminderRide(
        mode = mode,
        routeLabel = "10",
        tripId = "trip",
        board = stop("board", boardMeters),
        penultimate = stop("before", penultimateMeters),
        alight = stop("destination", alightMeters),
        scheduledStart = ServerTime(1),
        scheduledEnd = ServerTime(2)
    )

    /**
     * The same straight north-south ride as [ride], but carrying the shape it runs along. On a
     * straight line the two coordinates agree, so a shaped ride is a drop-in for the unshaped one —
     * which is what makes the pair usable as a controlled comparison.
     *
     * [stopOffsetDegrees] pushes the stops sideways off the path, modelling a platform beside the
     * track: their offsets along the route are unchanged, but their own coordinates are not on it.
     */
    private fun shapedRide(
        mode: ReminderMode = ReminderMode.BUS,
        penultimateMeters: Double = 0.0,
        alightMeters: Double = 1_000.0,
        boardMeters: Double = -1_000.0,
        stopOffsetDegrees: Double = 0.0
    ): ReminderRide {
        fun offsetStop(id: String, northMeters: Double) = ReminderStop(id, id, ReminderPoint(northMeters / METERS_PER_DEGREE, stopOffsetDegrees))

        // A dense straight path spanning the whole ride, so projection has real segments to work on.
        val path = generateSequence(boardMeters - 200.0) { it + 100.0 }
            .takeWhile { it <= alightMeters + 200.0 }
            .map { GeoPoint(it / METERS_PER_DEGREE, 0.0) }
            .toList()
        return ride(mode, penultimateMeters, alightMeters, boardMeters).copy(
            board = offsetStop("board", boardMeters),
            penultimate = offsetStop("before", penultimateMeters),
            alight = offsetStop("destination", alightMeters),
            shape = shapeAlong(path, boardMeters, penultimateMeters, alightMeters)
        )
    }

    /**
     * A hooked route — east, north, then back west — whose destination sits due north of its
     * boarding stop. Travelling the outbound leg increases the straight-line distance to the
     * destination while genuinely making progress, which is the case straight-line distances cannot
     * read and the route shape can.
     */
    private fun hookedRide(withShape: Boolean): ReminderRide {
        val path = buildList {
            var east = 0.0
            while (east <= 0.02) {
                add(GeoPoint(0.0, east))
                east += 0.001
            }
            var north = 0.0
            while (north <= 0.02) {
                add(GeoPoint(north, 0.02))
                north += 0.001
            }
            var west = 0.02
            while (west >= 0.0) {
                add(GeoPoint(0.02, west))
                west -= 0.001
            }
        }
        val polyline = Polyline(path)
        val board = ReminderStop("board", "board", ReminderPoint(0.0, 0.0))
        val penultimate = ReminderStop("before", "before", ReminderPoint(0.02, 0.005))
        val alight = ReminderStop("destination", "destination", ReminderPoint(0.02, 0.0))
        return ReminderRide(
            mode = ReminderMode.BUS,
            routeLabel = "10",
            tripId = "trip",
            board = board,
            penultimate = penultimate,
            alight = alight,
            scheduledStart = ServerTime(1),
            scheduledEnd = ServerTime(2),
            shape = if (!withShape) {
                null
            } else {
                ReminderShape(
                    encodedPoints = encodePolyline(path),
                    pointCount = path.size,
                    boardOffsetMeters = polyline.nearestProjection(0.0, 0.0)!!.distanceAlong,
                    penultimateOffsetMeters = polyline.nearestProjection(0.02, 0.005)!!.distanceAlong,
                    alightOffsetMeters = polyline.nearestProjection(0.02, 0.0)!!.distanceAlong
                )
            }
        )
    }

    /** A point on the hooked route's outbound (eastward) leg, [degreesEast] from its start. */
    private fun hookPoint(degreesEast: Double) = ReminderPoint(0.0, degreesEast)

    private fun shapeAlong(
        path: List<GeoPoint>,
        boardMeters: Double,
        penultimateMeters: Double,
        alightMeters: Double
    ): ReminderShape {
        val polyline = Polyline(path)
        fun offsetAt(northMeters: Double) = polyline.nearestProjection(northMeters / METERS_PER_DEGREE, 0.0)!!.distanceAlong
        return ReminderShape(
            encodedPoints = encodePolyline(path),
            pointCount = path.size,
            boardOffsetMeters = offsetAt(boardMeters),
            penultimateOffsetMeters = offsetAt(penultimateMeters),
            alightOffsetMeters = offsetAt(alightMeters)
        )
    }

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
