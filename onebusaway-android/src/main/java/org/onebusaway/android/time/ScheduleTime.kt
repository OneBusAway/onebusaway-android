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
package org.onebusaway.android.time

import kotlin.time.Duration

/*
 * Civil (schedule) time — the third kind of time, distinct from the clock instants in TypedTime.kt.
 *
 * A clock instant (`ServerTime`/`WallTime`/`ElapsedTime`) is a point on a timeline. A schedule time is
 * *not*: "the 08:15 to Ballard" denotes a different instant on every service day. So a schedule time
 * has no clock domain until it is resolved against a concrete service day — it is the recurring civil
 * label, the service day is the interpretation context, and [ScheduleTime.resolve] is the one place
 * that pairs them to land on the [ServerTime] timeline. (This is the same split java.time draws between
 * `LocalTime` and `Instant`, specialized to GTFS's "time since the service day start".)
 *
 * These are pure domain wrappers, mirroring TypedTime.kt: mint at the boundary, unwrap only at a
 * platform edge (formatting, a plotting coordinate). The offset→millis and DST conventions live in
 * exactly one place — [ScheduleTime.resolve] — so no other site re-derives them.
 */

/**
 * A **service-day anchor**: the OBA server's service date, as epoch millis at that day's start.
 *
 * Opaque on purpose. The only thing the client does with it is anchor a [ScheduleTime]; it is never
 * subtracted or ordered as an instant (it is a calendar date, not a point on the clock), so it exposes
 * no arithmetic. The primary constructor takes the value the **server** sends as the service date —
 * the GTFS "noon minus 12 hours" anchor — which is what makes [ScheduleTime.resolve] DST-correct. The
 * one off-contract source (device-local midnight, for the schedule-only path) is minted through the
 * named [ServiceDate.approximateFromDeviceMidnight] instead, so the two provenances stay distinguishable
 * at a grep.
 */
@JvmInline
value class ServiceDate(val epochMs: Long) {
    companion object {
        /**
         * A **fallback** anchor for the schedule-only path (no vehicle, so the server sent no service
         * date): device-local civil midnight, taken from the device clock. Named apart from the plain
         * constructor because it does **not** meet the server-anchor contract above — it is a
         * device-clock quantity, so it carries the device's clock skew, and on the two DST-transition
         * days a year it sits an hour off the GTFS noon-minus-12h anchor. Schedule times
         * [ScheduleTime.resolve]d against it inherit that slop. Use only where an approximate day is
         * better than none; never treat its result as interchangeable with a server-sent date.
         */
        fun approximateFromDeviceMidnight(deviceMidnightMs: Long): ServiceDate =
            ServiceDate(deviceMidnightMs)
    }
}

/**
 * A **schedule time**: the offset from a [ServiceDate] to a scheduled event, i.e. GTFS "time since the
 * service day start" (`ObaTripSchedule.StopTime.arrivalTime`/`departureTime`). Recurring civil time,
 * not an instant — it carries no clock domain until [resolve]d against a concrete service day.
 *
 * Unlike the clock instants (torsors, with no canonical zero), schedule time has a real origin — the
 * service-day start — so it is represented literally as its [Duration] offset from that origin. That
 * choice also buys the interpolation algebra for free: [Duration] scales by a `Double` and divides to
 * a `Double`, exactly the two operations walking a fractional point between whole-second stop times
 * needs (see `ScheduleReplayExtrapolator`). Same-domain subtraction yields the **scheduled interval**
 * between two points; the group action ([plus]/[minus] a [Duration]) shifts a point by an elapsed
 * interval. There is deliberately no addition of two schedule times and no comparison against any clock
 * instant — the only bridge to the timeline is [resolve].
 */
@JvmInline
value class ScheduleTime(val sinceServiceDayStart: Duration) : Comparable<ScheduleTime> {

    override fun compareTo(other: ScheduleTime): Int =
        sinceServiceDayStart.compareTo(other.sinceServiceDayStart)

    /** The scheduled interval from [other] to this schedule point. Same-domain only. */
    operator fun minus(other: ScheduleTime): Duration =
        sinceServiceDayStart - other.sinceServiceDayStart

    /** This schedule point shifted later by [elapsed] (the group action); still schedule time. */
    operator fun plus(elapsed: Duration): ScheduleTime = ScheduleTime(sinceServiceDayStart + elapsed)

    /** This schedule point shifted earlier by [elapsed]. Distinct from `minus(ScheduleTime)` by type. */
    operator fun minus(elapsed: Duration): ScheduleTime = ScheduleTime(sinceServiceDayStart - elapsed)

    /**
     * Resolves this recurring schedule time to a concrete server-clock instant on [day].
     *
     * The single sanctioned schedule→instant adapter — the civil-time analogue of the API layer's
     * `situationEpochToMillis`. `day.epochMs + offset` is DST-correct **only** because the OBA server
     * defines the service date as the GTFS noon-minus-12h anchor and has already done the timezone
     * arithmetic upstream, so a fixed 86 400-second day plus the raw offset lands on the right wall
     * instant even across a DST transition. The client trusts that server convention here rather than
     * re-deriving it from a timezone; its failure mode is a schedule drawn one hour off if a server
     * ever emitted a non-GTFS-conformant service date. Lands in [ServerTime] because schedules are
     * always compared against the server's "now", never the device clock (CLAUDE.md "Time domains").
     */
    fun resolve(day: ServiceDate): ServerTime =
        ServerTime(day.epochMs + sinceServiceDayStart.inWholeMilliseconds)
}
