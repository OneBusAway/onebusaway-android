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
        displayColorRes = 0
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

    // --- The road to the stop -------------------------------------------------------------------

    @Test
    fun `a bus arriving now is at the stop end of the bar`() {
        assertEquals(trackingBarSpan(), trackingBarPosition(Duration.ZERO))
    }

    @Test
    fun `a bus a full horizon away is at the far end`() {
        assertEquals(0, trackingBarPosition(TRACKING_HORIZON))
    }

    @Test
    fun `a bus drives toward the stop as it closes in`() {
        // The whole point of the flip: the rider stands still at the right-hand end and the buses
        // come to them, so a shorter ETA is further right.
        assertTrue(trackingBarPosition(4.minutes) > trackingBarPosition(12.minutes))
        assertTrue(trackingBarPosition(12.minutes) > trackingBarPosition(24.minutes))
    }

    @Test
    fun `the nearest bus leads and the rest trail behind it`() {
        val departures = live(match(4.minutes), match(12.minutes), match(24.minutes)).departures
        val positions = departures.map { trackingBarPosition(it.eta) }

        assertEquals(positions.sortedDescending(), positions)
    }

    @Test
    fun `a bus beyond the horizon waits at the far end`() {
        assertEquals(0, trackingBarPosition(TRACKING_HORIZON + 20.minutes))
    }

    @Test
    fun `a bus at the stop stays pinned there rather than running off the end`() {
        // Still inside its linger, so it is on the card with a negative ETA.
        assertEquals(trackingBarSpan(), trackingBarPosition(-30.seconds))
    }

    @Test
    fun `the scale never moves`() {
        // A constant span is what makes the motion real: a minute of waiting is the same distance
        // whatever else is on the bar, so the buses travel at their true speed.
        assertEquals(TRACKING_HORIZON.inWholeSeconds.toInt(), trackingBarSpan())
    }

    // --- Giving up -----------------------------------------------------------------------------

    @Test
    fun `a card with no data yet holds while the fetches are still young`() {
        assertEquals(TrackingOutcome.Pending, pendingOutcome(TRACKING_PENDING_TIMEOUT))
    }

    @Test
    fun `a card whose stop never answers is given up on`() {
        assertEquals(TrackingOutcome.Retire, pendingOutcome(TRACKING_PENDING_TIMEOUT + 1.seconds))
    }
}
