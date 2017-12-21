/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida,
 * Benjamin Du (bendu@me.com),
 * Microsoft Corporation
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
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.report.ui.InfrastructureIssueActivity;
import org.onebusaway.android.util.ArrayAdapterWithIcon;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.EmbeddedSocialUtils;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.ShowcaseViewUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//
// We don't use the ListFragment because the support library's version of
// the ListFragment doesn't work well with our header.
//
public class ArrivalsListFragment extends ListFragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse>,
        ArrivalsListHeader.Controller {

    private static final String TAG = "ArrivalsListFragment";

    public static final String STOP_NAME = ".StopName";

    public static final String STOP_CODE = ".StopCode";

    public static final String STOP_DIRECTION = ".StopDir";

    public static final String DISCUSSION = ".Discussion";

    /**
     * Comma-delimited set of routes that serve this stop
     * See {@link UIUtils#serializeRouteDisplayNames(ObaStop,
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

    private ArrivalsListAdapterBase mAdapter;

    private ArrivalsListHeader mHeader;

    private View mHeaderView;

    private View mFooter;

    private View mEmptyList;

    private AlertList mAlertList;

    private ObaStop mStop;

    private String mStopId;

    private Uri mStopUri;

    private ObaReferences mObaReferences;

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

        /**
         * Called when the user selects the "Show route on map" for a particular route/trip
         *
         * @param arrivalInfo The arrival information for the route/trip that the user selected
         * @return true if the listener has consumed the event, false otherwise
         */
        boolean onShowRouteOnMapSelected(ArrivalInfo arrivalInfo);

        /**
         * Called when the user selects the "Sort by" option
         */
        void onSortBySelected();
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
            setStopCode(stop.getStopCode());
            setStopDirection(stop.getDirection());
            setStopRoutes(UIUtils.serializeRouteDisplayNames(stop, routes));
            setStopLocation(stop.getLocation());
        }

        public IntentBuilder setStopName(String stopName) {
            mIntent.putExtra(ArrivalsListFragment.STOP_NAME, stopName);
            return this;
        }

        public IntentBuilder setStopCode(String stopCode) {
            mIntent.putExtra(ArrivalsListFragment.STOP_CODE, stopCode);
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
         * See {@link UIUtils#serializeRouteDisplayNames(ObaStop,
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

        initArrivalInfoViews(BuildFlavorUtils.getArrivalInfoStyleFromPreferences(), inflater);

        return inflater.inflate(R.layout.fragment_arrivals_list, null);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        //This is required to avoid an NullPointerException when the user rotates
        // their phone while setting a route favorite - see #480
        RouteFavoriteDialogFragment dialogFragment = (RouteFavoriteDialogFragment)
                getFragmentManager().findFragmentByTag(RouteFavoriteDialogFragment.TAG);
        if (dialogFragment != null) {
            setCallbackToDialogFragment(dialogFragment);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set the list view properties for Style B
        setListViewProperties(BuildFlavorUtils.getArrivalInfoStyleFromPreferences());

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        mAlertList = new AlertList(getActivity());
        mAlertList.initView(getView().findViewById(R.id.arrivals_alert_list));

        setupHeader(savedInstanceState);

        setupFooter();

        setupEmptyList(null);

        // This sets the stopId and uri
        setStopId();
        setUserInfo();

        // Create an empty adapter we will use to display the loaded data
        instantiateAdapter(BuildFlavorUtils.getArrivalInfoStyleFromPreferences());

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
                UIUtils.getNoArrivalsMessage(getActivity(), getArrivalsLoader().getMinutesAfter(),
                        false, false)
        );

        if (mHeader != null) {
            mHeader.refresh();
            if (getStopId() != null) {
                Bundle extras = getActivity().getIntent().getExtras();
                if (extras != null && extras.containsKey(DISCUSSION)) {
                    String discussionTitle = extras.getString(DISCUSSION);
                    displaySocialFragment(discussionTitle, false);
                }
            }
        }
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
        // Make sure we're using the correct adapter based on user preferences, in case they changed
        // after the Fragment was initialized
        checkAdapterStylePreference();

        // Notify listener that ListView is now created
        if (mListener != null) {
            mListener.onListViewCreated(getListView());
        }

        // Try to show any old data just in case we're coming out of sleep
        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader != null) {
            ObaArrivalInfoResponse lastGood = loader.getLastGoodResponse();
            if (lastGood != null) {
                setResponseData(lastGood.getArrivalInfo(), UIUtils.getAllSituations(lastGood),
                        lastGood.getRefs());
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

        // Refresh the favorite status and stop name, in case we're returning from another view
        setUserInfo();

        if (mHeader != null) {
            mHeader.refresh();
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
                    Context.ACCESSIBILITY_SERVICE);

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
                               final ObaArrivalInfoResponse result) {
        showProgress(false);

        ObaArrivalInfo[] info = null;
        List<ObaSituation> situations = null;
        ObaReferences refs = null;

        if (result.getCode() == ObaApi.OBA_OK) {
            if (mStop == null) {
                mStop = result.getStop();
                addToDB(mStop);
            }
            info = result.getArrivalInfo();
            situations = UIUtils.getAllSituations(result);
            refs = result.getRefs();

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
                setEmptyText(UIUtils.getStopErrorString(getActivity(), result.getCode()));
            }
        }

        setResponseData(info, situations, refs);

        // The list should now be shown.
        if (isResumed()) {
            setListShown(true);
        } else {
            if (isAdded()) {
                setListShownNoAnimation(true);
            }
        }

        // Clear any pending refreshes
        mRefreshHandler.removeCallbacks(mRefresh);

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
                        UIUtils.getNoArrivalsMessage(getActivity(),
                                getArrivalsLoader().getMinutesAfter(), true, false),
                        Toast.LENGTH_LONG
                ).show();
                mLoadedMoreArrivals = false;  // Only show the toast once
            }
        }

        ShowcaseViewUtils.showTutorial(ShowcaseViewUtils.TUTORIAL_ARRIVAL_SORT,
                (AppCompatActivity) getActivity(), null);

        // Notify listener that we have new arrival info
        if (mListener != null) {
            mListener.onArrivalTimesUpdated(result);
        }
    }

    @Override
    protected void showProgress(boolean visibility) {
        super.showProgress(visibility);
        if (mHeader != null) {
            mHeader.showProgress(visibility);
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
        if (header != null) {
            mHeader.initView(mHeaderView);
        }
        mExternalHeader = true;
    }

    private void setResponseData(ObaArrivalInfo[] info, List<ObaSituation> situations,
                                 ObaReferences refs) {
        mArrivalInfo = info;

        mObaReferences = refs;

        // Convert any stop situations into a list of alerts
        if (situations != null) {
            refreshSituations(situations);
        } else {
            refreshSituations(new ArrayList<ObaSituation>());
        }

        if (info != null) {
            ArrivalsListLoader loader = getArrivalsLoader();
            int minutesAfter;
            if (loader != null) {
                minutesAfter = loader.getMinutesAfter();
            } else {
                minutesAfter = ArrivalsListLoader.DEFAULT_MINUTES_AFTER;
            }
            // Reset the empty text just in case there is no data.
            setEmptyText(UIUtils.getNoArrivalsMessage(Application.get().getApplicationContext(),
                    minutesAfter, false, false));
            mAdapter.setData(info, mRoutesFilter, System.currentTimeMillis());
        }

        if (mHeader != null) {
            mHeader.refresh();
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
        showProgress(false);
        mAdapter.setData(null, mRoutesFilter, System.currentTimeMillis());

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
        if (!isAdded()) {
            // not attached to an activity (possibly due to screen rotation)
            return;
        }

        String title = mFavorite ?
                getString(R.string.stop_info_option_removestar) :
                getString(R.string.stop_info_option_addstar);
        menu.findItem(R.id.toggle_favorite)
                .setTitle(title)
                .setTitleCondensed(title);

        MenuItem menuItemHeaderArrivals = menu.findItem(R.id.show_header_arrivals);
        if (mHeader != null) {
            title = mHeader.isShowingArrivals() ?
                    getString(R.string.stop_info_option_hide_header_arrivals) :
                    getString(R.string.stop_info_option_show_header_arrivals);
            menuItemHeaderArrivals.setTitle(title);
            menuItemHeaderArrivals.setTitleCondensed(title);
        }

        if (mExternalHeader) {
            // If we're using an external header, it means that this fragment is being shown
            // in the bottom sliding panel, and therefore the map is already visible.
            // So, we can remove the "Show Map" option
            menu.findItem(R.id.show_on_map).setVisible(false);
            // We want to hide the "show/hide arrivals in header" setting if we are in the sliding panel
            menuItemHeaderArrivals.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.show_on_map) {
            if (mStop != null) {
                HomeActivity.start(getActivity(), mStop);
            }
            return true;
        } else if (id == R.id.refresh) {
            refresh();
            return true;
        } else if (id == R.id.sort_arrivals) {
            ShowcaseViewUtils.doNotShowTutorial(ShowcaseViewUtils.TUTORIAL_ARRIVAL_SORT);
            showSortByDialog();
        } else if (id == R.id.filter) {
            if (mStop != null) {
                showRoutesFilterDialog();
            }
        } else if (id == R.id.show_header_arrivals) {
            doShowHideHeaderArrivals();
        } else if (id == R.id.edit_name) {
            if (mHeader != null) {
                mHeader.beginNameEdit(null);
            }
        } else if (id == R.id.toggle_favorite) {
            setFavoriteStop(!mFavorite);
            if (mHeader != null) {
                mHeader.refresh();
            }
        } else if (id == R.id.show_stop_details) {
            showStopDetailsDialog();
        } else if (id == R.id.report_stop_problem) {
            if (mStop != null) {
                Intent intent = makeIntent(getActivity(), mStop.getId(), mStop.getName(),
                        mStop.getStopCode(), mStop.getLatitude(), mStop.getLongitude());
                InfrastructureIssueActivity.startWithService(getActivity(), intent,
                        getString(R.string.ri_selected_service_stop));
            }
        } else if (id == R.id.night_light) {
            NightLightActivity.start(getActivity());
        }
        return false;
    }

    public static Intent makeIntent(Context context,
                                    String focusId,
                                    String stopName,
                                    String stopCode,
                                    double lat,
                                    double lon) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, focusId);
        myIntent.putExtra(MapParams.STOP_NAME, stopName);
        myIntent.putExtra(MapParams.STOP_CODE, stopCode);
        myIntent.putExtra(MapParams.CENTER_LAT, lat);
        myIntent.putExtra(MapParams.CENTER_LON, lon);
        return myIntent;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final ArrivalInfo stop = (ArrivalInfo) getListView().getItemAtPosition(position);
        showListItemMenu(v, stop);
    }

    public void showListItemMenu(View v, final ArrivalInfo arrivalInfo) {
        if (arrivalInfo == null) {
            return;
        }
        Log.d(TAG, "Tapped on route=" + arrivalInfo.getInfo().getShortName() +
                ", tripId=" + arrivalInfo.getInfo().getTripId() +
                ", vehicleId=" + arrivalInfo.getInfo().getVehicleId());

        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader == null) {
            return;
        }
        final ObaArrivalInfoResponse response = loader.getLastGoodResponse();
        if (response == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.stop_info_item_options_title);

        final String routeId = arrivalInfo.getInfo().getRouteId();
        final ObaRoute route = response.getRoute(routeId);
        final String url = route != null ? route.getUrl() : null;
        final boolean hasUrl = !TextUtils.isEmpty(url);
        // Check to see if the reminder is visible, for whether we show "add" or "edit" reminder
        // (we don't have any other state, so this is good enough)
        View tripView = v.findViewById(R.id.reminder);
        boolean isReminderVisible = tripView != null && tripView.getVisibility() != View.GONE;
        // Check route favorite, for whether we show "Add star" or "Remove star"
        final boolean isRouteFavorite = ObaContract.RouteHeadsignFavorites.isFavorite(routeId,
                arrivalInfo.getInfo().getHeadsign(), arrivalInfo.getInfo().getStopId());

        List<String> items = UIUtils
                .buildTripOptions(getActivity(), isRouteFavorite, hasUrl, isReminderVisible);
        List<Integer> icons = UIUtils.buildTripOptionsIcons(getContext(), isRouteFavorite, hasUrl);

        ListAdapter adapter = new ArrayAdapterWithIcon(getActivity(), items, icons);
        ObaRegion currentRegion = Application.get().getCurrentRegion();
        final boolean isSocialEnabled = currentRegion != null && EmbeddedSocialUtils.isSocialEnabled(getContext());

        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // Show dialog for setting route favorite
                    RouteFavoriteDialogFragment routeDialog
                            = new RouteFavoriteDialogFragment.Builder(
                            route.getId(), arrivalInfo.getInfo().getHeadsign())
                            .setRouteShortName(route.getShortName())
                            .setRouteLongName(arrivalInfo.getInfo().getRouteLongName())
                            .setRouteUrl(route.getUrl())
                            .setStopId(arrivalInfo.getInfo().getStopId())
                            .setFavorite(!isRouteFavorite)
                            .build();

                    setCallbackToDialogFragment(routeDialog);

                    routeDialog.show(getFragmentManager(), RouteFavoriteDialogFragment.TAG);
                } else if (which == 1) {
                    showRouteOnMap(arrivalInfo);
                } else if (which == 2) {
                    goToTripDetails(arrivalInfo);
                } else if (which == 3) {
                    goToTrip(arrivalInfo);
                } else if (which == 4) {
                    ArrayList<String> routes = new ArrayList<String>(1);
                    routes.add(arrivalInfo.getInfo().getRouteId());
                    setRoutesFilter(routes);
                    if (mHeader != null) {
                        mHeader.refresh();
                    }
                } else if (hasUrl && which == 5) {
                    UIUtils.goToUrl(getActivity(), url);
                } else if ((!hasUrl && which == 5) || (hasUrl && which == 6)) {
                    // Find agency name
                    String routeId = arrivalInfo.getInfo().getRouteId();
                    String agencyName = null;
                    String blockId = null;

                    if (mObaReferences != null) {
                        String agencyId = mObaReferences.getRoute(routeId).getAgencyId();
                        agencyName = mObaReferences.getAgency(agencyId).getName();
                        ObaTrip trip = mObaReferences.getTrip(arrivalInfo.getInfo().getTripId());
                        blockId = trip.getBlockId();
                    }

                    Intent intent = makeIntent(getActivity(), mStop.getId(), mStop.getName(),
                            mStop.getStopCode(), mStop.getLatitude(), mStop.getLongitude());

                    InfrastructureIssueActivity.startWithService(getActivity(), intent,
                            getString(R.string.ri_selected_service_trip), arrivalInfo.getInfo(),
                            agencyName, blockId);
                } else if (isSocialEnabled && (!hasUrl && which == 6) || (hasUrl && which == 7)) {
                    ObaAnalytics.reportEventWithCategory(
                            ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getActivity().getString(R.string.analytics_action_button_press),
                            getActivity().getString(
                                    R.string.analytics_label_button_press_social_route_options));
                    openRouteDiscussion(arrivalInfo.getInfo().getRouteId());
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    /**
     * Opens the discussion item related to this route ID
     * @param routeId route ID to use
     */
    public void openRouteDiscussion(String routeId) {
        long regionId = Application.get().getCurrentRegion().getId();
        String discussionTitle = EmbeddedSocialUtils.createRouteDiscussionTitle(regionId, routeId);
        openDiscussion(discussionTitle);
    }

    /**
     * Opens the discussion item related to the currently selected stop
     */
    public void openStopDiscussion() {
        long regionId = Application.get().getCurrentRegion().getId();
        String discussionTitle = EmbeddedSocialUtils.createStopDiscussionTitle(regionId, getStopId());
        openDiscussion(discussionTitle);
    }

    /**
     * Opens a discussion item in an ArrivalsListActivity
     * @param discussionTitle title of the Embedded Social topic
     */
    private void openDiscussion(String discussionTitle) {
        if (getActivity() instanceof ArrivalsListActivity) {
            // do not create a new instance of ArrivalsListActivity
            if (getFragmentManager().findFragmentByTag(discussionTitle) == null) {
                // only fetch this fragment if it is not already displayed
                displaySocialFragment(discussionTitle, true);
            }
        } else {
            ArrivalsListActivity.start(getContext(), getStopId(), getStopName(), getStopDirection(), discussionTitle);
        }
    }

    /**
     * Displays a social fragment
     * @param discussionTitle title of the Embedded Social topic
     * @param addToBackStack true if this transaction should be added to the back stack
     */
    public void displaySocialFragment(String discussionTitle, boolean addToBackStack) {
        Fragment discussionFragment = EmbeddedSocialUtils.getDiscussionFragment(discussionTitle);

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.add(R.id.listContainer, discussionFragment, discussionTitle);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private void setCallbackToDialogFragment(RouteFavoriteDialogFragment routeDialog) {
        routeDialog.setCallback(new RouteFavoriteDialogFragment.Callback() {
            @Override
            public void onSelectionComplete(boolean savedFavorite) {
                if (savedFavorite) {
                    refreshLocal();
                }
            }
        });
    }

    public void showRouteOnMap(ArrivalInfo arrivalInfo) {
        boolean handled = false;
        if (mListener != null) {
            handled = mListener.onShowRouteOnMapSelected(arrivalInfo);
        }
        // If the event hasn't been handled by the listener, start a new activity
        if (!handled) {
            HomeActivity.start(getActivity(), arrivalInfo.getInfo().getRouteId());
        }
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
                location = LocationUtils.makeLocation(latitude, longitude);
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
        return UIUtils.formatDisplayText(name);
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
            list = ArrivalInfoUtils.convertObaArrivalInfo(getActivity(), mArrivalInfo, mRoutesFilter,
                    System.currentTimeMillis(), true);
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
        refreshLocal();
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
                displayNames.add(UIUtils.getRouteDisplayName(r));
            }
            return displayNames;
        } else {
            // Check the arguments
            Bundle args = getArguments();
            String serializedRoutes = args.getString(STOP_ROUTES);
            if (serializedRoutes != null) {
                return UIUtils.deserializeRouteDisplayNames(serializedRoutes);
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
    public boolean isFavoriteStop() {
        return mFavorite;
    }

    @Override
    public boolean setFavoriteStop(boolean favorite) {
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

    /**
     * Checks to see if the user has changed the arrival info style preference after this Fragment
     * was initialized
     */
    private void checkAdapterStylePreference() {
        int currentArrivalInfoStyle = BuildFlavorUtils.getArrivalInfoStyleFromPreferences();

        if (currentArrivalInfoStyle == BuildFlavorUtils.ARRIVAL_INFO_STYLE_A &&
                !(mAdapter instanceof ArrivalsListAdapterStyleA)) {
            // Change to Style A adapter
            reinitAdapterStyleOnPreferenceChange(BuildFlavorUtils.ARRIVAL_INFO_STYLE_A);
        } else if (currentArrivalInfoStyle == BuildFlavorUtils.ARRIVAL_INFO_STYLE_B &&
                !(mAdapter instanceof ArrivalsListAdapterStyleB)) {
            // Change to Style B adapter
            reinitAdapterStyleOnPreferenceChange(BuildFlavorUtils.ARRIVAL_INFO_STYLE_B);
        }
    }

    /**
     * Reinitializes the adapter style after there has been a preference changed
     *
     * @param arrivalInfoStyle the adapter style to change to - should be one of the
     *                         BuildFlavorUtil.ARRIVAL_INFO_STYLE_* contants
     */
    private void reinitAdapterStyleOnPreferenceChange(int arrivalInfoStyle) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        // Remove any existing footer view
        if (mFooter != null) {
            getListView().removeFooterView(mFooter);
        }
        CharSequence emptyText = null;
        // Remove any existing empty list view
        if (mEmptyList != null) {
            TextView noArrivals = (TextView) mEmptyList.findViewById(R.id.noArrivals);
            if (noArrivals != null) {
                emptyText = noArrivals.getText();
            }
            ((ViewGroup) getListView().getParent()).removeView(mEmptyList);
        }

        initArrivalInfoViews(arrivalInfoStyle, inflater);
        setupFooter();
        setupEmptyList(emptyText);
        setListViewProperties(arrivalInfoStyle);
        instantiateAdapter(arrivalInfoStyle);
    }

    /**
     * Initializes the adapter views
     *
     * @param arrivalInfoStyle the adapter style to use - should be one of the
     *                         BuildFlavorUtil.ARRIVAL_INFO_STYLE_* contants
     * @param inflater         inflater to use
     */
    private void initArrivalInfoViews(int arrivalInfoStyle, LayoutInflater inflater) {
        // Use a card-styled footer
        mFooter = inflater.inflate(R.layout.arrivals_list_footer_style_b, null);
        mEmptyList = inflater.inflate(R.layout.arrivals_list_empty_style_b, null);
    }

    /**
     * Sets up the footer with the load more arrivals button
     */
    private void setupFooter() {
        // Setup list footer button to load more arrivals (when arrivals are shown)
        Button loadMoreArrivals = (Button) mFooter.findViewById(R.id.load_more_arrivals);
        loadMoreArrivals.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMoreArrivals();
            }
        });
        getListView().addFooterView(mFooter);
        mFooter.requestLayout();
    }

    /**
     * Sets up the load more arrivals button in the empty list view
     *
     * @param currentText the text that should populate the empty list entry, or null if no text
     *                    should be set at this point
     */
    private void setupEmptyList(CharSequence currentText) {
        Button loadMoreArrivalsEmptyList = (Button) mEmptyList
                .findViewById(R.id.load_more_arrivals);
        loadMoreArrivalsEmptyList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMoreArrivals();
            }
        });
        // Set and add the view that is shown if no arrival information is returned by the REST API
        getListView().setEmptyView(mEmptyList);
        ((ViewGroup) getListView().getParent()).addView(mEmptyList);
        if (currentText != null) {
            setEmptyText(currentText);
        }
    }

    /**
     * Initializes the list view properties
     *
     * @param arrivalInfoStyle the adapter style to use - should be one of the
     *                         BuildFlavorUtil.ARRIVAL_INFO_STYLE_* contants
     */
    private void setListViewProperties(int arrivalInfoStyle) {
        ListView.MarginLayoutParams listParam = (ListView.MarginLayoutParams) getListView()
                .getLayoutParams();
        // Set margins for the CardViews
        listParam.bottomMargin = UIUtils.dpToPixels(getActivity(), 2);
        listParam.topMargin = UIUtils.dpToPixels(getActivity(), 3);
        listParam.leftMargin = UIUtils.dpToPixels(getActivity(), 5);
        listParam.rightMargin = UIUtils.dpToPixels(getActivity(), 5);
        // Set the listview background to give the cards more contrast
        getListView().setBackgroundColor(
                getResources().getColor(R.color.stop_info_arrival_list_background));
        // Update the layout parameters
        getListView().setLayoutParams(listParam);
    }

    /**
     * Instantiates the adapter based on the style to be used
     *
     * @param arrivalInfoStyle the adapter style to use - should be one of the
     *                         BuildFlavorUtil.ARRIVAL_INFO_STYLE_* contants
     */
    private void instantiateAdapter(int arrivalInfoStyle) {
        if (UIUtils.canSupportArrivalInfoStyleB()) {
            switch (arrivalInfoStyle) {
                case BuildFlavorUtils.ARRIVAL_INFO_STYLE_A:
                    mAdapter = new ArrivalsListAdapterStyleA(getActivity());
                    break;
                case BuildFlavorUtils.ARRIVAL_INFO_STYLE_B:
                    mAdapter = new ArrivalsListAdapterStyleB(getActivity());
                    ((ArrivalsListAdapterStyleB) mAdapter).setFragment(this);
                    break;
            }
        } else {
            // Always use Style A on Gingerbread
            mAdapter = new ArrivalsListAdapterStyleA(getActivity());
        }

        // We present arrivals as cards, so hide the divider in the listview
        getListView().setDivider(null);
        setListAdapter(mAdapter);
    }

    private void showSortByDialog() {
        // Do callback when user taps on button, so they can see result of dialog selection
        if (mListener != null) {
            mListener.onSortBySelected();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.menu_option_sort_by);

        int currentArrivalInfoStyle = BuildFlavorUtils.getArrivalInfoStyleFromPreferences();
        builder.setSingleChoiceItems(R.array.sort_arrivals, currentArrivalInfoStyle,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int index) {
                        if (index == 0) {
                            // Sort by eta
                            Log.d(TAG, "Sort by ETA");
                            ObaAnalytics.reportEventWithCategory(
                                    ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                    getActivity().getString(R.string.analytics_action_button_press),
                                    getActivity().getString(
                                            R.string.analytics_label_sort_by_eta_arrival));
                        } else if (index == 1) {
                            // Sort by route
                            Log.d(TAG, "Sort by route");
                            ObaAnalytics.reportEventWithCategory(
                                    ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                    getActivity().getString(R.string.analytics_action_button_press),
                                    getActivity().getString(
                                            R.string.analytics_label_sort_by_route_arrival));
                        }
                        String[] styles = getResources()
                                .getStringArray(R.array.arrival_info_style_options);
                        PreferenceUtils.saveString(getResources()
                                        .getString(R.string.preference_key_arrival_info_style),
                                styles[index]);
                        checkAdapterStylePreference();
                        refreshLocal();
                        getLoaderManager()
                                .restartLoader(TRIPS_FOR_STOP_LOADER, null, mTripsForStopCallback);
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(getActivity());
        dialog.show();
    }

    /**
     * Toggle the visibility of arrivals in the header
     */
    private void doShowHideHeaderArrivals() {
        if (mHeader == null) {
            return;
        }
        boolean showArrivals = Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_show_header_arrivals), false);

        if (showArrivals) {
            // Currently we're showing arrivals in header - we need to remove them
            mHeader.showArrivals(false);

            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_hide_arrivals_in_header));
        } else {
            // Currently we're hiding arrivals - we need to show them
            mHeader.showArrivals(true);

            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_show_arrivals_in_header));
        }

        PreferenceUtils.saveBoolean(getResources()
                .getString(R.string.preference_key_show_header_arrivals), !showArrivals);
        mHeader.refresh();
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
            items[i] = UIUtils.getRouteDisplayName(route);
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

    private void showStopDetailsDialog() {
        // Create dialog contents
        String stopCode = null;
        if (mStop != null) {
            stopCode = mStop.getStopCode();
        }
        Pair stopDetails = UIUtils.createStopDetailsDialogText(getContext(),
                getStopName(),
                getUserStopName(),
                stopCode,
                getStopDirection(),
                getRouteDisplayNames());

        UIUtils.buildAlertDialog(getContext(),
                (String) stopDetails.first,
                (String) stopDetails.second).show();

        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                getString(R.string.analytics_action_button_press),
                getString(R.string.analytics_label_button_press_stop_details));
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

        if (mHeader == null && !mExternalHeader) {
            // We should use the header contained in this fragment's layout, if none was provided
            // by the Activity via setHeader()
            mHeader = new ArrivalsListHeader(getActivity(), this, getFragmentManager());
            mHeaderView = getView().findViewById(R.id.arrivals_list_header);
            mHeader.initView(mHeaderView);
            mHeader.showExpandCollapseIndicator(false);
            // Header is not in a sliding panel, so set collapsed state to false
            mHeader.setSlidingPanelCollapsed(false);
            // Show or hide the header arrivals based on user preference
            boolean showArrivals = Application.getPrefs()
                    .getBoolean(getString(R.string.preference_key_show_header_arrivals), false);
            mHeader.showArrivals(showArrivals);
        } else {
            // The header is in another layout (e.g., sliding panel), so we need to remove the header in this layout
            getView().findViewById(R.id.arrivals_list_header).setVisibility(View.GONE);
        }

        if (mHeader != null) {
            mHeader.refresh();
        }
    }

    public static class RoutesFilterDialog extends DialogFragment
            implements OnMultiChoiceClickListener,
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
        if (routes.size() != len) {
            throw new IllegalArgumentException("checks.length must be equal to routes.size()");
        }

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

    private void goToTripDetails(ArrivalInfo stop) {
        TripDetailsActivity.start(getActivity(),
                stop.getInfo().getTripId(), stop.getInfo().getStopId(),
                TripDetailsListFragment.SCROLL_MODE_STOP);
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

        //Analytics
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                getActivity().getString(R.string.analytics_action_button_press),
                getActivity().getString(R.string.analytics_label_button_press));
    }

    /**
     * Full refresh of data from the OBA server
     */
    public void refresh() {
        if (isAdded()) {
            showProgress(true);
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

    /**
     * Refreshes ListFragment content using the most recent server response.  Does not trigger
     * another call to the OBA server.
     */
    public void refreshLocal() {
        ArrivalsListLoader loader = getArrivalsLoader();
        if (loader != null) {
            ObaArrivalInfoResponse response = loader.getLastGoodResponse();
            if (response == null) {
                // Nothing to refresh yet
                return;
            }
            mAdapter.setData(response.getArrivalInfo(), mRoutesFilter, System.currentTimeMillis());
        }
        if (mHeader != null) {
            mHeader.refresh();
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
        String name = UIUtils.formatDisplayText(stop.getName());

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
        ObaContract.Stops.insertOrUpdate(stop.getId(), values, true);
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
            // Call back into the adapter and header and say we've finished this.
            mAdapter.setTripsForStop(map);
            if (mHeader != null) {
                mHeader.setTripsForStop(map);
            }
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
            if (ObaSituation.SEVERITY_NO_IMPACT.equals(mSituation.getSeverity())) {
                return TYPE_INFO;
            } else if (ObaSituation.SEVERITY_SEVERE.equals(mSituation.getSeverity())
                    || ObaSituation.SEVERITY_VERY_SEVERE.equals(
                    mSituation.getSeverity())) {
                return TYPE_ERROR;
            } else {
                // Treat all other ObaSituation.SEVERITY_* types as a warning
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
            SituationDialogFragment dialog = SituationDialogFragment.newInstance(mSituation);
            dialog.setListener(new SituationDialogFragment.Listener() {
                @Override
                public void onDismiss(boolean isAlertHidden) {
                    if (isAlertHidden) {
                        // User hid a service alert, so we need to refresh the list
                        // TODO - refreshLocal() should support refreshing local situations
                        refresh();
                    }
                }

                @Override
                public void onUndo() {
                    // User hit undo, so we need to refresh the list
                    // TODO - refreshLocal() should support refreshing local situations
                    refresh();
                }
            });
            dialog.show(getFragmentManager(), SituationDialogFragment.TAG);

            reportAnalytics(mSituation);
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

    private void reportAnalytics(ObaSituation situation) {
        ObaSituation.AllAffects[] allAffects = situation.getAllAffects();
        Set<String> agencyIds = new HashSet<>();
        for (int i = 0; i < allAffects.length; i++) {
            agencyIds.add(allAffects[i].getAgencyId());
        }

        for (String agencyId : agencyIds) {
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_service_alerts),
                    getString(R.string.analytics_label_service_alerts) + agencyId);
        }
    }

    private void refreshSituations(List<ObaSituation> situations) {
        // First, remove any existing situations
        if (mSituationAlerts != null) {
            for (SituationAlert alert : mSituationAlerts) {
                mAlertList.remove(alert);
            }
        }
        mAlertList.setAlertHidden(false);
        mSituationAlerts = null;

        if (situations.isEmpty()) {
            // The normal scenario
            return;
        }

        mSituationAlerts = new ArrayList<>();

        ContentValues values = new ContentValues();

        int hiddenCount = 0;

        for (ObaSituation situation : situations) {
            values.clear();

            // Make sure this situation is added to the database
            ObaContract.ServiceAlerts.insertOrUpdate(situation.getId(), values, false, null);

            boolean isActive = UIUtils
                    .isActiveWindowForSituation(situation, System.currentTimeMillis());
            boolean isHidden = ObaContract.ServiceAlerts.isHidden(situation.getId());

            if (isActive && !isHidden) {
                SituationAlert alert = new SituationAlert(situation);
                mSituationAlerts.add(alert);
            }
            if (isHidden) {
                mAlertList.setAlertHidden(true);
                hiddenCount++;
            }
        }
        mAlertList.setHiddenAlertCount(hiddenCount);
        mAlertList.addAll(mSituationAlerts);
    }
}
