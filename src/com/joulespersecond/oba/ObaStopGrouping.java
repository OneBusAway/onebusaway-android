package com.joulespersecond.oba;

import com.joulespersecond.json.JSONObject;

public final class ObaStopGrouping {
    private final JSONObject mData;

    public final String TYPE_DIRECTION = "direction";
    
    /**
     * Constructor.
     * 
     * @param obj The encapsulated object.
     */
    ObaStopGrouping(JSONObject obj) {
        mData = obj;
    }
    
    /**
     * Returns whether or not this grouping is ordered.
     * @return A boolean indicating whether this grouping is ordered.
     */
    public boolean getOrdered() {
        return mData.optBoolean("ordered");
    }
    
    /**
     * Returns the list of stop groups.
     * 
     * @return The list of stop groups.
     */
    public ObaArray getStopGroups() {
        return new ObaArray(ObaApi.getChildArray(mData, "stopGroups"));
    }
}
