package org.onebusaway.android.ondemand

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong
import org.onebusaway.android.R
import org.onebusaway.android.map.render.CompassDirection
import org.onebusaway.android.map.render.ONDEMAND_NEAR_EDGE_METERS
import org.onebusaway.android.map.render.ZoneEdge
import org.onebusaway.android.map.render.bearingDegrees
import org.onebusaway.android.map.render.compassDirection
import org.onebusaway.android.util.GeoPoint

/**
 * A string resource with its arguments, composed without a `Resources` so the copy rules of spec
 * §2.8 are JVM-tested. Arguments may be a [TextSpec], a [PluralSpec], a [DistanceText], a `String`
 * or an `Int`; the Compose resolver (`resolve()` in `ui/home/ondemand/CopyResolvers.kt`) flattens them.
 */
data class TextSpec(@StringRes val res: Int, val args: List<Any> = emptyList())

data class PluralSpec(@PluralsRes val res: Int, val count: Int, val args: List<Any>)

enum class DistanceUnit(@StringRes val abbreviationRes: Int) {
    FEET(R.string.feet_abbreviation),
    MILES(R.string.miles_abbreviation),
    METERS(R.string.meters_abbreviation),
    KILOMETERS(R.string.kilometers_abbreviation)
}

/** A formatted distance: the number and the unit it is in; rendered as "value unit". */
data class DistanceText(val value: String, val unit: DistanceUnit)

private const val FEET_PER_METER = 3.28084
private const val FEET_PER_MILE = 5_280.0
private const val METERS_PER_KILOMETER = 1_000.0

/**
 * Spec §2.8 distances: imperial shows whole feet rounded to 10 below 0.1 mi and miles with one
 * decimal at or above; metric shows metres rounded to 10 below 1,000 m and kilometres with one decimal.
 */
fun formatDistance(meters: Double, metric: Boolean, locale: Locale): DistanceText {
    val whole = NumberFormat.getIntegerInstance(locale)
    val oneDecimal = NumberFormat.getInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return if (metric) {
        if (meters < METERS_PER_KILOMETER) {
            DistanceText(whole.format(roundToTen(meters)), DistanceUnit.METERS)
        } else {
            DistanceText(oneDecimal.format(meters / METERS_PER_KILOMETER), DistanceUnit.KILOMETERS)
        }
    } else {
        val feet = meters * FEET_PER_METER
        if (feet < FEET_PER_MILE / 10) {
            DistanceText(whole.format(roundToTen(feet)), DistanceUnit.FEET)
        } else {
            DistanceText(oneDecimal.format(feet / FEET_PER_MILE), DistanceUnit.MILES)
        }
    }
}

private fun roundToTen(value: Double): Long = (value / 10.0).roundToLong() * 10

fun formatClockTime(instant: Instant, zone: ZoneId, locale: Locale): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

private fun formatDateTime(instant: Instant, zone: ZoneId, locale: Locale): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

private fun formatDate(date: java.time.LocalDate, locale: Locale): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/** "today at 7:20 AM", "tomorrow at …", a weekday within the week, else a medium date. */
fun relativeDayTime(instant: Instant, zone: ZoneId, now: Instant, locale: Locale): TextSpec {
    val time = formatClockTime(instant, zone, locale)
    val today = now.atZone(zone).toLocalDate()
    val day = instant.atZone(zone).toLocalDate()
    return when {
        day == today -> TextSpec(R.string.ondemand_relative_today_at, listOf(time))
        day == today.plusDays(1) -> TextSpec(R.string.ondemand_relative_tomorrow_at, listOf(time))
        day.isBefore(today.plusDays(7)) -> TextSpec(R.string.ondemand_relative_day_at, listOf(day.dayOfWeek.getDisplayName(TextStyle.FULL, locale), time))
        else -> TextSpec(R.string.ondemand_relative_day_at, listOf(formatDate(day, locale), time))
    }
}

/** Which "open" key a surface uses: the card and picker say "Open · until", the detail page "Open now · until". */
enum class OpenStyle { OPEN_UNTIL, OPEN_NOW_UNTIL }

