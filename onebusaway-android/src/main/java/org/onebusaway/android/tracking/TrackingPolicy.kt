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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.ScheduleDeviation

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

/** One upcoming arrival on a tracked row, as lifted out of an arrivals response. */
data class TrackedMatch(
    /** The instant the countdown runs to: the predicted arrival/departure when the server gave a
     *  usable one, else the scheduled — the same choice the arrivals ETA pill makes. */
    val displayTime: ServerTime,
    /** True when [displayTime] is a real-time prediction rather than the timetable. */
    val predicted: Boolean,
    val canceled: Boolean,
    /** The arrival's bucketed schedule-deviation state. Carried as the state rather than a colour so
     *  the card can render it as the platform's own semantic tone as well as a hue. */
    val status: ScheduleDeviation.Status
)

/**
 * One departure as the card shows it: [match] measured against a particular "now". The arrival's own
 * facts are reached through it rather than copied out, so a new one (occupancy, a vehicle id) is
 * added to [TrackedMatch] alone.
 */
data class TrackedDeparture(
    val match: TrackedMatch,
    val eta: Duration,
    /** The whole minutes the card prints — see [etaMinutes]. */
    val etaMinutes: Long
) {
    /** When it is due, for the clock time printed beneath the countdown. */
    val displayTime: ServerTime get() = match.displayTime
    val predicted: Boolean get() = match.predicted
    val canceled: Boolean get() = match.canceled
    val status: ScheduleDeviation.Status get() = match.status
}

/** What a tick says one tracked row's card should do. */
sealed interface TrackingOutcome {

    /**
     * No arrivals response for this row's stop yet — the first fetch is still in flight or still
     * failing. Deliberately distinct from every other outcome: a row nobody has managed to look up
     * has not stopped running and has no numbers to show, so the card holds its placeholder rather
     * than inventing either.
     */
    data object Pending : TrackingOutcome

    /**
     * The row's upcoming departures, soonest first and never empty. This is the whole point of
     * tracking a row rather than a vehicle: when the soonest bus pulls away it simply drops off the
     * front and the one behind it becomes the countdown, instead of the card retiring.
     */
    data class Live(val departures: List<TrackedDeparture>) : TrackingOutcome

    /** Done: the row has no upcoming departures left. Drop it and take its card down. */
    data object Retire : TrackingOutcome
}

/**
 * How long a departure stays on the card past its own time. The rider is boarding in this window,
 * and a number that vanishes the instant it hits zero takes the confirmation away at the one moment
 * it is being looked at.
 */
val TRACKING_LINGER: Duration = 2.minutes

/**
 * How many departures the card lists. The row's strip can run much longer, but a notification is
 * read at a glance: past three the line stops being scannable and starts being a paragraph.
 */
const val TRACKING_MAX_DEPARTURES = 3

/**
 * How long a tracked row keeps running before the session is retired regardless. A row almost always
 * has a next bus, so unlike a pinned vehicle it has no natural end; without this, a rider who tracks
 * a row and forgets leaves a foreground service running until they notice — and, since the tracked
 * set is restored when the app reopens, would find it waiting for them days later. Generous on
 * purpose: the bound exists to end forgotten sessions, not to cut short a real wait.
 */
val MAX_TRACKING_DURATION: Duration = 2.hours

/**
 * How long a card may sit with nothing known about its row before it is given up on. Bounds the
 * other way tracking could outlive its usefulness: a stop whose arrivals fetch keeps failing would
 * otherwise leave an unresolvable card — and the foreground service behind it — up indefinitely.
 */
val TRACKING_PENDING_TIMEOUT: Duration = 2.minutes

/** Poll cadence while the next bus is still a way off — the arrivals screen's own 60s. */
val TRACKING_POLL_INTERVAL: Duration = 60.seconds

/**
 * Poll cadence inside [TRACKING_NEAR_THRESHOLD]. Tightened because this is the stretch where a
 * minute of stale prediction is the difference between walking out now and missing the bus; the
 * request cost is bounded by [MAX_TRACKED_ROUTES] stops and by how briefly the endgame lasts.
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
 * What to do with one tracked row, given every arrival the freshest response holds for it and the
 * server-clock [now].
 *
 * Departures already gone (past [TRACKING_LINGER]) drop off the front; what remains is sorted
 * soonest-first and capped at [TRACKING_MAX_DEPARTURES]. An empty result means the row has nothing
 * upcoming at all — service has ended for the day, or the row has left the arrivals window — and is
 * the row's only natural end.
 */
fun trackingOutcome(matches: List<TrackedMatch>, now: ServerTime): TrackingOutcome {
    val departures = matches
        .map { it.toDeparture(now) }
        .filter { it.eta >= -TRACKING_LINGER }
        .sortedBy { it.eta }
        .take(TRACKING_MAX_DEPARTURES)
    return if (departures.isEmpty()) TrackingOutcome.Retire else TrackingOutcome.Live(departures)
}

private fun TrackedMatch.toDeparture(now: ServerTime) = TrackedDeparture(
    match = this,
    eta = displayTime - now,
    etaMinutes = etaMinutes(displayTime, now)
)

/**
 * Whether a session begun at [startedAt] has outlived [MAX_TRACKING_DURATION] by [now]. Both are the
 * device wall clock — the one clock that survives the process dying, which is the point: a monotonic
 * reading restarts with the service and would let a forgotten session run again from zero every time
 * the app reopened.
 */
fun trackingSessionExpired(startedAt: WallTime, now: WallTime): Boolean = now - startedAt > MAX_TRACKING_DURATION

/** What to do with a card that has been [waited] long with no response for its stop. */
fun pendingOutcome(waited: Duration): TrackingOutcome = if (waited > TRACKING_PENDING_TIMEOUT) TrackingOutcome.Retire else TrackingOutcome.Pending

/**
 * How long to wait before the next arrivals fetch, given the [soonest] remaining ETA across every
 * tracked row (null when nothing is upcoming).
 */
fun trackingPollInterval(soonest: Duration?): Duration = if (soonest != null && soonest <= TRACKING_NEAR_THRESHOLD) {
    TRACKING_POLL_INTERVAL_NEAR
} else {
    TRACKING_POLL_INTERVAL
}

private const val MS_PER_MINUTE = 60 * 1000L
