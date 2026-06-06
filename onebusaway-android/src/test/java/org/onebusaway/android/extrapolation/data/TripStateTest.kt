/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.data

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.Occupancy
import org.onebusaway.android.io.elements.Status

class TripStateTest {

    // ================================================================
    // extrapolate gating — item #28
    // ================================================================

    @Test
    fun `extrapolate returns NoData when no anchor`() {
        val state = TripState.empty("trip1")
        val result = state.extrapolate(System.currentTimeMillis())
        assertTrue(result is ExtrapolationResult.NoData)
    }

    @Test
    fun `extrapolate returns NoData when anchor has null distance`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = null, lastUpdateTime = 1000L),
                                serverTimeMs = 1000L,
                                localTimeMs = 1000L
                        )
        // recorded() skips entries with null distance, so anchor stays null
        val result = state.extrapolate(1000L)
        assertTrue(result is ExtrapolationResult.NoData)
    }

    @Test
    fun `extrapolate returns NoData when anchorLocalTimeMs is 0`() {
        // serverTimeMs = 0 → recorded() skips the entry
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 0L),
                                serverTimeMs = 0L,
                                localTimeMs = 0L
                        )
        val result = state.extrapolate(1000L)
        assertTrue(result is ExtrapolationResult.NoData)
    }

    @Test
    fun `extrapolate returns Stale when query is before anchor`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = serverTime),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        // Query time is before anchor local time → dtMs < 0
        val result = state.extrapolate(localTime - 1)
        assertTrue(result is ExtrapolationResult.Stale)
    }

    @Test
    fun `extrapolate returns Stale when query exceeds horizon`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = serverTime),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        // MAX_HORIZON_MS = 15 * 60 * 1000 = 900_000
        val result = state.extrapolate(localTime + 900_001L)
        assertTrue(result is ExtrapolationResult.Stale)
    }

    @Test
    fun `extrapolate returns Stale at exactly max horizon`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = serverTime),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        // dtMs == MAX_HORIZON_MS should still be stale (> comparison)
        val result = state.extrapolate(localTime + 900_001L)
        assertTrue(result is ExtrapolationResult.Stale)
    }

    @Test
    fun `extrapolate returns TripNotStarted when distance below threshold`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        // PRE_DEPARTURE_DISTANCE_THRESHOLD = 50.0
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 49.0, lastUpdateTime = serverTime),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        val result = state.extrapolate(localTime + 1000L)
        assertTrue(result is ExtrapolationResult.TripNotStarted)
    }

    @Test
    fun `extrapolate returns TripNotStarted at exactly threshold`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 50.0, lastUpdateTime = serverTime),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        val result = state.extrapolate(localTime + 1000L)
        // 50.0 <= 50.0 is true, so TripNotStarted
        assertTrue(result is ExtrapolationResult.TripNotStarted)
    }

    @Test
    fun `extrapolate returns TripEnded when near end of trip`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        // TRIP_END_DISTANCE_THRESHOLD = 50.0
        // totalDist - lastDist < 50 → TripEnded
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(
                                        distanceAlongTrip = 9970.0,
                                        totalDistanceAlongTrip = 10000.0,
                                        lastUpdateTime = serverTime
                                ),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        val result = state.extrapolate(localTime + 1000L)
        assertTrue(result is ExtrapolationResult.TripEnded)
    }

    @Test
    fun `extrapolate does not return TripEnded when totalDist is null`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(
                                        distanceAlongTrip = 500.0,
                                        totalDistanceAlongTrip = null,
                                        lastUpdateTime = serverTime
                                ),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        val result = state.extrapolate(localTime + 1000L)
        // Without totalDist, trip-ended check is skipped; should reach the extrapolator
        assertFalse(result is ExtrapolationResult.TripEnded)
    }

    @Test
    fun `extrapolate does not return TripEnded when totalDist is zero`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(
                                        distanceAlongTrip = 500.0,
                                        totalDistanceAlongTrip = 0.0,
                                        lastUpdateTime = serverTime
                                ),
                                serverTimeMs = serverTime,
                                localTimeMs = localTime
                        )
        val result = state.extrapolate(localTime + 1000L)
        assertFalse(result is ExtrapolationResult.TripEnded)
    }

    // ================================================================
    // recorded — dedup and anchor logic — items #28/#29
    // ================================================================
    // Skip cases assert assertSame(before, after): recorded() returning `this`
    // is the contract that lets the store's StateFlow skip the emission.

    @Test
    fun `recorded skips null distance`() {
        val before = TripState.empty("trip1")
        val after =
                before.recorded(
                        status(distanceAlongTrip = null, lastUpdateTime = 1000L),
                        serverTimeMs = 1000L,
                        localTimeMs = 1000L
                )
        assertSame(before, after)
        assertEquals(0, after.history.size)
    }

    @Test
    fun `recorded skips zero serverTimeMs`() {
        val before = TripState.empty("trip1")
        val after =
                before.recorded(
                        status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                        serverTimeMs = 0L,
                        localTimeMs = 1000L
                )
        assertSame(before, after)
        assertEquals(0, after.history.size)
    }

    @Test
    fun `recorded skips negative serverTimeMs`() {
        val before = TripState.empty("trip1")
        val after =
                before.recorded(
                        status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                        serverTimeMs = -1L,
                        localTimeMs = 1000L
                )
        assertSame(before, after)
    }

    @Test
    fun `recorded records valid entry`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = 2000L,
                                localTimeMs = 2000L
                        )
        assertEquals(1, state.history.size)
    }

    @Test
    fun `recorded skips duplicate distance and time`() {
        val s = status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L)
        val before =
                TripState.empty("trip1").recorded(s, serverTimeMs = 2000L, localTimeMs = 2000L)
        val after = before.recorded(s, serverTimeMs = 3000L, localTimeMs = 3000L)
        assertSame(before, after)
        assertEquals(1, after.history.size)
    }

    @Test
    fun `recorded records entry with different distance`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = 2000L,
                                localTimeMs = 2000L
                        )
                        .recorded(
                                status(distanceAlongTrip = 600.0, lastUpdateTime = 1000L),
                                serverTimeMs = 3000L,
                                localTimeMs = 3000L
                        )
        assertEquals(2, state.history.size)
    }

    @Test
    fun `recorded records entry with different time`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = 2000L,
                                localTimeMs = 2000L
                        )
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 2000L),
                                serverTimeMs = 3000L,
                                localTimeMs = 3000L
                        )
        assertEquals(2, state.history.size)
    }

    @Test
    fun `recorded updates anchor to newest timestamp`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = 2000L,
                                localTimeMs = 2000L
                        )
                        .recorded(
                                status(distanceAlongTrip = 600.0, lastUpdateTime = 3000L),
                                serverTimeMs = 4000L,
                                localTimeMs = 4000L
                        )
        assertEquals(600.0, state.anchor!!.distanceAlongTrip!!, 0.0)
        assertEquals(3000L, state.anchorTimeMs)
    }

    @Test
    fun `recorded GPS wins ties`() {
        val state =
                TripState.empty("trip1")
                        // First: non-realtime (predicted=false)
                        .recorded(
                                status(
                                        distanceAlongTrip = 500.0,
                                        lastUpdateTime = 1000L,
                                        predicted = false
                                ),
                                serverTimeMs = 2000L,
                                localTimeMs = 2000L
                        )
                        // Second: realtime, same lastUpdateTime
                        .recorded(
                                status(
                                        distanceAlongTrip = 600.0,
                                        lastUpdateTime = 1000L,
                                        predicted = true,
                                        hasLocation = true
                                ),
                                serverTimeMs = 3000L,
                                localTimeMs = 3000L
                        )
        // GPS entry should win the tie
        assertEquals(600.0, state.anchor!!.distanceAlongTrip!!, 0.0)
    }

    @Test
    fun `recorded uses lastUpdateTime as effectiveTime`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 5000L),
                                serverTimeMs = 10_000L,
                                localTimeMs = 10_000L
                        )
        // effectiveTime = lastUpdateTime = 5000
        assertEquals(5000L, state.anchorTimeMs)
    }

    @Test
    fun `recorded falls back to serverTimeMs when lastUpdateTime is 0`() {
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 0L),
                                serverTimeMs = 10_000L,
                                localTimeMs = 10_000L
                        )
        // effectiveTime = serverTimeMs = 10000
        assertEquals(10_000L, state.anchorTimeMs)
    }

    @Test
    fun `recorded computes anchorLocalTimeMs from server-local offset`() {
        // Server clock is 2 seconds ahead of local clock
        val state =
                TripState.empty("trip1")
                        .recorded(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 5000L),
                                serverTimeMs = 12_000L,
                                localTimeMs = 10_000L
                        )
        // serverLocalOffset = 12000 - 10000 = 2000
        // anchorLocalTimeMs = effectiveTime - offset = 5000 - 2000 = 3000
        assertEquals(3000L, state.anchorLocalTimeMs)
    }

    @Test
    fun `recorded caps history at 100 entries`() {
        var state = TripState.empty("trip1")
        for (i in 1..120) {
            state =
                    state.recorded(
                            status(distanceAlongTrip = i.toDouble(), lastUpdateTime = i.toLong()),
                            serverTimeMs = i.toLong() + 1000,
                            localTimeMs = i.toLong() + 1000
                    )
        }
        assertEquals(100, state.history.size)
        // First entry should be #21 (entries 1-20 were evicted)
        assertEquals(21.0, state.history.first().status.distanceAlongTrip!!, 0.0)
    }

    // ================================================================
    // Test fixture
    // ================================================================

    /** Minimal ObaTripStatus for JVM tests. Never constructs android.location.Location. */
    private fun status(
            distanceAlongTrip: Double? = null,
            totalDistanceAlongTrip: Double? = null,
            lastUpdateTime: Long = 0L,
            predicted: Boolean = false,
            hasLocation: Boolean = false,
            activeTripId: String? = "trip1"
    ): ObaTripStatus = TestTripStatus(
            distanceAlongTrip = distanceAlongTrip,
            totalDistanceAlongTrip = totalDistanceAlongTrip,
            lastUpdateTime = lastUpdateTime,
            predicted = predicted,
            hasLocation = hasLocation,
            activeTripId = activeTripId
    )
}

