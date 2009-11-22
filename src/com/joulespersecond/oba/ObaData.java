package com.joulespersecond.oba;


public final class ObaData {
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
    private final ObaArray<ObaPolyline> polylines;
    private final boolean limitExceeded;
    
    // These are because for the Stop by ID and Route by ID requests, 
    // the information for <route> and <stop> are merged into the 
    // <data> element.
    // TODO: Once the API change has been rolled out, remove all of this.
    private final String id;
    // Route specific
    private final String longName;
    private final String shortName;
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
    ObaData() {
        stop = null;
        route = null;
        stops = null;
        routes = null;
        nearbyStops = null;
        arrivalsAndDepartures = null;
        stopGroupings = null;
        polylines = null;
        limitExceeded = false;  
        id = "";
        longName = "";
        shortName = "";
        agency = null;
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
        return (route != null) ? route : new ObaRoute(id, shortName, longName, agency);
    }
    
    /**
     * Retrieves this object as a stop.
     * 
     * @return This object as a stop.
     */
    public ObaStop getAsStop() {
        // TODO: Remove this method after the API change has been rolled out.
        return (stop != null) ? stop : new ObaStop(id, lat, lon, direction, name, code, routes);
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
