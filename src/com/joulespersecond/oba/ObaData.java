package com.joulespersecond.oba;

import org.json.JSONObject;

import android.os.Bundle;

public final class ObaData {
    private final JSONObject mData;
    
    /**
     * Constructor for ObaData
     * 
     * @param The JSON object representing the response.
     */
    ObaData(JSONObject obj) {
        mData = obj;
    }
    /**
     * Constructor for ObaData
     * 
     * @param bundle The bundle to convert to an ObaData.
     */
    ObaData(Bundle bundle) {
        mData = JSONHelp.toObject(bundle);
    }
    /**
     * Retrieves the list of stops, if they exist.
     * 
     * @return The list of stops, or an empty array.
     */
    public ObaArray getStops() {
        return new ObaArray(ObaApi.getChildArray(mData, "stops"));
    }
    /** 
     * Retrieves the list of routes, if they exist.
     * 
     * @return The list of routes, or an empty array.
     */
    public ObaArray getRoutes() {
        return new ObaArray(ObaApi.getChildArray(mData, "routes"));
    }
    /**
     * Retrieves the Stop for this response.
     * 
     * @return The list of stops, or an empty object.
     */
    public ObaStop getStop() {
        return new ObaStop(ObaApi.getChildObj(mData, "stop"));
    }
    /**
     * Retrieves the list of Nearby Stops for the stop.
     * 
     * @return The list of nearby stops, or an empty array.
     */
    public ObaArray getNearbyStops() {
        return new ObaArray(ObaApi.getChildArray(mData, "nearbyStops"));
    }
    
    /**
     * Retrieves this object as a route.
     * 
     * @return This object as a route.
     */
    public ObaRoute getThisRoute() {
        return new ObaRoute(mData);
    }
     
    /**
     * Retrieves the list of arrivals and departures.
     * 
     * @return The list of arrivals/departures, or an empty array.
     */
    public ObaArray
    getArrivalsAndDepartures() {
        return new ObaArray(ObaApi.getChildArray(mData, "arrivalsAndDepartures"));
    }
    
    /**
     * Retrieves the list of stop groupings.
     * 
     * @return The list of stop groupings, or an empty array.
     */
    public ObaArray
    getStopGroupings() {
        return new ObaArray(ObaApi.getChildArray(mData, "stopGroupings"));
    }
    /*
    public final ObaPolylinesArray  getPolylines() {
    }
    */
    
    @Override
    public String toString() {
        return mData.toString();
    }
    
    /**
     * Returns a bundle that represents this object.
     */
    public Bundle toBundle() {
        return JSONHelp.toBundle(mData);
    }
}
