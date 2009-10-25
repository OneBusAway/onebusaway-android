package com.joulespersecond.seattlebusbot;

import org.json.JSONObject;

public final class ObaArrivalInfo {
	private final JSONObject mData;
	
	/**
	 * Constructor.
	 * 
	 * @param obj The encapsulated object.
	 */
	public ObaArrivalInfo(JSONObject obj) {
		mData = obj;
	}
	/**
	 * Returns the ID of the route.
	 * @return The ID of the route.
	 */
	public final String 
	getRouteId() {
		return mData.optString("routeId");
	}
	/**
	 * Returns the short name of the route.
	 * @return The short name of the route.
	 */
	public final String
	getShortName() {
		return mData.optString("routeShortName");
	}
	/**
	 * Returns the trip ID of the route.
	 * @return The trip ID of the route.
	 */
	public final String 
	getTripId() {
		return mData.optString("tripId");
	}
	/**
	 * Returns the trip headsign.
	 * @return The trip headsign.
	 */
	public final String
	getHeadsign() {
		return mData.optString("tripHeadsign");
	}
	/**
	 * Returns the stop ID.
	 * @return The stop ID.
	 */
	public final String
	getStopId() {
		return mData.optString("stopId");
	}
	/**
	 * Returns the scheduled arrival time in milliseconds past the epoch.
	 * @return The scheduled arrival time.
	 */
	public final long
	getScheduledArrivalTime() {
		return mData.optLong("scheduledArrivalTime");
	}
	/**
	 * Returns the predicted arrival time in milliseconds past the epoch, 
	 * or 0 if no prediction data is available.
	 * @return The predicted arrival time, or 0.
	 */
	public final long
	getPredictedArrivalTime() {
		return mData.optLong("predictedArrivalTime");
	}
	/** 
	 * Returns the scheduled departure time in milliseconds past the epoch..
	 * @return The scheduled departure time.
	 */
	public final long
	getScheduledDepartureTime() {
		return mData.optLong("scheduledDepartureTime");
	}
	/**
	 * Returns the predicted departure time in milliseconds past the epoch, 
	 * or 0 if no prediction data is available.
	 * @return The predicted arrival time, or 0.
	 */
	public final long
	getPredictedDepartureTime() {
		return mData.optLong("predictedDepartureTime");
	}
	
	/**
	 * Returns the status of the route.
	 * @return The status of the route.
	 */
	public final String
	getStatus() {
		return mData.optString("status");
	}
}
