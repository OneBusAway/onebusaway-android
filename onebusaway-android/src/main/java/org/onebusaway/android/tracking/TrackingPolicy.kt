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
import kotlin.time.Duration.Companion.hours
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

/** One upcoming arrival on a tracked row, as lifted out of an arrivals response. */
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

/** One departure as the card shows it: the same arrival, measured against a particular "now". */
data class TrackedDeparture(
    val eta: Duration,
    /** The whole minutes the card prints — see [etaMinutes]. */
    val etaMinutes: Long,
    val predicted: Boolean,
    val canceled: Boolean,
    @param:ColorRes val fillColorRes: Int
)

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
 * The shortest span the progress bar is drawn over. Without a floor, a lone departure two minutes
 * out would span the whole bar and the tracker would never appear to move; with it, a single
 * imminent bus reads as what it is — nearly here.
 */
val TRACKING_MIN_HORIZON: Duration = 15.minutes

/**
 * How long a tracked row keeps running before the session is retired regardless. A row almost always
 * has a next bus, so unlike a pinned vehicle it has no natural end; without this, a rider who tracks
 * a row and forgets leaves a foreground service running until they notice. Generous on purpose — the
 * bound exists to end forgotten sessions, not to cut short a real wait.
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
    eta = displayTime - now,
    etaMinutes = etaMinutes(displayTime, now),
    predicted = predicted,
    canceled = canceled,
    fillColorRes = fillColorRes
)

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

/*
 * The progress bar is a timeline of the row, laid out left-to-right in time:
 *
 *   0 ─────────────●━━━━━━━━━╸────○──────────○───────── span
 *   (the stop)     tracker: your next bus    later departures
 *
 * The span runs out to the furthest departure shown (never shorter than [TRACKING_MIN_HORIZON]), the
 * tracker sits at the next one, and each departure after that is a point further along. So the
 * filled part is exactly the wait the rider is serving, and it drains as the bus closes in; when
 * that bus goes, the next becomes the tracker and the bar re-spans around what is left.
 *
 * Everything is derived per render from the departures themselves — nothing is captured at Track
 * time — so a delay simply moves the marks rather than silently rescaling a bar underneath them.
 */

/** The bar's span in seconds: out to the furthest shown departure, floored at [TRACKING_MIN_HORIZON]. */
fun trackingProgressMax(departures: List<TrackedDeparture>): Int = maxOf(departures.last().eta, TRACKING_MIN_HORIZON)
    .inWholeSeconds
    .coerceAtLeast(1L)
    .toInt()

/** Where the tracker sits: the next departure, in seconds along the bar. */
fun trackingProgress(departures: List<TrackedDeparture>): Int = departures.first().eta.clampToBar(trackingProgressMax(departures))

/** The departures after the next one, as points along the bar. */
fun trackingProgressPoints(departures: List<TrackedDeparture>): List<Int> {
    val span = trackingProgressMax(departures)
    return departures.drop(1).map { it.eta.clampToBar(span) }
}

/** Seconds along a bar of [span], with a just-departed (negative) ETA pinned to the near end. */
private fun Duration.clampToBar(span: Int): Int = inWholeSeconds.coerceIn(0L, span.toLong()).toInt()

private const val MS_PER_MINUTE = 60 * 1000L
