/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), and individual contributors.
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
package org.onebusaway.android.map.bike;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.map.BaseMapController;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.util.LayerUtils;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.VertexType;
import org.opentripplanner.routing.core.TraverseMode;

import android.os.Bundle;
import android.support.v4.content.Loader;

import java.util.ArrayList;
import java.util.List;

public class BikeshareMapController extends BaseMapController {

    private static final String TAG = "BikeshareMapController";

    private List<String> selectedBikeStationIds;
    private String mapMode;


    // Bike Station loader
    private static final int BIKE_STATIONS_LOADER = 8736;
    private BikeLoaderCallbacks bikeLoaderCallbacks;
    private BikeStationLoader bikeLoader;

    public BikeshareMapController(Callback callback) {
        //super(callback);
        super.mCallback = callback;
        createLoader();
    }

    @Override
    protected void createLoader() {
        updateData();
    }

    public void showBikes(boolean showBikes) {

        if (showBikes) {
            // Bike stations should be loaded if map mode is not DIRECTIONS OR if map mode is
            // DIRECTIONS and there are bike stations to display
            if (mapMode != null) {
                if (!mapMode.equals(MapParams.MODE_DIRECTIONS) ||
                        (mapMode.equals(MapParams.MODE_DIRECTIONS) &&
                                (selectedBikeStationIds != null ||
                                        selectedBikeStationIds.size() > 0))) {
                    bikeLoaderCallbacks = new BikeLoaderCallbacks(mCallback);
                    bikeLoaderCallbacks.setBikeStationFilter(selectedBikeStationIds);
                    bikeLoader = bikeLoaderCallbacks.onCreateLoader(BIKE_STATIONS_LOADER, null);
                    bikeLoader.registerListener(0, bikeLoaderCallbacks);
                    bikeLoader.startLoading();
                }
            }
        } else {
            if (bikeLoader != null) {
                mCallback.clearBikeStations();
                bikeLoader.stopLoading();
                bikeLoader = null;
                bikeLoaderCallbacks = null;
            }
        }
    }

    @Override
    public String getMode() {
        return mapMode;
    }

    public void setMode(String mode) {
        mapMode = mode;
    }

    @Override
    public void onHidden(boolean hidden) {

    }

    @Override
    protected Loader getLoader() {
        return bikeLoader;
    }

    @Override
    protected void updateData() {
        boolean isBikeActivated = Application.isBikeshareEnabled();
        if (isBikeActivated) {
            if (mapMode != null) {
                if (mapMode.equals(MapParams.MODE_DIRECTIONS) &&
                        (selectedBikeStationIds != null &&
                                selectedBikeStationIds.size() > 0)) {
                    showBikes(true);
                } else {
                    boolean isBikeSelected = LayerUtils.isBikeshareLayerVisible();
                    showBikes(isBikeSelected);
                }
            }
        }
    }

    @Override
    public void setState(Bundle args) {
        // If the controller is being called when the map is displaying directions, get the bike
        // stations that are part of the directions to display only them.
        Itinerary itinerary = (Itinerary) args.getSerializable(MapParams.ITINERARY);
        if (itinerary != null) {
            selectedBikeStationIds = getBikeStationIdsFromItinerary(itinerary);
        }

        // Do not call super in this controller because the map zoom and positioning is already
        // handled by the other controller. The bike controller is used together with another controller.

        // TODO The zoom and center positioning should probably be separated from the controller
        //   because it needs to be handled only once per map, not per controller
        // (super.setState() handles the zoom and map centering)

    }

    private List<String> getBikeStationIdsFromItinerary(Itinerary itinerary) {
        List<String> bikeStationIds = new ArrayList<>();
        for (Leg leg : itinerary.legs) {
            if (TraverseMode.BICYCLE.toString().equals(leg.mode)) {
                if (VertexType.BIKESHARE.equals(leg.from.vertexType)) {
                    bikeStationIds.add(leg.from.bikeShareId);
                }
                if (VertexType.BIKESHARE.equals(leg.to.vertexType)) {
                    bikeStationIds.add(leg.to.bikeShareId);
                }
            }
        }
        return bikeStationIds;
    }
}
