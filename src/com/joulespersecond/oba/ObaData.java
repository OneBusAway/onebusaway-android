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


public interface ObaData {
    /**
     * Retrieves the list of stops, if they exist.
     *
     * @return The list of stops, or an empty array.
     */
    public ObaArray<ObaStop> getStops();

    /**
     * Retrieves the list of routes, if they exist.
     *
     * @return The list of routes, or an empty array.
     */
    public ObaArray<ObaRoute> getRoutes();

    /**
     * Retrieves the Stop for this response.
     *
     * @return The child stop object, or an empty object.
     */
    public ObaStop getStop();

    /**
     * Retrieves the Route for this response.
     *
     * @return The child route object, or an empty object.
     */
    public ObaRoute getRoute();

    /**
     * Retrieves the list of Nearby Stops for the stop.
     *
     * @return The list of nearby stops, or an empty array.
     */
    public ObaArray<ObaStop> getNearbyStops();

    /**
     * Retrieves this object as a route.
     *
     * @return This object as a route.
     */
    public ObaRoute getAsRoute();

    /**
     * Retrieves this object as a stop.
     *
     * @return This object as a stop.
     */
    public ObaStop getAsStop();

    /**
     * Retrieves the list of arrivals and departures.
     *
     * @return The list of arrivals/departures, or an empty array.
     */
    public ObaArray<ObaArrivalInfo> getArrivalsAndDepartures();

    /**
     * Retrieves the list of stop groupings.
     *
     * @return The list of stop groupings, or an empty array.
     */
    public ObaArray<ObaStopGrouping> getStopGroupings();

    /**
     * Retrieves the list of polylines.
     *
     * @return The list of polylines, or an empty array.
     */
    public ObaArray<ObaPolyline> getPolylines();

    /**
     * For searches, returns whether the search exceeded the maximum
     * number of results.
     */
    public boolean getLimitExceeded();
}
