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
package org.onebusaway.android.map.googlemapsv2;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaShape;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.map.MapModeController;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.RouteMapController;
import org.onebusaway.android.map.StopMapController;
import org.onebusaway.android.region.ObaRegionsTask;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The MapFragment class is split into two basic modes:
 * stop mode and route mode. It needs to be able to switch
 * between the two.
 * <p/>
 * So this class handles the common functionality between
 * the two modes: zooming, the options menu,
 * saving/restoring state, and other minor bookkeeping
 * (the stop user map).
 * <p/>
 * Everything else is handed off to a specific
 * MapFragmentController instance.
 *
 * @author paulw, barbeau
 */
public class BaseMapFragment extends SupportMapFragment
        implements MapModeController.Callback, ObaRegionsTask.Callback,
        MapModeController.ObaMapView,
        LocationSource, LocationHelper.Listener,
        com.google.android.gms.maps.GoogleMap.OnCameraChangeListener,
        StopOverlay.OnFocusChangedListener, OnMapReadyCallback,
        VehicleOverlay.Controller {

    public static final String TAG = "BaseMapFragment";

    private static final int REQUEST_NO_LOCATION = 41;

    //
    // Location Services and Maps API v2 constants
    //
    public static final float CAMERA_DEFAULT_ZOOM = 16.0f;

    public static final float DEFAULT_MAP_PADDING_DP = 20.0f;

    // Keep track of current map padding
    private int mMapPaddingLeft = 0;

    private int mMapPaddingTop = 0;

    private int mMapPaddingRight = 0;

    private int mMapPaddingBottom = 0;

    // Use fully-qualified class name to avoid import statement, because it interferes with scripted
    // copying of Maps API v2 classes between Google/Amazon build flavors (see #254)
    private com.google.android.gms.maps.GoogleMap mMap;

    private String mFocusStopId;

    // The Fragment controls the stop overlay, since that
    // is used by both modes.
    private StopOverlay mStopOverlay;

    private VehicleOverlay mVehicleOverlay;

    // We only display the out of range dialog once
    private boolean mWarnOutOfRange = true;

    private boolean mRunning = false;

    private MapModeController mController;

    private ArrayList<Polyline> mLineOverlay = new ArrayList<Polyline>();

    // Markers that are added to the map by classes external to this map package
    private SimpleMarkerOverlay mSimpleMarkerOverlay;

    // We have to convert from LatLng to Location, so hold references to both
    private LatLng mCenter;

    private Location mCenterLocation;

    private OnLocationChangedListener mListener;

    // Listen to map tap events
    OnFocusChangedListener mOnFocusChangedListener;

    LocationHelper mLocationHelper;

    Bundle mLastSavedInstanceState;

    public interface OnFocusChangedListener {

        /**
         * Called when a stop on the map is clicked (i.e., tapped), which sets focus to a stop,
         * or when the user taps on an area away from the map for the first time after a stop
         * is already selected, which removes focus
         *
         * @param stop     the ObaStop that obtained focus, or null if no stop is in focus
         * @param routes   a HashMap of all route display names that serve this stop - key is
         *                 routeId
         * @param location the user touch location on the map
         */
        void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location);
    }

    public static BaseMapFragment newInstance() {
        return new BaseMapFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        mLocationHelper = new LocationHelper(getActivity());
        mLocationHelper.registerListener(this);

        mMap = getMap();

        if (MapHelpV2.isMapsInstalled(getActivity())) {
            if (mMap != null) {
                initMap(savedInstanceState);
            } else {
                // Save the savedInstanceState
                mLastSavedInstanceState = savedInstanceState;
                // Register for an async callback when the map is ready
                getMapAsync(this);
            }
        } else {
            MapHelpV2.promptUserInstallMaps(getActivity());
        }

        // If we have a recent location, show this while we're waiting on the LocationHelper
        Location l = Application
                .getLastKnownLocation(getActivity(), mLocationHelper.getGoogleApiClient());
        if (l != null) {
            final long TIME_THRESHOLD = TimeUnit.MINUTES.toMillis(5);
            if (System.currentTimeMillis() - l.getTime() < TIME_THRESHOLD) {
                onLocationChanged(l);
            }
        }

        return v;
    }

    @Override
    public void onMapReady(com.google.android.gms.maps.GoogleMap map) {
        initMap(mLastSavedInstanceState);
    }

    private void initMap(Bundle savedInstanceState) {
        UiSettings uiSettings = mMap.getUiSettings();
        // Show the location on the map
        mMap.setMyLocationEnabled(true);
        // Set location source
        mMap.setLocationSource(this);
        // Listener for camera changes
        mMap.setOnCameraChangeListener(this);
        // Hide MyLocation button on map, since we have our own button
        uiSettings.setMyLocationButtonEnabled(false);
        // Hide Zoom controls
        uiSettings.setZoomControlsEnabled(false);
        // Hide Toolbar
        uiSettings.setMapToolbarEnabled(false);
        // Instantiate class that holds generic markers to be added by outside classes
        mSimpleMarkerOverlay = new SimpleMarkerOverlay(mMap);

        if (savedInstanceState != null) {
            initMapState(savedInstanceState);
        } else {
            Bundle args = getActivity().getIntent().getExtras();
            // The rest of this code assumes a bundle exists, even if it's empty
            if (args == null) {
                args = new Bundle();
            }
            double lat = args.getDouble(MapParams.CENTER_LAT, 0.0d);
            double lon = args.getDouble(MapParams.CENTER_LON, 0.0d);
            if (lat == 0.0d && lon == 0.0d) {
                // Try to restore the latest map view location
                PreferenceUtils.maybeRestoreMapViewToBundle(args);
            }
            initMapState(args);
        }
    }

    private void initMapState(Bundle args) {
        mFocusStopId = args.getString(MapParams.STOP_ID);

        mMapPaddingLeft = args.getInt(MapParams.MAP_PADDING_LEFT, MapParams.DEFAULT_MAP_PADDING);
        mMapPaddingTop = args.getInt(MapParams.MAP_PADDING_TOP, MapParams.DEFAULT_MAP_PADDING);
        mMapPaddingRight = args.getInt(MapParams.MAP_PADDING_RIGHT, MapParams.DEFAULT_MAP_PADDING);
        mMapPaddingBottom = args
                .getInt(MapParams.MAP_PADDING_BOTTOM, MapParams.DEFAULT_MAP_PADDING);
        setPadding(mMapPaddingLeft, mMapPaddingTop, mMapPaddingRight, mMapPaddingBottom);

        String mode = args.getString(MapParams.MODE);
        if (mode == null) {
            mode = MapParams.MODE_STOP;
        }
        setMapMode(mode, args);
    }

    @Override
    public void onDestroy() {
        if (mController != null) {
            mController.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (mLocationHelper != null) {
            mLocationHelper.onPause();
        }
        if (mController != null) {
            mController.onPause();
        }

        Location center = getMapCenterAsLocation();
        if (center != null) {
            PreferenceUtils.saveMapViewToPreferences(center.getLatitude(), center.getLongitude(),
                    getZoomLevelAsFloat());
        }

        mRunning = false;
        super.onPause();
    }

    /**
     * This is called when fm.beginTransaction().hide() or fm.beginTransaction().show() is called
     *
     * @param hidden True if the fragment is now hidden, false if it is not visible.
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        if (mController != null) {
            mController.onHidden(hidden);
        }
        super.onHiddenChanged(hidden);
    }

    @Override
    public void onResume() {
        mLocationHelper.onResume();
        mRunning = true;

        if (mController != null) {
            mController.onResume();
        }

        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mController != null) {
            mController.onSaveInstanceState(outState);
        }
        outState.putString(MapParams.MODE, getMapMode());
        outState.putString(MapParams.STOP_ID, mFocusStopId);
        Location center = getMapCenterAsLocation();
        if (mMap != null) {
            outState.putDouble(MapParams.CENTER_LAT, center.getLatitude());
            outState.putDouble(MapParams.CENTER_LON, center.getLongitude());
            outState.putFloat(MapParams.ZOOM, getZoomLevelAsFloat());
        }
        outState.putInt(MapParams.MAP_PADDING_LEFT, mMapPaddingLeft);
        outState.putInt(MapParams.MAP_PADDING_TOP, mMapPaddingTop);
        outState.putInt(MapParams.MAP_PADDING_RIGHT, mMapPaddingRight);
        outState.putInt(MapParams.MAP_PADDING_BOTTOM, mMapPaddingBottom);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        mController.onViewStateRestored(savedInstanceState);
        super.onViewStateRestored(savedInstanceState);
    }

    public boolean isRouteDisplayed() {
        return (mController != null) &&
                MapParams.MODE_ROUTE.equals(mController.getMode());
    }

    public void setupStopOverlay() {
        if (mStopOverlay == null) {
            mStopOverlay = new StopOverlay(getActivity(), mMap);
            mStopOverlay.setOnFocusChangeListener(this);
        }
    }

    public void setupVehicleOverlay() {
        Activity a = getActivity();
        if (mVehicleOverlay == null && a != null) {
            mVehicleOverlay = new VehicleOverlay(a, mMap);
            mVehicleOverlay.setController(this);
        }
    }

    protected void showDialog(int id) {
        MapDialogFragment.newInstance(id, this).show(getFragmentManager(), MapDialogFragment.TAG);
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        switch (requestCode) {
//            case REQUEST_NO_LOCATION:
//                // Clear the map center so we can get the user's location again
//                setMyLocation();
//                break;
//        }
//    }

    //
    // Fragment Controller
    //
    @Override
    public String getMapMode() {
        if (mController != null) {
            return mController.getMode();
        }
        return null;
    }

    @Override
    public void setMapMode(String mode, Bundle args) {
        String oldMode = getMapMode();
        if (oldMode != null && oldMode.equals(mode)) {
            mController.setState(args);
            return;
        }
        if (mController != null) {
            mController.destroy();
        }
        if (mStopOverlay != null) {
            mStopOverlay.clear(false);
        }
        if (MapParams.MODE_ROUTE.equals(mode)) {
            mController = new RouteMapController(this);
        } else if (MapParams.MODE_STOP.equals(mode)) {
            mController = new StopMapController(this);
        }
        mController.setState(args);
        mController.onResume();
    }

    @Override
    public MapModeController.ObaMapView getMapView() {
        // We implement the ObaMapView interface too (since we're using MapFragment)
        return this;
    }

    /**
     * Adds a generic marker to the map and returns the ID associated with that marker, which can
     * be used to remove the marker via removeMarker()
     *
     * @param location Location at which the marker should be added
     * @return the ID associated with the marker that was just added
     */
    @Override
    public int addMarker(Location location) {
        return mSimpleMarkerOverlay.addMarker(location);
    }

    /**
     * Removes the marker from the map that has the given ID, which was previously generated by
     * addMarker() in this class
     *
     * @param markerId the ID for the marker that should be removed from the map
     */
    @Override
    public void removeMarker(int markerId) {
        mSimpleMarkerOverlay.removeMarker(markerId);
    }

    /**
     * Define a visible region on the map, to signal to the map that portions of the map around
     * the edges may be obscured, by setting padding on each of the four edges of the map.
     *
     * @param left   the number of pixels of padding to be added on the left of the map, or null
     *               if the existing padding should be used
     * @param top    the number of pixels of padding to be added on the top of the map, or null
     *               if the existing padding should be used
     * @param right  the number of pixels of padding to be added on the right of the map, or null
     *               if the existing padding should be used
     * @param bottom the number of pixels of padding to be added on the bottom of the map, or null
     *               if the existing padding should be used
     */
    @Override
    public void setPadding(Integer left, Integer top, Integer right, Integer bottom) {
        if (left != null) {
            mMapPaddingLeft = left;
        }
        if (top != null) {
            mMapPaddingTop = top;
        }
        if (right != null) {
            mMapPaddingRight = right;
        }
        if (bottom != null) {
            mMapPaddingBottom = bottom;
        }

        if (mMap != null) {
            mMap.setPadding(mMapPaddingLeft, mMapPaddingTop, mMapPaddingRight, mMapPaddingBottom);
        }
    }

    @Override
    public void showProgress(boolean show) {
//        setSupportProgressBarIndeterminateVisibility(show);
    }

    @Override
    public void showStops(List<ObaStop> stops, ObaReferences refs) {
        // Make sure that the stop overlay has been initialized
        setupStopOverlay();

        if (stops != null) {
            mStopOverlay.populateStops(stops, refs);
        }
    }

    @Override
    public void notifyOutOfRange() {
        //Before we trigger the out of range warning, make sure we have region info
        //or have a API URL that was custom set by the user in via Preferences
        //Otherwise, its premature since we don't know the device's relationship to
        //available OBA regions or the manually set API region
        String serverName = Application.get().getCustomApiUrl();
        if (mWarnOutOfRange && (Application.get().getCurrentRegion() != null || !TextUtils
                .isEmpty(serverName))) {
            if (mRunning && UIUtils.canManageDialog(getActivity())) {
                showDialog(MapDialogFragment.OUTOFRANGE_DIALOG);
            }
        }
    }

    //
    // Region Task Callback
    //
    @Override
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        if (!isAdded()) {
            // Too early or late in the Fragment lifecycle to take any action
            return;
        }

        Location l = Application
                .getLastKnownLocation(getActivity(), mLocationHelper.getGoogleApiClient());
        // If the region changed, and we don't have a location or the map center is still (0,0),
        // then zoom to the region
        if (currentRegionChanged &&
                (l == null ||
                        (getMapCenterAsLocation().getLatitude() == 0.0 &&
                                getMapCenterAsLocation().getLongitude() == 0.0))) {
            zoomToRegion();
        }

        // If region changed and was auto-selected, show user what region we're using
        if (currentRegionChanged
                && Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                && Application.get().getCurrentRegion() != null
                && mRunning
                && UIUtils.canManageDialog(getActivity())) {
            Toast.makeText(getActivity(),
                    getString(R.string.region_region_found,
                            Application.get().getCurrentRegion().getName()),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    public void setOnFocusChangeListener(OnFocusChangedListener onFocusChangedListener) {
        mOnFocusChangedListener = onFocusChangedListener;
    }

    //
    // Stop changed handler
    //
    final Handler mStopChangedHandler = new Handler();

    public void onFocusChanged(final ObaStop stop, final HashMap<String, ObaRoute> routes,
                               final Location location) {
        // Run in a separate thread, to avoid blocking UI for long running events
        mStopChangedHandler.post(new Runnable() {
            public void run() {
                if (stop != null) {
                    mFocusStopId = stop.getId();
                    //Log.d(TAG, "Focused changed to " + stop.getName());
                } else {
                    mFocusStopId = null;
                    //Log.d(TAG, "Removed focus");
                }

                // Pass overlay focus event up to listeners for this fragment
                if (mOnFocusChangedListener != null) {
                    mOnFocusChangedListener.onFocusChanged(stop, routes, location);
                }
            }
        });
    }

    /**
     * Sets the map view to the last available location
     *
     * @param useDefaultZoom    true if the CAMERA_DEFAULT_ZOOM should be used, false if the
     *                          current
     *                          zoom level should be kept
     * @param animateToLocation true if the map should animate the transition to the new view, or
     *                          false if it should snap to the new view without animation
     * @return true if there was a a location to set the map view to, false if there was not
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean setMyLocation(boolean useDefaultZoom, boolean animateToLocation) {
        if (!LocationUtils.isLocationEnabled(getActivity()) && mRunning && UIUtils.canManageDialog(
                getActivity())) {
            showDialog(MapDialogFragment.NOLOCATION_DIALOG);
            return false;
        }

        GoogleApiClient apiClient = null;
        if (mLocationHelper != null) {
            apiClient = mLocationHelper.getGoogleApiClient();
        }

        Location lastLocation = Application.getLastKnownLocation(getActivity(), apiClient);
        if (lastLocation == null) {
            Toast.makeText(getActivity(),
                    getResources()
                            .getString(R.string.main_waiting_for_location),
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        setMyLocation(lastLocation, useDefaultZoom, animateToLocation);
        return true;
    }

    private void setMyLocation(Location l, boolean useDefaultZoom, boolean animateToLocation) {
        if (mMap != null) {
            // Move camera to current location
            CameraPosition.Builder cameraPosition = new CameraPosition.Builder()
                    .target(MapHelpV2.makeLatLng(l));

            if (useDefaultZoom) {
                // Use default zoom level
                cameraPosition.zoom(CAMERA_DEFAULT_ZOOM);
            } else {
                // Use current zoom level
                cameraPosition.zoom(mMap.getCameraPosition().zoom);
            }

            if (animateToLocation) {
                // Smooth animation to position
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition.build()));
            } else {
                // Abrupt change to position
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition.build()));
            }
        }

        if (mController != null) {
            mController.onLocation();
        }
    }

    public void zoomToRegion() {
        // If we have a region, then zoom to it.
        ObaRegion region = Application.get().getCurrentRegion();

        if (region != null && mMap != null) {
            LatLngBounds b = MapHelpV2.getRegionBounds(region);
            int padding = 0;
            mMap.animateCamera((CameraUpdateFactory.newLatLngBounds(b, padding)));
        }
    }

    //
    // Error handlers
    //
    public static void showMapError(ObaResponse response) {
        Context context = Application.get().getApplicationContext();
        Toast.makeText(context,
                context.getString(UIUtils.getMapErrorString(context, response.getCode())),
                Toast.LENGTH_LONG).show();
    }

    //
    // MapView interactions
    //

    @Override
    public void setZoom(float zoomLevel) {
        if (mMap != null) {
            mMap.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel));
        }
    }

    @Override
    public Location getMapCenterAsLocation() {
        // If the center is the same as the last call to this method, pass back the same Location
        // object
        if (mMap != null) {
            LatLng center = mMap.getCameraPosition().target;
            if (mCenter == null || mCenter != center) {
                mCenter = center;
                mCenterLocation = MapHelpV2.makeLocation(mCenter);
            }
        }
        return mCenterLocation;
    }

    /**
     * Sets the map center to the given parameter
     *
     * @param location          location to center on
     * @param animateToLocation true if the map should animate to the location, false if it should
     *                          snap to it
     * @param overlayExpanded   true if the sliding panel is expanded, false if it is not
     */
    @Override
    public void setMapCenter(Location location, boolean animateToLocation,
                             boolean overlayExpanded) {
        if (mMap != null) {
            CameraPosition cp = mMap.getCameraPosition();

            LatLng target = MapHelpV2.makeLatLng(location);
            LatLng offsetTarget;

            if (isRouteDisplayed() && overlayExpanded) {
                // Adjust camera target if the route header is currently displayed - map padding
                // doesn't get this quite right, as the header is slid up some and full padding doesn't apply
                double percentageOffset = 0.2;
                double bias =
                        (getLongitudeSpanInDecDegrees() * percentageOffset) / 2;
                offsetTarget = new LatLng(target.latitude - bias, target.longitude);
                target = offsetTarget;
            }

            if (animateToLocation) {
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder().target(target)
                                .zoom(cp.zoom)
                                .bearing(cp.bearing)
                                .tilt(cp.tilt)
                                .build()
                ));
            } else {
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder().target(target)
                                .zoom(cp.zoom)
                                .bearing(cp.bearing)
                                .tilt(cp.tilt)
                                .build()
                ));
            }
        }
    }

    @Override
    public double getLatitudeSpanInDecDegrees() {
        VisibleRegion vr = mMap.getProjection().getVisibleRegion();
        return Math.abs(vr.latLngBounds.northeast.latitude - vr.latLngBounds.southwest.latitude);
    }

    @Override
    public double getLongitudeSpanInDecDegrees() {
        VisibleRegion vr = mMap.getProjection().getVisibleRegion();
        return Math.abs(vr.latLngBounds.northeast.longitude - vr.latLngBounds.southwest.longitude);
    }

    @Override
    public float getZoomLevelAsFloat() {
        return mMap.getCameraPosition().zoom;
    }

    @Override
    public void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes) {
        if (mMap != null) {
            mLineOverlay.clear();
            PolylineOptions lineOptions;

            int totalPoints = 0;

            for (ObaShape s : shapes) {
                lineOptions = new PolylineOptions();
                lineOptions.color(lineOverlayColor);

                for (Location l : s.getPoints()) {
                    lineOptions.add(MapHelpV2.makeLatLng(l));
                }
                // Add the line to the map, and keep a reference in the ArrayList
                mLineOverlay.add(mMap.addPolyline(lineOptions));

                totalPoints += lineOptions.getPoints().size();
            }

            Log.d(TAG, "Total points for route polylines = " + totalPoints);
        }
    }

    /**
     * Updates markers for the provided routeIds from the status info from the given
     * ObaTripsForRouteResponse
     *
     * @param routeIds markers representing real-time positions for the provided routeIds will be
     *                 added to the map
     * @param response response that contains the real-time status info
     */
    @Override
    public void updateVehicles(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
        setupVehicleOverlay();
        if (mVehicleOverlay != null) {
            mVehicleOverlay.updateVehicles(routeIds, response);
        }
    }

    @Override
    public void removeVehicleOverlay() {
        if (mVehicleOverlay != null) {
            mVehicleOverlay.clear();
        }
    }

    @Override
    public void zoomToRoute() {
        if (mMap != null) {
            if (!mLineOverlay.isEmpty()) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (Polyline p : mLineOverlay) {
                    for (LatLng l : p.getPoints()) {
                        builder.include(l);
                    }
                }

                Activity a = getActivity();
                if (a != null) {
                    int padding = UIUtils.dpToPixels(a, DEFAULT_MAP_PADDING_DP);
                    mMap.moveCamera(
                            (CameraUpdateFactory.newLatLngBounds(builder.build(), padding)));
                }
            } else {
                Toast.makeText(getActivity(), getString(R.string.route_info_no_shape_data),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Zoom to include the current map bounds plus the location of the nearest vehicle
     *
     * @param routeIds markers representing real-time positions for the provided routeIds will be
     *                 checked for proximity to the location (all other routes are ignored)
     * @param response trips-for-route API response, which includes real-time vehicle locations in
     *                 status
     */
    @Override
    public void zoomIncludeClosestVehicle(HashSet<String> routeIds,
                                          ObaTripsForRouteResponse response) {
        if (mMap == null) {
            return;
        }
        LatLng closestVehicleLocation = MapHelpV2
                .getClosestVehicle(response, routeIds, getMapCenterAsLocation());

        LatLngBounds visibleBounds = mMap.getProjection().getVisibleRegion().latLngBounds;

        if (closestVehicleLocation == null || visibleBounds.contains(closestVehicleLocation)) {
            // Closest vehicle is already in view or is null - don't change camera
            return;
        }

        // Zoom to include current map bounds and closest vehicle location
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(visibleBounds.northeast);
        builder.include(visibleBounds.southwest);
        builder.include(closestVehicleLocation);

        Activity a = getActivity();
        if (a != null) {
            int padding = UIUtils.dpToPixels(a, DEFAULT_MAP_PADDING_DP);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
        }
    }

    @Override
    public void removeRouteOverlay() {
        for (Polyline p : mLineOverlay) {
            p.remove();
        }

        mLineOverlay.clear();
    }

    /**
     * Clears any stop markers from the map
     *
     * @param clearFocusedStop true to clear the currently focused stop, false to leave it on map
     */
    @Override
    public void removeStopOverlay(boolean clearFocusedStop) {
        if (mStopOverlay != null) {
            mStopOverlay.clear(clearFocusedStop);
        }
    }

    @Override
    public boolean canWatchMapChanges() {
        // Android Map API v2 has an OnCameraChangeListener
        return true;
    }

    /**
     * Sets focus to a particular stop, or pass in null for the stop to clear the focus
     *
     * @param stop   ObaStop to focus on, or null to clear the focus
     * @param routes a list of all route display names that serve this stop, or null to clear the
     *               focus
     */
    @Override
    public void setFocusStop(ObaStop stop, List<ObaRoute> routes) {
        // Make sure that the stop overlay has been initialized
        setupStopOverlay();

        mStopOverlay.setFocus(stop, routes);
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        Log.d(TAG, "onCameraChange");
        if (mController != null) {
            mController.notifyMapChanged();
        }
    }

    // Maps V2 Location updates

    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void deactivate() {
        mListener = null;
    }

    public void onLocationChanged(Location l) {
        if (mListener != null) {
            // Show real-time location on map
            mListener.onLocationChanged(l);
        }
    }

    @Override
    public void postInvalidate() {
        // Do nothing - calling `this.postInvalidate()` causes a StackOverflowError
    }

    //
    // VehicleOverlay.Controller
    //
    @Override
    public String getFocusedStopId() {
        return mFocusStopId;
    }

    //
    // Dialogs
    //

    public static class MapDialogFragment extends android.support.v4.app.DialogFragment {

        private static final String TAG = "MapDialogFragment";

        int mDialogType;

        private static BaseMapFragment mMapFragment;

        private final static String DIALOG_TYPE_KEY = "dialog_type";

        private static final int NOLOCATION_DIALOG = 103;

        private static final int OUTOFRANGE_DIALOG = 104;

        /**
         * Creates a new dialog of type NOLOCATION_DIALOG or OUTOFRANGE_DIALOG
         *
         * @param dialogType NOLOCATION_DIALOG to create a no location dialog, or OUTOFRANGE_DIALOG
         *                   to create an out of range dialog
         * @return a fragment to show the dialog
         */
        static MapDialogFragment newInstance(int dialogType, BaseMapFragment fragment) {
            mMapFragment = fragment;
            MapDialogFragment f = new MapDialogFragment();

            // Provide dialog type as an argument.
            Bundle args = new Bundle();
            args.putInt(DIALOG_TYPE_KEY, dialogType);
            f.setArguments(args);
            f.setCancelable(false);

            return f;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mDialogType = getArguments().getInt(DIALOG_TYPE_KEY);

            switch (mDialogType) {
                case NOLOCATION_DIALOG:
                    return createNoLocationDialog();
                case OUTOFRANGE_DIALOG:
                    return createOutOfRangeDialog();
                default:
                    throw new IllegalArgumentException(
                            "Invalid map dialog type - " + DIALOG_TYPE_KEY);
            }
        }

        private Dialog createOutOfRangeDialog() {
            Drawable icon = getResources().getDrawable(android.R.drawable.ic_dialog_map);
            DrawableCompat.setTint(icon, getResources().getColor(R.color.theme_primary));

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.main_outofrange_title)
                    .setIcon(icon)
                    .setCancelable(false)
                    .setMessage(getString(R.string.main_outofrange,
                            Application.get().getCurrentRegion() != null ?
                                    Application.get().getCurrentRegion().getName() : ""
                    ))
                    .setPositiveButton(R.string.main_outofrange_yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mMapFragment.zoomToRegion();
                                }
                            }
                    )
                    .setNegativeButton(R.string.main_outofrange_no,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mMapFragment.mWarnOutOfRange = false;
                                }
                            }
                    );
            return builder.create();
        }

        @SuppressWarnings("deprecation")
        private Dialog createNoLocationDialog() {
            Drawable icon = getResources().getDrawable(android.R.drawable.ic_dialog_map);
            DrawableCompat.setTint(icon, getResources().getColor(R.color.theme_primary));

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.main_nolocation_title)
                    .setIcon(icon)
                    .setCancelable(false)
                    .setMessage(R.string.main_nolocation)
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivityForResult(
                                            new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                            REQUEST_NO_LOCATION);
                                }
                            }
                    )
                    .setNegativeButton(android.R.string.no,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Ok, I suppose we can just try looking from where we
                                    // are.
                                    mMapFragment.mController.onLocation();
                                }
                            }
                    );
            return builder.create();
        }
    }
}
