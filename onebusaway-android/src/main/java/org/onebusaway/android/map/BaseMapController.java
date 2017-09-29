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
package org.onebusaway.android.map;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaStopsForLocationRequest;
import org.onebusaway.android.io.request.ObaStopsForLocationResponse;
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

public abstract class BaseMapController implements MapModeController,
        MapWatcher.Listener {

    private static final String TAG = "BaseMapController";

    protected Callback mCallback;

    private MapWatcher mMapWatcher;

    /**
     * GoogleApiClient being used for Location Services
     */
    private GoogleApiClient mGoogleApiClient;

    public BaseMapController() {

    }

    public BaseMapController(Callback callback) {
        mCallback = callback;
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (api.isGooglePlayServicesAvailable(mCallback.getActivity())
                == ConnectionResult.SUCCESS) {
            Context context = mCallback.getActivity();
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(context);
            mGoogleApiClient.connect();
        }
        createLoader();
    }

    protected abstract void createLoader();

    /**
     * Sets the initial state of where the map is focused, and it's zoom level
     */
    @Override
    public void setState(Bundle args) {
        if (args != null) {
            Location center = UIUtils.getMapCenter(args);

            // If the STOP_ID was set in the bundle, then we should focus on that stop
            String stopId = args.getString(MapParams.STOP_ID);
            if (stopId != null && center != null) {
                mCallback.getMapView().setZoom(MapParams.DEFAULT_ZOOM);
                setMapCenter(center);
                return;
            }

            boolean dontCenterOnLocation = args.getBoolean(MapParams.DO_N0T_CENTER_ON_LOCATION);

            // Try to set map based on real-time location, unless state says no
            if (!dontCenterOnLocation) {
                boolean setLocation = mCallback.setMyLocation(true, false);
                if (setLocation) {
                    return;
                }
            }

            // If we have a previous map view, center map on that
            if (center != null) {
                float mapZoom = args.getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);
                mCallback.getMapView().setZoom(mapZoom);
                setMapCenter(center);
                return;
            }
        } else {
            // We don't have any state info - just center on last known location
            boolean setLocation = mCallback.setMyLocation(false, false);
            if (setLocation) {
                return;
            }
        }
        // If all else fails, just center on the region
        mCallback.zoomToRegion();
    }

    /**
     * Sets the map center and loads stops for the new map view
     *
     * @param center new coordinates for the map to center on
     */
    private void setMapCenter(Location center) {
        mCallback.getMapView().setMapCenter(center, false, false);
        onLocation();
    }

    @Override
    public void destroy() {
        if (getLoader() != null) {
            getLoader().reset();
        }
        watchMap(false);
    }

    @Override
    public void onPause() {
        watchMap(false);

        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onResume() {
        watchMap(true);

        // Make sure GoogleApiClient is connected, if available
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        // We don't need to handle the view state here. This is already been handled in HomeActivity
        // when the bus stop is selected on the map
    }

    @Override
    public void onLocation() {
        refresh();
    }

    @Override
    public void onNoLocation() {
    }

    protected abstract Loader getLoader();

    private void refresh() {
        // First we need to check to see if the current request we have can handle this.
        // Otherwise, we need to restart the loader with the new request.
        if (mCallback != null) {
            Activity a = mCallback.getActivity();
            if (a != null) {
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateData();
                    }
                });
            }
        }
    }

    protected abstract void updateData();

    //
    // Map watcher
    //
    private void watchMap(boolean watch) {
        // Only instantiate our own map watcher if the mapView isn't capable of watching itself
        if (watch && !mCallback.getMapView().canWatchMapChanges()) {
            if (mMapWatcher == null) {
                mMapWatcher = new MapWatcher(mCallback.getMapView(), this);
            }
            mMapWatcher.start();
        } else {
            if (mMapWatcher != null) {
                mMapWatcher.stop();
            }
            mMapWatcher = null;
        }
    }

    @Override
    public void onMapZoomChanging() {
        //Log.d(TAG, "Map zoom changing");
    }

    @Override
    public void onMapZoomChanged() {
        //Log.d(TAG, "Map zoom changed");
        refresh();
    }

    @Override
    public void onMapCenterChanging() {
        //Log.d(TAG, "Map center changing");
    }

    @Override
    public void onMapCenterChanged() {
        // Log.d(TAG, "Map center changed.");
        refresh();
    }

    @Override
    public void notifyMapChanged() {
        Log.d(TAG, "Map changed (called by MapView)");
        refresh();
    }
}
