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

package org.onebusaway.android.util

import androidx.annotation.ColorRes
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.R

/**
 * The single source of truth for "how far off schedule is this vehicle, and what color says so"
 * (#2043).
 *
 * Every surface that expresses schedule deviation with color — the arrivals drawer, the ETA pills,
 * the map's vehicle info window, trip details, the trip planner's real-time chips, the starred-stop
 * badges and the help legend — resolves through here, so they cannot drift apart on which hue means
 * what or on what counts as "on time". Before this existed there were five different rules in the
 * app (whole-minute floors, `inWholeMinutes` truncation, `roundToLong`, and one site that passed
 * milliseconds to a parameter documented as minutes).
 *
 * Deviation is a [Duration]: positive means the vehicle is running **late**, negative means
 * **early**. Callers pass the deviation at full precision; the band is applied here and only here.
 */
object ScheduleDeviation {

    /**
     * Half-width of the on-time band: a vehicle within this much of its scheduled time reads as on
     * time rather than early or late.
     *
     * **Sanctioned threshold.** `CLAUDE.md` ("No unsanctioned heuristics") gates magic thresholds on
     * explicit human sign-off, because a number picked by feel passes the happy path and misbehaves
     * silently at the edges. This one is not invented here: it is the band OBA iOS has shipped for
     * years — `ArrivalDeparture.scheduleStatus`, `ArrivalDeparture.swift:305`, which buckets on
     * `minutesDiff < -1.5` / `< 1.5` — and issue #2043 adopts it verbatim to bring the two apps to
     * parity. The named upstream source *is* the justification; it was approved on that issue rather
     * than chosen by an implementer.
     *
     * Failure mode if it drifts from iOS: the two apps disagree about whether the same vehicle is
     * "on time", which is precisely the defect #2043 set out to remove.
     */
    val ON_TIME_BAND: Duration = 90.seconds

    /**
     * Bucket a deviation into a display state, matching iOS's boundary handling exactly: the band is
     * half-open, so a deviation of exactly -[ON_TIME_BAND] is [ON_TIME] while exactly
     * +[ON_TIME_BAND] is [DELAYED].
     *
     * [isRealtime] false means we have no prediction to compare against, so [deviation] is ignored
     * entirely and the state is [SCHEDULED] regardless of what it holds.
     */
    fun status(isRealtime: Boolean, deviation: Duration): Status = when {
        !isRealtime -> Status.SCHEDULED
        deviation < -ON_TIME_BAND -> Status.EARLY
        deviation < ON_TIME_BAND -> Status.ON_TIME
        else -> Status.DELAYED
    }

    /**
     * The deviation rounded to the nearest whole minute — how a deviation is *worded* once [status]
     * has decided which bucket it falls in. Rounding (rather than truncating) is what keeps the
     * wording consistent with the band: the band's edges are at 1.5 minutes, so anything outside it
     * words as at least "2 min", never a "1 min late" that reads as inside the on-time window.
     */
    fun roundedMinutes(deviation: Duration): Long = (deviation.inWholeSeconds / 60.0).roundToLong()

    /**
     * The **display** deviation color — the iOS value, for large text and graphics. See
     * [Status.displayColorRes], and prefer [textColor] for anything set at a normal size.
     */
    @ColorRes
    fun displayColor(isRealtime: Boolean, deviation: Duration): Int = status(isRealtime, deviation).displayColorRes

    /**
     * The **text** deviation color — small text drawn on the app surface. See [Status.textColorRes].
     */
    @ColorRes
    fun textColor(isRealtime: Boolean, deviation: Duration): Int = status(isRealtime, deviation).textColorRes

    /**
     * The **on-fill** deviation color — for a filled surface that carries white text, such as an ETA
     * pill or a starred-stop badge. See [Status.fillColorRes].
     */
    @ColorRes
    fun fillColor(isRealtime: Boolean, deviation: Duration): Int = status(isRealtime, deviation).fillColorRes

    /**
     * The four display states a schedule deviation can take.
     *
     * Each state owns all three of its colors, so a new state cannot be added without deciding what
     * it looks like on every kind of surface. (The starred-stop badge used to recover the state by
     * reverse-matching the returned color resource id, which silently fell through to "scheduled" for
     * anything unrecognized.)
     *
     * The three tiers exist because the same state is drawn against three different grounds, each with
     * its own WCAG floor — they are not stylistic variants:
     *  - [displayColorRes] — the iOS color, for large text and graphics (3:1). The arrivals drawer's
     *    30sp ETA, which is the most visible deviation surface in the app and so the one that carries
     *    the parity.
     *  - [textColorRes] — small text on the app surface (4.5:1); the trip-planner chip, the directions
     *    time span. Mode-dependent, since it must contrast with a surface that inverts.
     *  - [fillColorRes] — a filled surface with white text on it (4.5:1); the ETA pills, the
     *    starred-stop badges, the status pills. Mode-independent, since white doesn't move.
     */
    enum class Status(
        @param:ColorRes val displayColorRes: Int,
        @param:ColorRes val textColorRes: Int,
        @param:ColorRes val fillColorRes: Int
    ) {
        /** Running ahead of schedule — the rider can miss it, so this is a warning, not praise. */
        EARLY(R.color.stop_info_early, R.color.stop_info_early_text, R.color.stop_info_early_fill),

        /** Within [ON_TIME_BAND] of schedule. */
        ON_TIME(R.color.stop_info_ontime, R.color.stop_info_ontime_text, R.color.stop_info_ontime_fill),

        /** Running behind schedule. */
        DELAYED(R.color.stop_info_delayed, R.color.stop_info_delayed_text, R.color.stop_info_delayed_fill),

        /** No real-time prediction — a timetable time, not a measurement. */
        SCHEDULED(
            R.color.stop_info_scheduled_time,
            R.color.stop_info_scheduled_text,
            R.color.stop_info_scheduled_fill
        )
    }
}
