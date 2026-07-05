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

import android.graphics.Color
import org.onebusaway.android.BuildConfig
import java.util.Locale
import java.util.concurrent.TimeUnit

object OTPConstants {

    @JvmField
    val INTENT_CHECK_TRIP_TIME = BuildConfig.APPLICATION_ID + ".directions.action.CHECK"

    @JvmField
    val INTENT_START_CHECKS = BuildConfig.APPLICATION_ID + ".directions.action.START_CHECKS"

    const val PREFERENCE_KEY_LIVE_UPDATES = "live_updates"

    @JvmField
    val DEFAULT_UPDATE_INTERVAL_TRIP_TIME = TimeUnit.SECONDS.toMillis(60)

    @JvmField
    val REALTIME_SERVICE_QUERY_WINDOW = TimeUnit.HOURS.toMillis(1)

    @JvmField
    val REALTIME_SERVICE_DELAY_THRESHOLD = TimeUnit.MINUTES.toSeconds(2)

    const val FORMAT_OTP_SERVER_DATE_RESPONSE = "yyyy-MM-dd'T'HH:mm:ssZZ"

    const val PREFERENCE_KEY_API_VERSION = "last_api_version"

    const val API_VERSION_V1 = 1

    const val FORMAT_DISTANCE_METERS = "%.0f"

    const val FORMAT_DISTANCE_KILOMETERS = "%.1f"

    const val ROUTE_SHORT_NAME_MAX_SIZE = 16

    const val TRIP_PLAN_TIME_STRING_FORMAT = "hh:mm a"

    const val TRIP_PLAN_DATE_STRING_FORMAT = "MMMM dd"

    const val TRIP_RESULTS_TIME_STRING_FORMAT = "MMMM dd '%s' hh:mm a"

    const val TRIP_RESULTS_TIME_STRING_FORMAT_SUMMARY = "hh:mma"

    const val ITINERARIES = "org.onebusaway.android.Itineraries"

    const val SELECTED_ITINERARY = "org.onebusaway.android.SELECTED_ITINERARY"

    const val SHOW_MAP = "org.onebusaway.android.SHOW_MAP"

    const val NOTIFICATION_TARGET = "org.onebusaway.android.NOTIFICATION_TARGET"

    const val FORMAT_OTP_SERVER_DATE_REQUEST = "MM-dd-yyyy"

    const val FORMAT_OTP_SERVER_TIME_REQUEST = "hh:mma"

    @JvmField
    val OTP_LOCALE: Locale = Locale.US

    // flag to indicate intent sent by or on behalf of TripPlanActivity
    const val INTENT_SOURCE = "org.onebusaway.android.INTENT_SOURCE"

    @JvmField
    val OTP_TRANSIT_COLOR = Color.parseColor("#006500")

    enum class Source { ACTIVITY, NOTIFICATION }
}
