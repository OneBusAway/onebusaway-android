/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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

package com.joulespersecond.seattlebusbot;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.joulespersecond.oba.ObaApi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import java.io.File;

public class MapViewActivity extends MapActivity {
    private static final String TAG = "MapViewActivity";


    private static final String FOCUS_STOP_ID = ".FocusStopId";
    private static final String CENTER_LAT = ".CenterLat";
    private static final String CENTER_LON = ".CenterLon";
    private static final String MAP_ZOOM = ".MapZoom";
    // Switches to 'route mode' -- stops aren't updated on move
    private static final String ROUTE_ID = ".RouteId";
    private static final String SHOW_ROUTES = ".ShowRoutes";

    MapView mMapView;

    // Values that are initialized by either the intent extras
    // or by the frozen state.
    private String mFocusStopId;
    private GeoPoint mMapCenter;
    private int mMapZoom = 16; // initial zoom
    private boolean mShowRoutes;

    private static final int REQUEST_SEARCH_RESULT = 42;

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context
     *            The context of the activity.
     * @param focusId
     *            The stop to focus.
     * @param lat
     *            The latitude of the map center.
     * @param lon
     *            The longitude of the map center.
     */
    public static final void start(Context context,
            String focusId,
            double lat,
            double lon) {
        context.startActivity(makeIntent(context, focusId, lat, lon));
    }

    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     *
     * @param context
     *            The context of the activity.
     * @param routeId
     *            The route to show.
     */
    public static final void start(Context context, String routeId) {
        context.startActivity(makeIntent(context, routeId));
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context
     *            The context of the activity.
     * @param focusId
     *            The stop to focus.
     * @param lat
     *            The latitude of the map center.
     * @param lon
     *            The longitude of the map center.
     */
    public static final Intent makeIntent(Context context,
            String focusId,
            double lat,
            double lon) {
        Intent myIntent = new Intent(context, MapViewActivity.class);
        myIntent.putExtra(FOCUS_STOP_ID, focusId);
        myIntent.putExtra(CENTER_LAT, lat);
        myIntent.putExtra(CENTER_LON, lon);
        return myIntent;
    }

    /**
     * Returns an intent that starts the MapActivity in "RouteMode", which shows
     * stops along a route, and does not get new stops when the user pans the
     * map.
     *
     * @param context
     *            The context of the activity.
     * @param routeId
     *            The route to show.
     */
    public static final Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, MapViewActivity.class);
        myIntent.putExtra(ROUTE_ID, routeId);
        return myIntent;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        boolean firstRun = firstRunCheck();
        super.onCreate(savedInstanceState);
        //
        // Static initialization (what should always be there, regardless of
        // intent)
        //
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        mMapView = (MapView)findViewById(R.id.mapview);

