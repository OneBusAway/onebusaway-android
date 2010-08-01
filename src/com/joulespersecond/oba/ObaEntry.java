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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

@Deprecated
public final class ObaEntry {
    public static final ObaEntry EMPTY_OBJECT = new ObaEntry();
    //
    // The deserialization code performs a heuristic on this object, to
    // push any stop- or route- specific data into a sub route or stop object.
    // This makes this object cleaner (not entirely clean, but cleaner)
    //
    static class Deserializer implements JsonDeserializer<ObaEntry> {
        // This holds private versions of the stop and route deserializer in order
        // to deserialize ourselves when we end up looking like a stop or route.
        private static final ObaRoute.Deserialize mRouteDeserializer = new ObaRoute.Deserialize();
        private static final ObaStop.Deserialize mStopDeserializer = new ObaStop.Deserialize();

        @Override
        public ObaEntry deserialize(JsonElement element,
                Type type,
                JsonDeserializationContext context) throws JsonParseException {
            final JsonObject obj = element.getAsJsonObject();

            final String id =
                JsonHelp.deserializeChild(obj, "id", String.class, context);
            ObaStop stop = null;
            ObaRoute route = null;

            if (id != null) {
                stop = mStopDeserializer.doDeserialize(obj, id, ObaStop.class, context);
                route = mRouteDeserializer.doDeserialize(obj, id, ObaRoute.class, context);
            }

            // Common data
            final ObaArray<ObaArrivalInfo> arrivals =
                JsonHelp.deserializeChild(obj,
                        "arrivalsAndDepartures", ObaArrivalInfo.ARRAY_TYPE, context);
            final ObaArray<ObaStopGrouping> stopGroups =
                JsonHelp.deserializeChild(obj,
                        "stopGroupings", ObaStopGrouping.ARRAY_TYPE, context);
            final ObaArray<ObaPolyline> polylines =
                JsonHelp.deserializeChild(obj,
                        "polylines", ObaPolyline.ARRAY_TYPE, context);

            final Boolean _limit =
                JsonHelp.deserializeChild(obj, "limitExceeded", Boolean.class, context);
            final boolean limit = _limit != null ? _limit : false;

            ObaArray<ObaStop> stops = null;
            ObaArray<ObaRoute> routes = null;
            ObaArray<ObaStop> nearbyStops = null;
            // If there are any child <stop>,<stopId>,<route>,<routeId> tags,
            // they take precedence over anything deserialized stops/routes
            // we created from ourselves above.
            ObaStop substop = null;
            ObaRoute subroute = null;

            // Finally, see if we have any references.
            ObaReferences refs = ObaApi.mRefMap.get(context);
            if (refs != null) {
                final ObaRefMap<ObaStop> stopMap = refs.getStopMap();
                final ObaRefMap<ObaRoute> routeMap = refs.getRouteMap();

                substop = JsonHelp.derefObject(obj,
                        context, "stopId", "stop", stopMap, ObaStop.class);
                subroute = JsonHelp.derefObject(obj,
                        context, "routeId", "route", routeMap, ObaRoute.class);
                stops = JsonHelp.derefArray(obj,
                        context, "stopIds", "stops", stopMap, ObaStop.ARRAY_TYPE);
                routes = JsonHelp.derefArray(obj,
                        context, "routeIds", "routes", routeMap, ObaRoute.ARRAY_TYPE);
                nearbyStops =
                    JsonHelp.derefArray(obj, context,
                            "nearbyStopIds", "nearbyStops", stopMap, ObaStop.ARRAY_TYPE);
            }
            else {
                // We can only use the normal, non-referenced values
                substop = JsonHelp.deserializeChild(obj, "stop", ObaStop.class, context);
                subroute = JsonHelp.deserializeChild(obj, "route", ObaRoute.class, context);
                stops = JsonHelp.deserializeChild(obj, "stops", ObaStop.ARRAY_TYPE, context);
                routes = JsonHelp.deserializeChild(obj, "routes", ObaRoute.ARRAY_TYPE, context);
                nearbyStops =
                    JsonHelp.deserializeChild(obj, "nearbyStops", ObaStop.ARRAY_TYPE, context);
            }
            if (substop != null) {
                stop = substop;
            }
            if (subroute != null) {
                route = subroute;
            }

            return new ObaEntry(stop, route, stops, routes,
                    nearbyStops, arrivals, stopGroups, polylines, limit);
        }
    }

