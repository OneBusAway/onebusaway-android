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
package org.onebusaway.android.map.render

import android.content.res.Resources
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.onebusaway.android.R

/**
 * Formats [elapsedSeconds] as "Data updated N min M sec ago" (or "… M sec ago" under a minute) — the
 * snippet on the selected vehicle's most-recent-data dot, in both map flavors.
 *
 * Lives beside the renderers rather than in `map/compose` because that is now its only home: it was
 * shared with the vehicle info window until the marker itself took over what that window said (#2194).
 *
 * The caller measures the age; this only words it. That keeps the server-vs-device clock choice at the
 * call site, where the paired timestamps are (#1612) — the dot's age is a server-clock interval.
 */
internal fun formatDataAge(res: Resources, elapsedSeconds: Long): String {
    val s = elapsedSeconds.coerceAtLeast(0)
    return if (s < 60) {
        res.getQuantityString(R.plurals.vehicle_last_updated_sec, s.toInt(), s)
    } else {
        res.getQuantityString(
            R.plurals.vehicle_last_updated_min_and_sec,
            (s % 60).toInt(),
            TimeUnit.SECONDS.toMinutes(s),
            s % 60
        )
    }
}

/**
 * A vehicle's data age in the one unit a rider reads it in, whole and rounded down; the map focus
 * banner's short form ("Updated 1 min ago"), beside [formatDataAge]'s exact one (#2471).
 */
internal sealed interface DataAgeUnit {
    val count: Int

    data class Seconds(override val count: Int) : DataAgeUnit
    data class Minutes(override val count: Int) : DataAgeUnit
    data class Hours(override val count: Int) : DataAgeUnit

    companion object {
        /**
         * A report from after its own poll (a clock quirk upstream) reads as just now. From a day on there
         * is nothing useful to say in a line this short, and the bus is stale by any measure: null.
         */
        fun of(age: Duration): DataAgeUnit? = when {
            age < 1.minutes -> Seconds(age.inWholeSeconds.coerceAtLeast(0L).toInt())
            age < 1.hours -> Minutes(age.inWholeMinutes.toInt())
            age < 1.days -> Hours(age.inWholeHours.toInt())
            else -> null
        }
    }
}

/** "Updated 17 sec ago", "Updated 1 min ago" — the unit abbreviated without a period. */
internal fun DataAgeUnit.text(res: Resources): String = res.getQuantityString(
    when (this) {
        is DataAgeUnit.Seconds -> R.plurals.focus_banner_updated_sec
        is DataAgeUnit.Minutes -> R.plurals.focus_banner_updated_min
        is DataAgeUnit.Hours -> R.plurals.focus_banner_updated_hr
    },
    count,
    count
)
