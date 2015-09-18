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
 * Retrieve info about a specific stop.
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/stop.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopRequest extends RequestBase implements Callable<ObaStopResponse> {

    protected ObaStopRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String stopId) {
            super(context, getPathWithId("/stop/", stopId));
        }

        public ObaStopRequest build() {
            return new ObaStopRequest(buildUri());
        }
    }

    /**
     * Helper method for constructing new instances.
     *
     * @param context The package context.
     * @param stopId  The stopId to request.
     * @return The new request instance.
     */
    public static ObaStopRequest newRequest(Context context, String stopId) {
        return new Builder(context, stopId).build();
    }

    @Override
    public ObaStopResponse call() {
        return call(ObaStopResponse.class);
    }

    @Override
    public String toString() {
        return "ObaStopRequest [mUri=" + mUri + "]";
    }
}
