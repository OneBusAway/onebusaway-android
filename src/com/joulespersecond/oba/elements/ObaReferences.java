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
package com.joulespersecond.oba.elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Element representing the <references> object in responses.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public class ObaReferences {
    public static final ObaReferences EMPTY_OBJECT = new ObaReferences();

    private final ObaStopElement[] stops;
    private final ObaRouteElement[] routes;
    //private final ObaTripElement[] trips;
    private final ObaAgencyElement[] agencies;

    public ObaReferences() {
        stops = ObaStopElement.EMPTY_ARRAY;
        routes = ObaRouteElement.EMPTY_ARRAY;
        agencies = ObaAgencyElement.EMPTY_ARRAY;
    }

    /**
     * Dereferences a stop by its ID.
     * @param id The stop ID.
     * @return The ObaStop if it exists, or null if it doesn't.
     */
    public ObaStopElement getStop(String id) {
        return findById(stops, id);
    }

    /**
     * Dereferences a list of stop IDs.
     * @param ids A list of stops to convert.
     * @return The list of converted stop.
     */
    public List<ObaStopElement> getStops(String[] ids) {
        return findList(stops, ids);
    }

    /**
     * Dereferences a route by its ID.
     * @param id The route ID.
     * @return The ObaRoute if it exists, or null if it doesn't.
     */
    public ObaRouteElement getRoute(String id) {
        return findById(routes, id);
    }

    /**
     * Dereferences a list of route IDs.
     * @param ids A list of routes to convert.
     * @return The list of converted routes.
     */
    public List<ObaRouteElement> getRoutes(String[] ids) {
        return findList(routes, ids);
    }

    /*
    public ObaTripElement getTrip(String id) {

    }
    */

    /**
     * Dereferences an agency by its ID.
     * @param id The agency ID.
     * @return The ObaAgency if it exists, or null if it doesn't.
     */
    public ObaAgencyElement getAgency(String id) {
        return findById(agencies, id);
    }

    /**
     * Dereferences a list of agency IDs.
     * @param ids A list of agency IDs to convert.
     * @return The list of converted agencies.
     */
    public List<ObaAgencyElement> getAgencies(String[] ids) {
        return findList(agencies, ids);
    }

    //
    // TODO: This will be much easier when we convert to HashMap storage.
    //
    private static <T extends ObaElement> T findById(T[] objects, String id) {
        final int len = objects.length;
        for (int i=0; i < len; ++i) {
            final T obj = objects[i];
            if (obj.getId().equals(id)) {
                return obj;
            }
        }
        return null;
    }
    private static <T extends ObaElement> List<T> findList(T[] objects, String[] ids) {
        ArrayList<T> result = new ArrayList<T>();
        final int len = ids.length;
        for (int i=0; i < len; ++i) {
            final String id = ids[i];
            final T obj = findById(objects, id);
            if (obj != null) {
                result.add(obj);
            }
        }
        return result;
    }
}
