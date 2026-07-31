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
package org.onebusaway.android.ui.tripplan

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.onebusaway.android.R
import org.onebusaway.android.ui.icons.AppIcons

/**
 * When the trip is for, picked in one dialog (#2117).
 *
 * The two menu rows this replaces — "Choose date…" and "Choose time…" — each opened a picker that set
 * the *whole* instant from one of its halves, so pinning a departure for tomorrow evening meant two
 * trips through the menu and two re-plans, and picking only one of them silently kept whatever the
 * other half happened to hold. Here they are one answer: a clock, which is what a rider is nearly
 * always changing, over a compact day dropdown that offers today and tomorrow outright and opens a
 * calendar only for the rarer case.
 *
 * [initialMillis] seeds both halves; the composed instant is reported to [onConfirm] once, on OK, so a
 * date and a time chosen together cost a single re-plan. Dismissing changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDateTimeDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    // The rider is picking a wall-clock date and time, so every conversion in here is in the device's
    // own zone — the same zone the form's date/time labels are formatted in.
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(initialMillis, zone) { Instant.ofEpochMilli(initialMillis).atZone(zone) }

    // The chosen day, held as an epoch day so it survives a rotation through rememberSaveable (which
    // stores only Bundle-native types). LocalDate is the working form; this is just its wire shape.
    var epochDay by rememberSaveable { mutableLongStateOf(initial.toLocalDate().toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)

    val time = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        // The device's own 12-/24-hour setting, which is a separate choice from the locale.
        is24Hour = DateFormat.is24HourFormat(context)
    )
    // Material's two ways to state a time: the dial, and the keyboard entry its toggle switches to.
    var typing by rememberSaveable { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val displayMode = if (typing) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker

    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(date.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli())
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        // Our own title rather than Material's, which reads "Select time" and so names only one of
        // this dialog's two halves. Styled as the default is, so it keeps its place in the layout.
        title = {
            Text(
                text = stringResource(R.string.trip_plan_select_date_time),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                displayMode = displayMode,
                onDisplayModeChange = { typing = !typing }
            )
        }
    ) {
        DaySelector(
            date = date,
            onDateChange = { epochDay = it.toEpochDay() },
            onChooseDate = { showCalendar = true }
        )
        if (typing) TimeInput(state = time) else TimePicker(state = time)
    }

    if (showCalendar) {
        DayCalendarDialog(
            date = date,
            onDismiss = { showCalendar = false },
            onDateChange = {
                epochDay = it.toEpochDay()
                showCalendar = false
            }
        )
    }
}

/**
 * Which day the trip is on: the two days a rider nearly always means, named outright, with the
 * calendar behind a third row rather than in front of every pick. Any other day shows as its own
 * date, so the button always states the day it holds rather than "Choose date…".
 */
@Composable
private fun DaySelector(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onChooseDate: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Read once, not per recomposition: the two named days have to keep meaning the same days for as
    // long as the dialog is open, or a rider who opened it at 23:59 could tap "Today" and get the
    // date the button no longer says.
    val today = remember { LocalDate.now() }
    val tomorrow = remember(today) { today.plusDays(1) }

    // Deliberately wrap-content, not fillMaxWidth: the dialog's layout sizes itself to its widest
    // child, so a row that claims all the width it is offered stretches the whole dialog past the
    // window and clips the clock. The layout centres what it is given, so wrapping is also what
    // centres this row.
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.trip_plan_date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag(TripPlanTestTags.PICKER_DAY)
            ) {
                Text(dayLabel(date, today, tomorrow))
                Icon(
                    imageVector = AppIcons.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_plan_date_today)) },
                    onClick = {
                        expanded = false
                        onDateChange(today)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_plan_date_tomorrow)) },
                    onClick = {
                        expanded = false
                        onDateChange(tomorrow)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_plan_choose_date)) },
                    onClick = {
                        expanded = false
                        onChooseDate()
                    }
                )
            }
        }
    }
}

/** The full calendar, reached from the day dropdown's third row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayCalendarDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onDateChange: (LocalDate) -> Unit
) {
    // Material states a calendar selection as UTC midnight of the day the rider tapped — a day, not an
    // instant — so the day is read back out of UTC rather than the device zone. Reading it locally
    // would land on the previous day for every rider east of Greenwich.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis ?: return@TextButton onDismiss()
                    onDateChange(Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate())
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

/** [date] as the rider would say it: "Today", "Tomorrow", or the date itself. */
@Composable
private fun dayLabel(date: LocalDate, today: LocalDate, tomorrow: LocalDate): String = when (date) {
    today -> stringResource(R.string.trip_plan_date_today)
    tomorrow -> stringResource(R.string.trip_plan_date_tomorrow)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