/** The status line per spec §2.8's composition rules; null for [OnDemandStatus.Unknown]. */
fun statusText(status: OnDemandStatus, zone: ZoneId?, now: Instant, locale: Locale, openStyle: OpenStyle = OpenStyle.OPEN_UNTIL): TextSpec? = when (status) {
    is OnDemandStatus.OpenNow -> {
        val until = status.until
        if (until == null || zone == null) {
            TextSpec(R.string.ondemand_status_open)
        } else {
            val res = if (openStyle == OpenStyle.OPEN_UNTIL) R.string.ondemand_status_open_until else R.string.ondemand_status_open_now_until
            TextSpec(res, listOf(formatClockTime(until, zone, locale)))
        }
    }
    is OnDemandStatus.OpensAt -> zone?.let { TextSpec(R.string.ondemand_status_opens, listOf(relativeDayTime(status.at, it, now, locale))) }
    is OnDemandStatus.BookingOpens -> zone?.let { TextSpec(R.string.ondemand_booking_opens, listOf(formatDateTime(status.at, it, locale), formatDate(status.travelDate, locale))) }
    is OnDemandStatus.BookBy -> zone?.let { TextSpec(R.string.ondemand_status_book_by, listOf(relativeDayTime(status.deadline, it, now, locale))) }
    OnDemandStatus.Closed -> TextSpec(R.string.ondemand_status_closed)
    OnDemandStatus.Unknown -> null
}

fun tagText(tag: OnDemandTag): TextSpec = TextSpec(
    when (tag) {
        OnDemandTag.NO_NOTICE_NEEDED -> R.string.ondemand_tag_no_notice
        OnDemandTag.SAME_DAY_BOOKING -> R.string.ondemand_tag_same_day
        OnDemandTag.ADVANCE_BOOKING -> R.string.ondemand_tag_advance
        OnDemandTag.ELIGIBILITY_REQUIRED -> R.string.ondemand_tag_eligibility
    }
)

/** The one booking tag (never the eligibility tag), or null for a service with no tier. */
fun bookingTag(tags: Set<OnDemandTag>): OnDemandTag? = tags.firstOrNull { it != OnDemandTag.ELIGIBILITY_REQUIRED }

/** The card meta line's segments — status then booking tag — with empty segments omitted; joined with " · ". */
fun cardMeta(availability: OnDemandAvailability, now: Instant, locale: Locale): List<TextSpec> = listOfNotNull(
    statusText(availability.status, availability.zone, now, locale),
    bookingTag(availability.tags)?.let(::tagText)
)

private val NEAR_EDGE_KEYS = mapOf(
    CompassDirection.NORTH to R.string.ondemand_bar_inside_near_edge_north,
    CompassDirection.NORTHEAST to R.string.ondemand_bar_inside_near_edge_northeast,
    CompassDirection.EAST to R.string.ondemand_bar_inside_near_edge_east,
    CompassDirection.SOUTHEAST to R.string.ondemand_bar_inside_near_edge_southeast,
    CompassDirection.SOUTH to R.string.ondemand_bar_inside_near_edge_south,
    CompassDirection.SOUTHWEST to R.string.ondemand_bar_inside_near_edge_southwest,
    CompassDirection.WEST to R.string.ondemand_bar_inside_near_edge_west,
    CompassDirection.NORTHWEST to R.string.ondemand_bar_inside_near_edge_northwest
)

private val OUTSIDE_KEYS = mapOf(
    CompassDirection.NORTH to R.string.ondemand_bar_outside_north,
    CompassDirection.NORTHEAST to R.string.ondemand_bar_outside_northeast,
    CompassDirection.EAST to R.string.ondemand_bar_outside_east,
    CompassDirection.SOUTHEAST to R.string.ondemand_bar_outside_southeast,
    CompassDirection.SOUTH to R.string.ondemand_bar_outside_south,
    CompassDirection.SOUTHWEST to R.string.ondemand_bar_outside_southwest,
    CompassDirection.WEST to R.string.ondemand_bar_outside_west,
    CompassDirection.NORTHWEST to R.string.ondemand_bar_outside_northwest
)