        // Set up everything we can from the intent --
        // all of the UI is set-up/torn down in onResume/onPause
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mFocusStopId = bundle.getString(FOCUS_STOP_ID);
            mapValuesFromBundle(bundle);
            //mRouteOverlay.setRouteId(bundle.getString(ROUTE_ID), true);
        }
        if (savedInstanceState != null) {
            mFocusStopId = savedInstanceState.getString(FOCUS_STOP_ID);
            mShowRoutes = savedInstanceState.getBoolean(SHOW_ROUTES);
            mapValuesFromBundle(savedInstanceState);
            //mRouteOverlay.setRouteId(savedInstanceState.getString(ROUTE_ID),
            //        false);
        }

        //mStopsController
        //        .setNonConfigurationInstance(getLastNonConfigurationInstance());

        // stop dropping new users in Tulsa (or users who do Manage app -> Clear data)
        if (firstRun) {
            firstRunSetLocation(mMapView.getController());
        }
    }

    @Override
    public void onDestroy() {
        //mStopsController.cancel();
        //mStopsController = null;
        super.onDestroy();
    }

    @Override
    public void onResume() {
        //
        // This is where we initialize all the UI elements.
        // They are torn down in onPause to save memory.
        //

        MapController mapCtrl = mMapView.getController();
        // First, if we have a previous center and zoom,
        // we want to reset the map to that.
        GeoPoint prevCenter = mMapCenter;
        if (prevCenter != null) {
            mapCtrl.setCenter(prevCenter);
        }
        if (mMapZoom != mMapView.getZoomLevel()) {
            mapCtrl.setZoom(mMapZoom);
        }

        // If we have previous stops, then we want to use those.

        // Otherwise, we want to make a new request to get some.
        // UNLESS we don't have a previous center, in which case
        // this is the first time we've started up, and in that
        // case we want to go to the user's current fix.
        /*
        ObaResponse response = mStopsController.getResponse();
        if (response != null) {
            setOverlays(response);
            if (mStopOverlay != null && mFocusStopId != null) {
                showRoutes(null, mShowRoutes);
            }
        }
        */

        /*
        if (prevCenter == null && !mRouteOverlay.isRouteMode()) {
            setMyLocation();
        } else {
            getStops();
        }
        watchMap(true);
        */

        super.onResume();
    }

    @Override
    public void onPause() {

        mMapCenter = mMapView.getMapCenter();
        mMapZoom = mMapView.getZoomLevel();
        //Log.d(TAG, "PAUSE: Saving center: " + mMapCenter);

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // The only thing we really need to save it the focused stop ID.
        outState.putString(FOCUS_STOP_ID, mFocusStopId);
        //outState.putString(ROUTE_ID, mRouteOverlay.getRouteId());
        outState.putBoolean(SHOW_ROUTES, mShowRoutes);
        GeoPoint center = mMapView.getMapCenter();
        outState.putDouble(CENTER_LAT, center.getLatitudeE6() / 1E6);
        outState.putDouble(CENTER_LON, center.getLongitudeE6() / 1E6);
        outState.putInt(MAP_ZOOM, mMapView.getZoomLevel());
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        //return mStopsController.onRetainNonConfigurationInstance();
        return null;
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "******** LOW MEMORY ******** ");
        ObaApi.clearCache();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
        case REQUEST_SEARCH_RESULT:
            if (resultCode == RESULT_OK) {
                String routeId = data.getStringExtra(MyTabActivityBase.RESULT_ROUTE_ID);
                if (routeId != null) {
                    //mRouteOverlay.setRouteId(routeId, true);
                    //getStops();
                }
            }
        }
    }

    private void mapValuesFromBundle(Bundle bundle) {
        double lat = bundle.getDouble(CENTER_LAT);
        double lon = bundle.getDouble(CENTER_LON);
        if (lat != 0.0 && lon != 0.0) {
            mMapCenter = ObaApi.makeGeoPoint(lat, lon);
        }
        mMapZoom = bundle.getInt(MAP_ZOOM, mMapZoom);
    }

    @Override
    public boolean onSearchRequested() {
        GeoPoint location = mMapCenter;
        // show popup to search for routes near current map center
        if (mMapView != null) {
            location = mMapView.getMapCenter();
        }
        Intent myIntent = new Intent(this, MyRoutesActivity.class);
        // Start on the search tab
        myIntent.putExtra(MyTabActivityBase.EXTRA_SEARCHMODE, true);

        myIntent.setData(MyTabActivityBase.getDefaultTabUri(
                MySearchRoutesActivity.TAB_NAME));
        MyTabActivityBase.putSearchCenter(myIntent,  location);

        startActivityForResult(myIntent, REQUEST_SEARCH_RESULT);
        return true;
    }

    /**
     * Returns true if no files in private directory
     * (MapView or MapActivity caches prefs and tiles)
     * This will fail if MapViewActivty never got to onPause
     */
    private boolean firstRunCheck() {
        File dir = getFilesDir();
        return (dir.list().length == 0);
    }

    /**
     * Center on Seattle with a region-level zoom, should
     * give first-time users better first impression
     */
    private void firstRunSetLocation(MapController controller) {
        mMapZoom = 11;
        controller.setCenter(new GeoPoint(47605990, -122331780));
        controller.setZoom(mMapZoom);
    }
}
