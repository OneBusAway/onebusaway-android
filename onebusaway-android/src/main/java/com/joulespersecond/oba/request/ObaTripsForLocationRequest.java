/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.request;

import com.google.android.maps.GeoPoint;

import android.content.Context;
import android.net.Uri;

import java.util.concurrent.Callable;

/**
 * Search for active trips near a specific location
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_TripsForLocation}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaTripsForLocationRequest extends RequestBase
        implements Callable<ObaTripsForLocationResponse> {
    protected ObaTripsForLocationRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {
        public Builder(Context context, GeoPoint location) {
            super(context, BASE_PATH + "/trips-for-location.json");
            mBuilder.appendQueryParameter("lat", String.valueOf(location.getLatitudeE6()/1E6));
            mBuilder.appendQueryParameter("lon", String.valueOf(location.getLongitudeE6()/1E6));
        }

        /**
         * An alternative to {@link #setRadius(int)} to set the search bounding box
         * @param latSpan The latitude span of the bounding box.
         * @param lonSpan The longitude span of the bounding box.
         */
        public Builder setSpan(double latSpan, double lonSpan) {
            mBuilder.appendQueryParameter("latSpan", String.valueOf(latSpan));
            mBuilder.appendQueryParameter("lonSpan", String.valueOf(lonSpan));
            return this;
        }

        /**
         * Determines whether the full trip element is included in the references.
         * Defaults to 'false'
         * @return This object.
         */
        public Builder setIncludeTrips(boolean includeTrips) {
            mBuilder.appendQueryParameter("includeTrips", String.valueOf(includeTrips));
            return this;
        }

        /**
         * Determines whether the schedule element is included in the result list.
         * Defaults to 'false'
         * @return This object.
         */
        public Builder setIncludeSchedules(boolean includeSchedules) {
            mBuilder.appendQueryParameter("includeSchedules", String.valueOf(includeSchedules));
            return this;
        }

        public ObaTripsForLocationRequest build() {
            return new ObaTripsForLocationRequest(buildUri());
        }
    }

    @Override
    public ObaTripsForLocationResponse call() {
        return call(ObaTripsForLocationResponse.class);
    }

    @Override
    public String toString() {
        return "ObaTripsForLocationRequest [mUri=" + mUri + "]";
    }
}
