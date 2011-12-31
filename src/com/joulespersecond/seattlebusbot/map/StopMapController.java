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
import com.joulespersecond.seattlebusbot.R;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;


class StopMapController implements MapFragmentController,
            LoaderManager.LoaderCallbacks<ObaStopsForLocationResponse>,
            MapWatcher.Listener {
    private static final String TAG = "StopMapController";
    private static final int STOPS_LOADER = 5678;

    private final FragmentCallback mFragment;

    private MapWatcher mMapWatcher;

    StopMapController(FragmentCallback callback) {
        mFragment = callback;
        mFragment.getLoaderManager().initLoader(STOPS_LOADER, null, this);
    }

    @Override
    public void setState(Bundle args) {
        if (args != null) {
            GeoPoint center = null;
            int mapZoom = args.getInt(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);

            double lat = args.getDouble(MapParams.CENTER_LAT);
            double lon = args.getDouble(MapParams.CENTER_LON);
            if (lat != 0.0 && lon != 0.0) {
                center = ObaApi.makeGeoPoint(lat, lon);
            }
            MapController mapCtrl = mFragment.getMapView().getController();
            mapCtrl.setZoom(mapZoom);
            if (center != null) {
                mapCtrl.setCenter(center);
                onLocation();
            } else {
                mFragment.setMyLocation();
            }
        }
    }

    @Override
    public String getMode() {
        return MapParams.MODE_STOP;
    }

    @Override
    public void destroy() {
        mFragment.getLoaderManager().destroyLoader(STOPS_LOADER);
        watchMap(false);
    }

    @Override
    public void onPause() {
        watchMap(false);
    }

    @Override
    public void onResume() {
        watchMap(true);
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
    public Loader<ObaStopsForLocationResponse> onCreateLoader(int id, Bundle args) {
        StopsLoader loader = new StopsLoader(mFragment);
        loader.update(mFragment.getMapView());
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<ObaStopsForLocationResponse> loader,
            ObaStopsForLocationResponse response) {
        Log.d(TAG, "Load finished!");

        if (response.getCode() != ObaApi.OBA_OK) {
            Activity act = mFragment.getActivity();
            Toast.makeText(act,
                    act.getString(R.string.main_stop_errors),
                    Toast.LENGTH_LONG);
            return;
        }

        if (response.getOutOfRange()) {
            mFragment.notifyOutOfRange();
            return;
        }

        List<ObaStop> stops = Arrays.asList(response.getStops());
        mFragment.showStops(stops, response);
        mFragment.showProgress(false);
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
        if (loader != null) {
            loader.update(mFragment.getMapView());
        }
    }

    //
    // Loader
    //
    private static class StopsLoader extends AsyncTaskLoader<ObaStopsForLocationResponse> {

        private final FragmentCallback mFragment;
        private GeoPoint mCenter;
        private int mLatSpan;
        private int mLonSpan;
        private int mZoomLevel;

        private ObaStopsForLocationResponse mResponse;

        public StopsLoader(FragmentCallback fragment) {
            super(fragment.getActivity());
            mFragment = fragment;
        }

        @Override
        public synchronized ObaStopsForLocationResponse loadInBackground() {
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
            if (takeContentChanged()) {
                forceLoad();
            }
        }

        @Override
        public void onForceLoad() {
            mFragment.showProgress(true);
            super.onForceLoad();
        }

        public synchronized void update(MapView view) {
            GeoPoint newCenter = view.getMapCenter();
            int newLatSpan = view.getLatitudeSpan();
            int newLonSpan = view.getLongitudeSpan();
            int newZoom = view.getZoomLevel();

            if (!canFulfill(newCenter, newZoom)) {
                mCenter = newCenter;
                mLatSpan = newLatSpan;
                mLonSpan = newLonSpan;
                mZoomLevel = newZoom;
                onContentChanged();
            }
        }

        private boolean canFulfill(GeoPoint newCenter, int newZoom) {
            // This is the old logic, we can do better:
            if (mResponse == null) {
                Log.d(TAG, "No response");
                return false;
            }
            if (mCenter == null) {
                Log.d(TAG, "No center");
                return false;
            }
            if (!mCenter.equals(newCenter)) {
                Log.d(TAG, "Center not the same");
                return false;
            }
            if (mResponse != null) {
                if ((newZoom > mZoomLevel) &&
                        mResponse.getLimitExceeded()) {
                    Log.d(TAG, "Zooming in -- limit exceeded");
                    return false;
                }
                else if (newZoom < mZoomLevel) {
                    Log.d(TAG, "Zooming out");
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
