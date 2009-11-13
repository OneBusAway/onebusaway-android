package com.joulespersecond.oba;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONException;

import com.joulespersecond.json.JSONObject;

public final class ObaResponse {
    //private static final String TAG = "ObaResponse";
    
    private final JSONObject mResponse;
    
    /**
     * Constructor for ObaResponse
     * 
     * @param The JSON object representing the response.
     */
    ObaResponse(JSONObject obj) {
        mResponse = obj;
    }
    private ObaResponse(String json) throws JSONException {
        mResponse = new JSONObject(json);
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
    }
    private ObaResponse(URL url) throws JSONException, IOException {
        URLConnection conn = url.openConnection();
        conn.connect();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream()),
                8*1024);
        mResponse = new JSONObject(in);
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
        return mResponse.toString();
    }
}
