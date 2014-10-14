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

import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaReferencesElement;
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripDetailsElement;

/**
 * Response object for ObaStopsForLocation objects.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaTripsForLocationResponse extends ObaResponseWithRefs {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references;

        private final ObaTripDetailsElement[] list;

        private final boolean outOfRange;

        private final boolean limitExceeded;

        private Data() {
            references = ObaReferencesElement.EMPTY_OBJECT;
            list = ObaTripDetailsElement.EMPTY_ARRAY;
            outOfRange = false;
            limitExceeded = false;
        }
    }

    private final Data data;

    private ObaTripsForLocationResponse() {
        data = Data.EMPTY_OBJECT;
    }

    /**
     * @return The list of trips.
     */
    public ObaTripDetails[] getTrips() {
        return data.list;
    }

    /**
     * @return Whether the request is out of range of the coverage area.
     */
    public boolean getOutOfRange() {
        return data.outOfRange;
    }

    /**
     * @return Whether the results exceeded the limits of the response.
     */
    public boolean getLimitExceeded() {
        return data.limitExceeded;
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
