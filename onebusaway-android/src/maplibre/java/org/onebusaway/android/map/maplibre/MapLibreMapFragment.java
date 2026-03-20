/*
 * Copyright (C) 2011-2024 Paul Watts (paulcwatts@gmail.com),
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
package org.onebusaway.android.map.maplibre;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.maps.SupportMapFragment;
import org.maplibre.android.maps.UiSettings;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.PlausibleAnalytics;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaShape;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.map.DirectionsMapController;
import org.onebusaway.android.map.LayerInfo;
import org.onebusaway.android.map.MapModeController;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.MapUtils;
import org.onebusaway.android.map.ObaMapFragment;
import org.onebusaway.android.map.ObaMapFragment.OnFocusChangedListener;
import org.onebusaway.android.map.ObaMapFragment.OnLocationPermissionResultListener;
import org.onebusaway.android.map.ObaMapFragment.OnProgressBarChangedListener;
import org.onebusaway.android.map.RouteMapController;
import org.onebusaway.android.map.StopMapController;
import org.onebusaway.android.map.bike.BikeshareMapController;
import org.onebusaway.android.region.ObaRegionsTask;
import org.onebusaway.android.ui.HomeActivity;
import org.onebusaway.android.ui.LayersSpeedDialAdapter;
import org.onebusaway.android.ui.weather.RegionCallback;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PermissionUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;

import static org.onebusaway.android.util.PermissionUtils.LOCATION_PERMISSIONS;
import static org.onebusaway.android.util.PermissionUtils.LOCATION_PERMISSION_REQUEST;
import static org.onebusaway.android.util.UIUtils.canManageDialog;

/**
 * MapLibre-based map fragment implementing the same contract as the Google Maps BaseMapFragment.
 * Supports stop mode, route mode, and directions mode.
 */
