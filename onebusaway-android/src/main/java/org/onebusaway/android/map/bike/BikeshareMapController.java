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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaStopsForLocationRequest;
import org.onebusaway.android.io.request.ObaStopsForLocationResponse;
import org.onebusaway.android.map.BaseMapController;
import org.onebusaway.android.map.MapModeController;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.MapWatcher;
import org.onebusaway.android.map.bike.BikeLoaderCallbacks;
import org.onebusaway.android.map.bike.BikeStationLoader;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.map.googlemapsv2.bike.BikeStationOverlay;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.RegionUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class BikeshareMapController extends BaseMapController {

    private static final String TAG = "BikeshareMapController";

    // Bike Station loader
    private static final int BIKE_STATIONS_LOADER = 8736;
    private BikeLoaderCallbacks bikeLoaderCallbacks;
    private BikeStationLoader bikeLoader;


    public BikeshareMapController(Callback callback) {
        super(callback);
    }

    @Override
    protected void createLoader() {
        updateData();
    }

    public void showBikes(boolean showBikes) {
        if (showBikes) {
            bikeLoaderCallbacks = new BikeLoaderCallbacks(mCallback);
            bikeLoader = bikeLoaderCallbacks.onCreateLoader(BIKE_STATIONS_LOADER, null);
            bikeLoader.registerListener(0, bikeLoaderCallbacks);
            bikeLoader.startLoading();
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
        return MapParams.MODE_STOP;
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
        SharedPreferences sp = Application.get().getPrefs();
        boolean isBikeSelected = sp.getBoolean(mCallback.getActivity().getString(R.string.preference_key_layer_bikeshare_activated), true)
                && sp.getBoolean(mCallback.getActivity().getString(R.string.preference_key_layer_bikeshare_visible), false);
        showBikes(isBikeSelected);
    }

    @Override
    public void setState(Bundle args) {
        // Avoid the layers controller to center the map.
        args.putBoolean(MapParams.DO_N0T_CENTER_ON_LOCATION, true);
        super.setState(args);
    }
}
