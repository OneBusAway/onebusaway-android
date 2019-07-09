/*
 * Copyright (C) 2019 University of South Florida
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
package org.onebusaway.android.travelbehavior.model;

import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.ObaTripStatusElement;
import org.onebusaway.android.io.elements.Occupancy;

import java.util.ArrayList;
import java.util.List;

public class ObaArrivalInfoPojo {

    private final String routeId;

    private final String routeShortName;

    private final String routeLongName;

    private final String tripId;

    private final String tripHeadsign;

    private final String stopId;

    private final long predictedArrivalTime;

    private final long scheduledArrivalTime;

    private final long predictedDepartureTime;

    private final long scheduledDepartureTime;

    private final String status;

    private final ObaArrivalInfo.Frequency frequency;

    private final String vehicleId;

    private final Double distanceFromStop;

    private final Integer numberOfStopsAway;

    private final long serviceDate;

    private final long lastUpdateTime;

    private final Boolean predicted;

    private final ObaTripStatusElement tripStatus;

    private final List<String> situationIds;

    private final boolean arrivalEnabled;

    private final boolean departureEnabled;

    private final int stopSequence;

    private final int totalStopsInTrip;

    private final int blockTripSequence;

    private final Occupancy historicalOccupancy;

    private final Occupancy predictedOccupancy;

    public ObaArrivalInfoPojo() {
        routeId = "";
        routeShortName = "";
        routeLongName = "";
        tripId = "";
        tripHeadsign = "";
        stopId = "";
        predictedArrivalTime = 0;
        scheduledArrivalTime = 0;
        predictedDepartureTime = 0;
        scheduledDepartureTime = 0;
        status = "";
        frequency = null;
        vehicleId = null;
        distanceFromStop = null;
        numberOfStopsAway = null;
        serviceDate = 0;
        lastUpdateTime = 0;
        predicted = null;
        tripStatus = null;
        situationIds = null;
        arrivalEnabled = true;
        departureEnabled = true;
        stopSequence = 0;
        totalStopsInTrip = 0;
        blockTripSequence = 0;
        historicalOccupancy = null;
        predictedOccupancy = null;
    }

    public ObaArrivalInfoPojo(ObaArrivalInfo info) {
        routeId = info.getRouteId();
        routeShortName = info.getShortName();
        routeLongName = info.getRouteLongName();
        tripId = info.getTripId();
        tripHeadsign = info.getHeadsign();
        stopId = info.getStopId();
        predictedArrivalTime = info.getPredictedArrivalTime();
        scheduledArrivalTime = info.getScheduledArrivalTime();
        predictedDepartureTime = info.getPredictedDepartureTime();
        scheduledDepartureTime = info.getScheduledDepartureTime();
        status = info.getStatus();
        frequency = info.getFrequency();
        vehicleId = info.getVehicleId();
        distanceFromStop = info.getDistanceFromStop();
        numberOfStopsAway = info.getNumberOfStopsAway();
        serviceDate = info.getServiceDate();
        lastUpdateTime = info.getLastUpdateTime();
        predicted = info.getPredicted();
        tripStatus = (ObaTripStatusElement) info.getTripStatus();
        situationIds = new ArrayList<>();
        if (info.getSituationIds() != null && info.getSituationIds().length != 0) {
            for(String id: info.getSituationIds()) {
                situationIds.add(id);
            }
        }
        arrivalEnabled = info.getArrivalEnabled();
        departureEnabled = info.getDepartureEnabled();
        stopSequence = info.getStopSequence();
        totalStopsInTrip = info.getTotalStopsInTrip();
        blockTripSequence = info.getBlockTripSequence();
        historicalOccupancy = info.getHistoricalOccupancy();
        predictedOccupancy = info.getPredictedOccupancy();
    }

    /**
     * @return The ID of the route.
     */
    public String getRouteId() {
        return routeId;
    }

    /**
     * @return The short name of the route.
     */
    public String getShortName() {
        return routeShortName;
    }

    /**
     * @return The long name of the route.
     */
    public String getRouteLongName() {
        return routeLongName;
    }

    /**
     * @return The trip ID of the route.
     */
    public String getTripId() {
        return tripId;
    }

    /**
     * @return The trip headsign.
     */
    public String getHeadsign() {
        return tripHeadsign;
    }

    /**
     * @return The stop ID.
     */
    public String getStopId() {
        return stopId;
    }

    /**
     * @return The scheduled arrival time.
     */
    public long getScheduledArrivalTime() {
        return scheduledArrivalTime;
    }

    /**
     * @return The predicted arrival time, or 0.
     */
    public long getPredictedArrivalTime() {
        return predictedArrivalTime;
    }

    /**
     * @return The scheduled departure time.
     */
    public long getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }

    /**
     * @return The predicted arrival time, or 0.
     */
    public long getPredictedDepartureTime() {
        return predictedDepartureTime;
    }

    /**
     * @return The status of the route.
     */
    public String getStatus() {
        return status;
    }

    /**
     * @return The frequency of the trip, for frequency-based scheduling. For
     * time-based schedules, this is null.
     */
    public ObaArrivalInfo.Frequency getFrequency() {
        return frequency;
    }

    /**
     * @return The vehicle ID of the trip, or null if it is not provided.
     */
    public String getVehicleId() {
        return vehicleId;
    }

    /**
     * @return The distance, in meters, of the transit vehicle from the stop,
     * or null if it is not provided.
     */
    public Double getDistanceFromStop() {
        return distanceFromStop;
    }

    /**
     * @return The number of stops between the transit vehicle and the current stop.
     */
    public Integer getNumberOfStopsAway() {
        return numberOfStopsAway;
    }

    /**
     * @return The midnight-based start time of the day of service of which a trip is
     * operating, in Unix time, or 0 if this is not provided.
     */
    public long getServiceDate() {
        return serviceDate;
    }

    /**
     * @return
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * @return Whether this arrival has prediction information. If the 'predicted'
     * value is set, then that is used; otherwise it is inferred from the existence
     * of a non-zero predicted start or end time.
     */
    public boolean getPredicted() {
        return (predicted != null) ? predicted : (predictedDepartureTime != 0);
    }

    /**
     * @return The trip status, if it exists.
     */
    public ObaTripStatus getTripStatus() {
        return tripStatus;
    }

    /**
     * @return The array of situation IDs, or null.
     */
    public List<String> getSituationIds() {
        return situationIds;
    }

    /**
     * @return True if arrivals are enabled for this trip, false otherwise.
     */
    public boolean getArrivalEnabled() {
        return arrivalEnabled;
    }

    /**
     * @return True if departures are enabled for this trip, false otherwise.
     */
    public boolean getDepartureEnabled() {
        return departureEnabled;
    }

    /**
     * @return the index of the stop into the sequence of stops that make up the trip for this
     * arrival. This value is 0-indexed, and is generated internally by OneBusAway (it is not the
     * GTFS stop_sequence). The first stop in the trip will always have stopSequence = 0, while the
     * last stop in the trip will always have stopSequence = totalStopsInTrip - 1.
     */
    public int getStopSequence() {
        return stopSequence;
    }

    /**
     * @return the total number of stops visited on the trip for this arrival, or 0 if the server
     * doesn't support this field. If the same stop is visited more than once in this trip, each
     * visitation is counted towards the total.
     */
    public int getTotalStopsInTrip() {
        return totalStopsInTrip;
    }

    /**
     * @return The index of this arrival's trip into the sequence of trips for the active block.
     * Compare to blockTripSequence in the TripStatus element to determine where the
     * arrival-and-departure
     * is on the block in comparison to the active block location.
     */
    public int getBlockTripSequence() {
        return blockTripSequence;
    }

}
