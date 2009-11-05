package com.joulespersecond.json;

import org.codehaus.jackson.JsonNode;
import org.json.JSONException;


import android.os.Bundle;
import android.util.Log;

public class JSONArray {
    private static final String TAG = "JSONArray";
    private final JsonNode mNode;
    
    public JSONArray() {
        mNode = JSONObject.getObjectMapper().createArrayNode();
    }
    public JSONArray(Bundle bundle) {
        Log.e(TAG, "Bundle not implemented yet!");
        mNode = JSONObject.getObjectMapper().createArrayNode();       
    }
    
    JSONArray(JsonNode node) {
        mNode = node;
    }
    
    public int length() {
        return mNode.size();
    }
    
    
    //
    // Strings
    //
    public String getString(int index) throws JSONException {
        JsonNode child = mNode.get(index);
        if ((child != null) && child.isTextual()) {
            return child.getTextValue();
        }
        else {
            throw new JSONException("Invalid string index: " + index);
        }        
    }
    
    //
    // Objects
    //
    public JSONObject getJSONObject(int index) throws JSONException {
        JsonNode child = mNode.get(index);
        if ((child != null) && child.isObject()) {
            return new JSONObject(child); 
        }
        else {
            throw new JSONException("Invalid object index: " + index);
        }
    }
    
    @Override
    public String toString() {
        return mNode.toString();
    }
    public Bundle toBundle() {
        return new Bundle();
    }
}
