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
 * Search for stops near a specific location, optionally by stop code
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_StopsForLocation}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopsForLocationRequest extends RequestBase
        implements Callable<ObaStopsForLocationResponse> {
    protected ObaStopsForLocationRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {
        public Builder(Context context, GeoPoint location) {
            super(context, BASE_PATH + "/stops-for-location.json");
            mBuilder.appendQueryParameter("lat", String.valueOf(location.getLatitudeE6()/1E6));
            mBuilder.appendQueryParameter("lon", String.valueOf(location.getLongitudeE6()/1E6));
        }

        /**
         * Sets the optional search radius.
         * @param radius The search radius, in meters.
         */
        public Builder setRadius(int radius) {
            mBuilder.appendQueryParameter("radius", String.valueOf(radius));
            return this;
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
         * An alternative to {@link #setRadius(int)} to set the search bounding box
         * @param latSpan The latitude span of the bounding box in microdegrees.
         * @param lonSpan The longitude span of the bounding box in microdegrees.
         */
        public Builder setSpan(int latSpan, int lonSpan) {
            mBuilder.appendQueryParameter("latSpan", String.valueOf(latSpan/1E6));
            mBuilder.appendQueryParameter("lonSpan", String.valueOf(lonSpan/1E6));
            return this;
        }

        /**
         * A specific route short name to search for.
         * @param query The short name query string.
         */
        public Builder setQuery(String query) {
            mBuilder.appendQueryParameter("query", query);
            return this;
        }

        public ObaStopsForLocationRequest build() {
            return new ObaStopsForLocationRequest(buildUri());
        }
    }

    @Override
    public ObaStopsForLocationResponse call() {
        return call(ObaStopsForLocationResponse.class);
    }

    @Override
    public String toString() {
        return "ObaStopsForLocationRequest [mUri=" + mUri + "]";
    }
}
