/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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
package org.onebusaway.android.util

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import kotlin.math.abs
import org.onebusaway.android.R

/**
 * Display-formatting helpers relocated from the legacy UIUtils.
 */
object DisplayFormat {

    private const val MINUTES_IN_HOUR = 60

    /**
     * Returns the time formatting as "1:10pm" to be displayed as an absolute time for an
     * arrival/departure
     *
     * @param time an arrival or departure time (e.g., from ArrivalInfo)
     * @return the time formatting as "1:10pm" to be displayed as an absolute time for an
     * arrival/departure
     */
    @JvmStatic
    fun formatTime(context: Context, time: Long): String = DateUtils.formatDateTime(
        context,
        time,
        DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_NO_NOON or
            DateUtils.FORMAT_NO_MIDNIGHT
    )

    /**
     * Takes the number of minutes, and returns a user-readable string
     * saying the number of minutes in which no arrivals are coming,
     * or the number of hours and minutes if minutes if minutes > 60
     *
     * @param minutes            number of minutes for which there are no upcoming arrivals
     * @param additionalArrivals true if the response should include the word additional, false if
     *                           it should not
     * @param shortFormat        true if the format should be abbreviated, false if it should be
     *                           long
     * @return a user-readable string saying the number of minutes in which no arrivals are coming,
     * or the number of hours and minutes if minutes > 60
     */
    @JvmStatic
    fun getNoArrivalsMessage(
        context: Context,
        minutes: Int,
        additionalArrivals: Boolean,
        shortFormat: Boolean
    ): String {
        if (minutes <= MINUTES_IN_HOUR) {
            // Return just minutes
            if (additionalArrivals) {
                return if (shortFormat) {
                    // Abbreviated version
                    context
                        .getString(
                            R.string.stop_info_no_additional_data_minutes_short_format,
                            minutes
                        )
                } else {
                    // Long version
                    context
                        .getString(R.string.stop_info_no_additional_data_minutes, minutes)
                }
            } else {
                return if (shortFormat) {
                    // Abbreviated version
                    context
                        .getString(R.string.stop_info_nodata_minutes_short_format, minutes)
                } else {
                    // Long version
                    context.getString(R.string.stop_info_nodata_minutes, minutes)
                }
            }
        } else {
            // Return hours and minutes
            if (additionalArrivals) {
                return if (shortFormat) {
                    // Abbreviated version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_no_additional_data_hours_minutes_short_format,
                            minutes / 60,
                            minutes % 60,
                            minutes / 60
                        )
                } else {
                    // Long version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_no_additional_data_hours_minutes,
                            minutes / 60,
                            minutes % 60,
                            minutes / 60
                        )
                }
            } else {
                return if (shortFormat) {
                    // Abbreviated version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_nodata_hours_minutes_short_format,
                            minutes / 60,
                            minutes % 60,
                            minutes / 60
                        )
                } else {
                    // Long version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_nodata_hours_minutes,
                            minutes / 60,
                            minutes % 60,
                            minutes / 60
                        )
                }
            }
        }
    }

    /** One piece of an ETA pill's display — a bold number or a small unit abbreviation, rendered in
     *  sequence — see [formatEtaParts]. */
    data class EtaPart(val text: String, val emphasized: Boolean)

    /**
     * Splits a non-zero ETA in minutes into one consistent "Xhr Ymin" shape of alternating
     * bold-number/small-unit parts, rather than switching formats by magnitude (#1777): under an
     * hour it's just `["23", "min"]` (the "0hr" is omitted), and past an hour it's
     * `["1", "hr", " 30", "min"]`. Every number stays bold-sized so the leftover minutes stay as
     * legible as the hour count. [minutes] may be negative for a recent-past arrival; the sign
     * stays on the leading number.
     */
    @JvmStatic
    fun formatEtaParts(context: Context, minutes: Long): List<EtaPart> = formatEtaParts(
        minutes = minutes,
        minutesAbbrev = context.getString(R.string.minutes_abbreviation),
        hoursAbbrev = context.getString(R.string.eta_hours_abbreviation)
    )

    /** Pure core of [formatEtaParts] — takes the localized abbreviations directly so the split/roundoff
     *  logic is unit-testable without an Android [Context]. */
    @VisibleForTesting
    fun formatEtaParts(minutes: Long, minutesAbbrev: String, hoursAbbrev: String): List<EtaPart> {
        val magnitude = abs(minutes)
        val sign = if (minutes < 0) "-" else ""
        val hours = magnitude / MINUTES_IN_HOUR
        val remainderMinutes = magnitude % MINUTES_IN_HOUR
        return if (hours == 0L) {
            listOf(
                EtaPart("$sign$remainderMinutes", emphasized = true),
                EtaPart(minutesAbbrev, emphasized = false)
            )
        } else {
            listOf(
                EtaPart("$sign$hours", emphasized = true),
                EtaPart(hoursAbbrev, emphasized = false),
                EtaPart(" $remainderMinutes", emphasized = true),
                EtaPart(minutesAbbrev, emphasized = false)
            )
        }
    }

    /** [direction] resolved to a localized display string ("Northbound"), or null when absent/blank. */
    @JvmStatic
    fun stopDirectionText(context: Context, direction: String?): String? = direction?.takeIf { it.isNotBlank() }
        ?.let { context.getString(getStopDirectionText(it)) }
        ?.takeIf { it.isNotEmpty() }

    @JvmStatic
    @StringRes
    fun getStopDirectionText(direction: String): Int = when (direction) {
        "N" -> R.string.direction_n
        "NW" -> R.string.direction_nw
        "W" -> R.string.direction_w
        "SW" -> R.string.direction_sw
        "S" -> R.string.direction_s
        "SE" -> R.string.direction_se
        "E" -> R.string.direction_e
        "NE" -> R.string.direction_ne
        else -> R.string.direction_none
    }
}
