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
package com.joulespersecond.seattlebusbot.map.googlemapsv2;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockMapFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.region.ObaRegionsTask;
import com.joulespersecond.oba.request.ObaResponse;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.map.MapModeController;
import com.joulespersecond.seattlebusbot.map.MapParams;
import com.joulespersecond.seattlebusbot.map.RouteMapController;
import com.joulespersecond.seattlebusbot.map.StopMapController;
import com.joulespersecond.seattlebusbot.util.UIHelp;

import java.util.ArrayList;
import java.util.List;

import static com.joulespersecond.seattlebusbot.map.MapModeController.Callback;


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
 * @author paulw
 */
public class BaseMapFragment extends SherlockMapFragment
        implements Callback, ObaRegionsTask.Callback, MapModeController.ObaMapView,
        LocationSource, LocationListener, GoogleMap.OnCameraChangeListener,
        GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener,
        StopOverlay.OnFocusChangedListener {
    private static final String TAG = "BaseMapFragment";

    private static final int NOLOCATION_DIALOG = 103;

    private static final int OUTOFRANGE_DIALOG = 104;

    private static final int REQUEST_NO_LOCATION = 41;

    //
    // Location Services and Maps API v2 constants
    //
    private static final int CAMERA_MOVE_NOTIFY_THRESHOLD_MS = 500;

    public static final float CAMERA_DEFAULT_ZOOM = 16.0f;

    private static final int MILLISECONDS_PER_SECOND = 1000;

    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;

    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    private GoogleMap mMap;

    private UIHelp.StopUserInfoMap mStopUserMap;

    private String mFocusStopId;
    private ObaStop mFocusStop;

    // The Fragment controls the stop overlay, since that
    // is used by both modes.
    private StopOverlay mStopOverlay;

    StopPopup mStopPopup;

    // We only display the out of range dialog once
    private boolean mWarnOutOfRange = true;

    private boolean mRunning = false;

    private MapModeController mController;

    private ArrayList<Polyline> mLineOverlay = new ArrayList<Polyline>();

    // We have to convert from LatLng to Location, so hold references to both
    private LatLng mCenter;
    private Location mCenterLocation;

    /**
     * Google Location Services
     */
    protected LocationClient mLocationClient;
    private OnLocationChangedListener mListener;
    LocationRequest mLocationRequest;
    // Use a single location instance over all BaseMapFragments
    static Location mLastLocation;

    // Listen to map tap events
    OnFocusChangedListener mOnFocusChangedListener;

    public interface OnFocusChangedListener {
        /**
         * Called when a stop on the map is clicked (i.e., tapped), which sets focus to a stop,
         * or when the user taps on an area away from the map for the first time after a stop
         * is already selected, which removes focus
         *
         * @param stop the ObaStop that obtained focus, or null if no stop is in focus
         */
        void onFocusChanged(ObaStop stop);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        mMap = getMap();

        if (MapHelpV2.isGoogleMapsInstalled(getActivity())) {
            if (mMap != null) {
                UiSettings uiSettings = mMap.getUiSettings();
                // Show the location on the map
                mMap.setMyLocationEnabled(true);
                // Set location source
                mMap.setLocationSource(this);
                // Listener for camera changes
                mMap.setOnCameraChangeListener(this);
                // Hide MyLocation button on map, since we have the action bar
                uiSettings.setMyLocationButtonEnabled(false);
                // Hide Zoom controls
                uiSettings.setZoomControlsEnabled(false);
            }
        } else {
            MapHelpV2.promptUserInstallGoogleMaps(getActivity());
        }

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create();
        // Use high accuracy
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 5 seconds
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        // Set the fastest update interval to 1 second
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Initialize the StopPopup (hidden)
//        mStopPopup = new StopPopup(this, v.getRootView().findViewById(R.id.stop_info));

        if (savedInstanceState != null) {
            initMap(savedInstanceState);
        } else {
            Bundle args = getActivity().getIntent().getExtras();
            // The rest of this code assumes a bundle exists,
            // even if it's empty
            if (args == null) {
                args = new Bundle();
            }
            initMap(args);
        }

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity()) == ConnectionResult.SUCCESS) {
            mLocationClient = new LocationClient(getActivity(), this, this);
            mLocationClient.connect();
        }

        return v;
    }

    private void initMap(Bundle args) {
        mFocusStopId = args.getString(MapParams.STOP_ID);

        String mode = args.getString(MapParams.MODE);
        if (mode == null) {
            mode = MapParams.MODE_STOP;
        }
        setMapMode(mode, args);
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy: " + mStopUserMap);
//        if (mStopUserMap != null) {
//            mStopUserMap.close();
//            mStopUserMap = null;
//            mStopPopup.setStopUserMap(null);
//        }
        if (mController != null) {
            mController.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (mController != null) {
            mController.onPause();
        }

        mRunning = false;
        super.onPause();
    }

    @Override
    public void onResume() {
        mRunning = true;

        //Log.d(TAG, "onResume: " + mStopUserMap);
//        if (mStopUserMap == null) {
//            mStopUserMap = new UIHelp.StopUserInfoMap(getActivity());
//        } else {
//            mStopUserMap.requery();
//        }
//        mStopPopup.setStopUserMap(mStopUserMap);

        if (mController != null) {
            mController.onResume();
        }

        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make sure LocationClient is connected, if available
        if (mLocationClient != null && !mLocationClient.isConnected()) {
            mLocationClient.connect();
        }
    }

    @Override
    public void onStop() {
        // Tear down LocationClient
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
            mLocationClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mController != null) {
            mController.onSaveInstanceState(outState);
        }
        outState.putString(MapParams.MODE, getMapMode());
        outState.putString(MapParams.STOP_ID, mFocusStopId);
        Location center = getMapCenterAsLocation();
        outState.putDouble(MapParams.CENTER_LAT, center.getLatitude());
        outState.putDouble(MapParams.CENTER_LON, center.getLongitude());
        outState.putFloat(MapParams.ZOOM, getZoomLevelAsFloat());
    }

    public boolean isRouteDisplayed() {
        return (mController != null) &&
                MapParams.MODE_ROUTE.equals(mController.getMode());
    }

