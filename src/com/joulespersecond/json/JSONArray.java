package com.joulespersecond.json;

import org.json.JSONException;

import android.os.Bundle;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JSONArray {
    private static final String TAG = "JSONArray";
    private final JsonArray mArray;
    
    public JSONArray() {
        mArray = new JsonArray();
    }
    public JSONArray(Bundle bundle) {
        Log.e(TAG, "Bundle not implemented yet!");
        mArray = null;
        assert(false);    
    }
    
    JSONArray(JsonArray array) {
        mArray = array;
    }
    
    public int length() {
        return mArray.size();
    }
    
    
    //
    // Strings
    //
    public String getString(int index) throws JSONException {
        JsonElement child = mArray.get(index);
        if ((child != null) && child.isJsonPrimitive()) {
            try {
                return child.getAsString();
            }
            catch (ClassCastException e) {
                throw new JSONException("Invalid string index: " + index);                
            }
        }
        else {
            throw new JSONException("Invalid string index: " + index);
        }        
    }
    
    //
    // Objects
    //
    public JSONObject getJSONObject(int index) throws JSONException {
        JsonElement child = mArray.get(index);
        if ((child != null) && child.isJsonObject()) {
            return new JSONObject((JsonObject)child); 
        }
        else {
            throw new JSONException("Invalid object index: " + index);
        }
    }
    
    @Override
    public String toString() {
        return mArray.toString();
    }
    public Bundle toBundle() {
        assert(false);
        return new Bundle();
    }
}
