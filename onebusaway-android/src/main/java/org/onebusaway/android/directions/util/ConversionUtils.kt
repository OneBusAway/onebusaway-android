/*
 * Copyright 2012 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.util

import android.content.Context
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import org.onebusaway.android.R
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.DisplayFormat
import org.onebusaway.android.util.PreferenceUtils

/**
 * @author Khoa Tran
 * @author Simon Jacobs - conversion for OBA. Added Imperial measurements
 */
object ConversionUtils {

    private const val TAG = "ConversionUtils"

    private const val FEET_PER_METER = 3.281

    /**
     * Return a formatted String for a distance. Should be in proper units according to
     * preferences (either metric or imperial).
     *
     * @param meters distance in meters
     * @param applicationContext context to look up resources
     * @return formatted string of distance
     */
    @JvmStatic
    fun getFormattedDistance(meters: Double, applicationContext: Context): String = getFormattedDistanceParts(meters, applicationContext).joinToString(" ") { it.text }

    /**
     * The same distance as [getFormattedDistance], but as its structured value + unit parts (mirroring
     * [DisplayFormat.formatEtaParts]) so a caller that styles the number and unit differently — e.g. a
     * bold value with a smaller unit — can do so without re-splitting a joined string. The value is the
     * emphasized part, the unit abbreviation the un-emphasized one.
     *
     * @param meters distance in meters
     * @param applicationContext context to look up resources
     */
    @JvmStatic
    fun getFormattedDistanceParts(
        meters: Double,
        applicationContext: Context
    ): List<DisplayFormat.EtaPart> {
        val (value, unitRes) = if (PreferenceUtils.getUnitsAreMetricFromPreferences(applicationContext)) {
            if (meters < 1000) {
                String.format(Locale.getDefault(), OTPConstants.FORMAT_DISTANCE_METERS, meters) to
                    R.string.meters_abbreviation
            } else {
                String.format(Locale.getDefault(), OTPConstants.FORMAT_DISTANCE_KILOMETERS, meters / 1000) to
                    R.string.kilometers_abbreviation
            }
        } else {
            val feet = meters * FEET_PER_METER
            if (feet < 1000) {
                String.format(Locale.getDefault(), OTPConstants.FORMAT_DISTANCE_METERS, feet) to
                    R.string.feet_abbreviation
            } else {
                String.format(Locale.getDefault(), OTPConstants.FORMAT_DISTANCE_KILOMETERS, feet / 5280) to
                    R.string.miles_abbreviation
            }
        }
        return listOf(
            DisplayFormat.EtaPart(value, emphasized = true),
            DisplayFormat.EtaPart(applicationContext.resources.getString(unitRes), emphasized = false)
        )
    }

    /**
     * Get a formatted string for a duration.
     *
     * @param sec duration in seconds
     * @param applicationContext context to look up resources
     * @return formatted duration string
     */
    @JvmStatic
    fun getFormattedDurationText(sec: Long, applicationContext: Context): String? {
        var text = ""
        val h = sec / 3600
        if (h >= 24) {
            return null
        }
        val m = (sec % 3600) / 60
        val s = (sec % 3600) % 60
        if (h > 0) {
            text += h.toString() + applicationContext.resources.getString(R.string.hours_abbreviation)
        }
        text += m.toString() + applicationContext.resources.getString(R.string.minutes_abbreviation)
        text += s.toString() + applicationContext.resources.getString(R.string.seconds_abbrevation)
        return text
    }

    /**
     * Get duration text but disallow "seconds" units.
     *
     * @param sec duration in seconds
     * @param longFormat true for long units ("minutes"), false for short units ("min")
     * @param applicationContext context to look up resources
     * @return formatted duration text
     */
    @JvmStatic
    fun getFormattedDurationTextNoSeconds(
        sec: Long,
        longFormat: Boolean,
        applicationContext: Context
    ): String {
        var text = ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val resources = applicationContext.resources
        if (h > 0) {
            val shortHours = resources.getString(R.string.hours_abbreviation)
            val shortMinutes = resources.getString(R.string.minutes_abbreviation)
            text += h.toString() + shortHours
            text += " " + m.toString() + shortMinutes
        } else {
            val longMinutes = resources.getString(R.string.minutes_full)
            text += when {
                m == 0L -> "< 1 $longMinutes"
                m == 1L || m == -1L -> {
                    val longMinutesSingular = if (longFormat) {
                        resources.getString(R.string.minute_singular)
                    } else {
                        resources.getString(R.string.minutes_abbreviation)
                    }
                    "$m $longMinutesSingular"
                }
                else -> "$m $longMinutes"
            }
        }
        return text
    }

