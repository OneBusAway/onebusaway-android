package com.joulespersecond.oba;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;

public class ObaEntry {
    //
    // The deserialization code performs a heuristic on this object, to
    // push any stop- or route- specific data into a sub route or stop object.
    // This makes this object cleaner (not entirely clean, but cleaner)
    //
    static class Deserialize implements JsonHelp.Deserialize<ObaEntry> {
        public ObaEntry doDeserialize(JsonObject obj,
                                String id,
                                Type type,
                                JsonDeserializationContext context) {
            //
            // Common data
            //

            //
            // V1 data
            //

            //
            // V2 data
            //

            //
            // First, check for the route or stop data. If any of that exists,
            // treat this as a <route> or <stop> tag.
            //

            final String shortName =
                JsonHelp.deserializeChild(obj, "shortName", String.class, context);
            final String longName =
                JsonHelp.deserializeChild(obj, "longName", String.class, context);
            final String description =
                JsonHelp.deserializeChild(obj, "description", String.class, context);
            final String url =
                JsonHelp.deserializeChild(obj, "url", String.class, context);
            final ObaAgency agency =
                JsonHelp.deserializeChild(obj, "agency", ObaAgency.class, context);
            //return new ObaRoute(id, shortName, longName, description, url, agency);
            return new ObaEntry();
        }
    }

    //
    // V1 Data
    //
    private final ObaStop stop;
    private final ObaRoute route;
    private final ObaArray<ObaStop> stops;
    private final ObaArray<ObaRoute> routes;
    private final ObaArray<ObaStop> nearbyStops;

    //
    // V2 data
    //
    private final String stopId;
    private final String[] stopIds;
    private final String[] routeIds;
    private final String[] nearbyStopIds;

    //
    // V1 & V2 data
    //
    private final ObaArray<ObaArrivalInfo> arrivalsAndDepartures;
    private final ObaArray<ObaStopGrouping> stopGroupings;
    private final ObaArray<ObaPolyline> polylines;
    private final boolean limitExceeded;

    /*
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
    private final String agencyId;
    // Stop specific
    private final double lat;
    private final double lon;
    private final String direction;
    private final String name;
    private final String code;
    */

    /**
     * Constructor for ObaData
     */
    ObaEntry() {
        stop = null;
        stopId = null;
        route = null;
        stops = null;
        routes = null;
        stopIds = null;
        routeIds = null;
        nearbyStops = null;
        nearbyStopIds = null;
        arrivalsAndDepartures = null;
        stopGroupings = null;
        polylines = null;
        limitExceeded = false;
        id = "";
        longName = "";
        shortName = "";
        description = "";
        url = "";
        agency = null;
        agencyId = null;
        lat = 0;
        lon = 0;
        direction = "";
        name = "";
        code = "";
    }

    /**
     * Retrieves the list of stops, if they exist.
     *
     * @return The list of stops, or an empty array.
     */
    public ObaArray<ObaStop> getStops() {
        return (stops != null) ? stops : new ObaArray<ObaStop>();
    }
    /**
     * Retrieves the list of routes, if they exist.
     *
     * @return The list of routes, or an empty array.
     */
    public ObaArray<ObaRoute> getRoutes() {
        return (routes != null) ? routes : new ObaArray<ObaRoute>();
    }
    /**
     * Retrieves the Stop for this response.
     *
     * @return The child stop object, or an empty object.
     */
    public ObaStop getStop() {
        return (stop != null) ? stop : new ObaStop();
    }
    /**
     * Retrieves the Route for this response.
     *
     * @return The child route object, or an empty object.
     */
    public ObaRoute getRoute() {
        return (route != null) ? route : new ObaRoute();
    }

    /**
     * Retrieves the list of Nearby Stops for the stop.
     *
     * @return The list of nearby stops, or an empty array.
     */
    public ObaArray<ObaStop> getNearbyStops() {
        return (nearbyStops != null) ? nearbyStops : new ObaArray<ObaStop>();
    }

    /**
     * Retrieves this object as a route.
     *
     * @return This object as a route.
     */
    public ObaRoute getAsRoute() {
        // TODO: Remove this method after the API change has been rolled out.
        if (route != null) {
            return route;
        }
        else {
            return new ObaRoute(id, shortName, longName, description, url, agency);
        }
    }

    /**
     * Retrieves this object as a stop.
     *
     * @return This object as a stop.
     */
    public ObaStop getAsStop() {
        // TODO: Remove this method after the API change has been rolled out.
        if (stop != null) {
            return stop;
        }
        else {
            return new ObaStop(id, lat, lon, direction, name, code, routes, routeIds);
        }
    }

    /**
     * Retrieves the list of arrivals and departures.
     *
     * @return The list of arrivals/departures, or an empty array.
     */
    public ObaArray<ObaArrivalInfo> getArrivalsAndDepartures() {
        return (arrivalsAndDepartures != null) ?
                arrivalsAndDepartures : new ObaArray<ObaArrivalInfo>();
    }

    /**
     * Retrieves the list of stop groupings.
     *
     * @return The list of stop groupings, or an empty array.
     */
    public ObaArray<ObaStopGrouping> getStopGroupings() {
        return (stopGroupings != null) ? stopGroupings : new ObaArray<ObaStopGrouping>();
    }

    /**
     * Retrieves the list of polylines.
     *
     * @return The list of polylines, or an empty array.
     */
    public ObaArray<ObaPolyline> getPolylines() {
        return (polylines != null) ? polylines : new ObaArray<ObaPolyline>();
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
