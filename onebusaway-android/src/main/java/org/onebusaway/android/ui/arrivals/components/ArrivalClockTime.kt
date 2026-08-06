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
package org.onebusaway.android.ui.arrivals.components

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.util.DisplayFormat

// The one place the app decides *and draws* "this bus is no longer coming at its timetable time"
// (issue #2167, parity with onebusaway-ios#1224/#1237). Every surface that prints a clock time for an
// arrival should read its pair from [arrivalClock] and draw it with [CorrectedClockTime], so the rule
// and the words live here rather than being re-derived per call site.

/**
 * An arrival's clock time as a rider should read it: the time it is now [expected] at, and — when a
 * prediction moved it — the timetable time that prediction [corrects], to be shown struck through.
 * [corrects] is null when there is nothing to correct, which is the common case.
 */
internal data class ArrivalClock(val expected: String, val corrects: String?)

/**
 * This arrival's [ArrivalClock], formatted for the current locale/12h-24h setting.
 *
 * The decision is made on the **formatted strings**, not the instants behind them — the same choice
 * onebusaway-ios made, and for two reasons that pull in opposite directions:
 *
 * - A prediction a few seconds off the timetable would otherwise render `10:42 AM 10:42 AM`, striking
 *   through a time and replacing it with itself.
 * - A trip inside the ±90s on-time band ([org.onebusaway.android.util.ScheduleDeviation.ON_TIME_BAND])
 *   can still land on a different clock minute, and that rider's time genuinely needs correcting even
 *   though the app calls the trip on time. A "strike through only when late" rule would miss it.
 *
 * Both fall out of comparing what is actually printed, so there is no threshold to pick here.
 */
internal fun ArrivalInfo.arrivalClock(context: Context): ArrivalClock = arrivalClockOf(
    expected = DisplayFormat.formatTime(context, displayTime.epochMs),
    scheduled = DisplayFormat.formatTime(context, scheduledTime.epochMs)
)

/** The formatted-string rule itself, split out so it is testable without a `Context`. */
@VisibleForTesting
internal fun arrivalClockOf(expected: String, scheduled: String): ArrivalClock = ArrivalClock(
    expected = expected,
    corrects = scheduled.takeIf { it != expected }
)

/** How much fainter the struck timetable time is drawn than the time that replaced it — present, but
 *  clearly the superseded one of the two. Applied to [CorrectedClockTime]'s own [Color], so it works
 *  the same on the pill's white-on-fill text as on a surface-coloured caller. */
private const val CORRECTED_ALPHA = 0.75f

/** The gap between the struck line and the time below it. Zero: [style]'s trimmed line boxes already
 *  sit flush, and these two are one reading rather than two lines of text. */
private val CORRECTED_LINE_SPACING = 0.dp

/**
 * A clock time, with the timetable time it corrects struck through directly above it when there is
 * one ([ArrivalClock.corrects]) — `~~10:42 AM~~` over `10:47 AM`. With nothing to correct this is
 * exactly the plain single [Text] it replaced, adding no layout node of its own.
 *
 * A strikethrough is inaudible, so the corrected pair merges into one spoken phrase — "Scheduled
 * 10:42 AM, now expected 10:47 AM" — rather than leaving a screen reader to read two bare times in a
 * row and imply nothing about their relationship.
 *
 * [canceled] strikes the whole thing through, as a canceled trip's other text is; the timetable line
 * is struck either way.
 */
@Composable
internal fun CorrectedClockTime(
    clock: ArrivalClock,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    canceled: Boolean = false
) {
    val canceledDecoration = if (canceled) TextDecoration.LineThrough else null
    val corrects = clock.corrects
    if (corrects == null) {
        Text(
            text = clock.expected,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            textDecoration = canceledDecoration,
            style = style
        )
        return
    }
    val spoken = stringResource(R.string.stop_info_clock_corrected, corrects, clock.expected)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CORRECTED_LINE_SPACING)
    ) {
        Text(
            text = corrects,
            color = if (color.isSpecified) color.copy(alpha = color.alpha * CORRECTED_ALPHA) else color,
            fontSize = fontSize,
            textDecoration = TextDecoration.LineThrough,
            style = style
        )
        Text(
            text = clock.expected,
            color = color,
            fontSize = fontSize,
            textDecoration = canceledDecoration,
            style = style
        )
    }
}
