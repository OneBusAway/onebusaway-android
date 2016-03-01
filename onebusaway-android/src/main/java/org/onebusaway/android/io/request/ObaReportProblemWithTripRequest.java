/*
 * Copyright (C) 2012-2016 Paul Watts (paulcwatts@gmail.com)
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
 * Report a problem with a trip.
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/report-problem-with-trip.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaReportProblemWithTripRequest extends RequestBase
        implements Callable<ObaReportProblemWithTripResponse> {

    // What is says.
    public static final String VEHICLE_NEVER_CAME = "vehicle_never_came";

    // The vehicle arrived earlier than predicted
    public static final String VEHICLE_CAME_EARLY = "vehicle_came_early";

    // The vehicle arrived later than predicted
    public static final String VEHICLE_CAME_LATE = "vehicle_came_late";

    // The headsign reported by OneBusAway differed from the vehicles actual headsign.
    public static final String WRONG_HEADSIGN = "wrong_headsign";

    // The trip in question does not actually service the indicated stop.
    public static final String VEHICLE_DOES_NOT_STOP_HERE = "vehicle_does_not_stop_here";

    // Other
    public static final String OTHER = "other";

    protected ObaReportProblemWithTripRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String tripId) {
            super(context, BASE_PATH + "/report-problem-with-trip.json");
            mBuilder.appendQueryParameter("tripId", tripId);
        }

        /**
         * Sets the optional stop ID indicating the stop where
         * the user is experiencing the problem.
         *
         * @param stopId The stop ID.
         */
        public Builder setStopId(String stopId) {
            mBuilder.appendQueryParameter("stopId", stopId);
            return this;
        }

        /**
         * Sets the optional service date of the trip.
         *
         * @param serviceDate The service date.
         */
        public Builder setServiceDate(long serviceDate) {
            mBuilder.appendQueryParameter("serviceDate", String.valueOf(serviceDate));
            return this;
        }

        /**
         * Sets the optional vehicle actively serving the trip.
         *
         * @param vehicleId The vehicle actively serving the trip.
         */
        public Builder setVehicleId(String vehicleId) {
            mBuilder.appendQueryParameter("vehicleId", vehicleId);
            return this;
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


        /**
         * Sets true/false to indicate if the user is on the transit vehicle experiencing the
         * problem.
         *
         * @param onVehicle If the user is on the vehicle.
         */
        public Builder setUserOnVehicle(boolean onVehicle) {
            mBuilder.appendQueryParameter("userOnVehicle", String.valueOf(onVehicle));
            return this;
        }

        /**
         * Set the vehicle number, as reported by the user.
         *
         * @param vehicleNumber The vehicle as reported by the user.
         */
        public Builder setUserVehicleNumber(String vehicleNumber) {
            mBuilder.appendQueryParameter("userVehicleNumber", vehicleNumber);
            return this;
        }

        public ObaReportProblemWithTripRequest build() {
            return new ObaReportProblemWithTripRequest(buildUri());
        }
    }

    @Override
    public ObaReportProblemWithTripResponse call() {
        return call(ObaReportProblemWithTripResponse.class);
    }

    @Override
    public String toString() {
        return "ObaReportProblemWithTripRequest [mUri=" + mUri + "]";
    }
}
