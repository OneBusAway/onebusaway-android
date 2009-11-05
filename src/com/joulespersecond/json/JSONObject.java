package com.joulespersecond.json;

import java.io.IOException;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;

import android.os.Bundle;
import android.util.Log;

/**
 * This class maps a Jackson JsonNode to look a bit what we use from org.json.JSONObject.
 * @author paulw
 *
 */
public class JSONObject {
    private static final ObjectMapper mObjectMapper = new ObjectMapper();

    static JsonFactory getJsonFactory() {
        return mObjectMapper.getJsonFactory();
    }
    static ObjectMapper getObjectMapper() {
        return mObjectMapper;
    }
    
    private static final String TAG = "JSONObject";
    private final JsonNode mNode;
       
    public JSONObject() {
        mNode = mObjectMapper.createObjectNode();
    }
    public JSONObject(String str) throws JSONException {
        long start = System.currentTimeMillis();
        mNode = stringToNode(str);
        long end = System.currentTimeMillis();
        Log.d(TAG, "JSONObject(str): " + (end-start));
    }
    public JSONObject(Bundle bundle) {
        Log.e(TAG, "Bundle not implemented!");
        mNode = mObjectMapper.createObjectNode();
    }
    JSONObject(JsonNode node) {
        mNode = node;
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
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isBoolean()) {
            return child.getBooleanValue();
        }
        return defaultValue;        
    }

    //
    // Strings
    //
    public String getString(String key) throws JSONException {
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isTextual()) {
            return child.getTextValue(); 
        }
        else {
            throw new JSONException("No such string child: " + key);
        }        
    }
    public String optString(String key) {
        return optString(key, "");
    }
    public String optString(String key, String defaultValue) {
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isTextual()) {
            return child.getTextValue();
        }
        return defaultValue;
    }
    //
    // Int
    //
    public int optInt(String key, int defaultValue) {
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isInt()) {
            return child.getIntValue();
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
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isLong()) {
            return child.getLongValue();
        }
        return defaultValue;        
    }
    
    //
    // Double
    //
    public double getDouble(String key) throws JSONException {
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isNumber()) {
            return child.getNumberValue().doubleValue(); 
        }
        else {
            throw new JSONException("No such double child: " + key);
        }           
    }
    public double optDouble(String key) {
        return optDouble(key, 0);
    }
    public double optDouble(String key, double defaultValue) {
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isNumber()) {
            return child.getNumberValue().doubleValue();
        }
        return defaultValue;         
    }
    
    //
    // Object
    //
    public JSONObject getJSONObject(String key) throws JSONException {
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isObject()) {
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
        JsonNode child = mNode.get(key);
        if ((child != null) && child.isArray()) {
            return new JSONArray(child);
        }
        else {
            throw new JSONException("No such array child: " + key);
        }
    }
    
    
    static JsonNode stringToNode(String str) throws JSONException {
        try {
            return mObjectMapper.readValue(str, JsonNode.class);
        } catch (JsonParseException e) {
            throw new JSONException(e.toString());
        } catch (IOException e) {
            throw new JSONException(e.toString());
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
