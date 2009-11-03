package com.joulespersecond.oba;

import java.util.Iterator;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.util.Log;

/**
 * This class provides some helpful methods to convert JSONObjects to Android bundles and back.
 * @author paulw
 */
final class JSONHelp {
    private static final String TAG = "JSONHelp";
    private static final String JSON_ARRAY_KEY = ".JSONArray";
    // Uninstantiatable
    private JSONHelp() { throw new AssertionError(); }
    
    /**
     * Converts a JSONObject to an Android bundle.
     * @param obj The JSONObject to convert.
     * @return An Android bundle that represents the JSONObject.
     */
    static final Bundle toBundle(JSONObject obj) {
        Bundle result = new Bundle();
        @SuppressWarnings("unchecked") Iterator<String> i = obj.keys();
        while (i.hasNext()) {
            final String key = i.next();
            try {
                convertItem(result, key, obj.get(key));
            } catch (JSONException e) {
                Log.e(TAG, "Unable to get key: " + key);
            }
        }
        return result;
    }
  
    /**
     * Converts a JSONArray to an Android bundle.
     * @param array The JSONArray to convert.
     * @return An Android bundle that represents the JSONArray.
     */
    static final Bundle toBundle(JSONArray array) {
        Bundle result = new Bundle();
        result.putBoolean(JSON_ARRAY_KEY, true);
        final int len = array.length();
        for (int i=0; i < len; ++i) {
            try {
                convertItem(result, String.valueOf(i), array.get(i));
            } catch (JSONException e) {
                Log.e(TAG, "Unable to get key: " + i);
            }
        }
        return result;
    }
    /**
     * Converts an Android bundle to a JSONObject.
     * The bundle must have been produced by objectToBundle.
     * 
     * @param bundle The bundle (produced by objectToBundle) to convert.
     * @return The converted JSONObject.
     */
    static final JSONObject toObject(Bundle bundle) {
        JSONObject result = new JSONObject();
        final Set<String> keys = bundle.keySet();
        Iterator<String> i = keys.iterator();
        while (i.hasNext()) {
            final String key = i.next();
            try {
                convertItem(result, key, bundle.get(key));
            } catch (JSONException e) {
                Log.e(TAG, "Unable to set key: " + key);
            }
        }
        return result;
    }
    /**
     * Converts an Android bundle to a JSONArray.
     * The bundle must have been produced by arrayToBundle.
     * 
     * @param bundle The bundle (produced by arrayToBundle) to convert.
     * @return The converted JSONArray.
     */
    static final JSONArray toArray(Bundle bundle) {
        JSONArray result = new JSONArray();
        if (!bundle.containsKey(JSON_ARRAY_KEY)) {
            Log.e(TAG, "Bundle doesn't look like a JSONArray bundle");
            return result;
        }
        final Set<String> keys = bundle.keySet();
        Iterator<String> i = keys.iterator();
        while (i.hasNext()) {
            final String key = i.next();
            try {
                if (JSON_ARRAY_KEY.equals(key)) {
                    continue;
                }
                int n = Integer.valueOf(key);
                convertItem(result, n, bundle.get(key));
            } catch (JSONException e) {
                Log.e(TAG, "Unable to set key: " + key);
            } catch (RuntimeException e) {
                Log.e(TAG, "Unxpected non-integer key: " + key);
            }
        }
        return result;
    }
    
    private static final void convertItem(Bundle bundle, String key, Object obj) {
        // Possible types are:
        //     JSONObject
        //     JSONArray
        //     Boolean
        //     Number
        //     String
        //     JSONObject.NULL
        // Convert all of these to the appropriate bundle type.
        if (obj instanceof JSONObject) {
            bundle.putBundle(key, toBundle((JSONObject)obj));
        }
        else if (obj instanceof JSONArray) {
            bundle.putBundle(key, toBundle((JSONArray)obj));
        }
        else if (obj instanceof Boolean) {
            bundle.putBoolean(key, (Boolean)obj);
        }
        else if (obj instanceof Number) {
            bundle.putDouble(key, ((Number)obj).doubleValue());
        }
        else if (obj instanceof String) {
            bundle.putString(key, ((String)obj));
        }
        else if (obj == null || JSONObject.NULL.equals(obj)) {
            // Nothing
        }
        else {
            Log.e(TAG, String.format("Ignoring unknown type: key=%s class=%s",
                    key, obj.getClass().getName()));
        }
    }
    
    private static final void convertItem(JSONObject json, String key, Object obj) 
            throws JSONException {
        if (obj instanceof Bundle) {
            // Is this an object or an array?
            Bundle bundle = (Bundle)obj;
            if (bundle.containsKey(JSON_ARRAY_KEY)) {
                json.put(key, toArray(bundle));
            }
            else {
                json.put(key, toObject(bundle));
            }
        }
        else if (obj instanceof Boolean) {
            json.put(key, (Boolean)obj);
        }
        else if (obj instanceof Double) {
            json.put(key, (Double)obj);
        }
        else if (obj instanceof String) {
            json.put(key, ((String)obj));
        }
        else if (obj == null || JSONObject.NULL.equals(obj)) {
            // Nothing
        }
        else {
            Log.e(TAG, String.format("Ignoring unknown type: key=%s class=%s",
                    key, obj.getClass().getName()));
        }       
    }

    private static final void convertItem(JSONArray json, int key, Object obj) 
        throws JSONException {
        if (obj instanceof Bundle) {
            // Is this an object or an array?
            Bundle bundle = (Bundle)obj;
            if (bundle.containsKey(JSON_ARRAY_KEY)) {
                json.put(key, toArray(bundle));
            }
            else {
                json.put(key, toObject(bundle));
            }
        }
        else if (obj instanceof Boolean) {
            json.put(key, (Boolean)obj);
        }
        else if (obj instanceof Double) {
            json.put(key, (Double)obj);
        }
        else if (obj instanceof String) {
            json.put(key, ((String)obj));
        }
        else if (obj == null || JSONObject.NULL.equals(obj)) {
            // Nothing
        }
        else {
            Log.e(TAG, String.format("Ignoring unknown type: key=%s class=%s",
                    key, obj.getClass().getName()));
        }       
    }
}
