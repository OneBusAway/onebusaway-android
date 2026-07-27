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
     * The **foreground** deviation color — drawn as the text or glyph color on the app surface.
     * See [Status.colorRes].
     */
    @ColorRes
    fun statusColor(isRealtime: Boolean, deviation: Duration): Int = status(isRealtime, deviation).colorRes

    /**
     * The **on-fill** deviation color — for a filled surface that carries white text, such as an ETA
     * pill or a starred-stop badge. See [Status.fillColorRes].
     */
    @ColorRes
    fun fillColor(isRealtime: Boolean, deviation: Duration): Int = status(isRealtime, deviation).fillColorRes

    /**
     * The four display states a schedule deviation can take.
     *
     * Each state owns both of its colors, so a new state cannot be added without deciding what it
     * looks like on both kinds of surface. (The starred-stop badge used to recover the state by
     * reverse-matching the returned color resource id, which silently fell through to "scheduled"
     * for anything unrecognized.)
     */
    enum class Status(@param:ColorRes val colorRes: Int, @param:ColorRes val fillColorRes: Int) {
        /** Running ahead of schedule — the rider can miss it, so this is a warning, not praise. */
        EARLY(R.color.stop_info_early, R.color.stop_info_early_fill),

        /** Within [ON_TIME_BAND] of schedule. */
        ON_TIME(R.color.stop_info_ontime, R.color.stop_info_ontime_fill),

        /** Running behind schedule. */
        DELAYED(R.color.stop_info_delayed, R.color.stop_info_delayed_fill),

        /** No real-time prediction — a timetable time, not a measurement. */
        SCHEDULED(R.color.stop_info_scheduled_time, R.color.stop_info_scheduled_fill)
    }
}