/**
 * Minimal ObaTripStatus for JVM tests. Returns null for all Location-typed methods
 * so that android.location.Location stub methods are never called.
 */
private class TestTripStatus(
        private val distanceAlongTrip: Double?,
        private val totalDistanceAlongTrip: Double?,
        private val lastUpdateTime: Long,
        private val predicted: Boolean,
        private val hasLocation: Boolean,
        private val activeTripId: String?
) : ObaTripStatus {
    override fun getServiceDate(): Long = 0L
    override fun isPredicted(): Boolean = predicted
    override fun getScheduleDeviation(): Long = 0L
    override fun getVehicleId(): String? = null
    override fun getClosestStop(): String? = null
    override fun getClosestStopTimeOffset(): Long = 0L
    override fun getPosition(): Location? = null
    override fun getActiveTripId(): String? = activeTripId
    override fun getDistanceAlongTrip(): Double? = distanceAlongTrip
    override fun getScheduledDistanceAlongTrip(): Double? = null
    override fun getTotalDistanceAlongTrip(): Double? = totalDistanceAlongTrip
    override fun getOrientation(): Double? = null
    override fun getNextStop(): String? = null
    override fun getNextStopTimeOffset(): Long? = null
    override fun getPhase(): String? = null
    override fun getStatus(): Status? = null
    override fun getLastUpdateTime(): Long = lastUpdateTime
    override fun getLastKnownLocation(): Location? =
            if (hasLocation) FAKE_LOCATION else null
    override fun getLastLocationUpdateTime(): Long = 0L
    override fun getLastKnownDistanceAlongTrip(): Double? = null
    override fun getLastKnownOrientation(): Double? = null
    override fun getBlockTripSequence(): Int = 0
    override fun getOccupancyStatus(): Occupancy? = null

    companion object {
        /**
         * A non-null Location sentinel. On the JVM test classpath the android.jar
         * stubs are present so the class resolves, but its methods throw "Stub!".
         * We only ever null-check this, never call methods on it.
         */
        private val FAKE_LOCATION: Location? = try {
            Location("test")
        } catch (e: RuntimeException) {
            // android.jar stubs throw — but we just need a non-null reference
            // Use Unsafe to allocate without calling the constructor
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val f = unsafeClass.getDeclaredField("theUnsafe")
            f.isAccessible = true
            val unsafe = f.get(null)
            val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
            allocate.invoke(unsafe, Location::class.java) as Location
        }
    }
}
