/*
 * Copyright (C) 2010-2013 Paul Watts (paulcwatts@gmail.com)
 * and individual contributors.
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
 * Request arrival information for a stop.
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/arrivals-and-departures-for-stop.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaArrivalInfoRequest extends RequestBase implements
        Callable<ObaArrivalInfoResponse> {

    protected ObaArrivalInfoRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String stopId) {
            super(context, getPathWithId("/arrivals-and-departures-for-stop/", stopId));
        }

        public Builder(Context context, String stopId, int minutesAfter) {
            super(context, getPathWithId("/arrivals-and-departures-for-stop/", stopId));
            mBuilder.appendQueryParameter("minutesAfter", String.valueOf(minutesAfter));
        }

        public ObaArrivalInfoRequest build() {
            return new ObaArrivalInfoRequest(buildUri());
        }
    }

    /**
     * Helper method for constructing new instances.
     *
     * @param context The package context.
     * @param stopId  The stop Id to request.
     * @return The new request instance.
     */
    public static ObaArrivalInfoRequest newRequest(Context context, String stopId) {
        return new Builder(context, stopId).build();
    }

    /**
     * Helper method for constructing new instances.
     *
     * @param context      The package context.
     * @param stopId       The stop Id to request.
     * @param minutesAfter includes vehicles arriving or departing in the next minutesAfter minutes
     * @return The new request instance.
     */
    public static ObaArrivalInfoRequest newRequest(Context context, String stopId,
            int minutesAfter) {
        return new Builder(context, stopId, minutesAfter).build();
    }

    @Override
    public ObaArrivalInfoResponse call() {
        return call(ObaArrivalInfoResponse.class);
    }

}
