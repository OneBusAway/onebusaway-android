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
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.ItemizedOverlay.OnFocusChangeListener;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;
import com.joulespersecond.seattlebusbot.map.StopOverlay.StopOverlayItem;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ZoomControls;

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
 *
 */
public class MapFragment extends Fragment
            implements MapFragmentController.FragmentCallback {
    //private static final String TAG = "MapFragment";

    // Fragment arguments.
    //private static final String FOCUS_STOP_ID = ".FocusStopId";
    //private static final String CENTER_LAT = ".CenterLat";
    //private static final String CENTER_LON = ".CenterLon";
    //private static final String MAP_ZOOM = ".MapZoom";
    // Switches to 'route mode' -- stops aren't updated on move
    //private static final String ROUTE_ID = ".RouteId";
    //private static final String SHOW_ROUTES = ".ShowRoutes";

    private MapView mMapView;
    private UIHelp.StopUserInfoMap mStopUserMap;

    // The Fragment controls the stop overlay, since that
    // is used by both modes.
    private StopOverlay mStopOverlay;
    StopPopup mStopPopup;

    private MyLocationOverlay mLocationOverlay;
    private ZoomControls mZoomControls;

    private MapFragmentController mController;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        View view = getView();
        mMapView = (MapView)view.findViewById(R.id.mapview);
        mMapView.setBuiltInZoomControls(false);
        mZoomControls = (ZoomControls)view.findViewById(R.id.zoom_controls);
        mZoomControls.setOnZoomInClickListener(mOnZoomIn);
        mZoomControls.setOnZoomOutClickListener(mOnZoomOut);

        // Initialize the StopPopup (hidden)
        mStopPopup = new StopPopup(getActivity(), view.findViewById(R.id.stop_info));

        // TODO: Our initial mode is basically determined by whether
        // or not there is a Route ID as an argument.
        // But for now we just create an StopsController.
        mController = new StopMapController(this);
        mController.initialize(savedInstanceState);

        UIHelp.checkAirplaneMode(getActivity());
    }

    @Override
    public void onDestroy() {
        if (mStopUserMap != null) {
            mStopUserMap.close();
            mStopUserMap = null;
        }
        if (mController != null) {
            mController.destroy();
        }
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.main, null);
    }

    @Override
    public void onPause() {
        mLocationOverlay.disableMyLocation();

        if (mController != null) {
            mController.onPause();
        }

        // Clear the overlays to save memory and re-establish them when we are
        // resumed.
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.clear();
        mLocationOverlay = null;
        mStopOverlay = null;

        super.onPause();
    }

    @Override
    public void onResume() {
        //
        // This is where we initialize all the UI elements.
        // They are torn down in onPause to save memory.
        //
        mLocationOverlay = new MyLocationOverlay(getActivity(), mMapView);
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.add(mLocationOverlay);
        mLocationOverlay.enableMyLocation();

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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.map, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.my_location) {
            setMyLocation();
            return true;
        } else if (id == R.id.search) {
            // TODO:
            //Intent myIntent = new Intent(this, MyRoutesActivity.class);
            //startActivity(myIntent);
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mController != null) {
            mController.onSaveInstanceState(outState);
        }
        // The only thing we really need to save it the focused stop ID.
        /*
        outState.putString(FOCUS_STOP_ID, mFocusStopId);
        outState.putString(ROUTE_ID, mRouteOverlay.getRouteId());
        outState.putBoolean(SHOW_ROUTES, mShowRoutes);
        GeoPoint center = mMapView.getMapCenter();
        outState.putDouble(CENTER_LAT, center.getLatitudeE6() / 1E6);
        outState.putDouble(CENTER_LON, center.getLongitudeE6() / 1E6);
        outState.putInt(MAP_ZOOM, mMapView.getZoomLevel());
        */
    }


    //
    // Fragment Controller
    //
    @Override
    public MapView getMapView() {
        return mMapView;
    }

    @Override
    public void showStops(List<ObaStop> stops, ObaReferences refs) {
        // If there is an eList<E>ing StopOverlay, remove it.
        List<Overlay> mapOverlays = mMapView.getOverlays();
        if (mStopOverlay != null) {
            mapOverlays.remove(mStopOverlay);
            mStopOverlay = null;
        }

        if (stops != null) {
            mStopOverlay = new StopOverlay(stops, getActivity());
            mStopOverlay.setOnFocusChangeListener(mFocusChangeListener);
            mStopPopup.setReferences(refs);
            // TODO: Refocus the stop id
            /*
            if (mFocusStopId != null) {
                mStopOverlay.setFocusById(mFocusStopId);
                stop = ((ObaResponseWithRefs)response).getStop(mFocusStopId);
                // if (routeResponse != null) {
                // ObaStopGroup group = routeResponse.getGroupForStop(mFocusStopId);
                // if (group != null)
                // Log.d(TAG, "StopGroup: " + group.getName());
                // }
            }
            */
            mapOverlays.add(mStopOverlay);
        }

        // TODO: Refresh the popup
        mMapView.postInvalidate();
    }

    @Override
    public void notifyOutOfRange() {
        // TODO:
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
                        final StopOverlay.StopOverlayItem item = (StopOverlayItem)newFocus;
                        final ObaStop stop = item.getStop();
                        //mFocusStopId = stop.getId();
                        mStopPopup.show(stop);
                    } else {
                        mStopPopup.hide();
                    }
                }
            });
        }
    };

    //
    // INitialization help
    //
    /*
    private void mapValuesFromBundle(Bundle bundle) {
        double lat = bundle.getDouble(CENTER_LAT);
        double lon = bundle.getDouble(CENTER_LON);
        if (lat != 0.0 && lon != 0.0) {
            mMapCenter = ObaApi.makeGeoPoint(lat, lon);
        }
        mMapZoom = bundle.getInt(MAP_ZOOM, mMapZoom);
    }
    */

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
    static final int WAIT_FOR_LOCATION_TIMEOUT = 5000;

    final Handler mWaitingForLocationHandler = new Handler();

    final Runnable mWaitingForLocation = new Runnable() {
        public void run() {
            if (mLocationOverlay != null
                    && mLocationOverlay.getMyLocation() == null) {
                Toast.makeText(getActivity(),
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
    public void setMyLocation() {
        // Not really sure how this happened, but it happened in issue #54
        if (mLocationOverlay == null) {
            return;
        }

        if (!mLocationOverlay.isMyLocationEnabled()) {
            // TODO: S
            //showDialog(NOLOCATION_DIALOG);
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
        mapCtrl.setZoom(16);
        if (mController != null) {
            mController.onLocation();
        }
    }

    //
    // Zoom help
    //
    static final int MAX_ZOOM = 21;
    static final int MIN_ZOOM = 1;

    void enableZoom() {
        mZoomControls.setIsZoomInEnabled(mMapView.getZoomLevel() != MAX_ZOOM);
        mZoomControls.setIsZoomOutEnabled(mMapView.getZoomLevel() != MIN_ZOOM);
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
}
