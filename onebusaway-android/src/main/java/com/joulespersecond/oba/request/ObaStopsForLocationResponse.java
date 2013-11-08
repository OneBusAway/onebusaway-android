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

import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaReferencesElement;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.elements.ObaStopElement;

/**
 * Response object for ObaStopsForLocation objects.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaStopsForLocationResponse extends ObaResponseWithRefs {
    private static final class Data {
        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references;
        private final ObaStopElement[] list;
        private final boolean outOfRange;
        private final boolean limitExceeded;

        private Data() {
            references = ObaReferencesElement.EMPTY_OBJECT;
            list = ObaStopElement.EMPTY_ARRAY;
            outOfRange = false;
            limitExceeded = false;
        }
    }
    private final Data data;

    private ObaStopsForLocationResponse() {
        data = Data.EMPTY_OBJECT;
    }

    /**
     * @return The list of stops.
     */
    public ObaStop[] getStops() {
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
