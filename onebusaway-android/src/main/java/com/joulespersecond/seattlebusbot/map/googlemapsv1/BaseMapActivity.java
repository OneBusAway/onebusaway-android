/*
 * Copyright (C) 2011-2013 Paul Watts (paulcwatts@gmail.com)
 * and individual contributors.
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
package com.joulespersecond.seattlebusbot.map.googlemapsv1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.actionbarsherlock.app.SherlockMapActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.ItemizedOverlay.OnFocusChangeListener;
import com.google.android.maps.MapController;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.region.ObaRegionsTask;
import com.joulespersecond.oba.region.RegionUtils;
import com.joulespersecond.oba.request.ObaResponse;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.map.MapModeController;
import com.joulespersecond.seattlebusbot.map.MapParams;
import com.joulespersecond.seattlebusbot.map.RouteMapController;
import com.joulespersecond.seattlebusbot.map.StopMapController;
import com.joulespersecond.seattlebusbot.map.googlemapsv1.StopOverlay.StopOverlayItem;
import com.joulespersecond.seattlebusbot.util.LocationHelp;
import com.joulespersecond.seattlebusbot.util.UIHelp;

import java.util.List;


/**
 * The MapFragment class is split into two basic modes:
 * stop mode and route mode. It needs to be able to switch
 * between the two.
 *
 * So this class handles the common functionality between
 * the two modes: zooming, the options menu,
 * saving/restoring state, and other minor bookkeeping
 * (the stop user map).
 *
 * Everything else is handed off to a specific
 * MapFragmentController instance.
 *
 * @author paulw
 */
