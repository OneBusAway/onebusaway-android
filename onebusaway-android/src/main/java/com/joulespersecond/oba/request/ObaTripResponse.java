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
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaTrip;
import com.joulespersecond.oba.elements.ObaTripElement;

public final class ObaTripResponse extends ObaResponseWithRefs implements
        ObaTrip {

    private static final class Data {
        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;
        private final ObaTripElement entry = ObaTripElement.EMPTY_OBJECT;
    }
    private final Data data;

    private ObaTripResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getId() {
        return data.entry.getId();
    }

    @Override
    public String getShortName() {
        return data.entry.getShortName();
    }

    @Override
    public int getDirectionId() {
        return data.entry.getDirectionId();
    }

    @Override
    public String getHeadsign() {
        return data.entry.getHeadsign();
    }

    @Override
    public String getServiceId() {
        return data.entry.getServiceId();
    }

    @Override
    public String getShapeId() {
        return data.entry.getShapeId();
    }

    @Override
    public String getTimezone() {
        return data.entry.getTimezone();
    }

    @Override
    public String getRouteId() {
        return data.entry.getRouteId();
    }

    /**
     * @return The route for the trip.
     */
    public ObaRoute getRoute() {
        return data.references.getRoute(data.entry.getRouteId());
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
