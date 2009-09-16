package com.joulespersecond.seattlebusbot;

import org.json.JSONException;
import org.json.JSONObject;

public class ObaResponse {
	JSONObject mResponse;
	
	/**
	 * Constructor for ObaResponse
	 * 
	 * @param The JSON object representing the response.
	 */
	public ObaResponse(JSONObject obj) {
		mResponse = obj;
	}
	public ObaResponse(String error) {
		mResponse = new JSONObject();
		try {
			mResponse.put("text", error);
			mResponse.put("code", 0);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	public String
	getVersion() {
		return mResponse.optString("version", "");
	}
	public int 
	getCode() {
		return mResponse.optInt("code", 0);
	}
	public String
	getText() {
		return mResponse.optString("text", "FAIL");
	}
	public ObaData 
	getData() {
		return new ObaData(ObaApi.getChildObj(mResponse, "data"));
	}
	public String
	toString() {
		return mResponse.toString();
	}
}
