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


public final class ObaRouteIdsForAgencyResponse extends ObaResponse {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        // At this point this can have references, but never does.
        //private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;
        private final String[] list;

        private boolean limitExceeded;

        private Data() {
            list = new String[]{};
            limitExceeded = false;
        }
    }

    private final Data data;

    private ObaRouteIdsForAgencyResponse() {
        data = Data.EMPTY_OBJECT;
    }

    /**
     * @return The list of route IDs for this agency.
     */
    public String[] getRouteIds() {
        return data.list;
    }

    /**
     * @return Whether or not this list has exceeded the size of the response.
     */
    public boolean getLimitExceeded() {
        return data.limitExceeded;
    }
}
