/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.MyTextUtils;
import org.onebusaway.android.util.UIHelp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//
// We don't use the ListFragment because the support library's version of
// the ListFragment doesn't work well with our header.
//
public class ArrivalsListFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse>,
        ArrivalsListHeader.Controller {

    private static final String TAG = "ArrivalsListFragment";

    public static final String STOP_NAME = ".StopName";

    public static final String STOP_DIRECTION = ".StopDir";

    /**
     * Comma-delimited set of routes that serve this stop
     * See {@link org.onebusaway.android.util.UIHelp#serializeRouteDisplayNames(ObaStop,
     * java.util.HashMap)}
     */
    public static final String STOP_ROUTES = ".StopRoutes";

    public static final String STOP_LAT = ".StopLatitude";

    public static final String STOP_LON = ".StopLongitude";

    /**
     * If set to true, the fragment is using a header external to this layout, and shouldn't
     * instantiate its own header view
     */
    public static final String EXTERNAL_HEADER = ".ExternalHeader";

    private static final long RefreshPeriod = 60 * 1000;

    private static int TRIPS_FOR_STOP_LOADER = 1;

    private static int ARRIVALS_LIST_LOADER = 2;

    private ArrivalsListAdapter mAdapter;

    private ArrivalsListHeader mHeader;

    private View mHeaderView;

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

    // Set to true if we're using an external header not in this layout (e.g., if this fragment is in a sliding panel)
    private boolean mExternalHeader = false;

    private Listener mListener;

    ObaArrivalInfo[] mArrivalInfo;

    public interface Listener {

        /**
         * Called when the ListView has been created
         *
         * @param listView the ListView that was just created
         */
        void onListViewCreated(ListView listView);

        /**
         * Called when new arrival times have been retrieved
         *
         * @param response new arrival information
         */
        void onArrivalTimesUpdated(final ObaArrivalInfoResponse response);
    }

    /**
     * Builds an intent used to set the stop for the ArrivalListFragment directly
     * (i.e., when ArrivalsListActivity is not used)
     */
    public static class IntentBuilder {

        private Intent mIntent;

        public IntentBuilder(Context context, String stopId) {
            mIntent = new Intent(context, ArrivalsListFragment.class);
            mIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));
        }

        /**
         * @param stop   ObaStop to be set
         * @param routes a HashMap of all route display names that may serve this stop - key is
         *               routeId
         */
        public IntentBuilder(Context context, ObaStop stop, HashMap<String, ObaRoute> routes) {
            mIntent = new Intent(context, ArrivalsListFragment.class);
            mIntent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
            setStopName(stop.getName());
            setStopDirection(stop.getDirection());
            setStopRoutes(UIHelp.serializeRouteDisplayNames(stop, routes));
            setStopLocation(stop.getLocation());
        }

        public IntentBuilder setStopName(String stopName) {
            mIntent.putExtra(ArrivalsListFragment.STOP_NAME, stopName);
            return this;
        }

        public IntentBuilder setStopDirection(String stopDir) {
            mIntent.putExtra(ArrivalsListFragment.STOP_DIRECTION, stopDir);
            return this;
        }

        public IntentBuilder setStopLocation(Location stopLocation) {
            mIntent.putExtra(ArrivalsListFragment.STOP_LAT, stopLocation.getLatitude());
            mIntent.putExtra(ArrivalsListFragment.STOP_LON, stopLocation.getLongitude());
            return this;
        }

        /**
         * Sets the routes that serve this stop via a comma-delimited set of route display names
         * <p/>
         * See {@link org.onebusaway.android.util.UIHelp#serializeRouteDisplayNames(ObaStop,
         * java.util.HashMap)}
         *
         * @param routes comma-delimited list of route display names that serve this stop
         */
        public IntentBuilder setStopRoutes(String routes) {
            mIntent.putExtra(ArrivalsListFragment.STOP_ROUTES, routes);
            return this;
        }

        public Intent build() {
            return mIntent;
        }
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
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set and add the view that is shown if no arrival information is returned by the REST API
        getListView().setEmptyView(mEmptyList);
        ((ViewGroup) getListView().getParent()).addView(mEmptyList);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        mAlertList = new AlertList(getActivity());
        mAlertList.initView(getView().findViewById(R.id.arrivals_alert_list));

        setupHeader(savedInstanceState);

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
                        false, false)
        );
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTERNAL_HEADER, mExternalHeader);
    }

    @Override
    public void onPause() {
        mRefreshHandler.removeCallbacks(mRefresh);
        if (mHeader != null) {
            mHeader.onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        // Notify listener that ListView is now created
        if (mListener != null) {
            mListener.onListViewCreated(getListView());
        }

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

        if (mHeader != null) {
            mHeader.onResume();
            mHeader.refresh();
        }

        super.onResume();
    }

    @Override
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        return new ArrivalsListLoader(getActivity(), mStopId);
    }

    //
    // This is where the bulk of the initialization takes place to create
    // this screen.
    //
    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader,
            final ObaArrivalInfoResponse result) {
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
                setEmptyText(UIHelp.getStopErrorString(getActivity(), result.getCode()));
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
                                getArrivalsLoader().getMinutesAfter(), true, false),
                        Toast.LENGTH_LONG
                ).show();
                mLoadedMoreArrivals = false;  // Only show the toast once
            }
        }

        // Notify listener that we have new arrival info
        if (mListener != null) {
            mListener.onArrivalTimesUpdated(result);
        }
    }

    /**
     * Sets the header for this list to be instantiated in another layout, but still controlled by
     * this fragment
     *
     * @param header     header that will be controlled by this fragment
     * @param headerView View that contains this header
     */
    public void setHeader(ArrivalsListHeader header, View headerView) {
        mHeader = header;
        mHeaderView = headerView;
        mHeader.initView(mHeaderView);
        mExternalHeader = true;
    }

    private void setResponseData(ObaArrivalInfo[] info, List<ObaSituation> situations) {
        mArrivalInfo = info;

        // Convert any stop situations into a list of alerts
        if (situations != null) {
            refreshSituations(situations);
        } else {
            refreshSituations(new ArrayList<ObaSituation>());
        }

        if (info != null) {
            // Reset the empty text just in case there is no data.
            setEmptyText(UIHelp.getNoArrivalsMessage(getActivity(),
                    getArrivalsLoader().getMinutesAfter(), false, false));
            mAdapter.setData(info, mRoutesFilter);
        }

        if (mHeader != null) {
            mHeader.refresh();
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        UIHelp.showProgress(this, false);
        mAdapter.setData(null, mRoutesFilter);

        mArrivalInfo = null;

        if (mHeader != null) {
            mHeader.refresh();
        }
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
            if (mHeader != null) {
                mHeader.beginNameEdit(null);
            }
        } else if (id == R.id.toggle_favorite) {
            setFavorite(!mFavorite);
            if (mHeader != null) {
                mHeader.refresh();
            }
        } else if (id == R.id.report_problem) {
            if (mStop != null) {
                ReportStopProblemFragment.show(
                        (android.support.v7.app.ActionBarActivity) getActivity(), mStop);
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
                    if (mHeader != null) {
                        mHeader.refresh();
                    }
                } else if (hasUrl && which == 3) {
                    UIHelp.goToUrl(getActivity(), url);
                } else if ((!hasUrl && which == 3) || (hasUrl && which == 4)) {
                    ReportTripProblemFragment.show(
                            (android.support.v7.app.ActionBarActivity) getActivity(),
                            stop.getInfo());
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
    public String getStopId() {
        return mStopId;
    }

    @Override
    public Location getStopLocation() {
        Location location = null;
        if (mStop != null) {
            location = mStop.getLocation();
        } else {
            // Check the arguments
            Bundle args = getArguments();
            double latitude = args.getDouble(STOP_LAT);
            double longitude = args.getDouble(STOP_LON);
            if (latitude != 0 && longitude != 0) {
                location = LocationUtil.makeLocation(latitude, longitude);
            }
        }
        return location;
    }

    @Override
    public String getStopName() {
        String name;
        if (mStop != null) {
            name = mStop.getName();
        } else {
            // Check the arguments
            Bundle args = getArguments();
            name = args.getString(STOP_NAME);
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
            return args.getString(STOP_DIRECTION);
        }
    }

    /**
     * Returns a sorted list (by ETA) of arrival times for the current stop
     *
     * @return a sorted list (by ETA) of arrival times for the current stop
     */
    @Override
    public ArrayList<ArrivalInfo> getArrivalInfo() {
        ArrayList<ArrivalInfo> list = null;

        if (mArrivalInfo != null) {
            list = ArrivalInfo.convertObaArrivalInfo(getActivity(), mArrivalInfo, mRoutesFilter);
        }
        return list;
    }

    /**
     * Returns the range of arrival times (i.e., for the next "minutesAfter" minutes), or -1 if
     * this information isn't available
     *
     * @return the range of arrival times (i.e., for the next "minutesAfter" minutes), or -1 if
     * this information isn't available
     */
    @Override
    public int getMinutesAfter() {
        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader != null) {
            return loader.getMinutesAfter();
        } else {
            return -1;
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
    public List<String> getRouteDisplayNames() {
        if (mStop != null && getArrivalsLoader() != null) {
            ObaArrivalInfoResponse response =
                    getArrivalsLoader().getLastGoodResponse();
            List<ObaRoute> routes = response.getRoutes(mStop.getRouteIds());
            ArrayList<String> displayNames = new ArrayList<String>();
            for (ObaRoute r : routes) {
                displayNames.add(UIHelp.getRouteDisplayName(r));
            }
            return displayNames;
        } else {
            // Check the arguments
            Bundle args = getArguments();
            String serializedRoutes = args.getString(STOP_ROUTES);
            if (serializedRoutes != null) {
                return UIHelp.deserializeRouteDisplayNames(serializedRoutes);
            }
        }
        // If we've gotten this far, we don't have any routeIds to share
        return null;
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

    /**
     * Sets the listener
     *
     * @param listener the listener
     */
    public void setListener(Listener listener) {
        this.mListener = listener;
    }

    private void setupHeader(Bundle bundle) {
        if (bundle != null) {
            mExternalHeader = bundle.getBoolean(EXTERNAL_HEADER);
        }

        if (mHeader == null && mExternalHeader == false) {
            // We should use the header contained in this fragment's layout, if none was provided
            // by the Activity via setHeader()
            mHeader = new ArrivalsListHeader(getActivity(), this);
            mHeaderView = getView().findViewById(R.id.arrivals_list_header);
            mHeader.initView(mHeaderView);
            mHeader.showExpandCollapseIndicator(false);
            // Header is not in a sliding panel, so set collapsed state to false
            mHeader.setSlidingPanelCollapsed(false);
        } else {
            // The header is in another layout (e.g., sliding panel), so we need to remove the header in this layout
            getView().findViewById(R.id.arrivals_list_header).setVisibility(View.GONE);
        }

        if (mHeader != null) {
            mHeader.refresh();
        }
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
            Activity act = getActivity();
            ArrivalsListFragment frag = null;

            // Get the fragment we want...
            if (act instanceof ArrivalsListActivity) {
                frag = ((ArrivalsListActivity) act).getArrivalsListFragment();
            } else if (act instanceof HomeActivity) {
                frag = ((HomeActivity) act).getArrivalsListFragment();
            }

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
        if (mHeader != null) {
            mHeader.refresh();
        }
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
        // If the Fragment hasn't been attached to an Activity yet, return null
        if (!isAdded()) {
            return null;
        }
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
                return TYPE_INFO;
            } else if ("severe".equals(mSituation.getSeverity())) {
                return TYPE_ERROR;
            } else {
                return TYPE_WARNING;
            }
        }

        @Override
        public int getFlags() {
            return FLAG_HASMORE;
        }

        @Override
        public CharSequence getString() {
            return mSituation.getSummary();
        }

        @Override
        public void onClick() {
            SituationFragment
                    .show((android.support.v7.app.ActionBarActivity) getActivity(), mSituation);
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
            SituationAlert alert = new SituationAlert(situation);
            mSituationAlerts.add(alert);
        }
        mAlertList.addAll(mSituationAlerts);
    }

}
