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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.util.DisplayFormat

// The one place the app decides *and draws* "this bus is no longer coming at its timetable time"
// (issue #2167, parity with onebusaway-ios#1224/#1237), so every surface that prints a clock time for
// an arrival can take both from here rather than re-deriving them.

/**
 * An arrival's clock time as a rider should read it: the time it is now [expected] at, and — when a
 * prediction moved it — the timetable time that prediction [corrects], to be shown struck through.
 * [corrects] is null when there is nothing to correct, which is the common case.
 *
 * A plain display pair, not a validated one: [arrivalClockOf] is the canonical producer and will never
 * hand back a [corrects] equal to [expected], but the constructor doesn't enforce that, so a caller
 * building one by hand (the strip's measured-only reference pill) can.
 */
internal data class ArrivalClock(val expected: String, val corrects: String? = null)

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
internal fun ArrivalInfo.arrivalClock(context: Context): ArrivalClock {
    val expected = DisplayFormat.formatTime(context, displayTime.epochMs)
    // Without a usable prediction the two are the same instant, so the second format call is a
    // guaranteed-identical string — skip it rather than pay for it on every scheduled-only arrival.
    if (scheduledTime == displayTime) return ArrivalClock(expected)
    return arrivalClockOf(expected = expected, scheduled = DisplayFormat.formatTime(context, scheduledTime.epochMs))
}

/** The formatted-string rule itself, split out so it is testable without a `Context`. */
@VisibleForTesting
internal fun arrivalClockOf(expected: String, scheduled: String): ArrivalClock = ArrivalClock(
    expected = expected,
    corrects = scheduled.takeIf { it != expected }
)

/** How much fainter the struck timetable time is drawn than the time that replaced it — present, but
 *  clearly the superseded one of the two. */
private const val CORRECTED_ALPHA = 0.75f

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
 * is struck either way. It's the trip's state rather than a [TextDecoration] because this composable
 * already owns one strikethrough of its own, and only it can say how the two compose.
 */
@Composable
internal fun CorrectedClockTime(
    clock: ArrivalClock,
    color: Color,
    fontSize: TextUnit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    canceled: Boolean = false
) {
    val canceledDecoration = strikeThroughIf(canceled)
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
    val context = LocalContext.current
    // Held across recompositions: a corrected pill recomposes on every ETA rollover, and this is a
    // getString + format each time. It only changes when a fresh poll brings a new pair.
    val spoken = remember(clock, context) {
        context.getString(R.string.stop_info_clock_corrected, corrects, clock.expected)
    }
    // No verticalArrangement: [style]'s trimmed line boxes already sit flush, which is what these two
    // want — they are one reading, not two lines of text.
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = corrects,
            color = color.copy(alpha = color.alpha * CORRECTED_ALPHA),
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
