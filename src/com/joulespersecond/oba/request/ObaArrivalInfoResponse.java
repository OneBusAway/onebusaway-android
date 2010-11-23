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

import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaReferencesElement;
import com.joulespersecond.oba.elements.ObaSituation;
import com.joulespersecond.oba.elements.ObaStop;

import java.util.List;

/**
 * Response object for ObaArrivalInfoRequest requests.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaArrivalInfoResponse extends ObaResponseWithRefs {
    private static final class Entry {
        private static final Entry EMPTY_OBJECT = new Entry();

        private final String stopId;
        private final ObaArrivalInfo[] arrivalsAndDepartures;
        private final String[] nearbyStopIds;
        private final String[] situationIds;

        private Entry() {
            stopId = "";
            arrivalsAndDepartures = ObaArrivalInfo.EMPTY_ARRAY;
            nearbyStopIds = new String[] {};
            situationIds = new String[] {};
        }
    }

    private static final class Data {
        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;
        private final Entry entry = Entry.EMPTY_OBJECT;
    }
    private final Data data;

    ObaArrivalInfoResponse() {
        data = Data.EMPTY_OBJECT;
    }

    /**
     * @return The stop information for this arrival info.
     */
    public ObaStop getStop() {
        return data.references.getStop(data.entry.stopId);
    }

    /**
     * @return The list of nearby stops.
     */
    public List<ObaStop> getNearbyStops() {
        return data.references.getStops(data.entry.nearbyStopIds);
    }

    public ObaArrivalInfo[] getArrivalInfo() {
        return data.entry.arrivalsAndDepartures;
    }

    public List<ObaSituation> getSituations() {
        return data.references.getSituations(data.entry.situationIds);
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
