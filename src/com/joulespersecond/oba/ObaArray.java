package com.joulespersecond.oba;

import java.util.HashMap;
import java.util.Map;

import com.joulespersecond.json.JSONArray;

public final class ObaArray {
    private final JSONArray mArray;
    
    /**
     * Constructor.
     * 
     * @param The encapsulated array.
     */
    ObaArray(JSONArray obj) {
        mArray = obj;
    }
    /**
     * Returns the length of the array.
     * 
     * @return The length of the array.
     */
    public int length() {
        return mArray.length();
    }
    /**
     * Returns a stop object for the specified index.
     * 
     * @param index The child index.
     * @return The stop object, or an empty object if it isn't a stop.
     */
    public ObaStop getStop(int index) {
        return new ObaStop(ObaApi.getChildObj(mArray, index));
    }
    /**
     * Returns a route object for the specified index.
     * 
     * @param index The child index.
     * @return The route object, or an empty object if it isn't a route.
     */
    public ObaRoute getRoute(int index) {
        return new ObaRoute(ObaApi.getChildObj(mArray, index));
    }
    
    /**
     * Returns an arrival object for the specified index.
     * 
     * @param index The child index.
     * @return The arrival object, or an empty object if it isn't an arrival info.
     */
    public ObaArrivalInfo getArrivalInfo(int index) {
        return new ObaArrivalInfo(ObaApi.getChildObj(mArray, index));
    }
    
    /**
     * Returns a stop grouping object for the specified index.
     * 
     * @param index The child index.
     * @return The stop grouping object, or an empty object if it isn't an stop grouping.
     */
    public ObaStopGrouping getStopGrouping(int index) {
        return new ObaStopGrouping(ObaApi.getChildObj(mArray, index));
    }
    
    /** 
     * Returns a stop group object for the specified index.
     * 
     * @param index The child index.
     * @return The stop group object, or an empty object if it isn't a stop group.
     */
    public ObaStopGroup getStopGroup(int index) {
        return new ObaStopGroup(ObaApi.getChildObj(mArray, index));
    }
    
    /**
     * Returns a polyline object for the specified index.
     * 
     * @param index The child index.
     * @return The polyline group object, or an empty object if it isn't a polyline.
     */
    public ObaPolyline getPolyline(int index) {
        return new ObaPolyline(ObaApi.getChildObj(mArray, index));
    }
    
    /**
     * Returns a map from stop ID to ObaStop objects for all the stops in this array.
     * 
     * @return A map where the key is a stop ID, and the value is an ObaStop object.
     */
    public Map<String,ObaStop> getStopMap() {
        final int len = mArray.length();
        HashMap<String,ObaStop> result = new HashMap<String,ObaStop>(len);
        for (int i=0; i < len; ++i) {
            ObaStop stop = getStop(i);
            result.put(stop.getId(), stop);
        }
        return result;
    }
    
    @Override
    public String toString() {
        return mArray.toString();
    }
}
