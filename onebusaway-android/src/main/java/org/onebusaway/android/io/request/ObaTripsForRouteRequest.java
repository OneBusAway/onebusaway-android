/*
 * Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com)
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
 * Retrieve info about trips for a specific route
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/trips-for-route.html}
 *
 * @author Sean Barbeau (sjbarbeau@gmail.com)
 */
public final class ObaTripsForRouteRequest extends RequestBase
        implements Callable<ObaTripsForRouteResponse> {

    protected ObaTripsForRouteRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends BuilderBase {

        public Builder(Context context, String routeId) {
            super(context, getPathWithId("/trips-for-route/", routeId));
        }

        /**
         * Determines whether the schedule element is included.
         * Defaults to 'true'
         *
         * @return This object.
         */
        public Builder setIncludeSchedule(boolean includeSchedule) {
            mBuilder.appendQueryParameter("includeSchedule", String.valueOf(includeSchedule));
            return this;
        }

        /**
         * Determines whether the status element is included.
         * Defaults to 'false'
         *
         * @return This object.
         */
        public Builder setIncludeStatus(boolean includeStatus) {
            mBuilder.appendQueryParameter("includeStatus", String.valueOf(includeStatus));
            return this;
        }

        public ObaTripsForRouteRequest build() {
            return new ObaTripsForRouteRequest(buildUri());
        }
    }

    /**
     * Helper method for constructing new instances.
     *
     * @param context The package context.
     * @param routeId The route Id to request.
     * @return The new request instance.
     */
    public static ObaTripsForRouteRequest newRequest(Context context, String routeId) {
        return new Builder(context, routeId).build();
    }

    @Override
    public ObaTripsForRouteResponse call() {
        return call(ObaTripsForRouteResponse.class);
    }

    @Override
    public String toString() {
        return "ObaTripsForRouteRequest [mUri=" + mUri + "]";
    }
}
