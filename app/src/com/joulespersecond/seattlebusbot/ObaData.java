package com.joulespersecond.seattlebusbot;

import org.json.JSONObject;

public class ObaData {
	JSONObject mData;
	
	/**
	 * Constructor for ObaData
	 * 
	 * @param The JSON object representing the response.
	 */
	public ObaData(JSONObject obj) {
		mData = obj;
	}
	/**
	 * Retrieves the list of stops, if they exist.
	 * 
	 * @return The list of stops, or an empty array.
	 */
	public ObaArray
	getStops() {
		return new ObaArray(ObaApi.getChildArray(mData, "stops"));
	}
	/*
	public ObaPolylinesArray
	getPolylines() {
	}
	*/
}
