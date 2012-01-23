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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentMapActivity;
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

    private static final String TAG_NO_LOCATION_DIALOG = ".NoLocation";
    private static final String TAG_OUT_OF_RANGE_DIALOG = ".OutOfRange";

    private MapView mMapView;
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

        mLocationOverlay = new MyLocationOverlay(getActivity(), mMapView);
        mLocationOverlay.enableMyLocation();
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.add(mLocationOverlay);

        // Initialize the StopPopup (hidden)
        mStopPopup = new StopPopup(this, view.findViewById(R.id.stop_info));

        UIHelp.checkAirplaneMode(getActivity());

        if (savedInstanceState != null) {
            initMap(savedInstanceState);
        } else {
            initMap(getArguments());
        }

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

        super.onPause();
    }

    @Override
    public void onResume() {
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
        outState.putString(MapParams.MODE, getMapMode());
        outState.putString(MapParams.STOP_ID, mFocusStopId);
        GeoPoint center = mMapView.getMapCenter();
        outState.putDouble(MapParams.CENTER_LAT, center.getLatitudeE6() / 1E6);
        outState.putDouble(MapParams.CENTER_LON, center.getLongitudeE6() / 1E6);
        outState.putInt(MapParams.ZOOM, mMapView.getZoomLevel());
    }

    public boolean isRouteDisplayed() {
        return (mController != null) &&
                MapParams.MODE_ROUTE.equals(mController.getMode());
    }

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
        if (MapParams.MODE_ROUTE.equals(mode)) {
            mController = new RouteMapController(this);
        } else if (MapParams.MODE_STOP.equals(mode)) {
            mController = new StopMapController(this);
        }
        mController.setState(args);
        mController.onResume();
    }

    @Override
    public MapView getMapView() {
        return mMapView;
    }

    @Override
    public void showProgress(boolean show) {
        ((FragmentMapActivity)getActivity()).setProgressBarIndeterminateVisibility(show ?
                Boolean.TRUE : Boolean.FALSE);
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
    public void notifyOutOfRange() {
        if (mWarnOutOfRange) {
            mShowOutOfRangeHandler.post(new Runnable() {
                @Override
                public void run() {
                    new OutOfRangeDialog().show(getSupportFragmentManager(),
                            TAG_OUT_OF_RANGE_DIALOG);
                }
            });
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
                        final StopOverlay.StopOverlayItem item = (StopOverlayItem)newFocus;
                        final ObaStop stop = item.getStop();
                        mFocusStopId = stop.getId();
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
    static final int WAIT_FOR_LOCATION_TIMEOUT = 5000;

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
    public void setMyLocation() {
        // Not really sure how this happened, but it happened in issue #54
        if (mLocationOverlay == null) {
            return;
        }

        if (!mLocationOverlay.isMyLocationEnabled()) {
            new NoLocationDialog().show(getSupportFragmentManager(),
                    TAG_NO_LOCATION_DIALOG);
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

    private class NoLocationDialog extends DialogFragment {
        private static final int REQUEST_NO_LOCATION = 1;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case REQUEST_NO_LOCATION:
                    setMyLocation();
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.main_nolocation_title);
            builder.setIcon(android.R.drawable.ic_dialog_map);
            builder.setMessage(R.string.main_nolocation);
            builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            NoLocationDialog.this.startActivityForResult(
                                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                                    REQUEST_NO_LOCATION);
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(android.R.string.no,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            return builder.create();
        }
    }

    // This is in lieu of a more complicated map of agencies screen
    // like on the iPhone app. Eventually that'd be cool, but I don't really
    // have time right now.

    // This array must be kept in sync with R.array.agency_locations!
    private static final GeoPoint[] AGENCY_LOCATIONS = new GeoPoint[] {
        new GeoPoint(47605990, -122331780), // Seattle, WA
        new GeoPoint(47252090, -122443740), // Tacoma, WA
        new GeoPoint(47979090, -122201530), // Everett, WA
    };

    private class OutOfRangeDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = getActivity().getLayoutInflater();
            builder.setCustomTitle(inflater.inflate(R.layout.main_outofrange_title,
                    null));

            builder.setItems(R.array.agency_locations,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which >= 0 && which < AGENCY_LOCATIONS.length) {
                                setMyLocation(AGENCY_LOCATIONS[which]);
                            }
                            dialog.dismiss();
                            mWarnOutOfRange = false;
                        }
                    });
            return builder.create();
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
