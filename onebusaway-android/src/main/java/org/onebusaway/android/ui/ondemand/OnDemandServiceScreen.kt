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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.models.ServiceDayTime
import org.onebusaway.android.ondemand.BookingState
import org.onebusaway.android.ondemand.LocationCheck
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.OnDemandTag
import org.onebusaway.android.ondemand.OpenStyle
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.detailLocationText
import org.onebusaway.android.ondemand.statusText
import org.onebusaway.android.ondemand.tagText
import org.onebusaway.android.ondemand.zoneCount
import org.onebusaway.android.ui.compose.components.ErrorContent
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.home.ondemand.ProbeDotStyle
import org.onebusaway.android.ui.home.ondemand.ZoneThumbnail
import org.onebusaway.android.ui.home.ondemand.resolve
import org.onebusaway.android.ui.home.ondemand.resolveCopy
import org.onebusaway.android.ui.home.ondemand.shapesCentre
import org.onebusaway.android.ui.home.ondemand.toThumbnailShapes

private val CARD_RADIUS = 18.dp
private val THUMBNAIL_HEIGHT = 210.dp
private val PRIMARY_HEIGHT = 46.dp
private val TAG_FILL = Color(0xFFE9F1DD)
private val WARN_FILL = Color(0xFFFBEFD5)
private val WARN_TEXT = Color(0xFF8A5A00)

