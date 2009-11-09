package com.joulespersecond.oba;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONException;

import android.os.Bundle;
import android.util.Log;

import com.joulespersecond.json.JSONObject;

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
    ObaResponse(JSONObject obj) {
        mResponse = obj;
        long start = System.currentTimeMillis();
        mResponseString = obj.toString();
        long end = System.currentTimeMillis();
        Log.d(TAG, "toString: " + (end-start));
    }
    private ObaResponse(Bundle bundle) {
        mResponse = new JSONObject(bundle);  
        mResponseString = mResponse.toString();
    }
    private ObaResponse(String json) throws JSONException {
        mResponse = new JSONObject(json);
        mResponseString = json;
    }
    private ObaResponse(String error, boolean unused) {
        JSONObject obj;
        try {
            obj = new JSONObject(
                    String.format("{text: \"%s\", code:0}",
                            org.json.JSONObject.quote(error)));
        } catch (JSONException e) {
            obj = new JSONObject();
        }
        mResponse = obj;
        mResponseString = mResponse.toString();
    }
    private ObaResponse(URL url) throws JSONException, IOException {
        URLConnection conn = url.openConnection();
        long start = System.currentTimeMillis();
        conn.connect();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream()),
                8*1024);
        long end = System.currentTimeMillis();
        Log.d(TAG, "Request: " + (end-start));
        
        start = System.currentTimeMillis();
        StringBuilder data;
        int len = conn.getContentLength();
        if (len == -1) {
            data = new StringBuilder(); // default size
        }
        else {
            data = new StringBuilder(len);
        }
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            data.append(inputLine);
        }
        mResponseString = data.toString();
        mResponse = new JSONObject(mResponseString);
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
    static public ObaResponse createFromError(String error) {
        return new ObaResponse(error, true);
    }
    static public ObaResponse createFromURL(URL url) throws JSONException, IOException {
        return new ObaResponse(url);
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
        return mResponse.toBundle();
    }
}