//    @Override
//    protected Dialog onCreateDialog(int id) {
//        switch (id) {
//            case NOLOCATION_DIALOG:
//                return createNoLocationDialog();
//
//            case OUTOFRANGE_DIALOG:
//                return createOutOfRangeDialog();
//        }
//        return null;
//    }

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
            mStopOverlay.clear();
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

    @Override
    public void showProgress(boolean show) {
//        setSupportProgressBarIndeterminateVisibility(show);
    }

    @Override
    public void showStops(List<ObaStop> stops, ObaReferences refs) {
        // Maintain focus through this step.
        // If we can't maintain focus through this step, then we
        // have to hide the stop popup
        String focusedId = mFocusStopId;
        ObaStop focusedStop = mFocusStop;

        if (mStopOverlay != null) {
            focusedStop = mStopOverlay.getFocus();
            if (focusedStop != null) {
                focusedId = focusedStop.getId();
            }
        }

        if (mStopOverlay == null) {
            mStopOverlay = new StopOverlay(getActivity(), mMap);
        }

        if (stops != null) {
            mStopOverlay.setOnFocusChangeListener(this);
//            mStopPopup.setReferences(refs);
//
//            if (focusedId != null) {
//                if (!mStopOverlay.setFocusById(focusedId)) {
//                    mStopPopup.hide();
//                }
//            }
            mStopOverlay.setStops(stops);
        }
    }

    // Apparently you can't show a dialog from within OnLoadFinished?
    final Handler mShowOutOfRangeHandler = new Handler();

    @Override
    @SuppressWarnings("deprecation")
    public void notifyOutOfRange() {
        //Before we trigger the out of range warning, make sure we have region info
        //or have a API URL that was custom set by the user in via Preferences
        //Otherwise, its premature since we don't know the device's relationship to
        //available OBA regions or the manually set API region
        String serverName = Application.get().getCustomApiUrl();
        if (mWarnOutOfRange && (Application.get().getCurrentRegion() != null || !TextUtils
                .isEmpty(serverName))) {
            if (mRunning) {
                getActivity().showDialog(OUTOFRANGE_DIALOG);
            }
        }
    }

    //
    // Region Task Callback
    //
    @Override
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        if (currentRegionChanged) {
            // Move map view after a new region has been selected
            setMyLocation(false, false);
        }

        // If region changed and was auto-selected, show user what region we're using
        if (currentRegionChanged
                && Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                && Application.get().getCurrentRegion() != null) {
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

    public void onFocusChanged(final ObaStop stop) {
        // Run in a separate thread, to avoid blocking UI for long running events
        mStopChangedHandler.post(new Runnable() {
            public void run() {
                if (stop != null) {
                    mFocusStop = stop;
                    mFocusStopId = stop.getId();
                    //Log.d(TAG, "Focused changed to " + stop.getName());
                    //mStopPopup.show(stop);
                } else {
                    //Log.d(TAG, "Removed focus");
                    //mStopPopup.hide();
                }

                // Pass overlay focus event up to listeners for this fragment
                mOnFocusChangedListener.onFocusChanged(stop);
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setMyLocation(boolean useDefaultZoom, boolean animateToLocation) {

        if (mLastLocation == null) {
            //showDialog(NOLOCATION_DIALOG);
            return;
        }

        setMyLocation(mLastLocation, useDefaultZoom, animateToLocation);
    }

    private void setMyLocation(Location l, boolean useDefaultZoom, boolean animateToLocation) {
        if (mMap != null && mLastLocation != null) {
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

    //
    // Dialogs
    //
//    @SuppressWarnings("deprecation")
//    private Dialog createNoLocationDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle(R.string.main_nolocation_title);
//        builder.setIcon(android.R.drawable.ic_dialog_map);
//        builder.setMessage(R.string.main_nolocation);
//        builder.setPositiveButton(android.R.string.yes,
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        startActivityForResult(
//                                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
//                                REQUEST_NO_LOCATION);
//                        dismissDialog(NOLOCATION_DIALOG);
//                    }
//                }
//        );
//        builder.setNegativeButton(android.R.string.no,
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        // Ok, I suppose we can just try looking from where we
//                        // are.
//                        mController.onLocation();
//                        dismissDialog(NOLOCATION_DIALOG);
//                    }
//                }
//        );
//        return builder.create();
//    }

//    @SuppressWarnings("deprecation")
//    private Dialog createOutOfRangeDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this)
//                .setTitle(R.string.main_outofrange_title)
//                .setIcon(android.R.drawable.ic_dialog_map)
//                .setMessage(getString(R.string.main_outofrange,
//                        Application.get().getCurrentRegion() != null ?
//                                Application.get().getCurrentRegion().getName() : ""
//                ))
//                .setPositiveButton(R.string.main_outofrange_yes,
//                        new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                zoomToRegion();
//                                dismissDialog(OUTOFRANGE_DIALOG);
//                            }
//                        }
//                )
//                .setNegativeButton(R.string.main_outofrange_no,
//                        new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                dismissDialog(OUTOFRANGE_DIALOG);
//                                mWarnOutOfRange = false;
//                            }
//                        }
//                );
//        return builder.create();
//    }

    void zoomToRegion() {
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
    public static void showMapError(Context context, ObaResponse response) {
        Toast.makeText(context,
                context.getString(UIHelp.getMapErrorString(context, response.getCode())),
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

    @Override
    public void setMapCenter(Location location, boolean animateToLocation, boolean overlayExpanded) {
        if (mMap != null) {
            CameraPosition cp = mMap.getCameraPosition();

            LatLng target = MapHelpV2.makeLatLng(location);
            LatLng offsetTarget;

            if (overlayExpanded) {
                // Adjust camera target based on MapModeController.OVERLAY_PERCENTAGE
                // so it appears in the center of the visible map
                double bias = (getLongitudeSpanInDecDegrees() * MapModeController.OVERLAY_PERCENTAGE) / 2;
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

            for (ObaShape s : shapes) {
                lineOptions = new PolylineOptions();
                lineOptions.color(lineOverlayColor);

                for (Location l : s.getPoints()) {
                    lineOptions.add(MapHelpV2.makeLatLng(l));
                }
                // Add the line to the map, and keep a reference in the ArrayList
                mLineOverlay.add(mMap.addPolyline(lineOptions));
            }
        }
    }

    @Override
    public void zoomToRoute() {
        if (mMap != null) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Polyline p : mLineOverlay) {
                for (LatLng l : p.getPoints()) {
                    builder.include(l);
                }
            }

            int padding = 0;
            mMap.animateCamera((CameraUpdateFactory.newLatLngBounds(builder.build(), padding)));
        }
    }

    @Override
    public void removeRouteOverlay() {
        for (Polyline p : mLineOverlay) {
            p.remove();
        }

        mLineOverlay.clear();
    }

    @Override
    public boolean canWatchMapChanges() {
        // Android Map API v2 has an OnCameraChangeListener
        return true;
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        Log.d(TAG, "onCameraChange");
        if (mController != null) {
            mController.notifyMapChanged();
        }
    }

    /*
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle dataBundle) {
        Log.d(TAG, "Location Services connected");
        // Request location updates
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Location Services connection failed");
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
        Log.d(TAG, "Location Services disconnected");
    }

    /**
     * Maps V2 Location updates
     */
    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
    }

    /**
     * Maps V2 Location updates
     */
    @Override
    public void deactivate() {
        mListener = null;
    }

    public void onLocationChanged(Location l) {
        boolean firstFix = false;
        if (mLastLocation == null) {
            // mLastLocation is static, so this is a first fix across all BaseMapFragments
            firstFix = true;
        }
        mLastLocation = l;
        if (mListener != null) {
            // Show real-time location on map
            mListener.onLocationChanged(l);
        }

        if (firstFix) {
            // If its our first location (across all BaseMapFragments), move the camera to it
            setMyLocation(true, false);
        }
    }

    @Override
    public void postInvalidate() {
        // Do nothing - calling `this.postInvalidate()` causes a StackOverflowError
    }
}
