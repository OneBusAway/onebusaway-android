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
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.ItemizedOverlay.OnFocusChangeListener;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.seattlebusbot.StopOverlay.StopOverlayItem;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MapViewActivity extends MapActivity
        implements MapWatcher.Listener, StopsController.Listener {
    private static final String TAG = "MapViewActivity";

    public static final String HELP_URL = "http://www.joulespersecond.com/onebusaway-userguide2/";
    public static final String TWITTER_URL = "http://mobile.twitter.com/seattlebusbot";

    private static final String FOCUS_STOP_ID = ".FocusStopId";
    private static final String CENTER_LAT = ".CenterLat";
    private static final String CENTER_LON = ".CenterLon";
    private static final String MAP_ZOOM = ".MapZoom";
    // Switches to 'route mode' -- stops aren't updated on move
    private static final String ROUTE_ID = ".RouteId";
    private static final String SHOW_ROUTES = ".ShowRoutes";

    MapView mMapView;
    private MyLocationOverlay mLocationOverlay;
    StopOverlay mStopOverlay;
    UIHelp.StopUserInfoMap mStopUserMap;
    //private RouteOverlay mRouteOverlay;

    // Values that are initialized by either the intent extras
    // or by the frozen state.
    private String mRouteId;
    private String mFocusStopId;
    private GeoPoint mMapCenter;
    private int mMapZoom = 16; // initial zoom
    private boolean mShowRoutes;

    private StopsController mStopsController;
    private MapWatcher mMapWatcher;

    private static final int HELP_DIALOG = 1;
    private static final int WHATSNEW_DIALOG = 2;

    /**
     * Starts the MapActivity with a particular stop focused with the
     * center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat The latitude of the map center.
     * @param lon The longitude of the map center.
     */
    public static final void start(Context context, String focusId, double lat, double lon) {
        Intent myIntent = new Intent(context, MapViewActivity.class);
        myIntent.putExtra(FOCUS_STOP_ID, focusId);
        myIntent.putExtra(CENTER_LAT, lat);
        myIntent.putExtra(CENTER_LON, lon);
        context.startActivity(myIntent);
    }
    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     * @param stopData If this is non-null, this is string representation of
     *          the ObaResponse to use as the stops for the route.
     */
    public static final void start(Context context, String routeId) {
        Intent myIntent = new Intent(context, MapViewActivity.class);
        myIntent.putExtra(ROUTE_ID, routeId);
        context.startActivity(myIntent);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //
        // Static initialization (what should always be there, regardless of intent)
        //
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        mMapView = (MapView)findViewById(R.id.mapview);
        mMapView.setBuiltInZoomControls(true);
        mStopsController = new StopsController(this, this);

        // Initialize the links
        UIHelp.setChildClickable(this, R.id.show_arrival_info, mOnShowArrivals);
        UIHelp.setChildClickable(this, R.id.show_routes, mOnShowRoutes);

        // If you click on the popup but not on a link, nothing happens
        // (if this weren't there, the popup would be dismissed)
        View popup = findViewById(R.id.map_popup);
        popup.setOnClickListener(mPopupClick);

        // Set up everything we can from the intent --
        // all of the UI is set-up/torn down in onResume/onPause
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            mRouteId = bundle.getString(ROUTE_ID);
            mFocusStopId = bundle.getString(FOCUS_STOP_ID);
            mapValuesFromBundle(bundle);
        }
        if (savedInstanceState != null) {
            mFocusStopId = savedInstanceState.getString(FOCUS_STOP_ID);
            mShowRoutes = savedInstanceState.getBoolean(SHOW_ROUTES);
            mapValuesFromBundle(savedInstanceState);
        }

        mStopsController.setNonConfigurationInstance(getLastNonConfigurationInstance());

        autoShowWhatsNew();
        UIHelp.checkAirplaneMode(this);
    }
    @Override
    public void onDestroy() {
        mStopUserMap.close();
        mStopUserMap = null;
        mStopsController.cancel();
        mStopsController = null;
        super.onDestroy();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.my_location) {
            setMyLocation();
            return true;
        }
        else if (id == R.id.find_route) {
            Intent myIntent = new Intent(this, MyRoutesActivity.class);
            startActivity(myIntent);
            return true;
        }
        else if (id == R.id.find_stop) {
            Intent myIntent = new Intent(this, MyStopsActivity.class);
            startActivity(myIntent);
            return true;
        }
        else if (id == R.id.view_trips) {
            Intent myIntent = new Intent(this, TripListActivity.class);
            startActivity(myIntent);
            return true;
        }
        else if (id == R.id.help) {
            showDialog(HELP_DIALOG);
            return true;
        }
        return false;
    }
    @Override
    public void onResume() {
        //
        // This is where we initialize all the UI elements.
        // They are torn down in onPause to save memory.
        //
        mLocationOverlay = new MyFixedLocationOverlay(this, mMapView);
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.add(mLocationOverlay);
        mLocationOverlay.enableMyLocation();

        if (mStopUserMap == null) {
            mStopUserMap = new UIHelp.StopUserInfoMap(this);
        }
        else {
            mStopUserMap.requery();
        }

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
        ObaResponse response = mStopsController.getResponse();
        if (response != null) {
            setOverlays(response);
            if (mStopOverlay != null) {
                showRoutes(null, mShowRoutes);
            }
        }
        else if (isRouteMode()) {
            getStops();
        }

        // We only care about watching the map when we're not in route mode.
        if (!isRouteMode()) {
            if (prevCenter == null) {
                setMyLocation();
            }
            else {
                getStops();
            }
            mMapWatcher = new MapWatcher(mMapView, this);
            mMapWatcher.start();
        }

        super.onResume();
    }
    @Override
    public void onPause() {
        mLocationOverlay.disableMyLocation();
        mStopsController.cancel();

        if (mMapWatcher != null) {
            mMapWatcher.stop();
            mMapWatcher = null;
        }

        mMapCenter = mMapView.getMapCenter();
        mMapZoom = mMapView.getZoomLevel();
        Log.d(TAG, "PAUSE: Saving center: " + mMapCenter);
        // Clear the overlays to save memory and re-establish them when we are resumed.
        List<Overlay> mapOverlays = mMapView.getOverlays();
        mapOverlays.clear();
        mStopOverlay = null;
        mLocationOverlay = null;

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // The only thing we really need to save it the focused stop ID.
        outState.putString(FOCUS_STOP_ID, mFocusStopId);
        outState.putBoolean(SHOW_ROUTES, mShowRoutes);
        GeoPoint center = mMapView.getMapCenter();
        outState.putDouble(CENTER_LAT, center.getLatitudeE6()/1E6);
        outState.putDouble(CENTER_LON, center.getLongitudeE6()/1E6);
        outState.putInt(MAP_ZOOM, mMapView.getZoomLevel());
    }
    @Override
    public Object onRetainNonConfigurationInstance() {
        return mStopsController.onRetainNonConfigurationInstance();
    }
    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case HELP_DIALOG:
            return createHelpDialog();

        case WHATSNEW_DIALOG:
            return createWhatsNewDialog();
        }
        return null;
    }
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "******** LOW MEMORY ******** ");
        ObaApi.clearCache();
    }

    //
    // MapWatcher.Listener
    //
    @Override
    public void onMapZoomChanging() {
        Log.d(TAG, "Map zoom changing");
    }

    @Override
    public void onMapZoomChanged() {
        Log.d(TAG, "Map zoom changed");
        getStops();
    }

    @Override
    public void onMapCenterChanging() {
        Log.d(TAG, "Map center changing");
    }

    @Override
    public void onMapCenterChanged() {
        mMapCenter = mMapView.getMapCenter();
        Log.d(TAG, "Map center changed: "+mMapCenter);
        getStops();
    }

    //
    // StopsController.Listener
    //
    @Override
    public void onRequestFulfilled(ObaResponse response) {
        setOverlays(response);
    }

    private void getStops() {
        mStopsController.setCurrentRequest(
                StopsController.requestFromView(mMapView, mRouteId));
    }

    // This is a bit annoying: runOnFirstFix() calls its runnable either
    // immediately or on another thread (AsyncTask). Since we don't know
    // what thread the runnable will be run on , and since AsyncTasks have
    // to be created from the UI thread, we need to post a message back to the
    // UI thread just to create another AsyncTask.
    final Handler mGetStopsHandler = new Handler();
    final Runnable mGetStops = new Runnable() {
        public void run() {
            if (mLocationOverlay != null) {
                setMyLocation(mLocationOverlay.getMyLocation());
            }
        }
    };
    private void setMyLocation() {
        // Not really sure how this happened, but it happened in issue #54
        if (mLocationOverlay == null) {
            return;
        }
        GeoPoint point = mLocationOverlay.getMyLocation();
        if (point == null) {
            mLocationOverlay.runOnFirstFix(new Runnable() {
                public void run() {
                    mGetStopsHandler.post(mGetStops);
                }
            });
        }
        else {
            setMyLocation(point);
        }
    }
    private void setMyLocation(GeoPoint point) {
        MapController mapCtrl = mMapView.getController();
        mapCtrl.animateTo(point);
        mapCtrl.setZoom(16);
        mMapZoom = 16;
        if (!isRouteMode()) {
            getStops();
        }
    }

    private class RouteArrayAdapter extends Adapters.BaseArrayAdapter<ObaRoute> {
        public RouteArrayAdapter(ObaArray<ObaRoute> routes) {
            super(MapViewActivity.this, routes, R.layout.main_popup_route_item);
        }
        @Override
        protected void setData(View view, int position) {
            TextView shortName = (TextView)view.findViewById(R.id.short_name);

            ObaRoute route = mArray.get(position);
            shortName.setText(route.getShortName());
        }
    }

    void populateRoutes(ObaStop stop, boolean force) {
        GridView grid = (GridView)findViewById(R.id.route_list);
        if (grid.getVisibility() != View.GONE || force) {
            grid.setAdapter(new RouteArrayAdapter(stop.getRoutes()));
        }
    }

    final Handler mStopChangedHandler = new Handler();
    final OnFocusChangeListener mFocusChangeListener = new OnFocusChangeListener() {
        public void onFocusChanged(@SuppressWarnings("unchecked") ItemizedOverlay overlay,
                final OverlayItem newFocus) {
             mStopChangedHandler.post(new Runnable() {
                 public void run() {
                     // There are times when this can be fired after we've already destroyed
                     // ourselves (so mStopUserMap == null).
                     // If that happens, just ignore it (later we could potentially remove the
                     // runnable from the handler)
                     if (mStopUserMap == null) {
                         mFocusStopId = null;
                         return;
                     }
                     final View popup = findViewById(R.id.map_popup);
                     if (newFocus == null) {
                         mFocusStopId = null;
                         popup.setVisibility(View.GONE);
                         return;
                     }

                     final StopOverlay.StopOverlayItem item = (StopOverlayItem)newFocus;
                     final ObaStop stop = item.getStop();
                     mFocusStopId = stop.getId();

                     // Is this a favorite?
                     mStopUserMap.setView(popup, stop.getId(), stop.getName());
                     UIHelp.setStopDirection(popup.findViewById(R.id.direction),
                             stop.getDirection(),
                             false);

                     populateRoutes(stop, false);

                     // Right now the popup is always at the top of the screen.
                     popup.setVisibility(View.VISIBLE);
                }
             });
        }
    };
    final ClickableSpan mOnShowArrivals = new ClickableSpan() {
        public void onClick(View v) {
            StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();
            if (item != null) {
                goToStop(MapViewActivity.this, item.getStop());
            }
        }
    };
    private final ClickableSpan mOnShowRoutes = new ClickableSpan() {
        public void onClick(View v) {
            showRoutes((TextView)v, !mShowRoutes);
        }
    };
    private final View.OnClickListener mPopupClick = new View.OnClickListener() {
        public void onClick(View v) {
            // Eat the click so the Map doesn't get it.
        }
    };

    private void showRoutes(TextView text, boolean show) {
        final GridView grid = (GridView)findViewById(R.id.route_list);
        if (text == null) {
            text = (TextView)findViewById(R.id.show_routes);
        }
        if (show) {
            final StopOverlayItem item = (StopOverlayItem)mStopOverlay.getFocus();
            if (item != null) {
                populateRoutes(item.getStop(), true);
            }
            // TODO: Animate at some point...
            grid.setVisibility(View.VISIBLE);
            text.setText(R.string.main_hide_routes);
        }
        else {
            grid.setVisibility(View.GONE);
            text.setText(R.string.main_show_routes);
        }
        mShowRoutes = show;
        // When the text changes, we need to reset its clickable status
        Spannable span = (Spannable)text.getText();
        span.setSpan(mOnShowRoutes, 0, span.length(), 0);
    }

    private void setOverlays(ObaResponse response) {
        if (response.getCode() != ObaApi.OBA_OK) {
            return;
        }
        final ObaArray<ObaStop> stops = response.getData().getStops();

        List<Overlay> mapOverlays = mMapView.getOverlays();
        // If there is an existing StopOverlay, remove it.
        final View popup = findViewById(R.id.map_popup);
        popup.setVisibility(View.GONE);

        if (mStopOverlay != null) {
            mapOverlays.remove(mStopOverlay);
            mStopOverlay = null;
        }

        mStopOverlay = new StopOverlay(stops, this);
        mStopOverlay.setOnFocusChangeListener(mFocusChangeListener);
        if (mFocusStopId != null) {
            mStopOverlay.setFocusById(mFocusStopId);
        }

        if (isRouteMode()) {
            /*
            if (mRouteOverlay == null) {
                mRouteOverlay = new RouteOverlay();
                mapOverlays.add(mRouteOverlay);
            }
            setRouteOverlayLines(mRouteOverlay, response);
            */
        }

        mapOverlays.add(mStopOverlay);
        mMapView.postInvalidate();
    }

    /*
    private static void setRouteOverlayLines(RouteOverlay overlay, ObaResponse response) {
        overlay.clearLines();
        final ObaArray<ObaPolyline> lines = response.getData().getPolylines();
        if (lines.length() > 0) {
            overlay.addLine(Color.BLUE, lines.get(0));
        }
        // Get all the stop groupings
        ObaArray<ObaStopGrouping> stopGroupings = response.getData().getStopGroupings();
        // For each stop grouping, get
        int color = 0;
        final int numGroupings = stopGroupings.length();
        for (int i=0; i < numGroupings; ++i) {
            final ObaArray<ObaStopGroup> groups = stopGroupings.get(i).getStopGroups();
            final int numGroups = groups.length();
            for (int j=0; j < numGroups; ++j) {
                final ObaArray<ObaPolyline> lines = groups.get(j).getPolylines();
                final int numLines = lines.length();
                for (int k=0; k < numLines; ++k) {
                    overlay.addLine(mColors[color], lines.get(k));
                    color = (color+1)%mColors.length;
                }
            }

        }
    }
    */

    static void goToStop(Context context, ObaStop stop) {
        StopInfoActivity.start(context, stop);
    }

    private Dialog createHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_title);
        builder.setItems(R.array.main_help_options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case 0:
                    UIHelp.goToUrl(MapViewActivity.this, HELP_URL);
                    break;
                case 1:
                    UIHelp.goToUrl(MapViewActivity.this, TWITTER_URL);
                    break;
                case 2:
                    showDialog(WHATSNEW_DIALOG);
                    break;
                case 3:
                    goToBugReport();
                    break;
                case 4:
                    Intent preferences = new Intent(MapViewActivity.this, EditPreferencesActivity.class);
                    startActivity(preferences);
                    break;
                }
            }
        });
        return builder.create();
    }
    private Dialog createWhatsNewDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_whatsnew_title);
        builder.setIcon(R.drawable.icon);
        builder.setMessage(R.string.main_help_whatsnew);
        builder.setNeutralButton(R.string.main_help_close, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(WHATSNEW_DIALOG);
            }
        });
        return builder.create();
        /*
        // If we get here, we need to show the dialog.
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.whats_new);
        // OK dismisses
        Button button = (Button)dialog.findViewById(android.R.id.closeButton);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismissDialog(WHATSNEW_DIALOG);
            }
        });
        return dialog;
        */
    }

    private static final String WHATS_NEW_VER = "whatsNewVer";

    private void autoShowWhatsNew() {
        SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0);

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }

        final int oldVer = settings.getInt(WHATS_NEW_VER, 0);
        final int newVer = appInfo.versionCode;

        if (oldVer != newVer) {
            // It's impossible to tell the difference from people updating
            // from an older version without a What's New dialog and people
            // with fresh installs just by the settings alone.
            // So we'll do a heuristic and just check to see if they have
            // visited any stops -- in most cases that will mean they have
            // just installed.
            if (oldVer == 0 && newVer == 7) {
                Integer count = UIHelp.intForQuery(this,
                        ObaContract.Stops.CONTENT_URI,
                        ObaContract.Stops._COUNT);
                if (count != null && count != 0) {
                    showDialog(WHATSNEW_DIALOG);
                }
            }
            else if ((oldVer > 0) && (oldVer < newVer)) {
                showDialog(WHATSNEW_DIALOG);
            }
            // Updates will remove the alarms. This should put them back.
            // (Unfortunately I can't find a way to reschedule them without
            // having the app run again).
            TripService.scheduleAll(this);

            SharedPreferences.Editor edit = settings.edit();
            edit.putInt(WHATS_NEW_VER, appInfo.versionCode);
            edit.commit();
        }
    }

    private void goToBugReport() {
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        // appInfo.versionName
        // Build.MODEL
        // Build.VERSION.RELEASE
        // Build.VERSION.SDK
        // %s\nModel: %s\nOS Version: %s\nSDK Version: %s\
        final String body = getString(R.string.bug_report_body,
                 appInfo.versionName,
                 Build.MODEL,
                 Build.VERSION.RELEASE,
                 Build.VERSION.SDK); // TODO: Change to SDK_INT when we switch to 1.6
        Intent send = new Intent(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_EMAIL,
                    new String[] { getString(R.string.bug_report_dest) });
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.bug_report_subject));
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.setType("message/rfc822");
        try {
            startActivity(Intent.createChooser(send, getString(R.string.bug_report_subject)));
        }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.bug_report_error, Toast.LENGTH_LONG).show();
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

    private boolean isRouteMode() {
        return mRouteId != null;
    }
}
