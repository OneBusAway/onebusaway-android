package com.joulespersecond.oba;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.util.Log;

public final class ObaResponse {
    private static final String TAG = "ObaResponse";
    
    private final JSONObject mResponse;
    // We need to convert this to a string quickly in order for
    // consumers to bundle this quickly in onSaveInstanceState.
    // Unfortunately JSONObject.toString() is *really* slow.
    private final String mResponseString;
    
    /**
     * Constructor for ObaResponse
     * 
     * @param The JSON object representing the response.
     */
    private ObaResponse(Bundle bundle) {
        mResponse = JSONHelp.toObject(bundle);  
        mResponseString = mResponse.toString();
    }
    private ObaResponse(String json) throws JSONException {
        //long start = System.currentTimeMillis();
        mResponse = new JSONObject(json);
        // Defensive copy
        mResponseString = new String(json);
        //long end = System.currentTimeMillis();
        //Log.d(TAG, "ObaResponse(String): " + (end-start));
    }
    private ObaResponse(StringBuilder builder) throws JSONException {
        //long start = System.currentTimeMillis();
        mResponseString = builder.toString();
        mResponse = new JSONObject(mResponseString);
        //long end = System.currentTimeMillis();
        //Log.d(TAG, "ObaResponse(StringBuilder): " + (end-start));
    }
    private ObaResponse(String error, boolean unused) {
        mResponse = new JSONObject();
        try {
            mResponse.put("text", error);
            mResponse.put("code", 0);
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "Unable to create JSON Object: " + e.getMessage());
        } 
        mResponseString = mResponse.toString();
    }
    static public ObaResponse createFromBundle(Bundle bundle) {
        return new ObaResponse(bundle);
    }
    static public ObaResponse createFromString(String str)  {
        try {
            return new ObaResponse(str);
        } catch (JSONException e) {
            e.printStackTrace();
            return new ObaResponse("Parse error: " + e.getMessage(), true);
        }
    }
    // Not intended for public use.
    static ObaResponse createFromString(StringBuilder str)  {
        try {
            return new ObaResponse(str);
        } catch (JSONException e) {
            e.printStackTrace();
            return new ObaResponse("Parse error: " + e.getMessage(), true);
        }
    }
    static public ObaResponse createFromError(String error) {
        return new ObaResponse(error, true);
    }
    public String getVersion() {
        return mResponse.optString("version", "");
    }
    public int getCode() {
        return mResponse.optInt("code", 0);
    }
    public String getText() {
        return mResponse.optString("text", "FAIL");
    }
    public ObaData getData() {
        return new ObaData(ObaApi.getChildObj(mResponse, "data"));
    }
    @Override
    public String toString() {
        return mResponseString;
    }
    public Bundle toBundle() {
        return JSONHelp.toBundle(mResponse);
    }
}
