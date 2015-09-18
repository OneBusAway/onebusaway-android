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
 * Retrieve the list of all stops for a particular agency by ID.
 * {http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/stop-ids-for-agency.html}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopIdsForAgencyRequest extends RequestBase
        implements Callable<ObaStopIdsForAgencyResponse> {

    protected ObaStopIdsForAgencyRequest(Uri uri) {
        super(uri);
    }

    public static class Builder extends RequestBase.BuilderBase {

        public Builder(Context context, String agencyId) {
            super(context, getPathWithId("/stop-ids-for-agency/", agencyId));
        }

        public ObaStopIdsForAgencyRequest build() {
            return new ObaStopIdsForAgencyRequest(buildUri());
        }
    }

    /**
     * Helper method for constructing new instances.
     *
     * @param context  The package context.
     * @param agencyId The agencyId to request.
     * @return The new request instance.
     */
    public static ObaStopIdsForAgencyRequest newRequest(Context context, String agencyId) {
        return new Builder(context, agencyId).build();
    }

    @Override
    public ObaStopIdsForAgencyResponse call() {
        return call(ObaStopIdsForAgencyResponse.class);
    }

    @Override
    public String toString() {
        return "ObaStopIdsForAgencyRequest [mUri=" + mUri + "]";
    }
}