abstract public class BaseMapActivity extends SherlockMapActivity
        implements MapModeController.Callback, ObaRegionsTask.Callback {
    //private static final String TAG = "BaseMapActivity";

    private static final int API_KEY = BuildConfig.DEBUG ? R.string.api_key_debug
            : R.string.api_key_release;

    private static final int NOLOCATION_DIALOG = 103;

    private static final int OUTOFRANGE_DIALOG = 104;

    private static final int REQUEST_NO_LOCATION = 41;

    private ObaMapViewV1 mMapView;

    private UIHelp.StopUserInfoMap mStopUserMap;

    private String mFocusStopId;

    // The Fragment controls the stop overlay, since that
    // is used by both modes.
    private StopOverlay mStopOverlay;

    StopPopup mStopPopup;

    private MyLocationOverlay mLocationOverlay;

    private ZoomControls mZoomControls;

    // We only display the out of range dialog once
    private boolean mWarnOutOfRange = true;

    private boolean mRunning = false;

    private MapModeController mController;

    /**
     * Google Location Services
     */
    protected LocationClient mLocationClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //boolean firstRun = firstRunCheck();
        super.onCreate(savedInstanceState);
        // call this from the subclass? or call an overrideable
        setContentView(getContentView());

        View view = getView();
        mMapView = createMap(view);

        mZoomControls = (ZoomControls) view.findViewById(R.id.zoom_controls);
        mZoomControls.setOnZoomInClickListener(mOnZoomIn);
        mZoomControls.setOnZoomOutClickListener(mOnZoomOut);

        mLocationOverlay = new MyLocationOverlay(getActivity(), mMapView);
        mLocationOverlay.enableMyLocation();
        List<com.google.android.maps.Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.add(mLocationOverlay);

        // Initialize the StopPopup (hidden)
        mStopPopup = new StopPopup(this, view.findViewById(R.id.stop_info));

        if (savedInstanceState != null) {
            initMap(savedInstanceState);
        } else {
            Bundle args = getIntent().getExtras();
            // The rest of this code assumes a bundle exists,
            // even if it's empty
            if (args == null) {
                args = new Bundle();
            }
            initMap(args);
        }

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity()) == ConnectionResult.SUCCESS) {
            LocationHelp.LocationServicesCallback locCallback = new LocationHelp.LocationServicesCallback();
            mLocationClient = new LocationClient(getActivity(), locCallback, locCallback);
            mLocationClient.connect();
        }
    }

    private ObaMapViewV1 createMap(View view) {
        ObaMapViewV1 map = new ObaMapViewV1(this, getString(API_KEY));
        map.setBuiltInZoomControls(false);
        map.setClickable(true);
        map.setFocusableInTouchMode(true);

        RelativeLayout mainLayout = (RelativeLayout) view.findViewById(R.id.mainlayout);
        RelativeLayout.LayoutParams lp =
                new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT);
        mainLayout.addView(map, 0, lp);

        return map;
    }

    abstract protected int getContentView();

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
        if (mStopUserMap != null) {
            mStopUserMap.close();
            mStopUserMap = null;
            mStopPopup.setStopUserMap(null);
        }
        if (mController != null) {
            mController.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        mLocationOverlay.disableMyLocation();

        if (mController != null) {
            mController.onPause();
        }

        mRunning = false;
        super.onPause();
    }

    @Override
    public void onResume() {
        mRunning = true;
        mLocationOverlay.enableMyLocation();

        //Log.d(TAG, "onResume: " + mStopUserMap);
        if (mStopUserMap == null) {
            mStopUserMap = new UIHelp.StopUserInfoMap(getActivity());
        } else {
            mStopUserMap.requery();
        }
        mStopPopup.setStopUserMap(mStopUserMap);

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
            mLocationClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.my_location) {
            setMyLocation();
            return true;
        } else if (id == R.id.search) {
            onSearchRequested();
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mController != null) {
            mController.onSaveInstanceState(outState);
        }
        outState.putString(MapParams.MODE, getMapMode());
        outState.putString(MapParams.STOP_ID, mFocusStopId);
        Location center = mMapView.getMapCenterAsLocation();
        outState.putDouble(MapParams.CENTER_LAT, center.getLatitude());
        outState.putDouble(MapParams.CENTER_LON, center.getLongitude());
        outState.putFloat(MapParams.ZOOM, mMapView.getZoomLevelAsFloat());
    }

    public boolean isRouteDisplayed() {
        return (mController != null) &&
                MapParams.MODE_ROUTE.equals(mController.getMode());
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case NOLOCATION_DIALOG:
                return createNoLocationDialog();

            case OUTOFRANGE_DIALOG:
                return createOutOfRangeDialog();
        }
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_NO_LOCATION:
                // Clear the map center so we can get the user's location again
                setMyLocation();
                break;
        }
    }

    //
    // Fragment Controller
    //
    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public View getView() {
        return findViewById(android.R.id.content);
    }

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
        return mMapView;
    }

    @Override
    public void showProgress(boolean show) {
        setSupportProgressBarIndeterminateVisibility(show);
    }

    @Override
    public void showStops(List<ObaStop> stops, ObaReferences refs) {
        // Maintain focus through this step.
        // If we can't maintain focus through this step, then we
        // have to hide the stop popup
        String focusedId = mFocusStopId;

        // If there is an List<E>ing StopOverlay, remove it.
        List<Overlay> mapOverlays = mMapView.getOverlays();
        if (mStopOverlay != null) {
            focusedId = mStopOverlay.getFocusedId();
            mapOverlays.remove(mStopOverlay);
            mStopOverlay = null;
        }

        if (stops != null) {
            mStopOverlay = new StopOverlay(stops, getActivity());
            mStopOverlay.setOnFocusChangeListener(mFocusChangeListener);
            mStopPopup.setReferences(refs);

            if (focusedId != null) {
                if (!mStopOverlay.setFocusById(focusedId)) {
                    mStopPopup.hide();
                }
            }
            mapOverlays.add(mStopOverlay);
        }

        mMapView.postInvalidate();
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
                showDialog(OUTOFRANGE_DIALOG);
            }
        }
    }

    //
    // Region Task Callback
    //
    @Override
    public void onTaskFinished(boolean currentRegionChanged) {
        // Update map after a new region has been selected
        setMyLocation();

        // If region changed and was auto-selected, show user what region we're using
        if (currentRegionChanged
                && Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                && Application.get().getCurrentRegion() != null) {
            Toast.makeText(this,
                    getString(R.string.region_region_found,
                            Application.get().getCurrentRegion().getName()),
                    Toast.LENGTH_LONG).show();
        }
    }

    //
    // Stop changed handler
    //
    final Handler mStopChangedHandler = new Handler();

    final OnFocusChangeListener mFocusChangeListener = new OnFocusChangeListener() {
        public void onFocusChanged(@SuppressWarnings("rawtypes") ItemizedOverlay overlay,
                final OverlayItem newFocus) {
            mStopChangedHandler.post(new Runnable() {
                public void run() {
                    if (newFocus != null) {
                        final StopOverlay.StopOverlayItem item = (StopOverlayItem) newFocus;
                        final ObaStop stop = item.getStop();
                        mFocusStopId = stop.getId();
                        //Log.d(TAG, "Show stop popup");
                        mStopPopup.show(stop);
                    } else {
                        mStopPopup.hide();
                    }
                }
            });
        }
    };

    // This is a bit annoying: runOnFirstFix() calls its runnable either
    // immediately or on another thread (AsyncTask). Since we don't know
    // what thread the runnable will be run on , and since AsyncTasks have
    // to be created from the UI thread, we need to post a message back to the
    // UI thread just to create another AsyncTask.
    final Handler mSetMyLocationHandler = new Handler();

    final Runnable mSetMyLocation = new Runnable() {
        public void run() {
            if (mLocationOverlay != null) {
                setMyLocation(mLocationOverlay.getMyLocation());
            }
        }
    };

    //
    // Location help
    //
    static final int WAIT_FOR_LOCATION_TIMEOUT = 10000;

    final Handler mWaitingForLocationHandler = new Handler();

    final Runnable mWaitingForLocation = new Runnable() {
        public void run() {
            if (mLocationOverlay != null
                    && mLocationOverlay.getMyLocation() == null) {
                Activity act = getActivity();
                if (act == null) {
                    return;
                }
                Toast.makeText(act,
                        R.string.main_waiting_for_location, Toast.LENGTH_LONG)
                        .show();
                mWaitingForLocationHandler.postDelayed(mUnableToGetLocation,
                        2 * WAIT_FOR_LOCATION_TIMEOUT);
            }
        }
    };

    final Runnable mUnableToGetLocation = new Runnable() {
        public void run() {
            if (mLocationOverlay != null
                    && mLocationOverlay.getMyLocation() == null) {
                Toast.makeText(getActivity(),
                        R.string.main_location_unavailable, Toast.LENGTH_LONG)
                        .show();
                if (mController != null) {
                    mController.onNoLocation();
                }
            }
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    public void setMyLocation() {
        // Not really sure how this happened, but it happened in issue #54
        if (mLocationOverlay == null) {
            return;
        }

        if (!mLocationOverlay.isMyLocationEnabled()) {
            showDialog(NOLOCATION_DIALOG);
            return;
        }

        GeoPoint point = mLocationOverlay.getMyLocation();
        if (point == null) {
            mWaitingForLocationHandler.postDelayed(mWaitingForLocation,
                    WAIT_FOR_LOCATION_TIMEOUT);
            mLocationOverlay.runOnFirstFix(new Runnable() {
                public void run() {
                    mSetMyLocationHandler.post(mSetMyLocation);
                }
            });
        } else {
            setMyLocation(point);
        }
    }

    private void setMyLocation(GeoPoint point) {
        MapController mapCtrl = mMapView.getController();
        mapCtrl.animateTo(point);
        mapCtrl.setZoom(MapParams.DEFAULT_ZOOM);
        if (mController != null) {
            mController.onLocation();
        }
    }

    //
    // Dialogs
    //
    @SuppressWarnings("deprecation")
    private Dialog createNoLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_nolocation_title);
        builder.setIcon(android.R.drawable.ic_dialog_map);
        builder.setMessage(R.string.main_nolocation);
        builder.setPositiveButton(android.R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivityForResult(
                                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                REQUEST_NO_LOCATION);
                        dismissDialog(NOLOCATION_DIALOG);
                    }
                });
        builder.setNegativeButton(android.R.string.no,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Ok, I suppose we can just try looking from where we
                        // are.
                        mController.onLocation();
                        dismissDialog(NOLOCATION_DIALOG);
                    }
                });
        return builder.create();
    }

    @SuppressWarnings("deprecation")
    private Dialog createOutOfRangeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.main_outofrange_title)
                .setIcon(android.R.drawable.ic_dialog_map)
                .setMessage(getString(R.string.main_outofrange,
                        Application.get().getCurrentRegion() != null ?
                                Application.get().getCurrentRegion().getName() : ""))
                .setPositiveButton(R.string.main_outofrange_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                zoomToRegion();
                                dismissDialog(OUTOFRANGE_DIALOG);
                            }
                        })
                .setNegativeButton(R.string.main_outofrange_no,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(OUTOFRANGE_DIALOG);
                                mWarnOutOfRange = false;
                            }
                        });
        return builder.create();
    }

    void zoomToRegion() {
        // If we have a region, then zoom to it.
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null) {
            double results[] = new double[4];
            RegionUtils.getRegionSpan(region, results);
            MapController ctrl = mMapView.getController();
            ctrl.setCenter(MapHelp.makeGeoPoint(results[2], results[3]));
            ctrl.zoomToSpan((int) (results[0] * 1E6), (int) (results[1] * 1E6));
        } else {
            // If we don't have a region, then prompt to select a region.
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
    // Zoom help
    //
    static final int MAX_ZOOM = 21;

    static final int MIN_ZOOM = 1;

    void enableZoom() {
        mZoomControls.setIsZoomInEnabled(mMapView.getZoomLevelAsFloat() != MAX_ZOOM);
        mZoomControls.setIsZoomOutEnabled(mMapView.getZoomLevelAsFloat() != MIN_ZOOM);
    }

    private final View.OnClickListener mOnZoomIn = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mMapView.getController().zoomIn()) {
                mZoomControls.setIsZoomInEnabled(false);
                mZoomControls.setIsZoomOutEnabled(true);
            } else {
                enableZoom();
            }
        }
    };

    private final View.OnClickListener mOnZoomOut = new View.OnClickListener() {
        public void onClick(View v) {
            if (!mMapView.getController().zoomOut()) {
                mZoomControls.setIsZoomInEnabled(true);
                mZoomControls.setIsZoomOutEnabled(false);
            } else {
                enableZoom();
            }
        }
    };

    /**
     * Returns true if no files in private directory
     * (ObaMapView or MapActivity caches prefs and tiles)
     * This will fail if MapViewActivty never got to onPause
     */
    /*
    private boolean firstRunCheck() {
        File dir = getFilesDir();
        return (dir.list().length == 0);
    }
    */
}