/** The zone detail page (spec §3.6, screens 3–4): header card, promoted fact, location, thumbnail, Where, When, How to book, footnote. */
@Composable
fun OnDemandServiceScreen(
    state: OnDemandServiceUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = (state as? OnDemandServiceUiState.Content)?.service?.name ?: stringResource(R.string.ondemand_service_title)
    Scaffold(topBar = { ObaTopAppBar(title = title, onBack = onBack) }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) { padding ->
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
    val color = content.service.routeColor ?: colorResource(R.color.brand_color).toArgb()
    val promotion = detailPromotion(content.availability)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderCard(content, promotion, locale, onCall, onOpenUrl)
        if (promotion == DetailPromotion.DEADLINE_ROW) {
            DetailCard { IconRow(R.drawable.ic_schedule, deadlineRowText(content.availability.status, content.booking, locale)) }
        }
        content.locationCheck?.let { LocationRow(it) }
        Thumbnail(content, color)
        content.whereRows?.let { WhereSection(it, content) }
        WhenSection(content, locale)
        HowToBookSection(content, locale, onOpenUrl)
        content.booking?.messages?.forEach { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeaderCard(content: OnDemandServiceUiState.Content, promotion: DetailPromotion, locale: Locale, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val availability = content.availability
    DetailCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(content.service.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (availability.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availability.tags.sortedBy { it.ordinal }.forEach { tag -> TagChip(tag) }
                }
            }
            content.service.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (promotion == DetailPromotion.STATUS_LINE) {
                statusText(availability.status, availability.zone, content.presentedAt, locale, OpenStyle.OPEN_NOW_UNTIL)?.resolve()?.let { status ->
                    val green = colorResource(R.color.ondemand_open_green)
                    Text(
                        text = buildAnnotatedString {
                            if (availability.status is OnDemandStatus.OpenNow) withStyle(SpanStyle(color = green, fontWeight = FontWeight.SemiBold)) { append(status) } else append(status)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            val phone = content.booking?.phoneNumber
            val url = content.booking?.bookingUrl
            when {
                phone != null -> Button(onClick = { onCall(phone) }, modifier = Modifier.fillMaxWidth().height(PRIMARY_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_call, phone), modifier = Modifier.padding(start = 8.dp))
                }
                url != null -> Button(onClick = { onOpenUrl(url) }, modifier = Modifier.fillMaxWidth().height(PRIMARY_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_book_online), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: OnDemandTag) {
    val warn = tag == OnDemandTag.ELIGIBILITY_REQUIRED
    Text(
        text = tagText(tag).resolve(),
        style = MaterialTheme.typography.labelMedium,
        color = if (warn) WARN_TEXT else MaterialTheme.colorScheme.primary,
        modifier = Modifier.background(if (warn) WARN_FILL else TAG_FILL, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun LocationRow(check: LocationCheck) {
    val sub = check.locality?.takeIf { check.isInside }?.let { stringResource(R.string.ondemand_detail_location_sub, it) }
    DetailCard {
        IconRow(
            iconRes = if (check.isInside) R.drawable.ic_check_circle else R.drawable.ic_cancel,
            title = detailLocationText(check.source, check.isInside).resolve(),
            subtitle = sub,
            tint = if (check.isInside) colorResource(R.color.ondemand_open_green) else colorResource(R.color.ondemand_outside)
        )
    }
}

@Composable
private fun Thumbnail(content: OnDemandServiceUiState.Content, color: Int) {
    val shapes = content.service.areas.toThumbnailShapes(color)
    val centre = shapesCentre(shapes) ?: return
    val check = content.locationCheck
    ZoneThumbnail(
        shapes = shapes,
        centre = centre,
        probePoint = check?.point,
        probeStyle = when (check?.source) {
            ProbeSource.Rider -> ProbeDotStyle.RIDER
            ProbeSource.MapCenter -> ProbeDotStyle.MAP_CENTER
            is ProbeSource.Point, null -> ProbeDotStyle.POINT
        },
        fitProbe = false,
        modifier = Modifier.fillMaxWidth().height(THUMBNAIL_HEIGHT).clip(RoundedCornerShape(CARD_RADIUS)).background(MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun WhereSection(rows: WhereRows, content: OnDemandServiceUiState.Content) {
    val check = content.locationCheck
    val areaNames = rows.serviceAreaNames.joinToString(", ").ifEmpty { zoneCount(content.service.areas.size).resolveCopy() }
    val serviceAreaSub = if (check?.source == ProbeSource.Rider && check.isInside) stringResource(R.string.ondemand_detail_includes_location, areaNames) else areaNames
    val inside = check?.isInside == true
    SectionHeader(stringResource(R.string.ondemand_where_title))
    DetailCard {
        Column {
            IconRow(
                iconRes = if (inside) R.drawable.ic_check_circle else R.drawable.ic_location_on,
                title = stringResource(R.string.ondemand_detail_service_area),
                subtitle = serviceAreaSub,
                tint = if (inside) colorResource(R.color.ondemand_open_green) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            rows.dropOffNames?.let { names ->
                HorizontalDivider(Modifier.padding(start = 56.dp))
                IconRow(R.drawable.ic_location_on, stringResource(R.string.ondemand_detail_drop_off), names.joinToString(", ").ifEmpty { null })
            }
        }
    }
}

@Composable
private fun WhenSection(content: OnDemandServiceUiState.Content, locale: Locale) {
    SectionHeader(stringResource(R.string.ondemand_when_title))
    DetailCard {
        Column {
            if (content.whenRows.isEmpty()) {
                Text(stringResource(R.string.ondemand_no_rules), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            content.whenRows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                ScheduleRow(formatDayRanges(row.days, locale), formatWindow(row.start, row.end, locale))
            }
            if (content.noServiceDays.isNotEmpty()) {
                if (content.whenRows.isNotEmpty()) HorizontalDivider(Modifier.padding(start = 16.dp))
                ScheduleRow(formatDayRanges(content.noServiceDays, locale), stringResource(R.string.ondemand_detail_no_service), muted = true)
            }
        }
    }
}

@Composable
private fun HowToBookSection(content: OnDemandServiceUiState.Content, locale: Locale, onOpenUrl: (String) -> Unit) {
    // Non-null only when the service has rules to compute a deadline from (spec §3.6 item 7).
    val deadlineBooking = content.booking?.takeIf { content.service.rules.isNotEmpty() }
    val url = content.service.url
    val infoUrl = content.booking?.infoUrl
    if (deadlineBooking == null && url == null && infoUrl == null) return
    SectionHeader(stringResource(R.string.ondemand_how_to_book_title))
    DetailCard {
        Column {
            deadlineBooking?.let { IconRow(R.drawable.ic_schedule, deadlineLine(it, locale)) }
            url?.let {
                if (deadlineBooking != null) HorizontalDivider(Modifier.padding(start = 56.dp))
                IconRow(R.drawable.ic_open_in_new, stringResource(R.string.ondemand_open_agency_website), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenUrl(it) })
            }
            infoUrl?.let {
                if (deadlineBooking != null || url != null) HorizontalDivider(Modifier.padding(start = 56.dp))
                IconRow(R.drawable.ic_info, stringResource(R.string.ondemand_more_info), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenUrl(it) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(CARD_RADIUS), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun IconRow(iconRes: Int, title: String, subtitle: String? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (tint == MaterialTheme.colorScheme.primary) tint else MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ScheduleRow(days: String, hours: String, muted: Boolean = false) {
    val colour = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days, style = MaterialTheme.typography.bodyMedium, color = colour)
        Text(hours, style = MaterialTheme.typography.bodyMedium, color = if (muted) colour else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The promoted row's text (spec §3.6 item 2; PF-12): matched against [status] rather than gating
 * on the tier, since a same-day service can also promote a [OnDemandStatus.BookingOpens] row.
 */
@Composable
private fun deadlineRowText(status: OnDemandStatus, booking: BookingSummary?, locale: Locale): String = if (status is OnDemandStatus.Closed) {
    stringResource(R.string.ondemand_status_closed)
} else {
    booking?.let { deadlineLine(it, locale) } ?: stringResource(R.string.ondemand_no_deadline_published)
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
