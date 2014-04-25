/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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

import com.google.android.glass.touchpad.GestureDetector;
import com.google.glass.widget.SliderView;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaSituation;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.glass.ObaStopsForLocationTask;
import com.joulespersecond.oba.glass.SensorListController;
import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.region.ObaRegionsTask;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;

import android.app.ListActivity;
import android.app.LoaderManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


public class GlassArrivalsListActivity extends ListActivity
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse>, ObaRegionsTask.Callback,
        ObaStopsForLocationTask.Callback, LocationListener {

    public static final String STOP_NAME = ".StopName";

    public static final String STOP_DIRECTION = ".StopDir";

    private static final String TAG = "GlassArrivalsListActivity";

    private static final long RefreshPeriod = 60 * 1000;

    private static int TRIPS_FOR_STOP_LOADER = 1;

    private static int ARRIVALS_LIST_LOADER = 2;

    private GlassArrivalsListAdapter mAdapter;

    private ArrivalsListHeader mHeader;

    private View mFooter;

    private View mEmptyList;

    private AlertList mAlertList;

    private ObaStop mStop;

    private String mStopId;

    private Uri mStopUri;

    private ArrayList<String> mRoutesFilter;

    private int mLastResponseLength = -1; // Keep copy locally, since loader overwrites

    // encapsulated info before onLoadFinished() is called
    private boolean mLoadedMoreArrivals = false;

    private boolean mFavorite = false;

    private String mStopUserName;

    private SliderView mIndeterm;

    private static final long REGION_UPDATE_THRESHOLD = 1000 * 60 * 60 * 24 * 7;

    ObaRegionsTask mObaRegionsTask;

    ObaStopsForLocationTask mObaStopsForLocationTask;

    TextView mProgressMessage;

    Location mLastKnownLocation;

    LocationManager mLocationManager;

    ListView mListView;

    SensorListController mListController;

    GestureDetector mGestureDetector;

    public static class Builder {

        private Context mContext;

        private Intent mIntent;

        public Builder(Context context, String stopId) {
            mContext = context;
            mIntent = new Intent(context, GlassArrivalsListActivity.class);
            mIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        }

        public Builder(Context context, ObaStop stop) {
            mContext = context;
            mIntent = new Intent(context, GlassArrivalsListActivity.class);
            mIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
            setStopName(stop.getName());
            setStopDirection(stop.getDirection());
        }

        public Builder setStopName(String stopName) {
            mIntent.putExtra(STOP_NAME, stopName);
            return this;
        }

        public Builder setStopDirection(String stopDir) {
            mIntent.putExtra(STOP_DIRECTION, stopDir);
            return this;
        }

        public Builder setUpMode(String mode) {
            mIntent.putExtra(NavHelp.UP_MODE, mode);
            return this;
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void start() {
            if (mContext instanceof Service) {
                mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            mContext.startActivity(mIntent);
        }
    }

    //
    // Two of the most common methods of starting this activity.
    //
    public static void start(Context context, String stopId) {
        new Builder(context, stopId).start();
    }

    public static void start(Context context, ObaStop stop) {
        new Builder(context, stop).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.glass_arrival_card_list);

        // Setup progress bar shown while we're loading the list
        mEmptyList = getLayoutInflater().inflate(R.layout.glass_arrivals_list_empty, null);
        ((ViewGroup) getListView().getParent()).addView(mEmptyList);

        /* Following line causes the ListView to lose focus on Glass,
           and we can't scroll after that (or seem to regain focus)
           So, we manually turn the progress bar on and off instead
        getListView().setEmptyView(mEmptyList);
        */
        mIndeterm = (SliderView) mEmptyList.findViewById(R.id.indeterm_slider);
        mIndeterm.startIndeterminate();

        mProgressMessage = (TextView) mEmptyList.findViewById(R.id.progress_message);

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new GlassArrivalsListAdapter(this);
        setListAdapter(mAdapter);

        // Set up the LoaderManager now
        getLoaderManager();

        initListController();

        initLocation();

        initRegions();
    }

    @Override
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        return new GlassArrivalsListLoader(this, mStopId);
    }

    //
    // This is where the bulk of the initialization takes place to create
    // this screen.
    //
    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader,
            ObaArrivalInfoResponse result) {
//        UIHelp.showProgress(this, false);
        mIndeterm.stopIndeterminate();
        mIndeterm.stopProgress();
        mEmptyList.setVisibility(View.GONE);

        // Set the bus stop icon
        ImageView imageView = (ImageView) findViewById(R.id.bus_icon_footer);
        imageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_bus));

        ObaArrivalInfo[] info = null;
        List<ObaSituation> situations = null;

        if (result.getCode() == ObaApi.OBA_OK) {
            if (mStop == null) {
                mStop = result.getStop();
                addToDB(mStop);
            }
            info = result.getArrivalInfo();
            situations = result.getSituations();
        } else {
            // If there was a last good response, then this is a refresh
            // and we should use a toast. Otherwise, it's a initial
            // page load and we want to display the error in the empty text.
            ObaArrivalInfoResponse lastGood =
                    getArrivalsLoader().getLastGoodResponse();
            if (lastGood != null) {
                // Refresh error
                Toast.makeText(this,
                        R.string.generic_comm_error_toast,
                        Toast.LENGTH_LONG).show();
                info = lastGood.getArrivalInfo();
                situations = lastGood.getSituations();
            } else {
                setEmptyText(getString(UIHelp.getStopErrorString(this, result.getCode())));
            }
        }

        setResponseData(info, situations);

        TextView stopName = (TextView) findViewById(R.id.stop_name_footer);
        stopName.setText(mStop.getName());

        // The list should now be shown.
