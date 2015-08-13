/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
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
package com.joulespersecond.seattlebusbot;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.joulespersecond.oba.ObaAnalytics;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaSituation;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;
import com.joulespersecond.seattlebusbot.util.MyTextUtils;
import com.joulespersecond.seattlebusbot.util.UIHelp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

//
// We don't use the ListFragment because the support library's version of
// the ListFragment doesn't work well with our header.
//
public class ArrivalsListFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse>,
        ArrivalsListHeader.Controller {

    private static final String TAG = "ArrivalsListFragment";

    private static final long RefreshPeriod = 60 * 1000;

    private static int TRIPS_FOR_STOP_LOADER = 1;

    private static int ARRIVALS_LIST_LOADER = 2;

    private ArrivalsListAdapter mAdapter;

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

    private TripsForStopCallback mTripsForStopCallback;

    // The list of situation alerts
    private ArrayList<SituationAlert> mSituationAlerts;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set and add the view that is shown if no arrival information is returned by the REST API
        getListView().setEmptyView(mEmptyList);
        ((ViewGroup) getListView().getParent()).addView(mEmptyList);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        mAlertList = new AlertList(getActivity());
        mAlertList.initView(getView().findViewById(R.id.arrivals_alert_list));

        mHeader = new ArrivalsListHeader(getActivity(), this);
        View header = getView().findViewById(R.id.arrivals_list_header);
        mHeader.initView(header);
        mHeader.refresh();

        // Setup list footer button to load more arrivals (when arrivals are shown)
        Button loadMoreArrivals = (Button) mFooter.findViewById(R.id.load_more_arrivals);
        loadMoreArrivals.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMoreArrivals();
            }
        });
        getListView().addFooterView(mFooter);

        // Repeat for the load more arrivals button in the empty list view
        Button loadMoreArrivalsEmptyList = (Button) mEmptyList
                .findViewById(R.id.load_more_arrivals);
        loadMoreArrivalsEmptyList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMoreArrivals();
            }
        });

        // This sets the stopId and uri
        setStopId();
        setUserInfo();

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new ArrivalsListAdapter(getActivity());
        setListAdapter(mAdapter);

        // Start out with a progress indicator.
        setListShown(false);

        mRoutesFilter = ObaContract.StopRouteFilters.get(getActivity(), mStopId);
        mTripsForStopCallback = new TripsForStopCallback();

        //LoaderManager.enableDebugLogging(true);
        LoaderManager mgr = getLoaderManager();

        mgr.initLoader(TRIPS_FOR_STOP_LOADER, null, mTripsForStopCallback);
        mgr.initLoader(ARRIVALS_LIST_LOADER, getArguments(), this);

        // Set initial minutesAfter value in the empty list view
        setEmptyText(
                UIHelp.getNoArrivalsMessage(getActivity(), getArrivalsLoader().getMinutesAfter(),
                        false)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        mFooter = inflater.inflate(R.layout.arrivals_list_footer, null);
        mEmptyList = inflater.inflate(R.layout.arrivals_list_empty, null);
        return inflater.inflate(R.layout.fragment_arrivals_list, null);
    }

    @Override
    public void onPause() {
        mRefreshHandler.removeCallbacks(mRefresh);
        super.onPause();
    }

    @Override
    public void onResume() {
        // Try to show any old data just in case we're coming out of sleep
        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader != null) {
            ObaArrivalInfoResponse lastGood = loader.getLastGoodResponse();
            if (lastGood != null) {
                setResponseData(lastGood.getArrivalInfo(), lastGood.getSituations());
            }
        }

        getLoaderManager().restartLoader(TRIPS_FOR_STOP_LOADER, null, mTripsForStopCallback);

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
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        return new ArrivalsListLoader(getActivity(), mStopId);
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);

        if (Build.VERSION.SDK_INT >= 14) {
            AccessibilityManager am = (AccessibilityManager) getActivity().getSystemService(
                    getActivity().ACCESSIBILITY_SERVICE);

            Boolean isTalkBackEnabled = am.isTouchExplorationEnabled();
            if (isTalkBackEnabled)
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.ACCESSIBILITY.toString(),
                        getString(R.string.analytics_action_touch_exploration),
                        getString(R.string.analytics_label_talkback) + getClass().getSimpleName()
                                + " using TalkBack");
        }
    }

    //
    // This is where the bulk of the initialization takes place to create
    // this screen.
    //
    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader,
                               ObaArrivalInfoResponse result) {
        UIHelp.showProgress(this, false);

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
                Toast.makeText(getActivity(),
                        R.string.generic_comm_error_toast,
                        Toast.LENGTH_LONG).show();
                info = lastGood.getArrivalInfo();
                situations = lastGood.getSituations();
            } else {
                setEmptyText(getString(UIHelp.getStopErrorString(getActivity(), result.getCode())));
            }
        }

        setResponseData(info, situations);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }

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
                Toast.makeText(getActivity(),
                        UIHelp.getNoArrivalsMessage(getActivity(),
                                getArrivalsLoader().getMinutesAfter(), true),
                        Toast.LENGTH_LONG
                ).show();
                mLoadedMoreArrivals = false;  // Only show the toast once
            }
        }

        //TestHelp.notifyLoadFinished(getActivity());
    }

    private void setResponseData(ObaArrivalInfo[] info, List<ObaSituation> situations) {
        mHeader.refresh();

        // Convert any stop situations into a list of alerts
        if (situations != null) {
            refreshSituations(situations);
        } else {
            refreshSituations(new ArrayList<ObaSituation>());
        }

        if (info != null) {
            // Reset the empty text just in case there is no data.
            setEmptyText(UIHelp.getNoArrivalsMessage(getActivity(),
                    getArrivalsLoader().getMinutesAfter(), false));
            mAdapter.setData(info, mRoutesFilter);
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        UIHelp.showProgress(this, false);
        mAdapter.setData(null, mRoutesFilter);
    }

    //
    // Action Bar / Options Menu
    //
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.arrivals_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        String title = mFavorite ?
                getString(R.string.stop_info_option_removestar) :
                getString(R.string.stop_info_option_addstar);
        menu.findItem(R.id.toggle_favorite)
                .setTitle(title)
                .setTitleCondensed(title);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.show_on_map) {
            if (mStop != null) {
                HomeActivity.start(getActivity(),
                        mStop.getId(),
                        mStop.getLatitude(),
                        mStop.getLongitude());
            }
            return true;
        } else if (id == R.id.refresh) {
            refresh();
            return true;
        } else if (id == R.id.filter) {
            if (mStop != null) {
                showRoutesFilterDialog();
            }
        } else if (id == R.id.edit_name) {
            mHeader.beginNameEdit(null);
        } else if (id == R.id.toggle_favorite) {
            setFavorite(!mFavorite);
            mHeader.refresh();
        } else if (id == R.id.report_problem) {
            if (mStop != null) {
                ReportStopProblemFragment.show(getSherlockActivity(), mStop);
            }
        }
        return false;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final ArrivalInfo stop = (ArrivalInfo) getListView().getItemAtPosition(position);
        if (stop == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.stop_info_item_options_title);

        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        final ObaRoute route = response.getRoute(stop.getInfo().getRouteId());
        final String url = route != null ? route.getUrl() : null;
        final boolean hasUrl = !TextUtils.isEmpty(url);
        // Check to see if the trip name is visible.
        // (we don't have any other state, so this is good enough)
        int options;
        View tripView = v.findViewById(R.id.trip_info);
        if (tripView.getVisibility() != View.GONE) {
            if (hasUrl) {
                options = R.array.stop_item_options_edit;
            } else {
                options = R.array.stop_item_options_edit_noschedule;
            }
        } else if (hasUrl) {
            options = R.array.stop_item_options;
        } else {
            options = R.array.stop_item_options_noschedule;
        }
        builder.setItems(options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    goToTrip(stop);
                } else if (which == 1) {
                    goToRoute(stop);
                } else if (which == 2) {
                    ArrayList<String> routes = new ArrayList<String>(1);
                    routes.add(stop.getInfo().getRouteId());
                    setRoutesFilter(routes);
                    mHeader.refresh();
                } else if (hasUrl && which == 3) {
                    UIHelp.goToUrl(getActivity(), url);
                } else if ((!hasUrl && which == 3) || (hasUrl && which == 4)) {
                    ReportTripProblemFragment.show(getSherlockActivity(), stop.getInfo());
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    //
    // ActivityListHeader.Controller
    //
    @Override
    public String getStopName() {
        String name;
        if (mStop != null) {
            name = mStop.getName();
        } else {
            // Check the arguments
            Bundle args = getArguments();
            name = args.getString(ArrivalsListActivity.STOP_NAME);
        }
        return MyTextUtils.toTitleCase(name);
    }

    @Override
    public String getStopDirection() {
        if (mStop != null) {
            return mStop.getDirection();
        } else {
            // Check the arguments
            Bundle args = getArguments();
            return args.getString(ArrivalsListActivity.STOP_DIRECTION);
        }
    }

    @Override
    public String getUserStopName() {
        return mStopUserName;
    }

    @Override
    public void setUserStopName(String name) {
        ContentResolver cr = getActivity().getContentResolver();
        ContentValues values = new ContentValues();
        if (TextUtils.isEmpty(name)) {
            values.putNull(ObaContract.Stops.USER_NAME);
            mStopUserName = null;
        } else {
            values.put(ObaContract.Stops.USER_NAME, name);
            mStopUserName = name;
        }
        cr.update(mStopUri, values, null, null);
    }

    @Override
    public ArrayList<String> getRoutesFilter() {
        // If mStop is null, we don't want the ArrivalsListHeader calling
        // getNumRoutes, so if we don't have a stop we pretend we don't have
        // a route filter at all.
        if (mStop != null) {
            return mRoutesFilter;
        } else {
            return null;
        }
    }

    @Override
    public void setRoutesFilter(ArrayList<String> routes) {
        mRoutesFilter = routes;
        ObaContract.StopRouteFilters.set(getActivity(), mStopId, mRoutesFilter);
        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        mAdapter.setData(response.getArrivalInfo(), mRoutesFilter);
    }

    @Override
    public long getLastGoodResponseTime() {
        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader == null) {
            return 0;
        }
        return loader.getLastGoodResponseTime();
    }

    @Override
    public int getNumRoutes() {
        if (mStop != null) {
            return mStop.getRouteIds().length;
        } else {
            return 0;
        }
    }

    @Override
    public boolean isFavorite() {
        return mFavorite;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        if (ObaContract.Stops.markAsFavorite(getActivity(), mStopUri, favorite)) {
            mFavorite = favorite;
        }
        // Apparently we can't rely on onPrepareOptionsMenu to set the
        // menus like we did before...
        getActivity().supportInvalidateOptionsMenu();

        //Analytics
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                getString(R.string.analytics_action_edit_field),
                getString(R.string.analytics_label_edit_field));

        return mFavorite;
    }

    @Override
    public AlertList getAlertList() {
        return mAlertList;
    }

    private void showRoutesFilterDialog() {
        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        final List<ObaRoute> routes = response.getRoutes(mStop.getRouteIds());
        final int len = routes.size();
        final ArrayList<String> filter = mRoutesFilter;

        // mRouteIds = new ArrayList<String>(len);
        String[] items = new String[len];
        boolean[] checks = new boolean[len];

        // Go through all the stops, add them to the Ids and Names
        // For each stop, if it is in the enabled list, mark it as checked.
        for (int i = 0; i < len; ++i) {
            final ObaRoute route = routes.get(i);
            // final String id = route.getId();
            // mRouteIds.add(i, id);
            items[i] = UIHelp.getRouteDisplayName(route);
            if (filter.contains(route.getId())) {
                checks[i] = true;
            }
        }
        // Arguments
        Bundle args = new Bundle();
        args.putStringArray(RoutesFilterDialog.ITEMS, items);
        args.putBooleanArray(RoutesFilterDialog.CHECKS, checks);
        RoutesFilterDialog frag = new RoutesFilterDialog();
        frag.setArguments(args);
        frag.show(getActivity().getSupportFragmentManager(), ".RoutesFilterDialog");
    }

    public static class RoutesFilterDialog extends DialogFragment
            implements DialogInterface.OnMultiChoiceClickListener,
            DialogInterface.OnClickListener {

        static final String ITEMS = ".items";

        static final String CHECKS = ".checks";

        private boolean[] mChecks;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            String[] items = args.getStringArray(ITEMS);
            mChecks = args.getBooleanArray(CHECKS);
            if (savedInstanceState != null) {
                mChecks = args.getBooleanArray(CHECKS);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            return builder.setTitle(R.string.stop_info_filter_title)
                    .setMultiChoiceItems(items, mChecks, this)
                    .setPositiveButton(R.string.stop_info_save, this)
                    .setNegativeButton(R.string.stop_info_cancel, null)
                    .create();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putBooleanArray(CHECKS, mChecks);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            ArrivalsListActivity act = (ArrivalsListActivity) getActivity();
            // Get the fragment we want...
            ArrivalsListFragment frag = act.getArrivalsListFragment();
            frag.setRoutesFilter(mChecks);
            dialog.dismiss();
        }

        @Override
        public void onClick(DialogInterface arg0, int which, boolean isChecked) {
            mChecks[which] = isChecked;
        }
    }

    private void setRoutesFilter(boolean[] checks) {
        final int len = checks.length;
        final ArrayList<String> newFilter = new ArrayList<String>(len);

        ObaArrivalInfoResponse response =
                getArrivalsLoader().getLastGoodResponse();
        final List<ObaRoute> routes = response.getRoutes(mStop.getRouteIds());
        assert (routes.size() == len);

        for (int i = 0; i < len; ++i) {
            final ObaRoute route = routes.get(i);
            if (checks[i]) {
                newFilter.add(route.getId());
            }
        }
        // If the size of the filter is the number of routes
        // (i.e., the user selected every checkbox) act then
        // don't select any.
        if (newFilter.size() == len) {
            newFilter.clear();
        }

        setRoutesFilter(newFilter);
        mHeader.refresh();
    }

    //
    // Navigation
    //
    private void goToTrip(ArrivalInfo stop) {
        ObaArrivalInfo stopInfo = stop.getInfo();
        TripInfoActivity.start(getActivity(),
                stopInfo.getTripId(),
                mStopId,
                stopInfo.getRouteId(),
                stopInfo.getShortName(),
                mStop.getName(),
                stopInfo.getScheduledDepartureTime(),
                stopInfo.getHeadsign());
    }

    private void goToRoute(ArrivalInfo stop) {
        RouteInfoActivity.start(getActivity(),
                stop.getInfo().getRouteId());
    }

    //
    // Helpers
    //
    private ArrivalsListLoader getArrivalsLoader() {
        Loader<ObaArrivalInfoResponse> l =
                getLoaderManager().getLoader(ARRIVALS_LIST_LOADER);
        return (ArrivalsListLoader) l;
    }

    @Override
    public void setEmptyText(CharSequence text) {
        TextView noArrivals = (TextView) mEmptyList.findViewById(R.id.noArrivals);
        noArrivals.setText(text);
    }

    private void loadMoreArrivals() {
        getArrivalsLoader().incrementMinutesAfter();
        mLoadedMoreArrivals = true;
        refresh();

        //Analytics
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                getActivity().getString(R.string.analytics_action_button_press),
                getActivity().getString(R.string.analytics_label_button_press));
    }

    //
    // Refreshing!
    //
    private void refresh() {
        if (isAdded()) {
            UIHelp.showProgress(this, true);
            // Get last response length now, since its overwritten within
            // ArrivalsListLoader before onLoadFinished() is called
            ObaArrivalInfoResponse lastGood =
                    getArrivalsLoader().getLastGoodResponse();
            if (lastGood != null) {
                mLastResponseLength = lastGood.getArrivalInfo().length;
            }
            getArrivalsLoader().onContentChanged();
        }
    }

    private final Handler mRefreshHandler = new Handler();

    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refresh();
        }
    };

    private void setStopId() {
        Uri uri = (Uri) getArguments().getParcelable(FragmentUtils.URI);
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
        ContentResolver cr = getActivity().getContentResolver();
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
        ObaContract.Stops.insertOrUpdate(getActivity(), stop.getId(), values, true);
    }

    private static final String[] TRIPS_PROJECTION = {
            ObaContract.Trips._ID, ObaContract.Trips.NAME
    };

    //
    // The asynchronously loads the trips for stop list.
    //
    private class TripsForStopCallback
            implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getActivity(),
                    ObaContract.Trips.CONTENT_URI,
                    TRIPS_PROJECTION,
                    ObaContract.Trips.STOP_ID + "=?",
                    new String[]{mStopId},
                    null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
            ContentQueryMap map =
                    new ContentQueryMap(c, ObaContract.Trips._ID, true, null);
            // Call back into the fragment and say we've finished this.
            mAdapter.setTripsForStop(map);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    }

    //
    // Situations
    //
    private class SituationAlert implements AlertList.Alert {

        private final ObaSituation mSituation;

        SituationAlert(ObaSituation situation) {
            mSituation = situation;
        }

        @Override
        public String getId() {
            return mSituation.getId();
        }

        @Override
        public int getType() {
            if ("noImpact".equals(mSituation.getSeverity())) {
                return AlertList.Alert.TYPE_INFO;
            } else if ("severe".equals(mSituation.getSeverity())) {
                return AlertList.Alert.TYPE_ERROR;
            } else {
                return AlertList.Alert.TYPE_WARNING;
            }
        }

        @Override
        public int getFlags() {
            return AlertList.Alert.FLAG_HASMORE;
        }

        @Override
        public CharSequence getString() {
            return mSituation.getSummary();
        }

        @Override
        public void onClick() {
            SituationFragment.show(getSherlockActivity(), mSituation);
        }

        @Override
        public int hashCode() {
            return getId().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SituationAlert other = (SituationAlert) obj;
            if (!getId().equals(other.getId())) {
                return false;
            }
            return true;
        }
    }

    private void refreshSituations(List<ObaSituation> situations) {
        // First, remove any existing situations
        if (mSituationAlerts != null) {
            for (SituationAlert alert : mSituationAlerts) {
                mAlertList.remove(alert);
            }
        }
        mSituationAlerts = null;

        if (situations.isEmpty()) {
            // The normal scenario
            return;
        }

        mSituationAlerts = new ArrayList<SituationAlert>();

        for (ObaSituation situation : situations) {
            if (UIHelp.isActiveWindowForSituation(situation, System.currentTimeMillis())) {
                SituationAlert alert = new SituationAlert(situation);
                mSituationAlerts.add(alert);
            }
        }
        mAlertList.addAll(mSituationAlerts);
    }
}
