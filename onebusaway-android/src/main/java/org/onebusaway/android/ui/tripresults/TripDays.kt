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
package org.onebusaway.android.ui.tripresults

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.R
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.tripplan.TripDay

// Naming the day a planned trip is on (#2337). The results show clock times only, so a trip planned for
// tomorrow — or one whose day has already passed — read exactly like one leaving within the hour. These
// say which day a time belongs to whenever that isn't today.
//
// "Today" is the device's: it is the rider's calendar, and the one the times themselves are rendered in
// (DisplayFormat.formatTime formats in the device zone). Comparing a server-clock instant's *calendar
// day* against it is a deliberate crossing, but not a #1612-class one: no duration is measured, and skew
// can only misname a time within that skew of midnight.

/** The calendar day this instant falls on in [zone] — an unwrap for java.time, which wants millis. */
internal fun ServerTime.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

/**
 * The instant a log row prints in its time column, or null for a row that prints none. The column
 * itself reads it from here, so the rows that print a time and the rows [rowDays] can name a day on are
 * one list.
 */
internal val RowContent.clockTime: ServerTime?
    get() = when (this) {
        is RowContent.Terminal -> entry.time
        is RowContent.BoardHeader -> entry.boardTime
        is RowContent.ExitNode -> entry.exitTime
        else -> null
    }

/**
 * Which of [rows] name a day under their clock time, keyed by [LogRowModel.key]: the first timed row
 * whose day isn't [today], and every timed row after it that starts a new day. So a trip later today
 * names nothing; a trip tomorrow names "Tomorrow" once, at its start; and a trip that runs past midnight
 * names the new day at the first time that falls on it — the log reads top to bottom like a diary, each
 * day stated once, where it begins.
 */
internal fun rowDays(rows: List<LogRowModel>, today: LocalDate, zone: ZoneId): Map<Long, LocalDate> {
    val days = mutableMapOf<Long, LocalDate>()
    var current = today
    for (row in rows) {
        val day = row.content.clockTime?.localDate(zone) ?: continue
        if (day != current) {
            days[row.key] = day
            current = day
        }
    }
    return days
}

/** The device's calendar day now — the "today" the results name other days against. */
internal fun deviceToday(): LocalDate = LocalDate.now(ZoneId.systemDefault())

/**
 * [day] as the results name it: "Today" and "Tomorrow" in words, like the trip-plan form's date picker,
 * and any other day as a short weekday-and-date ("Wed, Sep 24") — short because it sits in the trip
 * log's narrow time column and under an option card's time range.
 */
@Composable
internal fun dayName(day: LocalDate, today: LocalDate): String = when (TripDay.of(day, today)) {
    TripDay.TODAY -> stringResource(R.string.trip_plan_date_today)
    TripDay.TOMORROW -> stringResource(R.string.trip_plan_date_tomorrow)
    TripDay.OTHER -> DateUtils.formatDateTime(
        LocalContext.current,
        day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
    )
}