    @JvmStatic
    @JvmOverloads
    fun getTimeWithContext(
        applicationContext: Context,
        time: Long,
        inLine: Boolean,
        color: Int = -1,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault())
    ): CharSequence {
        // Times render in the device's local zone. Date/time math is java.time (see wallClock /
        // isToday / isTomorrow); the actual string is still produced by android.text.format.DateFormat
        // because it honors the user's 12-/24-hour device setting in addition to the locale
        // (DateTimeFormatter honors only the locale). DateFormat needs a java.util.Date, so the
        // java.time instant is converted for that single call.
        // getTimeFormat/getDateFormat already return formatters in the device's default zone.
        val timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext)
        val dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext)

        val zone = ZoneId.systemDefault()
        val local = wallClock(time, zone)
        val displayDate = Date.from(local.toInstant())
        val localDay = local.toLocalDate()

        val spannableTime = SpannableString(timeFormat.format(displayDate))
        if (color != -1) {
            spannableTime.setSpan(ForegroundColorSpan(color), 0, spannableTime.length, 0)
        }

        return if (inLine) {
            when {
                isToday(localDay, today) -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ",
                    spannableTime
                )

                isTomorrow(localDay, today) -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_next_day),
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ",
                    spannableTime
                )

                else -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_date),
                    " ",
                    dateFormat.format(displayDate),
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ",
                    spannableTime
                )
            }
        } else {
            when {
                isToday(localDay, today) -> spannableTime

                isTomorrow(localDay, today) -> TextUtils.concat(
                    " ",
                    spannableTime,
                    ", ",
                    applicationContext.resources.getString(R.string.time_connector_next_day)
                )

                else -> TextUtils.concat(spannableTime, ", ", dateFormat.format(displayDate))
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getTimeUpdated(
        applicationContext: Context,
        oldTime: Long,
        newTime: Long,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault())
    ): CharSequence {
        // See getTimeWithContext: times render in the device's local zone; java.time for the date math,
        // android.text.format.DateFormat (fed a converted java.util.Date) for the localized,
        // device-12/24h-aware string.
        // getTimeFormat/getDateFormat already return formatters in the device's default zone.
        val timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext)
        val dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext)

        val zone = ZoneId.systemDefault()
        val oldLocal = wallClock(oldTime, zone, round = false)
        val newLocal = wallClock(newTime, zone, round = false)
        val oldDisplay = Date.from(oldLocal.toInstant())
        val newDisplay = Date.from(newLocal.toInstant())

        var beforeDateString: CharSequence = ""
        var newDateString: CharSequence = ""

        val oldDateString: SpannableString
        if (isTomorrow(newLocal.toLocalDate(), today)) {
            oldDateString = SpannableString(
                applicationContext.resources.getString(R.string.time_connector_next_day) + " "
            )
        } else {
            beforeDateString =
                applicationContext.resources.getString(R.string.time_connector_before_date) + " "
            oldDateString = SpannableString(dateFormat.format(newDisplay) + " ")
        }

        if (newLocal.dayOfMonth != oldLocal.dayOfMonth) {
            beforeDateString =
                applicationContext.resources.getString(R.string.time_connector_before_date) + " "
            newDateString = dateFormat.format(newDisplay) + " "
            oldDateString.setSpan(StrikethroughSpan(), 0, oldDateString.length - 1, 0)
        }

        val beforeTimeString: CharSequence =
            applicationContext.resources.getString(R.string.time_connector_before_time) + " "

        val color = ContextCompat.getColor(
            applicationContext,
            ArrivalInfoUtils.computeColorFromDeviation(newTime - oldTime)
        )

        val newTimeString = SpannableString(timeFormat.format(newDisplay) + " ")
        newTimeString.setSpan(ForegroundColorSpan(color), 0, newTimeString.length, 0)

        val oldTimeString: SpannableString =
            if (oldLocal.hour != newLocal.hour || oldLocal.minute != newLocal.minute) {
                SpannableString(timeFormat.format(oldDisplay) + " ").apply {
                    setSpan(StrikethroughSpan(), 0, length - 1, 0)
                }
            } else {
                SpannableString(" ")
            }

        return TextUtils.concat(
            beforeDateString,
            newDateString,
            oldDateString,
            beforeTimeString,
            oldTimeString,
            newTimeString
        )
    }

    /**
     * The epoch-millis instant [time] as a wall-clock date-time in [zone], optionally rounded to the
     * nearest minute (>= 30s rounds up — reproducing the legacy `add(MINUTE, 1)` when `SECOND >= 30`).
     * Trip times are rendered in the device's zone ([ZoneId.systemDefault]); the [zone] parameter keeps
     * this deterministically testable.
     */
    internal fun wallClock(time: Long, zone: ZoneId, round: Boolean = true): ZonedDateTime {
        val zoned = Instant.ofEpochMilli(time).atZone(zone)
        return if (round && zoned.second >= 30) zoned.plusMinutes(1) else zoned
    }

    /** True when [date] is the same calendar day as [today] (both device-local dates). */
    internal fun isToday(date: LocalDate, today: LocalDate): Boolean = date == today

    /** True when [date] is the calendar day after [today] (both device-local dates). */
    internal fun isTomorrow(date: LocalDate, today: LocalDate): Boolean = date == today.plusDays(1)

    /**
     * Shows only the last n words of a sentence, being n the number of words to make the longer
     * sentence that still is smaller than maxLength.
     *
     * @param sentence  phrase to shrink
     * @param maxLength max length of the new sentence
     * @return the reduced sentence
     */
    @JvmStatic
    fun tailAndTruncateSentence(sentence: String, maxLength: Int): String {
        val reversedWords = sentence.split(" ").reversed()

        var modifiedSentence = ""

        for (word in reversedWords) {
            if (modifiedSentence.length >= maxLength) {
                return modifiedSentence
            }
            modifiedSentence = "$word $modifiedSentence"
        }
        return modifiedSentence
    }

    /**
     * Always creates a correct value for the short name of the route. Using the routeShortName,
     * processing the long name if the short is null or returning an empty string if both names are
     * null.
     *
     * Route short name will be preceded by adequate connector.
     *
     * @param routeLongName  to convert it to a route short name if necessary
     * @param routeShortName to be returned if is not null
     * @return a valid route short name
     */
    @JvmStatic
    fun getRouteShortNameSafe(
        routeShortName: String?,
        routeLongName: String?,
        context: Context
    ): String {
        var routeName = ""

        if (routeShortName != null || routeLongName != null) {
            routeName += context.resources.getString(R.string.connector_before_route)
            if (routeShortName != null) {
                routeName += " $routeShortName"
            } else if (routeLongName != null) {
                routeName += " " +
                    tailAndTruncateSentence(
                        routeLongName,
                        OTPConstants.ROUTE_SHORT_NAME_MAX_SIZE
                    )
            }
        }
        return routeName
    }

    /**
     * Always creates a correct value for the long name of the route. Using the routeLongName,
     * returning the short name if the long is null or returning an empty string if both names are
     * null.
     *
     * @param routeLongName  to be returned if is not null
     * @param routeShortName to use if necessary
     * @return a valid route long name
     */
    @JvmStatic
    fun getRouteLongNameSafe(
        routeLongName: String?,
        routeShortName: String?,
        includeShortName: Boolean
    ): String {
        var routeName = ""

        if (routeShortName != null || routeLongName != null) {
            if (routeLongName != null) {
                routeName = if (includeShortName && routeShortName != null) {
                    "$routeShortName ($routeLongName)"
                } else {
                    routeLongName
                }
            } else if (routeShortName != null) {
                routeName += routeShortName
            }
        }
        return routeName
    }

    /**
     * Convert meters to feet.
     */
    @JvmStatic
    fun metersToFeet(meters: Double): Double = meters * FEET_PER_METER

    /**
     * Convert feet to meters.
     */
    @JvmStatic
    fun feetToMeters(feet: Double): Double = feet / FEET_PER_METER
}
