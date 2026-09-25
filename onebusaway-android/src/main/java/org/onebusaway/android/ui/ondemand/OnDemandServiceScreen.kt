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
package org.onebusaway.android.ui.ondemand

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.models.OnDemandServiceKind
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingState
import org.onebusaway.android.ui.compose.components.ErrorContent
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.ObaTopAppBar

@StringRes
private fun OnDemandServiceKind.labelRes(): Int = when (this) {
    OnDemandServiceKind.ZONE -> R.string.ondemand_kind_zone
    OnDemandServiceKind.ZONE_TO_ZONE -> R.string.ondemand_kind_zone_to_zone
    OnDemandServiceKind.STOP_GROUP -> R.string.ondemand_kind_stop_group
    OnDemandServiceKind.DEVIATED_ROUTE -> R.string.ondemand_kind_deviated_route
    OnDemandServiceKind.UNKNOWN -> R.string.ondemand_kind_unknown
}

/** The service page: name, kind, when it runs, how to book, and the feed's own notes. */
@Composable
fun OnDemandServiceScreen(
    state: OnDemandServiceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = (state as? OnDemandServiceUiState.Content)?.service?.name ?: stringResource(R.string.ondemand_service_title)
    Scaffold(topBar = { ObaTopAppBar(title = title, onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                OnDemandServiceUiState.Loading -> LoadingContent(Modifier.align(Alignment.Center))
                OnDemandServiceUiState.Error -> ErrorContent(onRetry = onRetry, modifier = Modifier.align(Alignment.Center))
                OnDemandServiceUiState.NotFound -> Text(
                    text = stringResource(R.string.ondemand_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                is OnDemandServiceUiState.Content -> ServiceContent(state, onCall, onOpenUrl)
            }
        }
    }
}

@Composable
private fun ServiceContent(content: OnDemandServiceUiState.Content, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    // LocalLocale observes the app's locale so this recomposes on a locale change,
    // unlike Locale.getDefault() which reads it once and never updates.
    val locale = LocalLocale.current.platformLocale
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(content.service.kind.labelRes()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content.service.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(stringResource(R.string.ondemand_when_title), style = MaterialTheme.typography.titleMedium)
        if (content.whenRows.isEmpty()) {
            Text(stringResource(R.string.ondemand_no_rules), style = MaterialTheme.typography.bodyMedium)
        }
        for (row in content.whenRows) {
            Text(formatDays(row.days, locale), style = MaterialTheme.typography.bodyMedium)
            Text(formatWindow(row.start, row.end, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        content.booking?.let { booking -> BookingSection(booking, hasRules = content.service.rules.isNotEmpty(), locale, onCall, onOpenUrl) }
    }
}

/**
 * [hasRules] is false for a service with no availability rules: it has nothing to compute a
 * deadline from, so the section skips the deadline line rather than claiming "no deadline
 * published" for a service that was never going to publish one.
 */
@Composable
private fun BookingSection(booking: BookingSummary, hasRules: Boolean, locale: Locale, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    Text(stringResource(R.string.ondemand_how_to_book_title), style = MaterialTheme.typography.titleMedium)
    if (hasRules) {
        Text(deadlineLine(booking, locale), style = MaterialTheme.typography.bodyMedium)
    }
    booking.phoneNumber?.let { phone ->
        Button(onClick = { onCall(phone) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ondemand_call, phone)) }
    }
    booking.bookingUrl?.let { url ->
        OutlinedButton(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ondemand_book_online)) }
    }
    booking.infoUrl?.let { url ->
        OutlinedButton(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ondemand_more_info)) }
    }
    if (booking.messages.isNotEmpty()) {
        Text(stringResource(R.string.ondemand_details_title), style = MaterialTheme.typography.titleMedium)
        for (message in booking.messages) Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun deadlineLine(booking: BookingSummary, locale: Locale): String {
    val evaluation = booking.evaluation
    val travelDate = booking.travelDate
    val zone = booking.zone
    if (evaluation == null || travelDate == null || zone == null) return stringResource(R.string.ondemand_no_deadline_published)
    val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    return when (evaluation.state) {
        BookingState.NOT_YET_OPEN -> stringResource(R.string.ondemand_booking_opens, dateTime.format(evaluation.openInstant), date.format(travelDate))
        BookingState.OPEN, BookingState.CLOSED_FOR_DATE -> {
            val cutoff = evaluation.cutoffInstant
            if (cutoff == null) stringResource(R.string.ondemand_book_at_ride_time) else stringResource(R.string.ondemand_book_by, dateTime.format(cutoff), date.format(travelDate))
        }
        BookingState.UNKNOWN -> stringResource(R.string.ondemand_no_deadline_published)
    }
}

private fun formatDays(days: Set<DayOfWeek>, locale: Locale): String = DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }

@Composable
private fun formatWindow(start: ServiceDayTime?, end: ServiceDayTime?, locale: Locale): String {
    if (start == null && end == null) return stringResource(R.string.ondemand_all_hours)
    return stringResource(R.string.ondemand_time_window, formatTime(start ?: ServiceDayTime(0), locale), formatTime(end ?: ServiceDayTime(24 * 3600), locale))
}

@Composable
private fun formatTime(time: ServiceDayTime, locale: Locale): String {
    val clock = LocalTime.of(time.hours % 24, time.minutesOfHour).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return if (time.hours >= 24) stringResource(R.string.ondemand_time_next_day, clock) else clock
}
