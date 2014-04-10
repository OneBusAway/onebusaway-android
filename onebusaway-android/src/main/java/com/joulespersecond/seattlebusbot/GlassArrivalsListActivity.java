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

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaSituation;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;

import android.app.ListActivity;
import android.app.LoaderManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class GlassArrivalsListActivity extends ListActivity
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse> {
    //private static final String TAG = "ArrivalInfoActivity";

    public static final String STOP_NAME = ".StopName";

    public static final String STOP_DIRECTION = ".StopDir";

    private static final String TAG = "ArrivalsListFragment";

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

        // This sets the stopId and uri
        setStopId();
        //setUserInfo();

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new GlassArrivalsListAdapter(this);
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        //setListShown(false);
        Log.d(TAG, "Selected stopID = " + mStopId);
        Log.d(TAG, "Selected stop name = " + getIntent().getStringExtra(STOP_NAME));

        mRoutesFilter = ObaContract.StopRouteFilters.get(this, mStopId);

        //LoaderManager.enableDebugLogging(true);
        LoaderManager mgr = getLoaderManager();

        //mgr.initLoader(TRIPS_FOR_STOP_LOADER, null, mTripsForStopCallback);
        mgr.initLoader(ARRIVALS_LIST_LOADER, savedInstanceState, this);

        // Set initial minutesAfter value in the empty list view
        setEmptyText(
                UIHelp.getNoArrivalsMessage(this, getArrivalsLoader().getMinutesAfter(),
                        false)
        );


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
        //UIHelp.showProgress(this, false);

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
            setEmptyText(UIHelp.getNoArrivalsMessage(this,
                    getArrivalsLoader().getMinutesAfter(), false));
            mAdapter.setData(info, mRoutesFilter);
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        //UIHelp.showProgress(this, false);
        mAdapter.setData(null, mRoutesFilter);
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
        }

        //getLoaderManager().restartLoader(TRIPS_FOR_STOP_LOADER, null, mTripsForStopCallback);

        // If our timer would have gone off, then refresh.
        long lastResponseTime = getArrivalsLoader().getLastResponseTime();
        long newPeriod = Math.min(RefreshPeriod, (lastResponseTime + RefreshPeriod)
                - System.currentTimeMillis());
        // Wait at least one second at least, and the full minute at most.
        //Log.d(TAG, "Refresh period:" + newPeriod);
        if (newPeriod <= 0) {
            refresh();
        } else {
            mRefreshHandler.postDelayed(mRefresh, newPeriod);
        }

        super.onResume();
    }

    @Override
    protected void onPause() {
        mRefreshHandler.removeCallbacks(mRefresh);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }


}