    private final ObaStop stop;
    private final ObaRoute route;
    private final ObaArray<ObaStop> stops;
    private final ObaArray<ObaRoute> routes;
    private final ObaArray<ObaStop> nearbyStops;
    private final ObaArray<ObaArrivalInfo> arrivalsAndDepartures;
    private final ObaArray<ObaStopGrouping> stopGroupings;
    private final ObaArray<ObaPolyline> polylines;
    private final boolean limitExceeded;

    /**
     * Constructor for ObaData
     */
    ObaEntry() {
        stop = ObaStop.EMPTY_OBJECT;
        route = ObaRoute.EMPTY_OBJECT;
        stops = ObaStop.EMPTY_ARRAY;
        routes = ObaRoute.EMPTY_ARRAY;
        nearbyStops = ObaStop.EMPTY_ARRAY;
        arrivalsAndDepartures = ObaArrivalInfo.EMPTY_ARRAY;
        stopGroupings = ObaStopGrouping.EMPTY_ARRAY;
        polylines = ObaPolyline.EMPTY_ARRAY;
        limitExceeded = false;
    }
    private ObaEntry(ObaStop _stop,
            ObaRoute _route,
            ObaArray<ObaStop> _stops,
            ObaArray<ObaRoute> _routes,
            ObaArray<ObaStop> _nearby,
            ObaArray<ObaArrivalInfo> _arrivals,
            ObaArray<ObaStopGrouping> _groups,
            ObaArray<ObaPolyline> _polys,
            boolean limit) {
        stop = _stop != null ? _stop : ObaStop.EMPTY_OBJECT;
        route = _route != null ? _route : ObaRoute.EMPTY_OBJECT;
        stops = _stops != null ? _stops : ObaStop.EMPTY_ARRAY;
        routes = _routes != null ? _routes : ObaRoute.EMPTY_ARRAY;
        nearbyStops = _nearby != null ? _nearby : ObaStop.EMPTY_ARRAY;
        arrivalsAndDepartures = _arrivals != null ? _arrivals : ObaArrivalInfo.EMPTY_ARRAY;
        stopGroupings = _groups != null ? _groups : ObaStopGrouping.EMPTY_ARRAY;
        polylines = _polys != null ? _polys : ObaPolyline.EMPTY_ARRAY;
        limitExceeded = limit;
    }

    /**
     * Retrieves the list of stops, if they exist.
     *
     * @return The list of stops, or an empty array.
     */
    public ObaArray<ObaStop> getStops() {
        return stops;
    }
    /**
     * Retrieves the list of routes, if they exist.
     *
     * @return The list of routes, or an empty array.
     */
    public ObaArray<ObaRoute> getRoutes() {
        return routes;
    }
    /**
     * Retrieves the Stop for this response.
     *
     * @return The child stop object, or an empty object.
     */
    public ObaStop getStop() {
        return stop;
    }
    /**
     * Retrieves the Route for this response.
     *
     * @return The child route object, or an empty object.
     */
    public ObaRoute getRoute() {
        return route;
    }

    /**
     * Retrieves the list of Nearby Stops for the stop.
     *
     * @return The list of nearby stops, or an empty array.
     */
    public ObaArray<ObaStop> getNearbyStops() {
        return nearbyStops;
    }

    /**
     * Retrieves this object as a route.
     *
     * @return This object as a route.
     */
    public ObaRoute getAsRoute() {
        return route;
    }

    /**
     * Retrieves this object as a stop.
     *
     * @return This object as a stop.
     */
    public ObaStop getAsStop() {
        return stop;
    }

    /**
     * Retrieves the list of arrivals and departures.
     *
     * @return The list of arrivals/departures, or an empty array.
     */
    public ObaArray<ObaArrivalInfo> getArrivalsAndDepartures() {
        return arrivalsAndDepartures;
    }

    /**
     * Retrieves the list of stop groupings.
     *
     * @return The list of stop groupings, or an empty array.
     */
    public ObaArray<ObaStopGrouping> getStopGroupings() {
        return stopGroupings;
    }

    /**
     * Retrieves the list of polylines.
     *
     * @return The list of polylines, or an empty array.
     */
    public ObaArray<ObaPolyline> getPolylines() {
        return polylines;
    }

    /**
     * For searches, returns whether the search exceeded the maximum
     * number of results.
     */
    public boolean getLimitExceeded() {
        return limitExceeded;
    }

    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