public class MapLibreMapFragment extends SupportMapFragment
        implements ObaMapFragment, MapModeController.Callback, ObaRegionsTask.Callback,
        MapModeController.ObaMapView,
        LocationHelper.Listener,
        StopOverlay.OnFocusChangedListener, OnMapReadyCallback,
        LayersSpeedDialAdapter.LayerActivationListener {

    private static final String TAG = "MapLibreMapFragment";

    private static final String STYLE_URL_LIGHT = "https://tiles.openfreemap.org/styles/liberty";
    private static final String STYLE_URL_DARK = "https://tiles.openfreemap.org/styles/dark";

    private static final int REQUEST_NO_LOCATION = 41;

    public static final float CAMERA_DEFAULT_ZOOM = 16.0f;

    public static final float DEFAULT_MAP_PADDING_DP = 20.0f;

    private int mMapPaddingLeft = 0;
    private int mMapPaddingTop = 0;
    private int mMapPaddingRight = 0;
    private int mMapPaddingBottom = 0;

    private MapLibreMap mMap;

    private String mFocusStopId;

    private StopOverlay mStopOverlay;

    private boolean mWarnOutOfRange = true;
    private boolean mRunning = false;

    private List<MapModeController> mControllers;
    private String mMapMode = "";

    private SimpleMarkerOverlay mSimpleMarkerOverlay;

    private LatLng mCenter;
    private Location mCenterLocation;

    OnFocusChangedListener mOnFocusChangedListener;
    OnProgressBarChangedListener mOnProgressBarChangedListener;
    OnLocationPermissionResultListener mOnLocationPermissionResultListener;

    LocationHelper mLocationHelper;
    Bundle mLastSavedInstanceState;

    private boolean mUserDeniedPermission = false;

    private AlertDialog locationPermissionDialog;

    private RegionCallback regionCallback;

    // ============================================================================================
    // LayerActivationListener
    // ============================================================================================

    @Override
    public void onActivateLayer(LayerInfo layer) {
        switch (layer.getLayerlabel()) {
            case "Bikeshare": {
                for (MapModeController controller : mControllers) {
                    if (controller instanceof BikeshareMapController) {
                        ((BikeshareMapController) controller).showBikes(true);
                        ObaAnalytics.reportUiEvent(null,
                                Application.get().getPlausibleInstance(),
                                PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                                getString(R.string.analytics_layer_bikeshare),
                                getString(R.string.analytics_label_bikeshare_activated));
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onDeactivateLayer(LayerInfo layer) {
        switch (layer.getLayerlabel()) {
            case "Bikeshare": {
                for (MapModeController controller : mControllers) {
                    if (controller instanceof BikeshareMapController) {
                        ((BikeshareMapController) controller).showBikes(false);
                        ObaAnalytics.reportUiEvent(null,
                                Application.get().getPlausibleInstance(),
                                PlausibleAnalytics.REPORT_MAP_EVENT_URL,
                                getString(R.string.analytics_layer_bikeshare),
                                getString(R.string.analytics_label_bikeshare_deactivated));
                    }
                }
                break;
            }
        }
    }

    // ============================================================================================
    // Fragment lifecycle
    // ============================================================================================

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Initialize MapLibre before any map usage
        MapLibre.getInstance(requireContext());

        View v = super.onCreateView(inflater, container, savedInstanceState);

        mUserDeniedPermission = PreferenceUtils.userDeniedLocationPermission();
        mLocationHelper = new LocationHelper(getActivity());

        mLastSavedInstanceState = savedInstanceState;
        getMapAsync(this);

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

    private boolean inDarkMode() {
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES || (
                AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO &&
                        (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                                == Configuration.UI_MODE_NIGHT_YES
        );
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        mMap = map;

        String styleUrl = inDarkMode() ? STYLE_URL_DARK : STYLE_URL_LIGHT;
        mMap.setStyle(new Style.Builder().fromUri(styleUrl), style -> {
            initMap(mLastSavedInstanceState);

            // Setup location component after style is loaded
            if (!mUserDeniedPermission) {
                setupLocationComponent(style);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void setupLocationComponent(Style style) {
        Activity activity = getActivity();
        if (activity == null || mMap == null) {
            return;
        }
        if (PermissionUtils.hasGrantedAtLeastOnePermission(activity, LOCATION_PERMISSIONS)) {
            LocationComponent locationComponent = mMap.getLocationComponent();
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(activity, style).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.NONE);
            locationComponent.setRenderMode(RenderMode.COMPASS);
            mLocationHelper.registerListener(this);
        } else {
            showLocationPermissionDialog();
        }
    }

    private void initMap(Bundle savedInstanceState) {
        UiSettings uiSettings = mMap.getUiSettings();
        mUserDeniedPermission = PreferenceUtils.userDeniedLocationPermission();

        // Listen for camera changes
        mMap.addOnCameraMoveListener(() -> {
            Log.d(TAG, "onCameraMove");
            if (mControllers != null) {
                for (MapModeController controller : mControllers) {
                    controller.notifyMapChanged();
                }
            }
        });

        // Listen for map/marker clicks
        mMap.addOnMapClickListener(point -> {
            if (mStopOverlay != null) {
                mStopOverlay.removeMarkerClicked(point);
            }
            return false;
        });

        mMap.setOnMarkerClickListener(marker -> {
            if (mStopOverlay != null) {
                if (mStopOverlay.markerClicked(marker)) {
                    return true;
                }
            }
            return false;
        });

        // Hide the default compass (we rely on the app's own controls)
        uiSettings.setCompassEnabled(false);

        mSimpleMarkerOverlay = new SimpleMarkerOverlay(mMap);

        if (savedInstanceState != null) {
            initMapState(savedInstanceState);
        } else {
            Bundle args = null;
            if (getActivity() != null) {
                args = getActivity().getIntent().getExtras();
            }
            if (args == null) {
                args = new Bundle();
            }
            double lat = args.getDouble(MapParams.CENTER_LAT, 0.0d);
            double lon = args.getDouble(MapParams.CENTER_LON, 0.0d);
            if (lat == 0.0d && lon == 0.0d) {
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
        mMapPaddingBottom = args.getInt(MapParams.MAP_PADDING_BOTTOM, MapParams.DEFAULT_MAP_PADDING);
        setPadding(mMapPaddingLeft, mMapPaddingTop, mMapPaddingRight, mMapPaddingBottom);

        String mode = args.getString(MapParams.MODE);
        if (mode == null) {
            mode = MapParams.MODE_STOP;
        }
        setMapMode(mode, args);
    }

    @Override
    public void onDestroy() {
        if (mLocationHelper != null) {
            mLocationHelper.unregisterListener(this);
        }
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.destroy();
            }
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (mLocationHelper != null) {
            mLocationHelper.onPause();
        }
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onPause();
            }
        }

        Location center = getMapCenterAsLocation();
        if (center != null) {
            PreferenceUtils.saveMapViewToPreferences(center.getLatitude(), center.getLongitude(),
                    getZoomLevelAsFloat());
        }

        mRunning = false;
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onHidden(hidden);
            }
        }
        super.onHiddenChanged(hidden);
    }

    @Override
    public void onResume() {
        mUserDeniedPermission = PreferenceUtils.userDeniedLocationPermission();
        if (mLocationHelper != null) {
            mLocationHelper.onResume();
        }
        mRunning = true;

        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onResume();
                controller.notifyMapChanged();
            }
        }
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onSaveInstanceState(outState);
            }
        }
        outState.putString(MapParams.MODE, getMapMode());
        outState.putString(MapParams.STOP_ID, mFocusStopId);
        Location center = getMapCenterAsLocation();
        if (mMap != null && center != null) {
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
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onViewStateRestored(savedInstanceState);
            }
        }
        super.onViewStateRestored(savedInstanceState);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        int result = PackageManager.PERMISSION_DENIED;
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mUserDeniedPermission = false;
                // Enable location display
                if (mMap != null) {
                    Style style = mMap.getStyle();
                    if (style != null) {
                        setupLocationComponent(style);
                    }
                }
                result = PackageManager.PERMISSION_GRANTED;
            } else {
                mUserDeniedPermission = true;
            }
        } else if (HomeActivity.BATTERY_OPTIMIZATIONS_PERMISSION_REQUEST == requestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                UIUtils.openBatteryIgnoreIntent(getActivity());
            }
        }

        if (requestCode == LOCATION_PERMISSION_REQUEST && mOnLocationPermissionResultListener != null) {
            mOnLocationPermissionResultListener.onLocationPermissionResult(result);
        }
    }

    // ============================================================================================
    // ObaMapFragment interface
    // ============================================================================================

    @Override
    public void zoomIn() {
        if (mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.zoomIn());
        }
    }

    @Override
    public void zoomOut() {
        if (mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.zoomOut());
        }
    }

    @Override
    public void setOnFocusChangeListener(OnFocusChangedListener listener) {
        mOnFocusChangedListener = listener;
    }

    @Override
    public void setOnProgressBarChangedListener(OnProgressBarChangedListener listener) {
        mOnProgressBarChangedListener = listener;
    }

    @Override
    public void setOnLocationPermissionResultListener(OnLocationPermissionResultListener listener) {
        mOnLocationPermissionResultListener = listener;
    }

    @Override
    public void setRegionCallback(RegionCallback callback) {
        this.regionCallback = callback;
    }

    @Override
    public MapModeController.ObaMapView getMapView() {
        return this;
    }

    // ============================================================================================
    // MapModeController.Callback
    // ============================================================================================

    @Override
    public String getMapMode() {
        if (!"".equals(mMapMode)) {
            return mMapMode;
        }
        return null;
    }

    @Override
    public void setMapMode(String mode, Bundle args) {
        String oldMode = getMapMode();
        if (oldMode != null && oldMode.equals(mode)) {
            for (MapModeController controller : mControllers) {
                controller.setState(args);
            }
            return;
        }
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.destroy();
            }
            mControllers.clear();
        } else {
            mControllers = new ArrayList<>();
        }
        if (mStopOverlay != null) {
            mStopOverlay.clear(false);
        }
        BikeshareMapController bikeshareMapController = new BikeshareMapController(this);
        if (MapParams.MODE_ROUTE.equals(mode)) {
            RouteMapController controller = new RouteMapController(this);
            mControllers.add(controller);
            bikeshareMapController.setMode(controller.getMode());
        } else if (MapParams.MODE_STOP.equals(mode)) {
            StopMapController controller = new StopMapController(this);
            mControllers.add(controller);
            bikeshareMapController.setMode(controller.getMode());
        } else if (MapParams.MODE_DIRECTIONS.equals(mode)) {
            DirectionsMapController controller = new DirectionsMapController(this);
            mControllers.add(controller);
            bikeshareMapController.setMode(controller.getMode());
        }
        mControllers.add(bikeshareMapController);
        for (MapModeController controller : mControllers) {
            controller.setState(args);
            controller.onResume();
        }
        mMapMode = mode;
    }

    public boolean isRouteDisplayed() {
        return MapParams.MODE_ROUTE.equals(mMapMode);
    }

    @Override
    public void showProgress(boolean show) {
        if (mOnProgressBarChangedListener != null) {
            mOnProgressBarChangedListener.onProgressBarChanged(show);
        }
    }

    @Override
    public void showStops(List<ObaStop> stops, ObaReferences refs) {
        if (setupStopOverlay() && stops != null) {
            mStopOverlay.populateStops(stops, refs);
            checkRegionWeather(false);
        }
    }

    @Override
    public void showBikeStations(List<BikeRentalStation> bikeStations) {
        // Stub for milestone 1 — BikeStationOverlay not yet ported
        Log.w(TAG, "showBikeStations() is not yet implemented for MapLibre");
    }

    @Override
    public void clearBikeStations() {
        // Stub for milestone 1
    }

    @Override
    public void notifyOutOfRange() {
        String serverName = Application.get().getCustomApiUrl();
        if (mWarnOutOfRange && (Application.get().getCurrentRegion() != null
                || !TextUtils.isEmpty(serverName))) {
            if (mRunning && canManageDialog(getActivity())) {
                showDialog(MapDialogFragment.OUTOFRANGE_DIALOG);
            }
        }
        checkRegionWeather(true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean setMyLocation(boolean useDefaultZoom, boolean animateToLocation) {
        if (!LocationUtils.isLocationEnabled(getActivity()) && mRunning && UIUtils.canManageDialog(
                getActivity())) {
            SharedPreferences prefs = Application.getPrefs();
            if (!prefs.getBoolean(getString(R.string.preference_key_never_show_location_dialog), false)) {
                showDialog(MapDialogFragment.NOLOCATION_DIALOG);
            }
            return false;
        }

        Location lastLocation = Application.getLastKnownLocation(getActivity(),
                mLocationHelper != null ? mLocationHelper.getGoogleApiClient() : null);
        if (lastLocation == null) {
            if (!PermissionUtils.hasGrantedAtLeastOnePermission(Application.get(), LOCATION_PERMISSIONS)) {
                if (!PreferenceUtils.userDeniedLocationPermission()) {
                    if (!mUserDeniedPermission) {
                        showLocationPermissionDialog();
                    }
                }
            } else {
                Toast.makeText(getActivity(),
                        getResources().getString(R.string.main_waiting_for_location),
                        Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        setMyLocation(lastLocation, useDefaultZoom, animateToLocation);
        return true;
    }

    private void setMyLocation(Location l, boolean useDefaultZoom, boolean animateToLocation) {
        if (mMap != null) {
            CameraPosition.Builder cameraPosition = new CameraPosition.Builder()
                    .target(MapHelpMapLibre.makeLatLng(l));

            if (useDefaultZoom) {
                cameraPosition.zoom(CAMERA_DEFAULT_ZOOM);
            } else {
                cameraPosition.zoom(mMap.getCameraPosition().zoom);
            }

            if (animateToLocation) {
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition.build()));
            } else {
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition.build()));
            }
        }

        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onLocation();
            }
        }
    }

    @Override
    public void zoomToRegion() {
        ObaRegion region = Application.get().getCurrentRegion();

        if (region != null && mMap != null) {
            LatLngBounds b = MapHelpMapLibre.getRegionBounds(region);
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int padding = 0;
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(b, padding));
        }
    }

    @Override
    public Location getSouthWest() {
        if (mMap != null) {
            LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            Location southWest = new Location("");
            southWest.setLatitude(bounds.getLatSouth());
            southWest.setLongitude(bounds.getLonWest());
            return southWest;
        }
        return null;
    }

    @Override
    public Location getNorthEast() {
        if (mMap != null) {
            LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            Location northEast = new Location("");
            northEast.setLatitude(bounds.getLatNorth());
            northEast.setLongitude(bounds.getLonEast());
            return northEast;
        }
        return null;
    }

    // ============================================================================================
    // ObaRegionsTask.Callback
    // ============================================================================================

    @Override
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        if (!isAdded()) {
            return;
        }

        Location l = Application
                .getLastKnownLocation(getActivity(),
                        mLocationHelper != null ? mLocationHelper.getGoogleApiClient() : null);
        Location mapCenter = getMapCenterAsLocation();
        if (currentRegionChanged &&
                (l == null ||
                        (mapCenter != null && mapCenter.getLatitude() == 0.0 &&
                                mapCenter.getLongitude() == 0.0))) {
            if (l != null) {
                setMyLocation(true, false);
            } else {
                zoomToRegion();
                checkRegionWeather(false);
            }
        }
    }

    // ============================================================================================
    // ObaMapView implementation
    // ============================================================================================

    @Override
    public void setZoom(float zoomLevel) {
        if (mMap != null) {
            mMap.moveCamera(CameraUpdateFactory.zoomTo(zoomLevel));
        }
    }

    @Override
    public Location getMapCenterAsLocation() {
        if (mMap != null) {
            LatLng center = mMap.getCameraPosition().target;
            if (mCenter == null || !mCenter.equals(center)) {
                mCenter = center;
                mCenterLocation = MapHelpMapLibre.makeLocation(mCenter);
            }
        }
        return mCenterLocation;
    }

    @Override
    public void setMapCenter(Location location, boolean animateToLocation,
                             boolean overlayExpanded) {
        if (mMap != null) {
            CameraPosition cp = mMap.getCameraPosition();

            LatLng target = MapHelpMapLibre.makeLatLng(location);

            if (isRouteDisplayed() && overlayExpanded) {
                double percentageOffset = 0.2;
                double bias = (getLongitudeSpanInDecDegrees() * percentageOffset) / 2;
                target = new LatLng(target.getLatitude() - bias, target.getLongitude());
            }

            CameraPosition newPos = new CameraPosition.Builder()
                    .target(target)
                    .zoom(cp.zoom)
                    .bearing(cp.bearing)
                    .tilt(cp.tilt)
                    .build();

            if (animateToLocation) {
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(newPos));
            } else {
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(newPos));
            }
        }
    }

    @Override
    public double getLatitudeSpanInDecDegrees() {
        if (mMap != null) {
            LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            return Math.abs(bounds.getLatNorth() - bounds.getLatSouth());
        }
        return 0;
    }

    @Override
    public double getLongitudeSpanInDecDegrees() {
        if (mMap != null) {
            LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
            return Math.abs(bounds.getLonEast() - bounds.getLonWest());
        }
        return 0;
    }

    @Override
    public float getZoomLevelAsFloat() {
        if (mMap != null) {
            return (float) mMap.getCameraPosition().zoom;
        }
        return 0;
    }

    @Override
    public void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes, boolean clear) {
        // Stub for milestone 1 — route polyline rendering not yet ported
        Log.w(TAG, "setRouteOverlay() is not yet implemented for MapLibre");
    }

    @Override
    public void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes) {
        setRouteOverlay(lineOverlayColor, shapes, true);
    }

    @Override
    public void updateVehicles(HashSet<String> routeIds, ObaTripsForRouteResponse response) {
        // Stub for milestone 1 — VehicleOverlay not yet ported
        Log.w(TAG, "updateVehicles() is not yet implemented for MapLibre");
    }

    @Override
    public void removeVehicleOverlay() {
        // Stub for milestone 1
    }

    @Override
    public void zoomToRoute() {
        // Stub for milestone 1
        Log.w(TAG, "zoomToRoute() is not yet implemented for MapLibre");
    }

    @Override
    public void zoomToItinerary() {
        // Stub for milestone 1
        Log.w(TAG, "zoomToItinerary() is not yet implemented for MapLibre");
    }

    @Override
    public void zoomIncludeClosestVehicle(HashSet<String> routeIds,
                                          ObaTripsForRouteResponse response) {
        // Stub for milestone 1
        Log.w(TAG, "zoomIncludeClosestVehicle() is not yet implemented for MapLibre");
    }

    @Override
    public void removeRouteOverlay() {
        // Stub for milestone 1
    }

    @Override
    public void removeStopOverlay(boolean clearFocusedStop) {
        if (mStopOverlay != null) {
            mStopOverlay.clear(clearFocusedStop);
        }
    }

    @Override
    public boolean canWatchMapChanges() {
        return true;
    }

    @Override
    public void setFocusStop(ObaStop stop, List<ObaRoute> routes) {
        if (setupStopOverlay()) {
            mStopOverlay.setFocus(stop, routes);
        }
    }

    @Override
    public int addMarker(Location location, Float hue) {
        if (mSimpleMarkerOverlay == null) {
            return -1;
        }
        return mSimpleMarkerOverlay.addMarker(location, hue);
    }

    @Override
    public void removeMarker(int markerId) {
        if (mSimpleMarkerOverlay == null) {
            return;
        }
        mSimpleMarkerOverlay.removeMarker(markerId);
    }

    @Override
    public void setPadding(Integer left, Integer top, Integer right, Integer bottom) {
        if (left != null) mMapPaddingLeft = left;
        if (top != null) mMapPaddingTop = top;
        if (right != null) mMapPaddingRight = right;
        if (bottom != null) mMapPaddingBottom = bottom;

        // MapLibre doesn't have a direct setPadding() on the map object.
        // Padding is applied through camera updates and content insets.
    }

    @Override
    public void postInvalidate() {
        // No-op for MapLibre
    }

    // ============================================================================================
    // Stop overlay management
    // ============================================================================================

    public boolean setupStopOverlay() {
        if (mStopOverlay != null) {
            return true;
        }
        if (mMap == null) {
            return false;
        }
        mStopOverlay = new StopOverlay(getActivity(), mMap);
        mStopOverlay.setOnFocusChangeListener(this);
        return true;
    }

    // ============================================================================================
    // StopOverlay.OnFocusChangedListener
    // ============================================================================================

    final Handler mStopChangedHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onFocusChanged(final ObaStop stop, final HashMap<String, ObaRoute> routes,
                               final Location location) {
        mStopChangedHandler.post(() -> {
            if (stop != null) {
                mFocusStopId = stop.getId();
            } else {
                mFocusStopId = null;
            }

            if (mOnFocusChangedListener != null) {
                mOnFocusChangedListener.onFocusChanged(stop, routes, location);
            }
        });
    }

    // ============================================================================================
    // LocationHelper.Listener
    // ============================================================================================

    @Override
    public void onLocationChanged(Location l) {
        if (mControllers != null) {
            for (MapModeController controller : mControllers) {
                controller.onLocation();
            }
        }
    }

    // ============================================================================================
    // Weather / region helpers
    // ============================================================================================

    public void checkRegionWeather(boolean isOutOfRange) {
        ObaRegion region = Application.get().getCurrentRegion();
        boolean isValid = (region != null && mMap != null && !isOutOfRange);
        if (regionCallback != null) {
            regionCallback.onValidRegion(isValid);
        }
    }

    // ============================================================================================
    // Dialogs
    // ============================================================================================

    protected void showDialog(int id) {
        MapDialogFragment.newInstance(id)
                .show(getChildFragmentManager(), MapDialogFragment.TAG);
    }

    public void showLocationPermissionDialog() {
        if (!canManageDialog(getActivity())) {
            return;
        }
        if (locationPermissionDialog != null && locationPermissionDialog.isShowing()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.location_permissions_title)
                .setMessage(R.string.location_permissions_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> {
                            PreferenceUtils.setUserDeniedLocationPermissions(false);
                            requestPermissions(LOCATION_PERMISSIONS, LOCATION_PERMISSION_REQUEST);
                        }
                )
                .setNegativeButton(R.string.no_thanks,
                        (dialog, which) -> {
                            if (mOnLocationPermissionResultListener != null) {
                                mUserDeniedPermission = true;
                                PreferenceUtils.setUserDeniedLocationPermissions(true);
                                mOnLocationPermissionResultListener.onLocationPermissionResult(
                                        PackageManager.PERMISSION_DENIED);
                            }
                        }
                );
        locationPermissionDialog = builder.create();
        locationPermissionDialog.show();
    }

    public static class MapDialogFragment extends DialogFragment {

        private static final String TAG = "MapDialogFragment";

        static final int NOLOCATION_DIALOG = 103;
        static final int OUTOFRANGE_DIALOG = 104;

        private static final String DIALOG_TYPE_KEY = "dialog_type";

        static MapDialogFragment newInstance(int dialogType) {
            MapDialogFragment f = new MapDialogFragment();
            Bundle args = new Bundle();
            args.putInt(DIALOG_TYPE_KEY, dialogType);
            f.setArguments(args);
            f.setCancelable(false);
            return f;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int dialogType = getArguments().getInt(DIALOG_TYPE_KEY);
            switch (dialogType) {
                case NOLOCATION_DIALOG:
                    return createNoLocationDialog();
                case OUTOFRANGE_DIALOG:
                    return createOutOfRangeDialog();
                default:
                    throw new IllegalArgumentException("Invalid dialog type: " + dialogType);
            }
        }

        @SuppressWarnings("deprecation")
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
                            (dialog, which) -> {
                                MapLibreMapFragment mapFragment =
                                        (MapLibreMapFragment) getParentFragment();
                                if (mapFragment != null && mapFragment.isAdded()) {
                                    mapFragment.zoomToRegion();
                                    mapFragment.checkRegionWeather(false);
                                }
                            }
                    )
                    .setNegativeButton(R.string.main_outofrange_no,
                            (dialog, which) -> {
                                MapLibreMapFragment mapFragment =
                                        (MapLibreMapFragment) getParentFragment();
                                if (mapFragment != null && mapFragment.isAdded()) {
                                    mapFragment.mWarnOutOfRange = false;
                                }
                            }
                    );
            return builder.create();
        }

        @SuppressWarnings("deprecation")
        private Dialog createNoLocationDialog() {
            View view = getActivity().getLayoutInflater().inflate(R.layout.no_location_dialog, null);
            CheckBox neverShowDialog = view.findViewById(R.id.location_never_ask_again);

            TextView noLocationText = view.findViewById(R.id.no_location_text);
            noLocationText.setText(getString(R.string.main_nolocation,
                    getString(R.string.app_name)));

            neverShowDialog.setOnCheckedChangeListener((compoundButton, isChecked) -> {
                PreferenceUtils.saveBoolean(
                        getString(R.string.preference_key_never_show_location_dialog), isChecked);
            });

            Drawable icon = getResources().getDrawable(android.R.drawable.ic_dialog_map);
            DrawableCompat.setTint(icon, getResources().getColor(R.color.theme_primary));

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.main_nolocation_title)
                    .setIcon(icon)
                    .setCancelable(false)
                    .setView(view)
                    .setPositiveButton(R.string.rt_yes,
                            (dialog, which) -> startActivityForResult(
                                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                    REQUEST_NO_LOCATION)
                    )
                    .setNegativeButton(R.string.rt_no,
                            (dialog, which) -> {
                                MapLibreMapFragment mapFragment =
                                        (MapLibreMapFragment) getParentFragment();
                                if (mapFragment != null && mapFragment.isAdded()
                                        && mapFragment.mControllers != null) {
                                    for (MapModeController controller : mapFragment.mControllers) {
                                        controller.onLocation();
                                    }
                                }
                            }
                    );
            return builder.create();
        }
    }
}
