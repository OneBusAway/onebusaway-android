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
package org.onebusaway.android.io.elements;

import java.util.List;

/**
 * Interface representing the <references> object in responses.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public interface ObaReferences {

    /**
     * Dereferences a stop by its ID.
     *
     * @param id The stop ID.
     * @return The ObaStop if it exists, or null if it doesn't.
     */
    public ObaStop getStop(String id);

    /**
     * Dereferences a list of stop IDs.
     *
     * @param ids A list of stops to convert.
     * @return The list of converted stop.
     */
    public List<ObaStop> getStops(String[] ids);

    /**
     * Dereferences a route by its ID.
     *
     * @param id The route ID.
     * @return The ObaRoute if it exists, or null if it doesn't.
     */
    public ObaRoute getRoute(String id);

    /**
     * Dereferences a list of route IDs.
     *
     * @param ids A list of routes to convert.
     * @return The list of converted routes.
     */
    public List<ObaRoute> getRoutes(String[] ids);

    /**
     * Returns all routes
     *
     * @return all routes
     */
    public List<ObaRoute> getRoutes();

    /**
     * References a trip by its ID.
     *
     * @param id The trip ID.
     * @return The ObaTrip if it exists, or null if it doesn't.
     */
    public ObaTrip getTrip(String id);

    /**
     * Dereferences a list of trip IDs.
     *
     * @param ids A list of trips to convert.
     * @return The list of converted trips.
     */
    public List<ObaTrip> getTrips(String[] ids);

    /**
     * Dereferences an agency by its ID.
     *
     * @param id The agency ID.
     * @return The ObaAgency if it exists, or null if it doesn't.
     */
    public ObaAgency getAgency(String id);

    /**
     * Dereferences a list of agency IDs.
     *
     * @param ids A list of agency IDs to convert.
     * @return The list of converted agencies.
     */
    public List<ObaAgency> getAgencies(String[] ids);

    /**
     * Dereferences a situation by ID.
     *
     * @param id The situation ID.
     * @return The ObaSituation if it exists, or null if it doesn't.
     */
    public ObaSituation getSituation(String id);

    /**
     * Dereferences a list of situation IDs.
     *
     * @param ids A list of situation IDs to convert.
     * @return The list of converted situations.
     */
    public List<ObaSituation> getSituations(String[] ids);
}
