package com.joulespersecond.json;

import java.io.Reader;

import org.json.JSONException;

import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * This class maps a Gson JsonObject to look a bit what we use from org.json.JSONObject.
 * @author paulw
 *
 */
public class JSONObject {
    private static class GsonHolder {
        static final Gson gsonObj = new Gson();
        static final JsonParser parser = new JsonParser();
    }

    static Gson getGson() {
        return GsonHolder.gsonObj;
    }
    static JsonParser getParser() {
        return GsonHolder.parser;
    }
    
    private static final String TAG = "JSONObject";
    private final JsonObject mObj;
       
    public JSONObject() {
        mObj = new JsonObject();
    }
    public JSONObject(String str) throws JSONException {
        try {
            JsonElement elem = getParser().parse(str);
            if (elem.isJsonObject()) {
                mObj = (JsonObject)elem;
            }
            else {
                throw new JSONException("Not an object: " + str);
            }
        }
        catch (JsonParseException e) {
            throw new JSONException("Parser error: " + e);
        }
    }
    public JSONObject(Reader reader) throws JSONException {
        try {
            JsonElement elem = getParser().parse(reader);
            if (elem.isJsonObject()) {
                mObj = (JsonObject)elem;
            }
            else {
                throw new JSONException("Unable to parse");
            }
        }
        catch (JsonParseException e) {
            throw new JSONException("Unable to parse");
        }        
    }
    public JSONObject(Bundle bundle) {
        Log.e(TAG, "Bundle not implemented!");
        assert(false);
        mObj = null;
    }
    JSONObject(JsonObject obj) {
        mObj = obj;
    }
    
    // One thing we do differently than org.json is that we don't
    // coerce values.
    
    
    //
    // Boolean
    //
    public boolean optBoolean(String key) {
        return optBoolean(key, false);
    }
    public boolean optBoolean(String key, boolean defaultValue) {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isBoolean()) {
            return child.getAsBoolean();
        }
        return defaultValue;        
    }

    //
    // Strings
    //
    public String getString(String key) throws JSONException {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isString()) {
            return child.getAsString(); 
        }
        else {
            throw new JSONException("No such string child: " + key);
        }        
    }
    public String optString(String key) {
        return optString(key, "");
    }
    public String optString(String key, String defaultValue) {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isString()) {
            return child.getAsString();
        }
        return defaultValue;
    }
    //
    // Int
    //
    public int optInt(String key, int defaultValue) {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isNumber()) {
            // This can still throw a ClassCastException
            try {
                return child.getAsInt();
            }
            catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    //
    // Long
    //
    public long optLong(String key) {
        return optLong(key, 0);
    }
    public long optLong(String key, int defaultValue) {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isNumber()) {
            try {
                return child.getAsLong();
            }
            catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;        
    }
    
    //
    // Double
    //
    public double getDouble(String key) throws JSONException {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isNumber()) {
            try {
                return child.getAsDouble();
            }
            catch (ClassCastException e) {
                throw new JSONException("No such double child: " + key);
            }
        }
        else {
            throw new JSONException("No such double child: " + key);
        }           
    }
    public double optDouble(String key) {
        return optDouble(key, 0);
    }
    public double optDouble(String key, double defaultValue) {
        JsonPrimitive child = mObj.getAsJsonPrimitive(key);
        if ((child != null) && child.isNumber()) {
            try {
                return child.getAsDouble();
            }
            catch (ClassCastException e) {
                return defaultValue;
            }
        }
        return defaultValue;         
    }
    
    //
    // Object
    //
    public JSONObject getJSONObject(String key) throws JSONException {
        JsonObject child = mObj.getAsJsonObject(key);
        if (child != null) {
            return new JSONObject(child); 
        }
        else {
            throw new JSONException("No such object child: " + key);
        }
    }
    
    //
    // Array
    //
    public JSONArray getJSONArray(String key) throws JSONException {
        JsonArray child = mObj.getAsJsonArray(key);
        if (child != null) {
            return new JSONArray(child);
        }
        else {
            throw new JSONException("No such array child: " + key);
        }
    }

    
    @Override
    public String toString() {
        return mObj.toString();
    }
    public Bundle toBundle() {
        assert(false);
        return new Bundle();
    }
}
