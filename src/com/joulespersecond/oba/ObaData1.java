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


@Deprecated
public final class ObaData1 implements ObaData {
    public static final ObaData1 EMPTY_OBJECT = new ObaData1();

    //
    // This is definitely the most complicated object, because unfortunately
    // there's no way here to tell what type of data we are.
    // So we have to pretty much store everything.
    //
    private final ObaStop stop;
    private final ObaRoute route;
    private final ObaArray<ObaStop> stops;
    private final ObaArray<ObaRoute> routes;
    private final ObaArray<ObaStop> nearbyStops;
    private final ObaArray<ObaArrivalInfo> arrivalsAndDepartures;
    private final ObaArray<ObaStopGrouping> stopGroupings;
    //private final ObaArray<ObaPolyline> polylines;
    private final boolean limitExceeded;

    // These are because for the Stop by ID and Route by ID requests,
    // the information for <route> and <stop> are merged into the
    // <data> element.
    // TODO: Once the API change has been rolled out, remove all of this.
    private final String id;
    // Route specific
    private final String longName;
    private final String shortName;
    private final String description;
    private final String url;
    private final ObaAgency agency;
    // Stop specific
    private final double lat;
    private final double lon;
    private final String direction;
    private final String name;
    private final String code;

    /**
     * Constructor for ObaData
     */
    ObaData1() {
        stop = null;
        route = null;
        stops = null;
        routes = null;
        nearbyStops = null;
        arrivalsAndDepartures = null;
        stopGroupings = null;
        //polylines = null;
        limitExceeded = false;
        id = "";
        longName = "";
        shortName = "";
        description = "";
        url = "";
        agency = null;
        lat = 0;
        lon = 0;
        direction = "";
        name = "";
        code = "";
    }

    @Override
    public ObaArray<ObaStop> getStops() {
        return (stops != null) ? stops : new ObaArray<ObaStop>();
    }

    @Override
    public ObaArray<ObaRoute> getRoutes() {
        return (routes != null) ? routes : new ObaArray<ObaRoute>();
    }

    @Override
    public ObaStop getStop() {
        return (stop != null) ? stop : new ObaStop();
    }

    @Override
    public ObaRoute getRoute() {
        return (route != null) ? route : new ObaRoute();
    }

    @Override
    public ObaArray<ObaStop> getNearbyStops() {
        return (nearbyStops != null) ? nearbyStops : new ObaArray<ObaStop>();
    }

    @Override
    public ObaRoute getAsRoute() {
        // TODO: Remove this method after the API change has been rolled out.
        if (route != null) {
            return route;
        }
        else {
            return new ObaRoute(id, shortName, longName, description, url, agency);
        }
    }

    @Override
    public ObaStop getAsStop() {
        // TODO: Remove this method after the API change has been rolled out.
        if (stop != null) {
            return stop;
        }
        else {
            return new ObaStop(id, lat, lon, direction, name, code, routes);
        }
    }

    @Override
    public ObaArray<ObaArrivalInfo> getArrivalsAndDepartures() {
        return (arrivalsAndDepartures != null) ?
                arrivalsAndDepartures : new ObaArray<ObaArrivalInfo>();
    }

    @Override
    public ObaArray<ObaStopGrouping> getStopGroupings() {
        return (stopGroupings != null) ? stopGroupings : new ObaArray<ObaStopGrouping>();
    }

    @Override
    public ObaArray<ObaPolyline> getPolylines() {
        return ObaPolyline.EMPTY_ARRAY;
        //return (polylines != null) ? polylines : new ObaArray<ObaPolyline>();
    }

    @Override
    public boolean getLimitExceeded() {
        return limitExceeded;
    }

    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
