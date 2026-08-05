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
package org.onebusaway.android.tracking

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.time.ServerTime

/**
 * The rules the tracking notification lives by: when it counts down, when it says the bus is here,
 * and when it takes itself away. Everything here is measured against a *server*-clock now that the
 * test supplies, which is the whole reason these decisions live outside the service.
 */
class TrackingPolicyTest {

    private val now = ServerTime(1_700_000_000_000L)

    private fun match(
        inTime: kotlin.time.Duration,
        predicted: Boolean = true,
        canceled: Boolean = false
    ) = TrackedMatch(
        displayTime = now + inTime,
        predicted = predicted,
        canceled = canceled,
        fillColorRes = 0
    )

    private fun trip(plannedWaitSeconds: Int = 600) = TrackedTrip(
        key = TrackedTripKey("1_100", "1_40", "Downtown Seattle"),
        tripId = "trip_1",
        serviceDate = 1L,
        routeName = "40",
        stopName = "Pine St & 3rd Ave",
        plannedWaitSeconds = plannedWaitSeconds
    )

    @Test
    fun `an upcoming arrival counts down`() {
        val outcome = trackingOutcome(match(4.minutes), now)

        assertEquals(TrackingOutcome.Waiting(4.minutes, etaMinutes = 4, predicted = true), outcome)
    }

    @Test
    fun `the minutes shown match the arrivals pill exactly`() {
        // Floor each instant to its minute, then subtract — not the whole minutes of the difference.
        // A 40-second gap that straddles a minute boundary reads "1 min" on the arrivals pill, so it
        // reads "1 min" in the shade too; `(displayTime - now).inWholeMinutes` would say 0.
        val displayTime = ServerTime(1_700_000_040_000L) // exactly on a minute boundary
        val fortySecondsEarlier = ServerTime(1_700_000_000_000L)

        assertEquals(0L, (displayTime - fortySecondsEarlier).inWholeMinutes)
        assertEquals(1L, etaMinutes(displayTime, fortySecondsEarlier))
    }

    @Test
    fun `a scheduled arrival counts down as scheduled`() {
        val outcome = trackingOutcome(match(4.minutes, predicted = false), now)

        assertEquals(false, (outcome as TrackingOutcome.Waiting).predicted)
    }

    @Test
    fun `the bus is arriving the moment the countdown reaches zero`() {
        assertEquals(TrackingOutcome.Arriving, trackingOutcome(match(0.seconds), now))
    }

    @Test
    fun `a card stays up through the boarding moment`() {
        assertEquals(TrackingOutcome.Arriving, trackingOutcome(match(-TRACKING_LINGER), now))
    }

    @Test
    fun `a card retires once the linger is over`() {
        val outcome = trackingOutcome(match(-TRACKING_LINGER - 1.seconds), now)

        assertEquals(TrackingOutcome.Retire, outcome)
    }

    @Test
    fun `a trip that has left the arrivals window retires`() {
        assertEquals(TrackingOutcome.Retire, trackingOutcome(null, now))
    }

    @Test
    fun `a cancelled trip says so instead of counting down`() {
        assertEquals(TrackingOutcome.Canceled, trackingOutcome(match(4.minutes, canceled = true), now))
    }

    @Test
    fun `a cancelled trip still retires on the usual linger`() {
        val outcome = trackingOutcome(match(-TRACKING_LINGER - 1.seconds, canceled = true), now)

        assertEquals(TrackingOutcome.Retire, outcome)
    }

    @Test
    fun `polling tightens as the bus gets close`() {
        assertEquals(TRACKING_POLL_INTERVAL, trackingPollInterval(TRACKING_NEAR_THRESHOLD + 1.seconds))
        assertEquals(TRACKING_POLL_INTERVAL_NEAR, trackingPollInterval(TRACKING_NEAR_THRESHOLD))
        assertEquals(TRACKING_POLL_INTERVAL_NEAR, trackingPollInterval(30.seconds))
    }

    @Test
    fun `nothing waiting polls at the relaxed cadence`() {
        assertEquals(TRACKING_POLL_INTERVAL, trackingPollInterval(null))
    }

    @Test
    fun `progress advances across the wait`() {
        val trip = trip(plannedWaitSeconds = 600)

        assertEquals(0, trackingProgress(trip, trackingOutcome(match(10.minutes), now)))
        assertEquals(300, trackingProgress(trip, trackingOutcome(match(5.minutes), now)))
    }

    @Test
    fun `a delay walks the tracker backwards rather than off the bar`() {
        // The bus was 10 minutes out when tracked and is now 15 minutes out. The honest rendering is
        // the tracker back at the start, not a silently rescaled bar that only ever advances.
        val trip = trip(plannedWaitSeconds = 600)

        assertEquals(0, trackingProgress(trip, trackingOutcome(match(15.minutes), now)))
    }

    @Test
    fun `an arrived trip fills the bar`() {
        val trip = trip(plannedWaitSeconds = 600)

        assertEquals(600, trackingProgress(trip, trackingOutcome(match(0.seconds), now)))
    }

    @Test
    fun `a card with no data yet holds while the fetches are still young`() {
        assertEquals(TrackingOutcome.Pending, pendingOutcome(TRACKING_PENDING_TIMEOUT))
    }

    @Test
    fun `a card whose stop never answers is given up on`() {
        assertEquals(TrackingOutcome.Retire, pendingOutcome(TRACKING_PENDING_TIMEOUT + 1.seconds))
    }

    @Test
    fun `nothing known yet leaves the bar at the start`() {
        assertEquals(0, trackingProgress(trip(), TrackingOutcome.Pending))
    }

    @Test
    fun `a bus tracked with no wait left still has a bar to fill`() {
        // A zero span would make the progress bar undrawable; the floor keeps it well-formed.
        assertEquals(1, trackingProgressMax(trip(plannedWaitSeconds = 0)))
    }
}
