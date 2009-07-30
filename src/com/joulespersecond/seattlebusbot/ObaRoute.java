package com.joulespersecond.seattlebusbot;

import org.json.JSONObject;

public class ObaRoute {
	JSONObject mData;
	
	/**
	 * Constructor.
	 * 
	 * @param obj The encapsulated object.
	 */
	public ObaRoute(JSONObject obj) {
		mData = obj;
	}
	/**
	 * Returns the route ID.
	 * 
	 * @return The route ID.
	 */
	public String
	getId() {
		return mData.optString("id");
	}
	/**
	 * Returns the short name of the route (ex. "10", "30").
	 * 
	 * @return The short name of the route.
	 */
	public String
	getShortName() {
		return mData.optString("shortName");
	}
	/**
	 * Returns the long name of the route (ex. "Sandpoint/QueenAnne")
	 * 
	 * @return The long name of the route.
	 */
	public String
	getLongName() {
		return mData.optString("longName");
	}
	// TODO: Agency information
	// (This might be useful if we wanted to display different artwork for the
	//  different agencies.)
}
