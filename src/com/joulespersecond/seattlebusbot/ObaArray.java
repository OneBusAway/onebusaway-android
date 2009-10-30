package com.joulespersecond.seattlebusbot;

import org.json.JSONArray;

public final class ObaArray {
    private final JSONArray mArray;
    
    /**
     * Constructor.
     * 
     * @param The encapsulated array.
     */
    public ObaArray(JSONArray obj) {
        mArray = obj;
    }
    /**
     * Returns the length of the array.
     * 
     * @return The length of the array.
     */
    public int
    length() {
        return mArray.length();
    }
    /**
     * Returns a stop object for the specified index.
     * 
     * @param index The child index.
     * @return The stop object, or an empty object if it isn't a stop.
     */
    public ObaStop
    getStop(int index) {
        return new ObaStop(ObaApi.getChildObj(mArray, index));
    }
    /**
     * Returns a route object for the specified index.
     * 
     * @param index The child index.
     * @return The route object, or an empty object if it isn't a route.
     */
    public ObaRoute
    getRoute(int index) {
        return new ObaRoute(ObaApi.getChildObj(mArray, index));
    }
    
    /**
     * Returns an arrival object for the specified index.
     * 
     * @param index The child index.
     * @return The arrival object, or an empty object if it isn't an arrival info.
     */
    public ObaArrivalInfo 
    getArrivalInfo(int index) {
        return new ObaArrivalInfo(ObaApi.getChildObj(mArray, index));
    }
}
