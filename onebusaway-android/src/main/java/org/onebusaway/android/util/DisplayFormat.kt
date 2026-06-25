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
    fun formatTime(context: Context, time: Long): String {
        return DateUtils.formatDateTime(
            context,
            time,
            DateUtils.FORMAT_SHOW_TIME or
                    DateUtils.FORMAT_NO_NOON or
                    DateUtils.FORMAT_NO_MIDNIGHT
        )
    }

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
        context: Context, minutes: Int,
        additionalArrivals: Boolean, shortFormat: Boolean
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
                            minutes / 60, minutes % 60, minutes / 60
                        )
                } else {
                    // Long version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_no_additional_data_hours_minutes,
                            minutes / 60, minutes % 60, minutes / 60
                        )
                }
            } else {
                return if (shortFormat) {
                    // Abbreviated version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_nodata_hours_minutes_short_format,
                            minutes / 60,
                            minutes % 60, minutes / 60
                        )
                } else {
                    // Long version
                    context.resources
                        .getQuantityString(
                            R.plurals.stop_info_nodata_hours_minutes,
                            minutes / 60,
                            minutes % 60, minutes / 60
                        )
                }
            }
        }
    }

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
