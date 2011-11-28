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

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaStopsForLocationRequest;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentMapActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import java.util.Arrays;
import java.util.List;


class StopMapController implements MapFragmentController,
            LoaderManager.LoaderCallbacks<ObaStopsForLocationResponse>,
            MapWatcher.Listener {
    private static final String TAG = "StopMapController";
    private static final int STOPS_LOADER = 5677;

    private final FragmentCallback mFragment;

    private MapWatcher mMapWatcher;
    // Cache the map center -- if we don't have one when we resume,
    // we want to get the user's location.
    private GeoPoint mMapCenter;

    StopMapController(FragmentCallback callback) {
        mFragment = callback;
    }

    @Override
    public void initialize(Bundle savedInstanceState) {
    }

    @Override
    public void destroy() {
        watchMap(false);
    }

    @Override
    public void onPause() {
        mMapCenter = mFragment.getMapView().getMapCenter();
        // Stop watching the map
        watchMap(false);
    }

    @Override
    public void onResume() {
        watchMap(true);
        // If we are resuming and we don't have a previous center,
        // we want to get the user's location.
        GeoPoint prevCenter = mMapCenter;
        if (prevCenter != null) {
            MapController mapCtrl = mFragment.getMapView().getController();
            mapCtrl.setCenter(prevCenter);
        } else {
            mFragment.setMyLocation();
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
        // TODO Auto-generated method stub

    }

    @Override
    public Loader<ObaStopsForLocationResponse> onCreateLoader(int id, Bundle args) {
        return new StopsLoader(mFragment.getActivity(), mFragment.getMapView());
    }

    @Override
    public void onLoadFinished(Loader<ObaStopsForLocationResponse> loader,
            ObaStopsForLocationResponse response) {
        Log.d(TAG, "Load finished!");

        if (response.getCode() != ObaApi.OBA_OK) {
            // TODO: Make a toast or something when we fail.
            //Log.d(TAG, String.format("setOverlays: unexpected response = %d",
            //       response.getCode()));
            return;
        }

        if (response.getOutOfRange()) {
            mFragment.notifyOutOfRange();
            return;
        }

        List<ObaStop> stops = Arrays.asList(response.getStops());
        mFragment.showStops(stops, response);

        ((FragmentMapActivity)mFragment.getActivity()).setProgressBarIndeterminateVisibility(Boolean.FALSE);
    }

    @Override
    public void onLoaderReset(Loader<ObaStopsForLocationResponse> loader) {
        // Clear the overlay.
        mFragment.showStops(null, null);
    }

    //
    // Loading
    //
    private StopsLoader getLoader() {
        Loader<ObaStopsForLocationResponse> l =
                mFragment.getLoaderManager().getLoader(STOPS_LOADER);
        return (StopsLoader)l;
    }

    private void refresh() {
        // First we need to check to see if the current request we have can handle this.
        // Otherwise, we need to restart the loader with the new request.
        StopsLoader loader = getLoader();
        if (loader == null || !loader.canFulfill(mFragment.getMapView())) {
            Log.d(TAG,  "Refreshing stops");
            ((FragmentMapActivity)mFragment.getActivity()).setProgressBarIndeterminateVisibility(Boolean.TRUE);
            mFragment.getLoaderManager().restartLoader(STOPS_LOADER, null, this);
        }
    }

    //
    // Loader
    //
    private static class StopsLoader extends AsyncTaskLoader<ObaStopsForLocationResponse> {
        private final GeoPoint mCenter;
        private final int mLatSpan;
        private final int mLonSpan;
        private final int mZoomLevel;

        private ObaStopsForLocationResponse mResponse;

        public StopsLoader(Context context, MapView view) {
            super(context);
            mCenter = view.getMapCenter();
            mLatSpan = view.getLatitudeSpan();
            mLonSpan = view.getLongitudeSpan();
            mZoomLevel = view.getZoomLevel();
        }

        @Override
        public ObaStopsForLocationResponse loadInBackground() {
            return new ObaStopsForLocationRequest.Builder(getContext(), mCenter)
                    .setSpan(mLatSpan, mLonSpan)
                    .build()
                    .call();
        }

        @Override
        public void deliverResult(ObaStopsForLocationResponse data) {
            mResponse = data;
            super.deliverResult(data);
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        public boolean canFulfill(MapView view) {
            GeoPoint newCenter = view.getMapCenter();
            int newZoom = view.getZoomLevel();

            // This is the old logic, we can do better:
            if (!mCenter.equals(newCenter)) {
                return false;
            }
            if (mResponse != null) {
                if ((newZoom > mZoomLevel) &&
                        mResponse.getLimitExceeded()) {
                    return false;
                }
                else if (newZoom < mZoomLevel) {
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

    //
    // Map watcher
    //
    private void watchMap(boolean watch) {
        if (watch) {
            if (mMapWatcher == null)
                mMapWatcher = new MapWatcher(mFragment.getMapView(), this);
            mMapWatcher.start();
        } else {
            if (mMapWatcher != null)
                mMapWatcher.stop();
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
        //Log.d(TAG, "Map center changed: " + mMapCenter);
        refresh();
    }
}
