package com.joulespersecond.oba;

import org.json.JSONException;

import com.joulespersecond.json.JSONObject;

public final class ObaRoute {
    private final JSONObject mData;
    
    /**
     * Constructor.
     * 
     * @param obj The encapsulated object.
     */
    ObaRoute(JSONObject obj) {
        mData = obj;
    }
    /**
     * Returns the route ID.
     * 
     * @return The route ID.
     */
    public String getId() {
        return mData.optString("id");
    }
    /**
     * Returns the short name of the route (ex. "10", "30").
     * 
     * @return The short name of the route.
     */
    public String getShortName() {
        return mData.optString("shortName");
    }
    /**
     * Returns the long name of the route (ex. "Sandpoint/QueenAnne")
     * 
     * @return The long name of the route.
     */
    public String getLongName() {
        return mData.optString("longName");
    }
    
    /**
     * Returns the name of the agency running this route.
     * 
     * @return The name of the agency running this route.
     */
    public String getAgencyName() {
        try {
            JSONObject agency = mData.getJSONObject("agency");
            return agency.getString("name");
        } catch (JSONException e) {
            return "";
        }
    }
    /**
     * Returns the ID of the agency running this route.
     * 
     * @return The ID of the agency running this route.
     */
    public String getAgencyId() {
        try {
            JSONObject agency = mData.getJSONObject("agency");
            return agency.getString("id");
        } catch (JSONException e) {
            return "";
        }        
    }
    @Override
    public String toString() {
        return mData.toString();
    }
}
