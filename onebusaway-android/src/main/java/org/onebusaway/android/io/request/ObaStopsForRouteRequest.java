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
 * Retrieve the set of stops serving a particular route
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/stops-for-route.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopsForRouteRequest extends RequestBase
        implements Callable<ObaStopsForRouteResponse> {

    protected ObaStopsForRouteRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String routeId) {
            super(context, getPathWithId("/stops-for-route/", routeId));
        }

        public Builder setIncludeShapes(boolean includePolylines) {
            mBuilder.appendQueryParameter("includePolylines",
                    includePolylines ? "true" : "false");
            return this;
        }

        public ObaStopsForRouteRequest build() {
            return new ObaStopsForRouteRequest(buildUri());
        }
    }

    @Override
    public ObaStopsForRouteResponse call() {
        return call(ObaStopsForRouteResponse.class);
    }

    @Override
    public String toString() {
        return "ObaStopsForRouteRequest [mUri=" + mUri + "]";
    }
}
