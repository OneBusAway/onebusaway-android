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

import androidx.annotation.ColorRes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.time.ServerTime

/**
 * Every rule the trip-tracking notification obeys, as pure functions over a server-clock "now".
 *
 * Split out from [TripTrackingService] on purpose: this is the part that decides what the rider
 * sees and when the card retires, and it is exactly the part that is impossible to exercise through
 * a foreground service and a real network. Nothing here reads a clock — the "now" is always a
 * parameter (CLAUDE.md's time-domain rule), and it is always the **server** clock, because a
 * notification that survives a device sleep is precisely where device clock skew shows up as a
 * wrong number on the Lock Screen.
 */

/** The freshest facts about one tracked trip, lifted out of an arrivals response. */
data class TrackedMatch(
    /** The instant the countdown runs to: the predicted arrival/departure when the server gave a
     *  usable one, else the scheduled — the same choice the arrivals ETA pill makes. */
    val displayTime: ServerTime,
    /** True when [displayTime] is a real-time prediction rather than the timetable. */
    val predicted: Boolean,
    val canceled: Boolean,
    /** The schedule-deviation fill colour of the arrival this was lifted from, so the card is tinted
     *  by lateness exactly like its ETA pill. */
    @param:ColorRes val fillColorRes: Int
)

/** What a tick says one tracked trip's card should do. */
sealed interface TrackingOutcome {

    /**
     * No arrivals response for this trip's stop yet — the first fetch is still in flight or still
     * failing. Deliberately distinct from every other outcome: a trip nobody has managed to look up
     * has not departed, has not been cancelled, and has no number to count down, so the card holds
     * its placeholder rather than inventing one of those.
     */
    data object Pending : TrackingOutcome

    /**
     * Still upcoming. [etaMinutes] is the number the card shows; [eta] is the exact remaining
     * interval, which drives the progress bar and the poll cadence.
     */
    data class Waiting(
        val eta: Duration,
        val etaMinutes: Long,
        val predicted: Boolean
    ) : TrackingOutcome

    /** At the stop now, or moments past it — the hand-off moment the whole feature exists for. */
    data object Arriving : TrackingOutcome

    /** The agency cancelled this trip. Shown, then retired on the usual linger. */
    data object Canceled : TrackingOutcome

    /** Done: drop the trip from the tracked set and take its card down. */
    data object Retire : TrackingOutcome
}

/**
 * How long a card stays up past its own arrival time before retiring. The rider is boarding in this
 * window, and a card that vanishes the instant the countdown hits zero takes the confirmation away
 * at the one moment it is being looked at.
 */
val TRACKING_LINGER: Duration = 2.minutes

/**
 * How long a card may sit with nothing known about its trip before it is given up on. Bounds the
 * one way tracking could otherwise outlive its usefulness indefinitely: a stop whose arrivals fetch
 * keeps failing would leave an unresolvable card — and the foreground service behind it — up for as
 * long as the rider left it there.
 */
val TRACKING_PENDING_TIMEOUT: Duration = 2.minutes

/** What to do with a card that has been [waited] long with no response for its stop. */
fun pendingOutcome(waited: Duration): TrackingOutcome = if (waited > TRACKING_PENDING_TIMEOUT) TrackingOutcome.Retire else TrackingOutcome.Pending

/** Poll cadence while the bus is still a way off — the arrivals screen's own 60s. */
val TRACKING_POLL_INTERVAL: Duration = 60.seconds

/**
 * Poll cadence inside [TRACKING_NEAR_THRESHOLD]. Tightened because this is the stretch where a
 * minute of stale prediction is the difference between walking out now and missing the bus; the
 * request cost is bounded by [MAX_TRACKED_TRIPS] stops and by how briefly the endgame lasts.
 */
val TRACKING_POLL_INTERVAL_NEAR: Duration = 20.seconds

/** Where the countdown stops being background information and starts being a decision. */
val TRACKING_NEAR_THRESHOLD: Duration = 5.minutes

/**
 * How often the cards are re-rendered between polls. Faster than the poll so the countdown keeps
 * advancing against the server clock projected forward locally, rather than freezing for a minute
 * at a time; the service suppresses a re-post whose rendered content is unchanged, so a tick that
 * changes nothing costs nothing.
 */
val TRACKING_TICK: Duration = 15.seconds

/**
 * The ETA in whole minutes: each instant floored to its minute and *then* subtracted, which is what
 * `ArrivalInfo.liveEta` does. Reproduced rather than approximated with [Duration.inWholeMinutes]
 * because the two disagree by a minute for most of every minute, and a shade card that says "3 min"
 * beside a drawer pill saying "4" reads as a bug in both.
 */
fun etaMinutes(displayTime: ServerTime, now: ServerTime): Long = displayTime.epochMs / MS_PER_MINUTE - now.epochMs / MS_PER_MINUTE

/**
 * What to do with one tracked trip, given the freshest [match] for it (null when the instance is no
 * longer in the arrivals window at all) and the server-clock [now].
 */
fun trackingOutcome(match: TrackedMatch?, now: ServerTime): TrackingOutcome {
    // Gone from the response entirely: either it has long since departed and fallen out of the
    // window, or the trip was dropped from the feed. Either way there is nothing left to count down
    // to, and continuing to show the last known number would be inventing data.
    if (match == null) return TrackingOutcome.Retire

    val eta = match.displayTime - now
    if (eta < -TRACKING_LINGER) return TrackingOutcome.Retire
    if (match.canceled) return TrackingOutcome.Canceled
    if (eta <= Duration.ZERO) return TrackingOutcome.Arriving
    return TrackingOutcome.Waiting(
        eta = eta,
        etaMinutes = etaMinutes(match.displayTime, now),
        predicted = match.predicted
    )
}

/**
 * How long to wait before the next arrivals fetch, given the [soonest] remaining ETA across every
 * tracked trip (null when nothing is waiting — an arriving or cancelled card needs no more data,
 * but the loop still ticks it down to its retirement).
 */
fun trackingPollInterval(soonest: Duration?): Duration = if (soonest != null && soonest <= TRACKING_NEAR_THRESHOLD) {
    TRACKING_POLL_INTERVAL_NEAR
} else {
    TRACKING_POLL_INTERVAL
}

/**
 * How far along the rider's wait a tracked trip is, in seconds, against a span of
 * [TrackedTrip.plannedWaitSeconds].
 *
 * Clamped at both ends, so an arrival that slips past the originally-promised time parks the
 * tracker at the start of the bar rather than running off it. A delay genuinely does move the
 * tracker backwards — that is the honest rendering of "it got further away", and the alternative
 * (rescaling the span on every poll) is a bar that only ever advances while the bus recedes.
 */
fun trackingProgress(trip: TrackedTrip, outcome: TrackingOutcome): Int {
    val span = trackingProgressMax(trip)
    return when (outcome) {
        // Nothing known yet, so nothing has been covered; the bar is drawn indeterminate anyway.
        TrackingOutcome.Pending -> 0
        // Here, cancelled, or done — the wait is over either way, and a full bar says so.
        is TrackingOutcome.Waiting -> (span - outcome.eta.inWholeSeconds).coerceIn(0L, span.toLong()).toInt()
        else -> span
    }
}

/** The progress bar's span for [trip]; at least one second, so a bar always has somewhere to go. */
fun trackingProgressMax(trip: TrackedTrip): Int = trip.plannedWaitSeconds.coerceAtLeast(1)

private const val MS_PER_MINUTE = 60 * 1000L
