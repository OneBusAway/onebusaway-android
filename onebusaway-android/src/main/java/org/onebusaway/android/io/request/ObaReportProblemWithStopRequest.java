/*
 * Copyright (C) 2012 - 2016 Paul Watts (paulcwatts@gmail.com)
 * University of South Florida (cagricetin@mail.usf.edu)
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
package org.onebusaway.android.io.request;

import android.content.Context;
import android.net.Uri;

import java.util.concurrent.Callable;

/**
 * Report a problem with a stop.
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/report-problem-with-stop.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaReportProblemWithStopRequest extends RequestBase
        implements Callable<ObaReportProblemWithStopResponse> {
    //private static final String TAG = "ObaReportProblemWithStopRequest";

    // The stop name in OneBusAway differs from the actual stop name.
    public static final String NAME_WRONG = "stop_name_wrong";

    // The stop number in OneBusAway differs from the actual stop number.
    public static final String NUMBER_WRONG = "stop_number_wrong";

    // The stop location in OneBusAway differs from the action location.
    public static final String LOCATION_WRONG = "stop_location_wrong";

    // An expected route or trip is missing from the stop.
    public static final String ROUTE_OR_TRIP_MISSING = "route_or_trip_missing";

    // Other
    public static final String OTHER = "other";

    protected ObaReportProblemWithStopRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String stopId) {
            super(context, BASE_PATH + "/report-problem-with-stop.json");
            mBuilder.appendQueryParameter("stopId", stopId);

        }

        /**
         * Sets the optional problem code.
         *
         * @param code The problem code.
         */
        public Builder setCode(String code) {
            mBuilder.appendQueryParameter("code", code);
            // This is also for the old, JSON-encoded "data" format of the API.
            String data = String.format("{\"code\":\"%s\"}", code);
            mBuilder.appendQueryParameter("data", data);
            return this;
        }

        /**
         * Sets the optional user comment.
         *
         * @param comment The user comment.
         */
        public Builder setUserComment(String comment) {
            mBuilder.appendQueryParameter("userComment", comment);
            return this;
        }

        /**
         * Sets the optional user location.
         *
         * @param lat The user's current location.
         * @param lon The user's current location.
         */
        public Builder setUserLocation(double lat, double lon) {
            mBuilder.appendQueryParameter("userLat", String.valueOf(lat));
            mBuilder.appendQueryParameter("userLon", String.valueOf(lon));
            return this;
        }

        /**
         * Sets the optional user's location accuracy, in meters.
         *
         * @param meters The user's location accuracy in meters.
         */
        public Builder setUserLocationAccuracy(int meters) {
            mBuilder.appendQueryParameter("userLocationAccuracy", String.valueOf(meters));
            return this;
        }

        public ObaReportProblemWithStopRequest build() {
            return new ObaReportProblemWithStopRequest(buildUri());
        }
    }

    @Override
    public ObaReportProblemWithStopResponse call() {
        return call(ObaReportProblemWithStopResponse.class);
    }

    @Override
    public String toString() {
        return "ObaReportProblemWithStopRequest [mUri=" + mUri + "]";
    }
}
