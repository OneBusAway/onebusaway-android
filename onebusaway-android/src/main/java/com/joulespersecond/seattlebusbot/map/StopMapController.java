/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com) and individual contributors.
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
package com.joulespersecond.seattlebusbot.map;

import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.region.RegionUtils;
import com.joulespersecond.oba.request.ObaStopsForLocationRequest;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;
import com.joulespersecond.seattlebusbot.map.googlemapsv1.BaseMapActivity;
import com.joulespersecond.seattlebusbot.util.LocationHelp;

import java.util.Arrays;
import java.util.List;

final class StopsRequest {

    private final Location mCenter;

    private final double mLatSpan;

    private final double mLonSpan;

    private final double mZoomLevel;

    StopsRequest(MapModeController.ObaMapView view) {
        mCenter = view.getMapCenterAsLocation();
        mLatSpan = view.getLatitudeSpanInDecDegrees();
        mLonSpan = view.getLongitudeSpanInDecDegrees();
        mZoomLevel = view.getZoomLevelAsFloat();
    }

    Location getCenter() {
        return mCenter;
    }

    double getLatSpan() {
        return mLatSpan;
    }

    double getLonSpan() {
        return mLonSpan;
    }

    double getZoomLevel() {
        return mZoomLevel;
    }
}

final class StopsResponse {

    private final StopsRequest mRequest;

    private final ObaStopsForLocationResponse mResponse;

    StopsResponse(StopsRequest req, ObaStopsForLocationResponse response) {
        mRequest = req;
        mResponse = response;
    }

    StopsRequest getRequest() {
        return mRequest;
    }

    ObaStopsForLocationResponse getResponse() {
        return mResponse;
    }

    /**
     * Returns true if newReq also fulfills response.
     */
    boolean fulfills(StopsRequest newReq) {
        if (mRequest.getCenter() == null) {
            //Log.d(TAG, "No center");
            return false;
        }
        if (!mRequest.getCenter().equals(newReq.getCenter())) {
            //Log.d(TAG, "Center not the same");
            return false;
        }
        if (mResponse != null) {
            if ((newReq.getZoomLevel() > mRequest.getZoomLevel()) &&
                    mResponse.getLimitExceeded()) {
                //Log.d(TAG, "Zooming in -- limit exceeded");
                return false;
            } else if (newReq.getZoomLevel() < mRequest.getZoomLevel()) {
                //Log.d(TAG, "Zooming out");
                return false;
            }
        }
        return true;

        // Otherwise:

        // If the new request's is zoomed in and the current
        // response has limitExceeded, then no.

        // If the new request's lat/lon span is contained
        // entirely within the old one:
        //  Then the new request is fulfilled IFF the old request's
        //  limitExceeded == false.

        // If the new request's lat/lon span is not contained
        // entirely within the old one (fuzzy match)
        //  FALSE
    }
}

