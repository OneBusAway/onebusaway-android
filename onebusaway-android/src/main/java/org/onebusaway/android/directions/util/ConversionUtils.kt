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
import org.onebusaway.android.R
import org.onebusaway.android.util.ArrivalInfoUtils
import org.onebusaway.android.util.PreferenceUtils
import org.opentripplanner.api.model.Itinerary
import org.opentripplanner.routing.core.TraverseMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone

/**
 * @author Khoa Tran
 * @author Simon Jacobs - conversion for OBA. Added Imperial measurements
 */
object ConversionUtils {

    private const val TAG = "ConversionUtils"

    private const val FEET_PER_METER = 3.281

    /**
     * OTP agency time-zone offsets are expressed relative to GMT, and trip times are rendered as
     * agency wall-clock time by shifting the instant by that offset and reading it in GMT. GMT is a
     * zero-offset zone, so [ZoneOffset.UTC] is equivalent for the day/hour/minute/second fields.
     */
    private val GMT: ZoneId = ZoneOffset.UTC

    /**
     * Given a date string from an OTP server, parse it into an [Instant].
     *
     * @param text string from OTP (epoch milliseconds)
     * @return parsed instant, or null if there is an error with parsing.
     */
    @JvmStatic
    fun parseOtpDate(text: String): Instant? {
        return try {
            Instant.ofEpochMilli(text.toLong())
        } catch (ex: NumberFormatException) {
            Log.e(TAG, "Error processing OTP response time text: $text")
            null
        }
    }

