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
package com.joulespersecond.oba;

public final class ObaReferences {
    private final ObaRefMap<ObaAgency> agencies;
    private final ObaRefMap<ObaRoute> routes;
    private final ObaRefMap<ObaStop> stops;

    ObaReferences() {
        agencies = null;
        routes = null;
        stops = null;
    }

    /**
     * Returns an agency by the specified id, or an empty agency object.
     */
    public ObaAgency getAgency(String id) {
        ObaAgency result = null;
        if (agencies != null) {
            result = agencies.get(id);
        }
        return (result != null) ? result : new ObaAgency();
    }
    /**
     * Returns a route by the specified id, or an empty route object.
     */
    public ObaRoute getRoute(String id) {
        ObaRoute result = null;
        if (routes != null) {
            result = routes.get(id);
        }
        return (result != null) ? result : new ObaRoute();
    }
    /**
     * Returns a stop by the specified id, or an empty stop object.
     */
    public ObaStop getStop(String id) {
        ObaStop result = null;
        if (stops != null) {
            result = stops.get(id);
        }
        return (result != null) ? result : new ObaStop();
    }
}
