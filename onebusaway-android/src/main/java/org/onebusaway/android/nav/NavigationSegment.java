/*
 * Copyright (C) 2005-2018 University of South Florida
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
package org.onebusaway.android.nav;

import org.onebusaway.android.io.elements.ObaStop;

import android.location.Location;
import android.location.LocationManager;

public class NavigationSegment {

    private int segmentId;

    public void setSegmentId(int segmentId) {
        this.segmentId = segmentId;
    }

    public int getSegmentId() {
        return segmentId;
    }

    private int agencyFeedId;

    public void setAgencyFeedId(int agencyFeedId) {
        this.agencyFeedId = agencyFeedId;
    }

    public int getAgencyFeedId() {
        return agencyFeedId;
    }

    private String routeIdGtfs;

    public void setRouteIdGtfs(String routeIdGtfs) {
        this.routeIdGtfs = routeIdGtfs;
    }

    public String getRouteIdGtfs() {
        return routeIdGtfs;
    }

    private String tripHeadsignGtfs;

    public void setTripHeadsignGtfs(String tripHeadsignGtfs) {
        this.tripHeadsignGtfs = tripHeadsignGtfs;
    }

    public String getTripHeadsignGtfs() {
        return tripHeadsignGtfs;
    }

    private int directionIdGtfs;

    public void setDirectionIdGtfs(int directionIdGtfs) {
        this.directionIdGtfs = directionIdGtfs;
    }

    public int getDirectionIdGtfs() {
        return directionIdGtfs;
    }

    private int idStopFrom;

    public void setIdStopFrom(int idStopFrom) {
        this.idStopFrom = idStopFrom;
    }

    public int getIdStopFrom() {
        return idStopFrom;
    }

    private String idStopFromTransitAgencyGTFS;

    public void setIdStopFromTransitAgencyGTFS(String idStopFromTransitAgencyGTFS) {
        this.idStopFromTransitAgencyGTFS = idStopFromTransitAgencyGTFS;
    }

    public String getIdStopFromTransitAgencyGTFS() {
        return idStopFromTransitAgencyGTFS;
    }


    private Location fromLocation;

    public Location getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(Location l) {
        fromLocation = l;
    }

    private int idStopTo;

    public void setIdStopTo(int idStopTo) {
        this.idStopTo = idStopTo;
    }

    public int getIdStopTo() {
        return idStopTo;
    }

    private String idStopToTransitAgencyGTFS;

    public void setIdStopToTransitAgencyGTFS(String idStopToTransitAgencyGTFS) {
        this.idStopToTransitAgencyGTFS = idStopToTransitAgencyGTFS;
    }

    public String getIdStopToTransitAgencyGTFS() {
        return idStopToTransitAgencyGTFS;
    }

    private Location toLocation;

    public Location getToLocation() {
        return toLocation;
    }

    public void setToLocation(Location l) {
        toLocation = l;
    }

    private Location beforeLocation;

    public Location getBeforeLocation() {
        return beforeLocation;
    }

    public void setBeforeLocation(Location l) {
        beforeLocation = l;
    }

    private float AlertDistance;

    public void setAlertDistance(float AlertDistance) {
        this.AlertDistance = AlertDistance;
    }

    public float getAlertDistance() {
        return AlertDistance;
    }

    private String tripId;

    public void setTripId(String trip) {
        tripId = trip;
    }

    public String getTripId() {
        return tripId;
    }

    /**
     * Construct a segment from two stops. From Location remains null.
     *
     * @param destination Destination Stop.
     * @param before      Second-to-last stop.
     */
    public NavigationSegment(ObaStop destination, ObaStop before) {
        toLocation = new Location(LocationManager.GPS_PROVIDER);
        toLocation.setLatitude(destination.getLatitude());
        toLocation.setLongitude(destination.getLongitude());
        beforeLocation = new Location(LocationManager.GPS_PROVIDER);
        beforeLocation.setLatitude(before.getLatitude());
        beforeLocation.setLongitude(before.getLongitude());
    }

    /**
     * Construct a segment from locations.
     *
     * @param beforeLoc Second to last location.
     * @param toLoc     Destination location.
     * @param fromLoc   User's from location.
     */
    public NavigationSegment(Location beforeLoc, Location toLoc, Location fromLoc) {
        fromLocation = fromLoc;
        toLocation = toLoc;
        beforeLocation = beforeLoc;
    }
}
