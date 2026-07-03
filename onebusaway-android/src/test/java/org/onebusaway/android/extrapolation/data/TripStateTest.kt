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

import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.extrapolation.ExtrapolationResult
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.testing.testTripStatus

class TripStateTest {

    // ================================================================
    // extrapolate gating — item #28
    // ================================================================

    @Test
    fun `extrapolate returns NoData when no anchor`() {
        val state = TripState.empty("trip1")
        val result = state.extrapolate(WallTime(System.currentTimeMillis()))
        assertTrue(result is ExtrapolationResult.NoData)
    }

    @Test
    fun `toServerClock leaves the time unchanged when there is no anchor`() {
        // No skew can be measured without an anchor, so the local clock passes through.
        assertEquals(12_345L, TripState.empty("trip1").toServerClock(WallTime(12_345L)).epochMs)
    }

    @Test
    fun `toServerClock lifts a local instant by the anchor skew`() {
        // Anchor seen at server 105_000 / local 100_000 → server runs 5s ahead.
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 105_000L),
                                serverTimeMs = ServerTime(105_000L),
                                localTimeMs = WallTime(100_000L)
                        )
        assertEquals(107_000L, state.toServerClock(WallTime(102_000L)).epochMs)
    }

    @Test
    fun `extrapolate returns NoData when anchor has null distance`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = null, lastUpdateTime = 1000L),
                                serverTimeMs = ServerTime(1000L),
                                localTimeMs = WallTime(1000L)
                        )
        // withStatus() skips entries with null distance, so anchor stays null
        val result = state.extrapolate(WallTime(1000L))
        assertTrue(result is ExtrapolationResult.NoData)
    }

    @Test
    fun `extrapolate returns NoData when anchorLocalTimeMs is 0`() {
        // serverTimeMs = 0 → withStatus() skips the entry
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 0L),
                                serverTimeMs = ServerTime(0L),
                                localTimeMs = WallTime(0L)
                        )
        val result = state.extrapolate(WallTime(1000L))
        assertTrue(result is ExtrapolationResult.NoData)
    }

    @Test
    fun `extrapolate returns Stale when query is before anchor`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = serverTime),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        // Query time is before anchor local time → dtMs < 0
        val result = state.extrapolate(WallTime(localTime - 1))
        assertTrue(result is ExtrapolationResult.Stale)
    }

    @Test
    fun `extrapolate returns Stale when query exceeds horizon`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = serverTime),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        // MAX_HORIZON_MS = 15 * 60 * 1000 = 900_000
        val result = state.extrapolate(WallTime(localTime + 900_001L))
        assertTrue(result is ExtrapolationResult.Stale)
    }

    @Test
    fun `extrapolate allows a query at exactly max horizon`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = serverTime),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        // The stale check is strict (dtMs > MAX_HORIZON_MS), so dtMs == MAX_HORIZON_MS is
        // still allowed and reaches the extrapolator — which reports MissingSchedule here
        // because this state has no schedule
        val result = state.extrapolate(WallTime(localTime + 900_000L))
        assertTrue(result is ExtrapolationResult.MissingSchedule)
    }

    @Test
    fun `extrapolate returns TripNotStarted when distance below threshold`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        // PRE_DEPARTURE_DISTANCE_THRESHOLD = 50.0
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 49.0, lastUpdateTime = serverTime),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        val result = state.extrapolate(WallTime(localTime + 1000L))
        assertTrue(result is ExtrapolationResult.TripNotStarted)
    }

    @Test
    fun `extrapolate returns TripNotStarted at exactly threshold`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 50.0, lastUpdateTime = serverTime),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        val result = state.extrapolate(WallTime(localTime + 1000L))
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
                        .withStatus(
                                status(
                                        distanceAlongTrip = 9970.0,
                                        totalDistanceAlongTrip = 10000.0,
                                        lastUpdateTime = serverTime
                                ),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        val result = state.extrapolate(WallTime(localTime + 1000L))
        assertTrue(result is ExtrapolationResult.TripEnded)
    }

    @Test
    fun `extrapolate does not return TripEnded when totalDist is null`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(
                                        distanceAlongTrip = 500.0,
                                        totalDistanceAlongTrip = null,
                                        lastUpdateTime = serverTime
                                ),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        val result = state.extrapolate(WallTime(localTime + 1000L))
        // Without totalDist, trip-ended check is skipped; should reach the extrapolator
        assertFalse(result is ExtrapolationResult.TripEnded)
    }

    @Test
    fun `extrapolate does not return TripEnded when totalDist is zero`() {
        val serverTime = 100_000L
        val localTime = 100_000L
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(
                                        distanceAlongTrip = 500.0,
                                        totalDistanceAlongTrip = 0.0,
                                        lastUpdateTime = serverTime
                                ),
                                serverTimeMs = ServerTime(serverTime),
                                localTimeMs = WallTime(localTime)
                        )
        val result = state.extrapolate(WallTime(localTime + 1000L))
        assertFalse(result is ExtrapolationResult.TripEnded)
    }

    // ================================================================
    // withStatus — dedup and anchor logic — items #28/#29
    // ================================================================
    // Skip cases assert assertSame(before, after): withStatus() returning `this`
    // is the contract that lets the store's StateFlow skip the emission.

    @Test
    fun `withStatus skips null distance`() {
        val before = TripState.empty("trip1")
        val after =
                before.withStatus(
                        status(distanceAlongTrip = null, lastUpdateTime = 1000L),
                        serverTimeMs = ServerTime(1000L),
                        localTimeMs = WallTime(1000L)
                )
        assertSame(before, after)
        assertEquals(0, after.history.size)
    }

    @Test
    fun `withStatus skips zero serverTimeMs`() {
        val before = TripState.empty("trip1")
        val after =
                before.withStatus(
                        status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                        serverTimeMs = ServerTime(0L),
                        localTimeMs = WallTime(1000L)
                )
        assertSame(before, after)
        assertEquals(0, after.history.size)
    }

    @Test
    fun `withStatus skips negative serverTimeMs`() {
        val before = TripState.empty("trip1")
        val after =
                before.withStatus(
                        status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                        serverTimeMs = ServerTime(-1L),
                        localTimeMs = WallTime(1000L)
                )
        assertSame(before, after)
    }

    @Test
    fun `withStatus records valid entry`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = ServerTime(2000L),
                                localTimeMs = WallTime(2000L)
                        )
        assertEquals(1, state.history.size)
    }

    @Test
    fun `withStatus skips duplicate distance and time`() {
        val s = status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L)
        val before =
                TripState.empty("trip1").withStatus(s, serverTimeMs = ServerTime(2000L), localTimeMs = WallTime(2000L))
        val after = before.withStatus(s, serverTimeMs = ServerTime(3000L), localTimeMs = WallTime(3000L))
        assertSame(before, after)
        assertEquals(1, after.history.size)
    }

    @Test
    fun `withStatus records entry with different distance`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = ServerTime(2000L),
                                localTimeMs = WallTime(2000L)
                        )
                        .withStatus(
                                status(distanceAlongTrip = 600.0, lastUpdateTime = 1000L),
                                serverTimeMs = ServerTime(3000L),
                                localTimeMs = WallTime(3000L)
                        )
        assertEquals(2, state.history.size)
    }

    @Test
    fun `withStatus records entry with different time`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = ServerTime(2000L),
                                localTimeMs = WallTime(2000L)
                        )
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 2000L),
                                serverTimeMs = ServerTime(3000L),
                                localTimeMs = WallTime(3000L)
                        )
        assertEquals(2, state.history.size)
    }

    @Test
    fun `withStatus updates anchor to newest timestamp`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 1000L),
                                serverTimeMs = ServerTime(2000L),
                                localTimeMs = WallTime(2000L)
                        )
                        .withStatus(
                                status(distanceAlongTrip = 600.0, lastUpdateTime = 3000L),
                                serverTimeMs = ServerTime(4000L),
                                localTimeMs = WallTime(4000L)
                        )
        assertEquals(600.0, state.anchor!!.distanceAlongTrip!!, 0.0)
        assertEquals(3000L, state.anchorTimeMs.epochMs)
    }

    @Test
    fun `withStatus GPS wins ties`() {
        val state =
                TripState.empty("trip1")
                        // First: non-realtime (predicted=false)
                        .withStatus(
                                status(
                                        distanceAlongTrip = 500.0,
                                        lastUpdateTime = 1000L,
                                        predicted = false
                                ),
                                serverTimeMs = ServerTime(2000L),
                                localTimeMs = WallTime(2000L)
                        )
                        // Second: realtime, same lastUpdateTime
                        .withStatus(
                                status(
                                        distanceAlongTrip = 600.0,
                                        lastUpdateTime = 1000L,
                                        predicted = true,
                                        hasLastKnownLocation = true
                                ),
                                serverTimeMs = ServerTime(3000L),
                                localTimeMs = WallTime(3000L)
                        )
        // GPS entry should win the tie
        assertEquals(600.0, state.anchor!!.distanceAlongTrip!!, 0.0)
    }

    @Test
    fun `withStatus uses lastUpdateTime as effectiveTime`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 5000L),
                                serverTimeMs = ServerTime(10_000L),
                                localTimeMs = WallTime(10_000L)
                        )
        // effectiveTime = lastUpdateTime = 5000
        assertEquals(5000L, state.anchorTimeMs.epochMs)
    }

    @Test
    fun `withStatus falls back to serverTimeMs when lastUpdateTime is 0`() {
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 0L),
                                serverTimeMs = ServerTime(10_000L),
                                localTimeMs = WallTime(10_000L)
                        )
        // effectiveTime = serverTimeMs = 10000
        assertEquals(10_000L, state.anchorTimeMs.epochMs)
    }

    @Test
    fun `withStatus computes anchorLocalTimeMs from server-local offset`() {
        // Server clock is 2 seconds ahead of local clock
        val state =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = 5000L),
                                serverTimeMs = ServerTime(12_000L),
                                localTimeMs = WallTime(10_000L)
                        )
        // serverLocalOffset = 12000 - 10000 = 2000
        // anchorLocalTimeMs = effectiveTime - offset = 5000 - 2000 = 3000
        assertEquals(3000L, state.anchorLocalTimeMs.epochMs)
    }

    @Test
    fun `withStatus caps history at 100 entries`() {
        var state = TripState.empty("trip1")
        for (i in 1..120) {
            state =
                    state.withStatus(
                            status(distanceAlongTrip = i.toDouble(), lastUpdateTime = i.toLong()),
                            serverTimeMs = ServerTime(i.toLong() + 1000),
                            localTimeMs = WallTime(i.toLong() + 1000)
                    )
        }
        assertEquals(100, state.history.size)
        // First entry should be #21 (entries 1-20 were evicted)
        assertEquals(21.0, state.history.first().status.distanceAlongTrip!!, 0.0)
    }

    // ================================================================
    // Strategy selection — late-arriving routeType
    // ================================================================

    // The strategy choice is observable from outside: the test status carries no
    // scheduledDistanceAlongTrip, so the gamma (bus) model cannot resolve a speed distribution
    // and reports MissingSchedule even when a schedule is present, while schedule replay needs
    // only the schedule itself and succeeds on the same data.

    @Test
    fun `routeType arriving late re-selects the strategy on the new snapshot`() {
        val localTime = 100_000L
        val unknownType =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = localTime),
                                serverTimeMs = ServerTime(localTime),
                                localTimeMs = WallTime(localTime)
                        )
                        .withSchedule(schedule)

        // No routeType yet → gamma model
        assertTrue(
                unknownType.extrapolate(WallTime(localTime + 10_000L))
                        is ExtrapolationResult.MissingSchedule
        )

        // routeType arrives on a later poll: the copy re-derives the extrapolator, so a
        // grade-separated trip switches to schedule replay instead of staying locked to gamma
        val rail = unknownType.withRouteType(ObaRoute.TYPE_SUBWAY)
        assertTrue(rail.extrapolate(WallTime(localTime + 10_000L)) is ExtrapolationResult.Success)
    }

    @Test
    fun `non grade-separated routeType keeps the gamma strategy`() {
        val localTime = 100_000L
        val bus =
                TripState.empty("trip1")
                        .withStatus(
                                status(distanceAlongTrip = 500.0, lastUpdateTime = localTime),
                                serverTimeMs = ServerTime(localTime),
                                localTimeMs = WallTime(localTime)
                        )
                        .withSchedule(schedule)
                        .withRouteType(ObaRoute.TYPE_BUS)
        assertTrue(bus.extrapolate(WallTime(localTime + 10_000L)) is ExtrapolationResult.MissingSchedule)
    }

    @Test
    fun `withRouteType is first-writer-wins`() {
        val state = TripState.empty("trip1").withRouteType(ObaRoute.TYPE_SUBWAY)
        assertSame(state, state.withRouteType(ObaRoute.TYPE_BUS))
        assertSame(state, state.withRouteType(null))
        assertEquals(ObaRoute.TYPE_SUBWAY, state.routeType)
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
            hasLastKnownLocation: Boolean = false,
            activeTripId: String? = "trip1"
    ): ObaTripStatus = testTripStatus(
            distanceAlongTrip = distanceAlongTrip,
            totalDistanceAlongTrip = totalDistanceAlongTrip,
            lastUpdateTime = lastUpdateTime,
            predicted = predicted,
            hasLastKnownLocation = hasLastKnownLocation,
            activeTripId = activeTripId
    )

    /** Three stops at 0/1000/3000 m — enough for schedule replay to succeed mid-route. */
    private val schedule =
            makeSchedule(
                    Triple(0.0, 0L, 0L),
                    Triple(1000.0, 100L, 130L),
                    Triple(3000.0, 330L, 330L)
            )

    private fun makeSchedule(vararg stops: Triple<Double, Long, Long>): ObaTripSchedule {
        val stopTimes: Array<ObaTripSchedule.StopTime> = Array(stops.size) { i ->
            val (dist, arrive, depart) = stops[i]
            StopTimeData(
                stopId = "stop_$i",
                arrivalTime = arrive,
                departureTime = depart,
                distanceAlongTrip = dist,
            )
        }
        return TripScheduleData(stopTimes)
    }
}