public class StopMapController implements MapModeController,
        LoaderManager.LoaderCallbacks<StopsResponse>,
        Loader.OnLoadCompleteListener<StopsResponse>,
        MapWatcher.Listener {

    private static final String TAG = "StopMapController";

    private static final int STOPS_LOADER = 5678;

    private final Callback mFragment;

    // In lieu of using an actual LoaderManager, which isn't
    // available in SherlockMapActivity
    private Loader<StopsResponse> mLoader;

    private MapWatcher mMapWatcher;

    /**
     * Google Location Services
     */
    LocationClient mLocationClient;

    public StopMapController(Callback callback) {
        mFragment = callback;

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(mFragment.getActivity()) == ConnectionResult.SUCCESS) {
            LocationHelp.LocationServicesCallback locCallback = new LocationHelp.LocationServicesCallback();
            mLocationClient = new LocationClient(mFragment.getActivity(), locCallback, locCallback);
            mLocationClient.connect();
        }

        //mFragment.getLoaderManager().initLoader(STOPS_LOADER, null, this);
        mLoader = onCreateLoader(STOPS_LOADER, null);
        mLoader.registerListener(0, this);
        mLoader.startLoading();
    }

    @Override
    public void setState(Bundle args) {
        if (args != null) {
            Location center = null;
            float mapZoom = args.getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);

            double lat = args.getDouble(MapParams.CENTER_LAT);
            double lon = args.getDouble(MapParams.CENTER_LON);
            if (lat != 0.0 && lon != 0.0) {
                center = LocationHelp.makeLocation(lat, lon);
            }
            mFragment.getMapView().setZoom(mapZoom);

            if (center != null) {
                mFragment.getMapView().setMapCenter(center);
                onLocation();
            } else {
                mFragment.setMyLocation(false, true);
            }
        } else {
            mFragment.setMyLocation(false, true);
        }
    }

    @Override
    public String getMode() {
        return MapParams.MODE_STOP;
    }

    @Override
    public void destroy() {
        //mFragment.getLoaderManager().destroyLoader(STOPS_LOADER);
        getLoader().reset();
        watchMap(false);
    }

    @Override
    public void onPause() {
        watchMap(false);

        // Tear down LocationClient
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.disconnect();
        }
    }

    @Override
    public void onResume() {
        watchMap(true);

        // Make sure LocationClient is connected, if available
        if (mLocationClient != null && !mLocationClient.isConnected()) {
            mLocationClient.connect();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    }

    @Override
    public void onLocation() {
        refresh();
    }

    @Override
    public void onNoLocation() {
    }

    @Override
    public Loader<StopsResponse> onCreateLoader(int id, Bundle args) {
        StopsLoader loader = new StopsLoader(mFragment);
        StopsRequest req = new StopsRequest(mFragment.getMapView());
        loader.update(req);
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<StopsResponse> loader,
            StopsResponse _response) {
        mFragment.showProgress(false);
        final ObaStopsForLocationResponse response = _response.getResponse();
        if (response == null) {
            return;
        }

        if (response.getCode() != ObaApi.OBA_OK) {
            BaseMapActivity.showMapError(mFragment.getActivity(), response);
            return;
        }

        if (response.getOutOfRange()) {
            mFragment.notifyOutOfRange();
            return;
        }

        //Workaround for https://github.com/OneBusAway/onebusaway-application-modules/issues/59
        //where outOfRange response element is false even if the location was out of range
        //We need to also make sure the list of stops is empty, otherwise we screen out valid responses
        //TODO - After above issue #59 is resolved, we should also only do this check on OBA server
        //versions below the version number in which this is fixed.
        Location myLocation = LocationHelp.getLocation2(mFragment.getActivity(), mLocationClient);
        if (myLocation != null && Application.get().getCurrentRegion() != null) {
            boolean inRegion = true;  // Assume user is in region unless we detect otherwise
            try {
                inRegion = RegionUtils
                        .isLocationWithinRegion(myLocation, Application.get().getCurrentRegion());
            } catch (IllegalArgumentException e) {
                // Issue #69 - some devices are providing invalid lat/long coordinates
                Log.e(TAG, "Invalid latitude or longitude - lat = " + myLocation.getLatitude()
                        + ", long = " + myLocation.getLongitude());
            }

            if (!inRegion && Arrays.asList(response.getStops()).isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Device location is outside region range, notifying...");
                }
                mFragment.notifyOutOfRange();
                return;
            }
        }

        List<ObaStop> stops = Arrays.asList(response.getStops());
        mFragment.showStops(stops, response);
    }

    @Override
    public void onLoaderReset(Loader<StopsResponse> loader) {
        // Clear the overlay.
        mFragment.showStops(null, null);
    }

    // Remove when adding back LoaderManager help.
    @Override
    public void onLoadComplete(Loader<StopsResponse> loader,
            StopsResponse response) {
        onLoadFinished(loader, response);
    }

    //
    // Loading
    //
    private StopsLoader getLoader() {
        //Loader<ObaStopsForLocationResponse> l =
        //        mFragment.getLoaderManager().getLoader(STOPS_LOADER);
        //return (StopsLoader)l;
        return (StopsLoader) mLoader;
    }

    private void refresh() {
        // First we need to check to see if the current request we have can handle this.
        // Otherwise, we need to restart the loader with the new request.
        mFragment.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                StopsLoader loader = getLoader();
                if (loader != null) {
                    StopsRequest req = new StopsRequest(mFragment.getMapView());
                    loader.update(req);
                }
            }
        });
    }

    //
    // Loader
    //
    private static class StopsLoader extends AsyncTaskLoader<StopsResponse> {

        private final Callback mFragment;

        private StopsRequest mRequest;

        private StopsResponse mResponse;

        public StopsLoader(Callback fragment) {
            super(fragment.getActivity());
            mFragment = fragment;
        }

        @Override
        public StopsResponse loadInBackground() {
            StopsRequest req = mRequest;
            if (Application.get().getCurrentRegion() == null &&
                    TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
                //We don't have region info or manually entered API to know what server to contact
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Trying to load stops from server without " +
                            "OBA REST API endpoint, aborting...");
                }
                return new StopsResponse(req, null);
            }
            //Make OBA REST API call to the server and return result
            ObaStopsForLocationResponse response =
                    new ObaStopsForLocationRequest.Builder(getContext(),
                            req.getCenter())
                            .setSpan(req.getLatSpan(), req.getLonSpan())
                            .build()
                            .call();
            return new StopsResponse(req, response);
        }

        @Override
        public void deliverResult(StopsResponse data) {
            mResponse = data;
            super.deliverResult(data);
        }

        @Override
        public void onStartLoading() {
            if (takeContentChanged()) {
                forceLoad();
            }
        }

        @Override
        public void onForceLoad() {
            mFragment.showProgress(true);
            super.onForceLoad();
        }

        public void update(StopsRequest req) {
            if (mResponse == null || !mResponse.fulfills(req)) {
                mRequest = req;
                onContentChanged();
            }
        }
    }

    //
    // Map watcher
    //
    private void watchMap(boolean watch) {
        // Only instantiate our own map watcher if the mapView isn't capable of watching itself
        if (watch && !mFragment.getMapView().canWatchMapChanges()) {
            if (mMapWatcher == null) {
                mMapWatcher = new MapWatcher(mFragment.getMapView(), this);
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
