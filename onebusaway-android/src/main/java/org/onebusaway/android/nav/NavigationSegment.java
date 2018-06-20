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

    private int mSegmentId;

    private int mAgencyFeedId;

    private String mRouteIdGtfs;

    private String mTripHeadsignGtfs;

    private int mDirectionIdGtfs;

    private int mIdStopFrom;

    private String mIdStopFromTransitAgencyGTFS;

    private Location mFromLocation;

    private int mIdStopTo;

    private String mIdStopToTransitAgencyGTFS;

    private Location mToLocation;

    private Location mBeforeLocation;

    private float mAlertDistance;

    private String mTripId;

    /**
     * Construct a segment from two stops. From Location remains null
     *
     * @param destination Destination Stop
     * @param before      Second-to-last stop
     */
    public NavigationSegment(ObaStop destination, ObaStop before) {
        mToLocation = new Location(LocationManager.GPS_PROVIDER);
        mToLocation.setLatitude(destination.getLatitude());
        mToLocation.setLongitude(destination.getLongitude());
        mBeforeLocation = new Location(LocationManager.GPS_PROVIDER);
        mBeforeLocation.setLatitude(before.getLatitude());
        mBeforeLocation.setLongitude(before.getLongitude());
    }

    /**
     * Construct a segment from locations
     *
     * @param beforeLoc Second to last location
     * @param toLoc     Destination location
     * @param fromLoc   User's origin location
     */
    public NavigationSegment(Location beforeLoc, Location toLoc, Location fromLoc) {
        mFromLocation = fromLoc;
        mToLocation = toLoc;
        mBeforeLocation = beforeLoc;
    }

    public int getSegmentId() {
        return mSegmentId;
    }

    public void setSegmentId(int segmentId) {
        this.mSegmentId = segmentId;
    }

    public int getAgencyFeedId() {
        return mAgencyFeedId;
    }

    public void setAgencyFeedId(int agencyFeedId) {
        this.mAgencyFeedId = agencyFeedId;
    }

    public String getRouteIdGtfs() {
        return mRouteIdGtfs;
    }

    public void setRouteIdGtfs(String routeIdGtfs) {
        this.mRouteIdGtfs = routeIdGtfs;
    }

    public String getTripHeadsignGtfs() {
        return mTripHeadsignGtfs;
    }

    public void setTripHeadsignGtfs(String tripHeadsignGtfs) {
        this.mTripHeadsignGtfs = tripHeadsignGtfs;
    }

    public int getDirectionIdGtfs() {
        return mDirectionIdGtfs;
    }

    public void setDirectionIdGtfs(int directionIdGtfs) {
        this.mDirectionIdGtfs = directionIdGtfs;
    }

    public int getIdStopFrom() {
        return mIdStopFrom;
    }

    public void setIdStopFrom(int idStopFrom) {
        this.mIdStopFrom = idStopFrom;
    }

    public String getIdStopFromTransitAgencyGTFS() {
        return mIdStopFromTransitAgencyGTFS;
    }

    public void setIdStopFromTransitAgencyGTFS(String idStopFromTransitAgencyGTFS) {
        this.mIdStopFromTransitAgencyGTFS = idStopFromTransitAgencyGTFS;
    }

    public Location getFromLocation() {
        return mFromLocation;
    }

    public void setFromLocation(Location l) {
        mFromLocation = l;
    }

    public int getIdStopTo() {
        return mIdStopTo;
    }

    public void setIdStopTo(int idStopTo) {
        this.mIdStopTo = idStopTo;
    }

    public String getIdStopToTransitAgencyGTFS() {
        return mIdStopToTransitAgencyGTFS;
    }

    public void setIdStopToTransitAgencyGTFS(String idStopToTransitAgencyGTFS) {
        this.mIdStopToTransitAgencyGTFS = idStopToTransitAgencyGTFS;
    }

    public Location getToLocation() {
        return mToLocation;
    }

    public void setToLocation(Location l) {
        mToLocation = l;
    }

    public Location getBeforeLocation() {
        return mBeforeLocation;
    }

    public void setBeforeLocation(Location l) {
        mBeforeLocation = l;
    }

    public float getAlertDistance() {
        return mAlertDistance;
    }

    public void setAlertDistance(float AlertDistance) {
        this.mAlertDistance = AlertDistance;
    }

    public String getTripId() {
        return mTripId;
    }

    public void setTripId(String trip) {
        mTripId = trip;
    }
}
