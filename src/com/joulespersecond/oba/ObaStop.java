package com.joulespersecond.oba;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;

import com.google.android.maps.GeoPoint;

public final class ObaStop {
    private final JSONObject mData;
    
    /**
     * Constructor.
     * 
     * @param obj The encapsulated object.
     */
    public ObaStop(JSONObject obj) {
        mData = obj;
    }
    /**
     * Constructor.
     * @param bundle The bundle to convert to an ObaStop.
     */
    public ObaStop(Bundle bundle) {
        mData = JSONHelp.toObject(bundle);
    }
    /**
     * Returns the stop ID.
     * 
     * @return The stop ID.
     */
    public String getId() {
        return mData.optString("id");
    }
    /**
     * Returns the passenger-facing stop identifier.
     * 
     * @return The passenger-facing stop ID.
     */
    public String getCode() {
        return mData.optString("code");
    }
    /**
     * Returns the passenger-facing name for the stop.
     * 
     * @return The passenger-facing name for the stop.
     */
    public String getName() {
        return mData.optString("name");
    }
    /**
     * Returns the location of the stop.
     * 
     * @return The location of the stop, or null if it can't be converted to a GeoPoint.
     */
    public GeoPoint getLocation() {
        try {
            final JSONObject data = mData;
            double lat = data.getDouble("lat");
            double lon = data.getDouble("lon");
            return ObaApi.makeGeoPoint(lat, lon);
        } catch (JSONException e) {
            return null;
        }
    }
    /**
     * Returns the latitude of the stop as a double.
     * 
     * @return The latitude of the stop, or 0 if it doesn't exist.
     */
    public double getLatitude() {
        return mData.optDouble("lat");
    }
    /**
     * Returns the longitude of the stop as a double.
     * 
     * @return The longitude of the stop, or 0 if it doesn't exist.
     */
    public double getLongitude() {
        return mData.optDouble("lon");
    }
    
    /**
     * Returns the direction of the stop (ex "NW", "E").
     * 
     * @return The direction of the stop.
     */
    public String getDirection() {
        return mData.optString("direction");
    }
    /**
     * Returns an array of routes serving this stop.
     * 
     * @return The routes serving this stop.
     */
    public ObaArray getRoutes() {
        return new ObaArray(ObaApi.getChildArray(mData, "routes"));
    }
    
    @Override
    public String toString() {
        return mData.toString();
    }
    
    /**
     * Converts this object to a bundle.
     * @return The bundle representing this object.
     */
    public Bundle toBundle() {
        return JSONHelp.toBundle(mData);
    }
}
