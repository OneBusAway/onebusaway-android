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

import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTrip;

import java.util.List;

public abstract class ObaResponseWithRefs extends ObaResponse implements ObaReferences {

    @Override
    public ObaStop getStop(String id) {
        return getRefs().getStop(id);
    }

    @Override
    public List<ObaStop> getStops(String[] ids) {
        return getRefs().getStops(ids);
    }

    @Override
    public ObaRoute getRoute(String id) {
        return getRefs().getRoute(id);
    }

    @Override
    public List<ObaRoute> getRoutes(String[] ids) {
        return getRefs().getRoutes(ids);
    }

    @Override
    public List<ObaRoute> getRoutes() {
        return getRefs().getRoutes();
    }

    @Override
    public ObaTrip getTrip(String id) {
        return getRefs().getTrip(id);
    }

    @Override
    public List<ObaTrip> getTrips(String[] ids) {
        return getRefs().getTrips(ids);
    }

    @Override
    public ObaAgency getAgency(String id) {
        return getRefs().getAgency(id);
    }

    @Override
    public List<ObaAgency> getAgencies(String[] ids) {
        return getRefs().getAgencies(ids);
    }

    @Override
    public ObaSituation getSituation(String id) {
        return getRefs().getSituation(id);
    }

    @Override
    public List<ObaSituation> getSituations(String[] ids) {
        return getRefs().getSituations(ids);
    }

    abstract protected ObaReferences getRefs();
}
