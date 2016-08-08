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

package org.onebusaway.android.directions.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class OTPConstants {

    public static final String INTENT_UPDATE_TRIP_TIME_ACTION
            = "org.onebusaway.android.UPDATE_TRIP_TIME";

    public static final String INTENT_NOTIFICATION_ACTION_OPEN_APP
            = "org.onebusaway.android.NOTIFICATION_OPEN_APP";

    public static final String PREFERENCE_KEY_LIVE_UPDATES = "live_updates";

    public static final long DEFAULT_UPDATE_INTERVAL_TRIP_TIME = TimeUnit.SECONDS.toMillis(60);

    public static final String FORMAT_OTP_SERVER_DATE_RESPONSE = "yyyy-MM-dd\'T\'HH:mm:ssZZ";

    public static final String PREFERENCE_KEY_API_VERSION = "last_api_version";

    public static final int API_VERSION_V1 = 1;

    public static final String FORMAT_DISTANCE_METERS = "%.0f";

    public static final String FORMAT_DISTANCE_KILOMETERS = "%.1f";

    public static final int ROUTE_SHORT_NAME_MAX_SIZE = 16;

    public static final String TRIP_PLAN_TIME_STRING_FORMAT = "hh:mm a";

    public static final String TRIP_PLAN_DATE_STRING_FORMAT = "MMMM dd";

    public static final String TRIP_RESULTS_TIME_STRING_FORMAT = "MMMM dd '%s' hh:mm a";

    public static final String TRIP_RESULTS_TIME_STRING_FORMAT_SUMMARY = "hh:mma";

    public static final String ITINERARIES = "org.onebusaway.android.Itineraries";

    public static final String SELECTED_ITINERARY = "org.onebusaway.android.SELECTED_ITINERARY";

    public static final String SHOW_MAP = "org.onebusaway.android.SHOW_MAP";

    public static final String FORMAT_OTP_SERVER_DATE_REQUEST = "MM-dd-yyyy";

    public static final String FORMAT_OTP_SERVER_TIME_REQUEST = "hh:mma";

    public static final Locale OTP_LOCALE = Locale.US;
}