    /**
     * Given start time and end time strings, compute the delta between them.
     * Strings should be in the OTP server return format, specified in OTPConstants
     *
     * @param startTimeText start time
     * @param endTimeText end time
     * @param applicationContext context to look up resources
     * @return duration
     */
    @JvmStatic
    fun getDuration(startTimeText: String, endTimeText: String, applicationContext: Context): Double {
        var duration = 0.0

        val startTime = parseOtpDate(startTimeText)
        val endTime = parseOtpDate(endTimeText)

        if (startTime != null && endTime != null) {
            duration = if (PreferenceUtils.getInt(
                    OTPConstants.PREFERENCE_KEY_API_VERSION, OTPConstants.API_VERSION_V1
                ) == OTPConstants.API_VERSION_V1
            ) {
                (endTime.toEpochMilli() - startTime.toEpochMilli()).toDouble()
            } else {
                ((endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000).toDouble()
            }
        }
        return duration
    }

    /**
     * Return a formatted String for a distance. Should be in proper units according to
     * preferences (either metric or imperial).
     *
     * @param meters distance in meters
     * @param applicationContext context to look up resources
     * @return formatted string of distance
     */
    @JvmStatic
    fun getFormattedDistance(meters: Double, applicationContext: Context): String {
        var text = ""
        var value = meters

        if (PreferenceUtils.getUnitsAreMetricFromPreferences(applicationContext)) {
            if (value < 1000) {
                text += String.format(OTPConstants.FORMAT_DISTANCE_METERS, value) + " " +
                    applicationContext.resources.getString(R.string.meters_abbreviation)
            } else {
                value /= 1000
                text += String.format(OTPConstants.FORMAT_DISTANCE_KILOMETERS, value) + " " +
                    applicationContext.resources.getString(R.string.kilometers_abbreviation)
            }
        } else {
            var feet = value * FEET_PER_METER
            if (feet < 1000) {
                text += String.format(OTPConstants.FORMAT_DISTANCE_METERS, feet) + " " +
                    applicationContext.resources.getString(R.string.feet_abbreviation)
            } else {
                feet /= 5280
                text += String.format(OTPConstants.FORMAT_DISTANCE_KILOMETERS, feet) + " " +
                    applicationContext.resources.getString(R.string.miles_abbreviation)
            }
        }
        return text
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
        applicationContext: Context,
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
    fun fixTimezoneOffsets(
        itineraries: List<Itinerary>?,
        useDeviceTimezone: Boolean,
    ): List<Itinerary>? {
        var agencyTimeZoneOffset = 0
        var containsTransitLegs = false

        if (!itineraries.isNullOrEmpty()) {
            val itinerariesFixed = ArrayList(itineraries)

            for (it in itinerariesFixed) {
                for (leg in it.legs) {
                    if (TraverseMode.valueOf(leg.mode).isTransit) {
                        containsTransitLegs = true
                    }
                    if (leg.agencyTimeZoneOffset != 0) {
                        agencyTimeZoneOffset = leg.agencyTimeZoneOffset
                        // If agencyTimeZoneOffset is different from 0, route contains transit legs
                        containsTransitLegs = true
                        break
                    }
                }
            }

            if (useDeviceTimezone || !containsTransitLegs) {
                agencyTimeZoneOffset =
                    TimeZone.getDefault().getOffset(itinerariesFixed[0].startTime.toLong())
            }

            if (agencyTimeZoneOffset != 0) {
                for (it in itinerariesFixed) {
                    for (leg in it.legs) {
                        leg.agencyTimeZoneOffset = agencyTimeZoneOffset
                    }
                }
            }

            return itinerariesFixed
        } else {
            return itineraries
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getTimeWithContext(
        applicationContext: Context,
        offsetGMT: Int,
        time: Long,
        inLine: Boolean,
        color: Int = -1,
    ): CharSequence {
        // Date/time math is java.time (see agencyWallClock / isToday / isTomorrow). The actual
        // string is still produced by android.text.format.DateFormat because it honors the user's
        // 12-/24-hour device setting in addition to the locale; DateTimeFormatter honors only the
        // locale, so swapping it would silently change the clock format for users who override the
        // device toggle. DateFormat needs a java.util.Date, so the java.time instant is converted
        // for that single call.
        val timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext)
        val dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext)
        timeFormat.timeZone = TimeZone.getTimeZone("GMT")
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")

        var noDeviceTimezoneNote = ""
        if (offsetGMT != TimeZone.getDefault().getOffset(time)) {
            noDeviceTimezoneNote = "GMT"
            if (offsetGMT != 0) {
                noDeviceTimezoneNote += offsetGMT / 3600000
            }
        }

        val agencyTime = agencyWallClock(time, offsetGMT)
        val displayDate = Date.from(agencyTime.toInstant())
        val agencyDay = agencyTime.toLocalDate()
        val today = LocalDate.now(ZoneId.systemDefault())

        val spannableTime = SpannableString(timeFormat.format(displayDate))
        if (color != -1) {
            spannableTime.setSpan(ForegroundColorSpan(color), 0, spannableTime.length, 0)
        }

        return if (inLine) {
            when {
                isToday(agencyDay, today) -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ", spannableTime, " ", noDeviceTimezoneNote
                )

                isTomorrow(agencyDay, today) -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_next_day), " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ", spannableTime, " ", noDeviceTimezoneNote
                )

                else -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_date), " ",
                    dateFormat.format(displayDate), " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ", spannableTime, " ", noDeviceTimezoneNote
                )
            }
        } else {
            when {
                isToday(agencyDay, today) -> TextUtils.concat(spannableTime, " ", noDeviceTimezoneNote)

                isTomorrow(agencyDay, today) -> TextUtils.concat(
                    " ", spannableTime, ", ",
                    applicationContext.resources.getString(R.string.time_connector_next_day), " ",
                    noDeviceTimezoneNote
                )

                else -> TextUtils.concat(
                    spannableTime, ", ", dateFormat.format(displayDate), " ", noDeviceTimezoneNote
                )
            }
        }
    }

    @JvmStatic
    fun getTimeUpdated(
        applicationContext: Context,
        offsetGMT: Int,
        oldTime: Long,
        newTime: Long,
    ): CharSequence {
        // See getTimeWithContext: java.time for the date math, android.text.format.DateFormat (fed a
        // converted java.util.Date) for the localized, device-12/24h-aware string.
        val timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext)
        val dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext)
        timeFormat.timeZone = TimeZone.getTimeZone("GMT")
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")

        var noDeviceTimezoneNote = ""
        if (offsetGMT != TimeZone.getDefault().getOffset(oldTime)) {
            noDeviceTimezoneNote = "GMT"
            if (offsetGMT != 0) {
                noDeviceTimezoneNote += offsetGMT / 3600000
            }
        }

        val oldAgency = agencyWallClock(oldTime, offsetGMT, round = false)
        val newAgency = agencyWallClock(newTime, offsetGMT, round = false)
        val oldDisplay = Date.from(oldAgency.toInstant())
        val newDisplay = Date.from(newAgency.toInstant())
        val today = LocalDate.now(ZoneId.systemDefault())

        var beforeDateString: CharSequence = ""
        var newDateString: CharSequence = ""

        val oldDateString: SpannableString
        if (isTomorrow(newAgency.toLocalDate(), today)) {
            oldDateString = SpannableString(
                applicationContext.resources.getString(R.string.time_connector_next_day) + " "
            )
        } else {
            beforeDateString =
                applicationContext.resources.getString(R.string.time_connector_before_date) + " "
            oldDateString = SpannableString(dateFormat.format(newDisplay) + " ")
        }

        if (newAgency.dayOfMonth != oldAgency.dayOfMonth) {
            beforeDateString =
                applicationContext.resources.getString(R.string.time_connector_before_date) + " "
            newDateString = dateFormat.format(newDisplay) + " "
            oldDateString.setSpan(StrikethroughSpan(), 0, oldDateString.length - 1, 0)
        }

        val beforeTimeString: CharSequence =
            applicationContext.resources.getString(R.string.time_connector_before_time) + " "
        val timezone: CharSequence = noDeviceTimezoneNote

        val color = applicationContext.resources.getColor(
            ArrivalInfoUtils.computeColorFromDeviation(newTime - oldTime)
        )

        val newTimeString = SpannableString(timeFormat.format(newDisplay) + " ")
        newTimeString.setSpan(ForegroundColorSpan(color), 0, newTimeString.length, 0)

        val oldTimeString: SpannableString =
            if (oldAgency.hour != newAgency.hour || oldAgency.minute != newAgency.minute) {
                SpannableString(timeFormat.format(oldDisplay) + " ").apply {
                    setSpan(StrikethroughSpan(), 0, length - 1, 0)
                }
            } else {
                SpannableString(" ")
            }

        return TextUtils.concat(
            beforeDateString, newDateString, oldDateString,
            beforeTimeString, oldTimeString, newTimeString, timezone
        )
    }

    /**
     * Shift an epoch-millis instant by the agency's GMT offset so it reads as agency wall-clock time
     * when rendered in [GMT], optionally rounding to the nearest minute (>= 30s rounds up). This
     * reproduces the legacy Calendar path: `add(MILLISECOND, offsetGMT)` followed by
     * `add(MINUTE, 1)` when `get(SECOND) >= 30`.
     */
    internal fun agencyWallClock(time: Long, offsetGMT: Int, round: Boolean = true): ZonedDateTime {
        val shifted = Instant.ofEpochMilli(time).plusMillis(offsetGMT.toLong()).atZone(GMT)
        return if (round && shifted.second >= 30) shifted.plusMinutes(1) else shifted
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
        context: Context,
    ): String {
        var routeName = ""

        if (routeShortName != null || routeLongName != null) {
            routeName += context.resources.getString(R.string.connector_before_route)
            if (routeShortName != null) {
                routeName += " $routeShortName"
            } else if (routeLongName != null) {
                routeName += " " + tailAndTruncateSentence(
                    routeLongName, OTPConstants.ROUTE_SHORT_NAME_MAX_SIZE
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
        includeShortName: Boolean,
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
