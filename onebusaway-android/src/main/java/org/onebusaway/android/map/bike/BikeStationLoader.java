/*
* Copyright (C) Sean J. Barbeau (sjbarbeau@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.onebusaway.android.map.bike;

import android.content.Context;
import android.location.Location;
import android.support.v4.content.AsyncTaskLoader;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.bike.OtpBikeStationRequest;
import org.onebusaway.android.io.request.bike.OtpBikeStationResponse;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;
import org.opentripplanner.routing.bike_rental.BikeRentalStationList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Class responsible for loading the list of bike stations and floating bikes from OpenTripPlanner.
 * OpenTripPlanner accept parameters lowerLeft and upperRight.
 * Google Maps work with southwest and northeast. loweLeft is equivalent to southeast and upperRight is equivalent to northeast.
 * <p>
 * This class external interface accept the parameters as found in Google Maps
 * (southWest and northEast). Internally it maps to OTP parameters (lowerLeft and upperRight).
 *
 */

public class BikeStationLoader extends AsyncTaskLoader<List<BikeRentalStation>> {

    private Location lowerLeft, upperRight;

    /**
     *
     * @param context
     * @param southWest southwest corner on the map in lat long
     * @param northEast northeast corner on the map in lat long
     */
    public BikeStationLoader(Context context, Location southWest, Location northEast) {
        super(context);
        updateCoordinates(southWest, northEast);
    }

    @Override
    public List<BikeRentalStation> loadInBackground() {
        OtpBikeStationResponse list
                = OtpBikeStationRequest.newRequest(getContext(), lowerLeft, upperRight).call();
        return list.stations;
    }

    @Override
    public void deliverResult(List<BikeRentalStation> data) {
        super.deliverResult(data);
    }

    @Override
    public void onStartLoading() {
        forceLoad();
    }

    /**
     * Update the bounding box to be used to load the bike stations.  This method is usually called
     * as a result of a map changing its location and/or zoom.
     *
     * Calls to this method forces the data to be reloaded.
     *
     * @param southWest south west corner of the bounding box
     * @param northEast north east corder of the bounding box
     */
    public void update(Location southWest, Location northEast) {
        updateCoordinates(southWest, northEast);
        onContentChanged();
    }

    /**
     * Updates the bounding box, converting the names from southWest/northEast to
     * lowerLeft/upperRight
     *
     * @param southWest
     * @param northEast
     */
    private void updateCoordinates(Location southWest, Location northEast) {
        this.lowerLeft = southWest;
        this.upperRight = northEast;
    }

}