//        if (isResumed()) {
//            setListShown(true);
//        } else {
//            setListShownNoAnimation(true);
//        }

        // Post an update
        mRefreshHandler.postDelayed(mRefresh, RefreshPeriod);

        // If the user just tried to load more arrivals, determine if we
        // should show a Toast in the case where no additional arrivals were loaded
        if (mLoadedMoreArrivals) {
            if (info == null || info.length == 0 || mLastResponseLength != info.length) {
                /*
                Don't show the toast, since:
                 1) an error occurred (and user has already seen the error message),
                 2) no records were returned (and empty list message is already shown), or
                 3) more arrivals were actually loaded
                */
                mLoadedMoreArrivals = false;
            } else if (mLastResponseLength == info.length) {
                // No additional arrivals were included in the response, show a toast
                Toast.makeText(this,
                        UIHelp.getNoArrivalsMessage(this,
                                getArrivalsLoader().getMinutesAfter(), true),
                        Toast.LENGTH_LONG
                ).show();
                mLoadedMoreArrivals = false;  // Only show the toast once
            }
        }

        //TestHelp.notifyLoadFinished(getActivity());
    }

    private void setResponseData(ObaArrivalInfo[] info, List<ObaSituation> situations) {
        //mHeader.refresh();

        // Convert any stop situations into a list of alerts
//        if (situations != null) {
//            refreshSituations(situations);
//        } else {
//            refreshSituations(new ArrayList<ObaSituation>());
//        }

        if (info != null) {
            // Reset the empty text just in case there is no data.
            if (info.length == 0) {
                setEmptyText(UIHelp.getNoArrivalsMessage(this,
                        getArrivalsLoader().getMinutesAfter(), false));
            }
            mAdapter.setData(info, mRoutesFilter);
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        //UIHelp.showProgress(this, false);
        mAdapter.setData(null, mRoutesFilter);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // We need to pass events through to the list controller
        if (mListController != null) {
            return mListController.onMotionEvent(event);
        }
        return false;
    }

    //
    // Helpers
    //
    private GlassArrivalsListLoader getArrivalsLoader() {
        Loader<ObaArrivalInfoResponse> l =
                getLoaderManager().getLoader(ARRIVALS_LIST_LOADER);
        return (GlassArrivalsListLoader) l;
    }

    public void setEmptyText(CharSequence text) {
//        TextView noArrivals = (TextView) mEmptyList.findViewById(R.id.noArrivals);
//        noArrivals.setText(text);
        mEmptyList.setVisibility(View.VISIBLE);
        mProgressMessage.setText(text);
    }

    private void initListController() {
        mListView = getListView();
        mListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        mListView.setSelector(android.R.color.transparent);
        mListView.setClickable(true);

        mListController = new SensorListController(this, mListView);
    }

    private void initLoader(Bundle bundle) {
        // This sets the stopId and uri
        setStopId();
        //setUserInfo();

        Log.d(TAG, "Selected stopID = " + mStopId);
        Log.d(TAG, "Selected stop name = " + getIntent().getStringExtra(STOP_NAME));

        mRoutesFilter = ObaContract.StopRouteFilters.get(this, mStopId);

        //LoaderManager.enableDebugLogging(true);
        LoaderManager mgr = getLoaderManager();

        //mgr.initLoader(TRIPS_FOR_STOP_LOADER, null, mTripsForStopCallback);
        mgr.initLoader(ARRIVALS_LIST_LOADER, bundle, this);

        getArrivalsLoader().onContentChanged();
    }

    //
    // Refreshing!
    //
    private void refresh() {
        //UIHelp.showProgress(this, true);
        // Get last response length now, since its overwritten within
        // ArrivalsListLoader before onLoadFinished() is called
        ObaArrivalInfoResponse lastGood =
                getArrivalsLoader().getLastGoodResponse();
        if (lastGood != null) {
            mLastResponseLength = lastGood.getArrivalInfo().length;
        }
        getArrivalsLoader().onContentChanged();
    }

    private final Handler mRefreshHandler = new Handler();

    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refresh();
        }
    };

    private void setStopId() {
        //Uri uri = (Uri) getIntent().getParcelableExtra(FragmentUtils.URI);
        Uri uri = (Uri) getIntent().getData();
        if (uri == null) {
            Log.e(TAG, "No URI in arguments");
            return;
        }
        mStopId = uri.getLastPathSegment();
        mStopUri = uri;
    }

    private static final String[] USER_PROJECTION = {
            ObaContract.Stops.FAVORITE,
            ObaContract.Stops.USER_NAME
    };

    private void setUserInfo() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(mStopUri, USER_PROJECTION, null, null, null);
        if (c != null) {
            try {
                if (c.moveToNext()) {
                    mFavorite = (c.getInt(0) == 1);
                    mStopUserName = c.getString(1);
                }
            } finally {
                c.close();
            }
        }
    }

    private void addToDB(ObaStop stop) {
        String name = MyTextUtils.toTitleCase(stop.getName());

        // Update the database
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops.CODE, stop.getStopCode());
        values.put(ObaContract.Stops.NAME, name);
        values.put(ObaContract.Stops.DIRECTION, stop.getDirection());
        values.put(ObaContract.Stops.LATITUDE, stop.getLatitude());
        values.put(ObaContract.Stops.LONGITUDE, stop.getLongitude());
        if (Application.get().getCurrentRegion() != null) {
            values.put(ObaContract.Stops.REGION_ID, Application.get().getCurrentRegion().getId());
        }
        ObaContract.Stops.insertOrUpdate(this, stop.getId(), values, true);
    }

    private static final String[] TRIPS_PROJECTION = {
            ObaContract.Trips._ID, ObaContract.Trips.NAME
    };


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        // Try to show any old data just in case we're coming out of sleep
        GlassArrivalsListLoader loader = getArrivalsLoader();
        if (loader != null) {
            ObaArrivalInfoResponse lastGood = loader.getLastGoodResponse();
            if (lastGood != null) {
                setResponseData(lastGood.getArrivalInfo(), lastGood.getSituations());
            }

            long lastResponseTime = loader.getLastResponseTime();
            long newPeriod = Math.min(RefreshPeriod, (lastResponseTime + RefreshPeriod)
                    - System.currentTimeMillis());
            // Wait at least one second at least, and the full minute at most.
            //Log.d(TAG, "Refresh period:" + newPeriod);
            if (newPeriod <= 0) {
                refresh();
            } else {
                mRefreshHandler.postDelayed(mRefresh, newPeriod);
            }
        }

        mListController.onResume();

        super.onResume();
    }

    @Override
    protected void onPause() {
        mRefreshHandler.removeCallbacks(mRefresh);
        mListController.onPause();
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    private void initLocation() {
        mLastKnownLocation = UIHelp.getLocation2(this);

        // Temp UATC location for testing multiple stops with layouts
//        mLastKnownLocation = new Location("temp");
//        mLastKnownLocation.setLatitude(28.066380);
//        mLastKnownLocation.setLongitude(-82.429886);
        // Temp location for testing "no arrivals in next X minutes"
//        mLastKnownLocation = new Location("temp");
//        mLastKnownLocation.setLatitude(27.9884);
//        mLastKnownLocation.setLongitude(-82.3024);

        if (mLastKnownLocation == null) {
            // Start a LocationListener to force a refresh of location
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            List<String> providers = mLocationManager.getProviders(true);
            for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
                mLocationManager.requestLocationUpdates(i.next(), 0, 0, this);
            }
        }
    }

    private void initRegions() {
        boolean forceReload = false;
        boolean showProgressDialog = false;

        SharedPreferences settings = Application.getPrefs();

        //If we don't have region info selected, or if enough time has passed since last region info update AND user has selected auto-refresh,
        //force contacting the server again
        if (Application.get().getCurrentRegion() == null ||
                (settings.getBoolean(getString(R.string.preference_key_auto_refresh_regions), true)
                        &&
                        new Date().getTime() - Application.get().getLastRegionUpdateDate()
                                > REGION_UPDATE_THRESHOLD)
                ) {
            forceReload = true;
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "Region info has expired (or does not exist), forcing a reload from the server...");
            }
        }

        if (Application.get().getCurrentRegion() != null) {
            Log.d(TAG, "Using previous region: " + Application.get().getCurrentRegion().getName());

            if (mLastKnownLocation != null) {
                getClosestStops(mLastKnownLocation);
            } else {
                Log.d(TAG,
                        "Couldn't get user's location to find nearby stops - will wait for location");
                return;
            }
        }

        if (mLastKnownLocation != null) {
            //Check region status, possibly forcing a reload from server and checking proximity to current region
            //Normal progress dialog doesn't work, so hard-code false as last argument
            mObaRegionsTask = new ObaRegionsTask(this, this, forceReload, showProgressDialog);
            mObaRegionsTask.execute();
        } else {
            Log.d(TAG,
                    "Couldn't get user's location to find closest region - will wait for location");
            return;
        }
    }

    private void getClosestStops(Location location) {
        // Update progress message
        mProgressMessage.setText(getString(R.string.finding_closest_stop));

        // Find the closest stops
        mObaStopsForLocationTask = new ObaStopsForLocationTask(this, this, location);
        mObaStopsForLocationTask.execute();
    }

    /*
     * Callbacks from tasks
     */

    // For Oba Regions Task
    @Override
    public void onTaskFinished(boolean currentRegionChanged) {
        if (currentRegionChanged) {
            Log.d(TAG, "New region selected - now finding stops...");
            // The current region has changed since last startup, so abort any existing stop
            // request in progress (which would be using the previous region API) and start a new one
            if (mObaStopsForLocationTask != null) {
                mObaStopsForLocationTask.cancel(true);
            }

            // Update progress message
            mProgressMessage.setText(getString(R.string.finding_closest_stop));

            // Find the closest stops
            mObaStopsForLocationTask = new ObaStopsForLocationTask(this, this, mLastKnownLocation);
            mObaStopsForLocationTask.execute();
        }
    }

    // For StopsForLocation Request
    @Override
    public void onTaskFinished(ObaStopsForLocationResponse response) {
        Log.d(TAG, "Found stops.");
        // Find closest stop
        ObaStop closestStop;
        closestStop = UIHelp.getClosestStop(this, response.getStops(), mLastKnownLocation);

        if (closestStop != null) {
            Log.d(TAG, "Closest stop is: " + closestStop.getName());
            mProgressMessage.setText(getString(R.string.getting_arrival_times));
            Intent i = new Builder(this, closestStop).getIntent();
            this.setIntent(i);
            initLoader(i.getExtras());
        } else {
            Log.e(TAG, "No stops returned");
        }

        // TODO - Set up options menu showing next 5 closest stops, ordered by distance
    }

    /*
     * Location Updates - a workaround for XE16, which seems to return null locations often
     * from getLastKnownLocation()
     */

    @Override
    public void onLocationChanged(Location location) {
        if (mLastKnownLocation == null) {
            Log.d(TAG, "Got new location update from listener.");
            // At least one location should be available now - get the best one
            mLastKnownLocation = UIHelp.getLocation2(this);

            if (mLastKnownLocation == null) {
                // This shouldn't happen, but if it does, use the location that was just passed in
                mLastKnownLocation = location;
            }

            // Stop listening for updates
            mLocationManager.removeUpdates(this);

            if (Application.get().getCurrentRegion() == null) {
                // Still need to figure out what region we're in
                mObaRegionsTask = new ObaRegionsTask(this, this, true, false);
                mObaRegionsTask.execute();
            } else {
                // We already know our region, so go straight to finding closest stops
                getClosestStops(mLastKnownLocation);
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
