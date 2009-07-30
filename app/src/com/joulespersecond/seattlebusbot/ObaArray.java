package com.joulespersecond.seattlebusbot;

import org.json.JSONArray;

public class ObaArray {
	JSONArray mArray;
	
	/**
	 * Constructor.
	 * 
	 * @param The encapsulated array.
	 */
	public ObaArray(JSONArray obj) {
		mArray = obj;
	}
	/**
	 * Returns the length of the array.
	 * 
	 * @return The length of the array.
	 */
	public int
	length() {
		return mArray.length();
	}
	/**
	 * Returns a stop object for the specified index.
	 * 
	 * @param index The child index.
	 * @return The stop object, or an empty object if it isn't a stop.
	 */
	public ObaStop
	getStop(int index) {
		return new ObaStop(ObaApi.getChildObj(mArray, index));
	}
}
