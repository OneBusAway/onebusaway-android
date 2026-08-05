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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.time.etaMinutes
import org.onebusaway.android.util.ScheduleDeviation

/**
 * The rules the tracking notification lives by: which departures a row shows, when it rolls onto the
 * next one, when it retires, and how its timeline is laid out. Everything here is measured against a
 * *server*-clock now that the test supplies, which is why these decisions live outside the service.
 */
class TrackingPolicyTest {

    private val now = ServerTime(1_700_000_000_000L)

    private fun match(
        inTime: Duration,
        predicted: Boolean = true,
        canceled: Boolean = false
    ) = TrackedMatch(
        displayTime = now + inTime,
        predicted = predicted,
        canceled = canceled,
        status = ScheduleDeviation.Status.ON_TIME
    )

    private fun live(vararg matches: TrackedMatch) = trackingOutcome(matches.toList(), now) as TrackingOutcome.Live

    @Test
    fun `a row lists its upcoming departures soonest first`() {
        val outcome = live(match(24.minutes), match(4.minutes), match(12.minutes))

        assertEquals(listOf(4L, 12L, 24L), outcome.departures.map { it.etaMinutes })
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
    fun `the card lists no more departures than it can show at a glance`() {
        val outcome = live(match(4.minutes), match(12.minutes), match(24.minutes), match(36.minutes))

        assertEquals(TRACKING_MAX_DEPARTURES, outcome.departures.size)
    }

    @Test
    fun `a departure stays on the card through the boarding moment`() {
        val outcome = live(match(-TRACKING_LINGER), match(12.minutes))

        assertEquals(2, outcome.departures.size)
        assertTrue(outcome.departures.first().etaMinutes <= 0)
    }

    @Test
    fun `the card rolls onto the next bus rather than retiring`() {
        // The whole reason tracking follows the row: the bus the rider was watching pulls away and
        // the one behind it becomes the countdown, instead of the card vanishing.
        val outcome = live(match(-TRACKING_LINGER - 1.seconds), match(12.minutes))

        assertEquals(listOf(12L), outcome.departures.map { it.etaMinutes })
    }

    @Test
    fun `a row with nothing upcoming retires`() {
        assertEquals(TrackingOutcome.Retire, trackingOutcome(emptyList(), now))
    }

    @Test
    fun `a row whose last departure has gone retires`() {
        val outcome = trackingOutcome(listOf(match(-TRACKING_LINGER - 1.seconds)), now)

        assertEquals(TrackingOutcome.Retire, outcome)
    }

    @Test
    fun `a cancelled departure is carried through so the card can say so`() {
        val outcome = live(match(4.minutes, canceled = true), match(12.minutes))

        assertTrue(outcome.departures.first().canceled)
    }

    @Test
    fun `a scheduled departure is carried through as scheduled`() {
        assertEquals(false, live(match(4.minutes, predicted = false)).departures.first().predicted)
    }

    @Test
    fun `polling tightens as the next bus gets close`() {
        assertEquals(TRACKING_POLL_INTERVAL, trackingPollInterval(TRACKING_NEAR_THRESHOLD + 1.seconds))
        assertEquals(TRACKING_POLL_INTERVAL_NEAR, trackingPollInterval(TRACKING_NEAR_THRESHOLD))
        assertEquals(TRACKING_POLL_INTERVAL_NEAR, trackingPollInterval(30.seconds))
    }

    @Test
    fun `nothing upcoming polls at the relaxed cadence`() {
        assertEquals(TRACKING_POLL_INTERVAL, trackingPollInterval(null))
    }

    // --- Giving up -----------------------------------------------------------------------------

    @Test
    fun `a session inside its bound keeps running`() {
        val started = WallTime(1_700_000_000_000L)

        assertEquals(false, trackingSessionExpired(started, started + MAX_TRACKING_DURATION))
    }

    @Test
    fun `a forgotten session expires`() {
        val started = WallTime(1_700_000_000_000L)

        assertTrue(trackingSessionExpired(started, started + MAX_TRACKING_DURATION + 1.seconds))
    }

    @Test
    fun `a row stored before sessions were dated reads as long expired`() {
        // Its startedAtMs decodes to 0, which is how those rows should go rather than coming back to
        // life on the next launch.
        assertTrue(trackingSessionExpired(WallTime(0), WallTime(1_700_000_000_000L)))
    }

    @Test
    fun `a card with no data yet holds while the fetches are still young`() {
        assertEquals(TrackingOutcome.Pending, pendingOutcome(TRACKING_PENDING_TIMEOUT))
    }

    @Test
    fun `a card whose stop never answers is given up on`() {
        assertEquals(TrackingOutcome.Retire, pendingOutcome(TRACKING_PENDING_TIMEOUT + 1.seconds))
    }
}
