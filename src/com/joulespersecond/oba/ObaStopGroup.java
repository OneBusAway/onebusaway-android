package com.joulespersecond.oba;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ObaStopGroup {
    private final JSONObject mData;

    public final String TYPE_DIRECTION = "direction";
    
    /**
     * Constructor.
     * 
     * @param obj The encapsulated object.
     */
    public ObaStopGroup(JSONObject obj) {
        mData = obj;
    }
    
    /**
     * Returns the type of grouping. 
     * 
     * @return One of the TYPE_* string constants.
     */
    public String getType() {
        return mData.optString("type");
    }
    
    /**
     * Returns the name of this grouping, or the empty string.
     * 
     * @return The name of this grouping, or the empty string.
     */
    public String getName() {
        try {
            final JSONObject name = mData.getJSONObject("name");
            final JSONArray names = name.getJSONArray("names");
            if (names.length() > 0) {
                return names.getString(0);
            }
            return "";
        }
        catch (JSONException e) {
            return "";
        }
    }
    
    /**
     * Returns a list of StopIDs for this grouping.
     * 
     * @return The stop IDs for this grouping.
     */
    public List<String> getStopIds() {
        try {
            final JSONArray ids = mData.getJSONArray("stopIds");
            final int len = ids.length();
            List<String> result = new ArrayList<String>(len);
            for (int i=0; i < len; ++i) {
                result.add(ids.getString(i));
            }
            return result;
        }
        catch (JSONException e) {
            return new ArrayList<String>();
        }
    }
}