/**
 * The docked bar's title by spec §2.8 precedence: outside → "… of the zone" (server distance and
 * point, client [edge] as fallback); inside and near the edge → "Inside · edge …"; a
 * [OnDemandStatus.BookingOpens] status at any tier → the status text (PF-12); tier 4 → the
 * eligibility tag; tiers 3 and 2 → the status; tier 1 → "Pickups available here"; tier 5 → "Closed"
 * when the status is [OnDemandStatus.Closed], else nothing. Null when there is nothing to say (an
 * unknown status, or outside with no direction), and the bar then shows the service name in the
 * title position.
 */
fun barTitle(match: OnDemandMatch, probe: GeoPoint, edge: ZoneEdge?, now: Instant, locale: Locale, metric: Boolean): TextSpec? {
    val availability = match.availability
    if (!match.isInside) {
        val distance = match.distanceToAreaMeters ?: edge?.distanceMeters ?: return null
        val riderDirection = match.nearestPointOnBoundary?.let { compassDirection(bearingDegrees(probe, it) + 180.0) }
            ?: edge?.riderDirection
            ?: return null
        return TextSpec(OUTSIDE_KEYS.getValue(riderDirection), listOf(formatDistance(distance, metric, locale)))
    }
    if (edge != null && edge.distanceMeters < ONDEMAND_NEAR_EDGE_METERS) {
        return TextSpec(NEAR_EDGE_KEYS.getValue(edge.edgeDirection), listOf(formatDistance(edge.distanceMeters, metric, locale)))
    }
    if (availability.status is OnDemandStatus.BookingOpens) {
        return statusText(availability.status, availability.zone, now, locale)
    }
    return when (availability.usabilityTier) {
        TIER_ELIGIBILITY -> tagText(OnDemandTag.ELIGIBILITY_REQUIRED)
        TIER_ADVANCE, TIER_SAME_DAY -> statusText(availability.status, availability.zone, now, locale)
        TIER_OPEN_NOW -> TextSpec(R.string.ondemand_bar_inside)
        else -> if (availability.status == OnDemandStatus.Closed) TextSpec(R.string.ondemand_status_closed) else null
    }
}

/** "%1$d of %2$d": the thumbnail badge and the paged bar's accessibility value. */
fun badgeText(index: Int, count: Int): TextSpec = TextSpec(R.string.ondemand_bar_badge, listOf(index, count))

/** The dropped pin's line (spec §3.7): a [TextSpec], or a [PluralSpec] when [othersInside] > 0. */
fun addressLine(serviceName: String, isInside: Boolean, othersInside: Int): Any = when {
    !isInside -> TextSpec(R.string.ondemand_address_outside, listOf(serviceName))
    othersInside > 0 -> PluralSpec(R.plurals.ondemand_address_inside_more, othersInside, listOf(serviceName, othersInside))
    else -> TextSpec(R.string.ondemand_address_inside, listOf(serviceName))
}

/** The picker subtitle by probe source, with the locality when known (spec §3.5). */
fun pickerSubtitle(source: ProbeSource, locality: String?): TextSpec = when (source) {
    ProbeSource.Rider -> if (locality == null) TextSpec(R.string.ondemand_picker_your_location) else TextSpec(R.string.ondemand_picker_subtitle_location, listOf(locality))
    ProbeSource.MapCenter -> if (locality == null) TextSpec(R.string.ondemand_picker_map_center) else TextSpec(R.string.ondemand_picker_subtitle_center, listOf(locality))
    is ProbeSource.Point -> if (locality == null) TextSpec(R.string.ondemand_picker_selected_place) else TextSpec(R.string.ondemand_picker_subtitle_point, listOf(locality))
}

/** The detail page's location row title for the six source × inside cases (spec §3.6 item 3). */
fun detailLocationText(source: ProbeSource, isInside: Boolean): TextSpec = TextSpec(
    when (source) {
        ProbeSource.Rider -> if (isInside) R.string.ondemand_detail_location_inside else R.string.ondemand_detail_location_outside
        ProbeSource.MapCenter -> if (isInside) R.string.ondemand_detail_center_inside else R.string.ondemand_detail_center_outside
        is ProbeSource.Point -> if (isInside) R.string.ondemand_detail_point_inside else R.string.ondemand_detail_point_outside
    }
)

fun zoneCount(count: Int): PluralSpec = PluralSpec(R.plurals.ondemand_detail_zone_count, count, listOf(count))
