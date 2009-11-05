package com.joulespersecond.oba;

import org.json.JSONObject;

import android.os.Bundle;

public final class ObaArrivalInfo {
    private final JSONObject mData;
    
    /**
     * Constructor.
     * 
     * @param obj The encapsulated object.
     */
    ObaArrivalInfo(JSONObject obj) {
        mData = obj;
    }
    /**
     * Constructor.
     * 
     * @param bundle The bundle to convert to an ObaArrivalInfo.
     */
    ObaArrivalInfo(Bundle bundle) {
        mData = JSONHelp.toObject(bundle);
    }
    /**
     * Returns the ID of the route.
     * @return The ID of the route.
     */
    public String getRouteId() {
        return mData.optString("routeId");
    }
    /**
     * Returns the short name of the route.
     * @return The short name of the route.
     */
    public String getShortName() {
        return mData.optString("routeShortName");
    }
    /**
     * Returns the trip ID of the route.
     * @return The trip ID of the route.
     */
    public String getTripId() {
        return mData.optString("tripId");
    }
    /**
     * Returns the trip headsign.
     * @return The trip headsign.
     */
    public String getHeadsign() {
        return mData.optString("tripHeadsign");
    }
    /**
     * Returns the stop ID.
     * @return The stop ID.
     */
    public String getStopId() {
        return mData.optString("stopId");
    }
    /**
     * Returns the scheduled arrival time in milliseconds past the epoch.
     * @return The scheduled arrival time.
     */
    public long getScheduledArrivalTime() {
        return mData.optLong("scheduledArrivalTime");
    }
    /**
     * Returns the predicted arrival time in milliseconds past the epoch, 
     * or 0 if no prediction data is available.
     * @return The predicted arrival time, or 0.
     */
    public long getPredictedArrivalTime() {
        return mData.optLong("predictedArrivalTime");
    }
    /** 
     * Returns the scheduled departure time in milliseconds past the epoch..
     * @return The scheduled departure time.
     */
    public long getScheduledDepartureTime() {
        return mData.optLong("scheduledDepartureTime");
    }
    /**
     * Returns the predicted departure time in milliseconds past the epoch, 
     * or 0 if no prediction data is available.
     * @return The predicted arrival time, or 0.
     */
    public long getPredictedDepartureTime() {
        return mData.optLong("predictedDepartureTime");
    }
    
    /**
     * Returns the status of the route.
     * @return The status of the route.
     */
    public String getStatus() {
        return mData.optString("status");
    }
    
    @Override 
    public String toString() {
        return mData.toString();
    }
    
    /**
     * Returns this object as a bundle.
     * @return The bundle that represents this object.
     */
    public Bundle toBundle() {
        return JSONHelp.toBundle(mData);
    }
}
