/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joulespersecond.oba;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

public final class ObaArrivalInfo {
    public static final ObaArrivalInfo EMPTY_OBJECT = new ObaArrivalInfo();
    public static final ObaArray<ObaArrivalInfo> EMPTY_ARRAY = new ObaArray<ObaArrivalInfo>();
    public static final Type ARRAY_TYPE = new TypeToken<ObaArray<ObaArrivalInfo>>(){}.getType();

    private final String routeId;
    private final String routeShortName;
    private final String tripId;
    private final String tripHeadsign;
    private final String stopId;
    private final long predictedArrivalTime;
    private final long scheduledArrivalTime;
    private final long predictedDepartureTime;
    private final long scheduledDepartureTime;
    private final String status;

    /**
     * Constructor.
     *
     * @param obj The encapsulated object.
     */
    ObaArrivalInfo() {
        routeId = "";
        routeShortName = "";
        tripId = "";
        tripHeadsign = "";
        stopId = "";
        predictedArrivalTime = 0;
        scheduledArrivalTime = 0;
        predictedDepartureTime = 0;
        scheduledDepartureTime = 0;
        status = "";
    }
    /**
     * Returns the ID of the route.
     * @return The ID of the route.
     */
    public String getRouteId() {
        return routeId;
    }
    /**
     * Returns the short name of the route.
     * @return The short name of the route.
     */
    public String getShortName() {
        return ObaRoute.getAlternateRouteName(routeId, routeShortName);
    }
    /**
     * Returns the trip ID of the route.
     * @return The trip ID of the route.
     */
    public String getTripId() {
        return tripId;
    }
    /**
     * Returns the trip headsign.
     * @return The trip headsign.
     */
    public String getHeadsign() {
        return tripHeadsign;
    }
    /**
     * Returns the stop ID.
     * @return The stop ID.
     */
    public String getStopId() {
        return stopId;
    }
    /**
     * Returns the scheduled arrival time in milliseconds past the epoch.
     * @return The scheduled arrival time.
     */
    public long getScheduledArrivalTime() {
        return scheduledArrivalTime;
    }
    /**
     * Returns the predicted arrival time in milliseconds past the epoch,
     * or 0 if no prediction data is available.
     * @return The predicted arrival time, or 0.
     */
    public long getPredictedArrivalTime() {
        return predictedArrivalTime;
    }
    /**
     * Returns the scheduled departure time in milliseconds past the epoch..
     * @return The scheduled departure time.
     */
    public long getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }
    /**
     * Returns the predicted departure time in milliseconds past the epoch,
     * or 0 if no prediction data is available.
     * @return The predicted arrival time, or 0.
     */
    public long getPredictedDepartureTime() {
        return predictedDepartureTime;
    }

    /**
     * Returns the status of the route.
     * @return The status of the route.
     */
    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return ObaApi.getGson().toJson(this);
    }
}
