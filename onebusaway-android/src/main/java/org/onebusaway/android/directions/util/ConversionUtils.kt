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
import java.util.Calendar
import java.util.TimeZone

/**
 * @author Khoa Tran
 * @author Simon Jacobs - conversion for OBA. Added Imperial measurements
 */
object ConversionUtils {

    private const val TAG = "ConversionUtils"

    private const val FEET_PER_METER = 3.281

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
        // NOTE: this locale-aware, GMT-anchored formatter is intentionally still Calendar-based.
        // It pairs android.text.format.DateFormat (which requires java.util.Date/Calendar) with
        // GMT wall-clock rounding and same-day comparisons; rewriting it on java.time risks a
        // subtle formatting regression and is deferred until it can be device-verified.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext)
        val dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext)
        timeFormat.timeZone = TimeZone.getTimeZone("GMT")
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")

        cal.timeInMillis = time

        var noDeviceTimezoneNote = ""
        if (offsetGMT != TimeZone.getDefault().getOffset(time)) {
            noDeviceTimezoneNote = "GMT"
            if (offsetGMT != 0) {
                noDeviceTimezoneNote += offsetGMT / 3600000
            }
        }

        cal.add(Calendar.MILLISECOND, offsetGMT)

        if (cal.get(Calendar.SECOND) >= 30) {
            cal.add(Calendar.MINUTE, 1)
        }

        val spannableTime = SpannableString(timeFormat.format(cal.time))
        if (color != -1) {
            spannableTime.setSpan(ForegroundColorSpan(color), 0, spannableTime.length, 0)
        }

        return if (inLine) {
            when {
                isToday(cal) -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ", spannableTime, " ", noDeviceTimezoneNote
                )

                isTomorrow(cal) -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_next_day), " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ", spannableTime, " ", noDeviceTimezoneNote
                )

                else -> TextUtils.concat(
                    " ",
                    applicationContext.resources.getString(R.string.time_connector_before_date), " ",
                    dateFormat.format(cal.time), " ",
                    applicationContext.resources.getString(R.string.time_connector_before_time),
                    " ", spannableTime, " ", noDeviceTimezoneNote
                )
            }
        } else {
            when {
                isToday(cal) -> TextUtils.concat(spannableTime, " ", noDeviceTimezoneNote)

                isTomorrow(cal) -> TextUtils.concat(
                    " ", spannableTime, ", ",
                    applicationContext.resources.getString(R.string.time_connector_next_day), " ",
                    noDeviceTimezoneNote
                )

                else -> TextUtils.concat(
                    spannableTime, ", ", dateFormat.format(cal.time), " ", noDeviceTimezoneNote
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
        // NOTE: intentionally still Calendar-based; see the note on getTimeWithContext above.
        val calOldTime = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val calNewTime = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val timeFormat = android.text.format.DateFormat.getTimeFormat(applicationContext)
        val dateFormat = android.text.format.DateFormat.getDateFormat(applicationContext)
        timeFormat.timeZone = TimeZone.getTimeZone("GMT")
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        var beforeDateString: CharSequence = ""
        val beforeTimeString: CharSequence
        var newDateString: CharSequence = ""
        val timezone: CharSequence
        val oldDateString: SpannableString
        val newTimeString: SpannableString
        val oldTimeString: SpannableString
        calOldTime.timeInMillis = oldTime
        calNewTime.timeInMillis = newTime

        var noDeviceTimezoneNote = ""
        if (offsetGMT != TimeZone.getDefault().getOffset(oldTime)) {
            noDeviceTimezoneNote = "GMT"
            if (offsetGMT != 0) {
                noDeviceTimezoneNote += offsetGMT / 3600000
            }
        }

        calOldTime.add(Calendar.MILLISECOND, offsetGMT)
        calNewTime.add(Calendar.MILLISECOND, offsetGMT)

        if (isTomorrow(calNewTime)) {
            oldDateString = SpannableString(
                applicationContext.resources.getString(R.string.time_connector_next_day) + " "
            )
        } else {
            beforeDateString =
                applicationContext.resources.getString(R.string.time_connector_before_date) + " "
            oldDateString = SpannableString(dateFormat.format(calNewTime.time) + " ")
        }

        if (calNewTime.get(Calendar.DAY_OF_MONTH) != calOldTime.get(Calendar.DAY_OF_MONTH)) {
            beforeDateString =
                applicationContext.resources.getString(R.string.time_connector_before_date) + " "
            newDateString = dateFormat.format(calNewTime.time) + " "
            oldDateString.setSpan(StrikethroughSpan(), 0, oldDateString.length - 1, 0)
        }

        beforeTimeString =
            applicationContext.resources.getString(R.string.time_connector_before_time) + " "
        timezone = noDeviceTimezoneNote

        val color = applicationContext.resources.getColor(
            ArrivalInfoUtils.computeColorFromDeviation(
                calNewTime.timeInMillis - calOldTime.timeInMillis
            )
        )

        newTimeString = SpannableString(timeFormat.format(calNewTime.time) + " ")
        newTimeString.setSpan(ForegroundColorSpan(color), 0, newTimeString.length, 0)
        if (calOldTime.get(Calendar.HOUR_OF_DAY) != calNewTime.get(Calendar.HOUR_OF_DAY) ||
            calOldTime.get(Calendar.MINUTE) != calNewTime.get(Calendar.MINUTE)
        ) {
            oldTimeString = SpannableString(timeFormat.format(calOldTime.time) + " ")
            oldTimeString.setSpan(StrikethroughSpan(), 0, oldTimeString.length - 1, 0)
        } else {
            oldTimeString = SpannableString(" ")
        }
        return TextUtils.concat(
            beforeDateString, newDateString, oldDateString,
            beforeTimeString, oldTimeString, newTimeString, timezone
        )
    }

    @JvmStatic
    fun isToday(cal: Calendar): Boolean {
        val actualTime = Calendar.getInstance()
        return actualTime.get(Calendar.ERA) == cal.get(Calendar.ERA) &&
            actualTime.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            actualTime.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

    @JvmStatic
    fun isTomorrow(cal: Calendar): Boolean {
        val tomorrowTime = Calendar.getInstance()
        tomorrowTime.add(Calendar.DAY_OF_YEAR, 1)
        return tomorrowTime.get(Calendar.ERA) == cal.get(Calendar.ERA) &&
            tomorrowTime.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            tomorrowTime.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }

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
