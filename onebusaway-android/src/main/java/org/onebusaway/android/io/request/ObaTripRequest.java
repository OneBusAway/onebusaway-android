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
package org.onebusaway.android.io.request;

import android.content.Context;
import android.net.Uri;

import java.util.concurrent.Callable;

/**
 * Retrieve info about a specific trip.
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/trip.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaTripRequest extends RequestBase implements Callable<ObaTripResponse> {

    protected ObaTripRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String tripId) {
            super(context, getPathWithId("/trip/", tripId));
        }

        public ObaTripRequest build() {
            return new ObaTripRequest(buildUri());
        }
    }

    /**
     * Helper method for constructing new instances.
     *
     * @param context The package context.
     * @param tripId  The tripId to request.
     * @return The new request instance.
     */
    public static ObaTripRequest newRequest(Context context, String tripId) {
        return new Builder(context, tripId).build();
    }

    @Override
    public ObaTripResponse call() {
        return call(ObaTripResponse.class);
    }

    @Override
    public String toString() {
        return "ObaTripRequest [mUri=" + mUri + "]";
    }
}
