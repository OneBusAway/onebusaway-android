/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import com.microsoft.embeddedsocial.sdk.EmbeddedSocial;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.map.MapModeController;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.map.googlemapsv2.LayerInfo;
import org.onebusaway.android.region.ObaRegionsTask;
import org.onebusaway.android.report.ui.ReportActivity;
import org.onebusaway.android.tripservice.TripService;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.RegionUtils;
import org.onebusaway.android.util.ShowcaseViewUtils;
import org.onebusaway.android.util.UIUtils;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_ACTIVITY_FEED;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_HELP;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_MY_REMINDERS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_NEARBY;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_PINS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_PLAN_TRIP;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_POPULAR;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_PROFILE;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_SEND_FEEDBACK;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_SETTINGS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_SIGN_IN;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_STARRED_STOPS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NavigationDrawerCallbacks;

public class HomeActivity extends AppCompatActivity
        implements BaseMapFragment.OnFocusChangedListener,
        BaseMapFragment.OnProgressBarChangedListener,
        ArrivalsListFragment.Listener, NavigationDrawerCallbacks,
        ObaRegionsTask.Callback {

    interface SlidingPanelController {

        /**
         * Sets the height of the sliding panel in pixels
         *
         * @param heightInPixels height of panel in pixels
         */
        void setPanelHeightPixels(int heightInPixels);

        /**
         * Returns the current height of the sliding panel in pixels, or -1 if the panel isn't yet
         * initialized
         *
         * @return the current height of the sliding panel in pixels, or -1 if the panel isn't yet
         * initialized
         */
        int getPanelHeightPixels();
    }

    public static final String TWITTER_URL = "http://mobile.twitter.com/onebusaway";

    private static final String WHATS_NEW_VER = "whatsNewVer";

    private static final String CHECK_REGION_VER = "checkRegionVer";

    private static final int HELP_DIALOG = 1;

    private static final int WHATSNEW_DIALOG = 2;

    private static final int LEGEND_DIALOG = 3;

    //One week, in milliseconds
    private static final long REGION_UPDATE_THRESHOLD = 1000 * 60 * 60 * 24 * 7;

    private static final String TAG = "HomeActivity";

    Context mContext;

    ArrivalsListFragment mArrivalsListFragment;

    ArrivalsListHeader mArrivalsListHeader;

    View mArrivalsListHeaderView;

    View mArrivalsListHeaderSubView;

    private FloatingActionButton mFabMyLocation;

    uk.co.markormesher.android_fab.FloatingActionButton mLayersFab;

    private static int MY_LOC_DEFAULT_BOTTOM_MARGIN;

    private static int LAYERS_FAB_DEFAULT_BOTTOM_MARGIN;

    private static final int MY_LOC_BTN_ANIM_DURATION = 100;  // ms

    Animation mMyLocationAnimation;

    /**
     * GoogleApiClient being used for Location Services
     */
    protected GoogleApiClient mGoogleApiClient;

    // Bottom Sliding panel
    SlidingUpPanelLayout mSlidingPanel;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Currently selected navigation drawer position (so we don't unnecessarily swap fragments
     * if the same item is selected).  Initialized to -1 so the initial callback from
     * NavigationDrawerFragment always instantiates the fragments
     */
    private int mCurrentNavDrawerPosition = -1;

    /**
     * Fragments that can be selected as main content via the NavigationDrawer
     */
    MyStarredStopsFragment mMyStarredStopsFragment;

    BaseMapFragment mMapFragment;

    MyRemindersFragment mMyRemindersFragment;

    /**
     * Control which menu options are shown per fragment menu groups
     */
    private boolean mShowStarredStopsMenu = false;

    private boolean mShowArrivalsMenu = false;

    /**
     * Stop that has current focus on the map.  We retain a reference to the StopId,
     * since during rapid rotations its possible that a reference to a ObaStop object in
     * mFocusedStop can still be null, and we don't want to lose the state of which stopId is in
     * focus.  We also need access to the focused stop properties, hence why we also have
     * mFocusedStop
     */
    String mFocusedStopId = null;

    /**
     * Bike rental station ID that has the focus currently.
     */
    String mBikeRentalStationId = null;

    ObaStop mFocusedStop = null;

    ImageView mExpandCollapse = null;

    ProgressBar mMapProgressBar = null;

    boolean mLastMapProgressBarState = true;

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static void start(Context context,
                             String focusId,
                             double lat,
                             double lon) {
        context.startActivity(makeIntent(context, focusId, lat, lon));
    }

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param stop    The stop to focus on.
     */
    public static void start(Context context, ObaStop stop) {
        context.startActivity(makeIntent(context, stop));
    }

    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     */
    public static void start(Context context, String routeId) {
        context.startActivity(makeIntent(context, routeId));
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static Intent makeIntent(Context context,
                                    String focusId,
                                    double lat,
                                    double lon) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, focusId);
        myIntent.putExtra(MapParams.CENTER_LAT, lat);
        myIntent.putExtra(MapParams.CENTER_LON, lon);
        return myIntent;
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param stop    The stop to focus on.
     */
    public static Intent makeIntent(Context context, ObaStop stop) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, stop.getId());
        myIntent.putExtra(MapParams.STOP_NAME, stop.getName());
        myIntent.putExtra(MapParams.STOP_CODE, stop.getStopCode());
        myIntent.putExtra(MapParams.CENTER_LAT, stop.getLatitude());
        myIntent.putExtra(MapParams.CENTER_LON, stop.getLongitude());
        return myIntent;
    }

    /**
     * Returns an intent that starts the MapActivity in "RouteMode", which shows
     * stops along a route, and does not get new stops when the user pans the
     * map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     */
    public static Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.MODE, MapParams.MODE_ROUTE);
        myIntent.putExtra(MapParams.ZOOM_TO_ROUTE, true);
        myIntent.putExtra(MapParams.ROUTE_ID, routeId);
        return myIntent;
    }

    SlidingPanelController mSlidingPanelController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mContext = this;

        setupNavigationDrawer();

        setupSlidingPanel();

        setupMapState(savedInstanceState);

        setupLayersSpeedDial();

        setupMyLocationButton();

        setupGooglePlayServices();

        UIUtils.setupActionBar(this);

        checkRegionStatus();

        // Check to see if we should show the welcome tutorial
        Bundle b = getIntent().getExtras();
        if (b != null) {
            if (b.getBoolean(ShowcaseViewUtils.TUTORIAL_WELCOME)) {
                // Show the welcome tutorial
                ShowcaseViewUtils.showTutorial(ShowcaseViewUtils.TUTORIAL_WELCOME, this, null);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make sure GoogleApiClient is connected, if available
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
        ObaAnalytics.reportActivityStart(this);
        if (Build.VERSION.SDK_INT >= 14) {
            AccessibilityManager am = (AccessibilityManager) getSystemService(
                    ACCESSIBILITY_SERVICE);
            Boolean isTalkBackEnabled = am.isTouchExplorationEnabled();
            if (isTalkBackEnabled) {
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.ACCESSIBILITY.toString(),
                        getString(R.string.analytics_action_touch_exploration),
                        getString(R.string.analytics_label_talkback) + getClass().getSimpleName()
                                + " using TalkBack");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Make sure header has sliding panel state
        if (mArrivalsListHeader != null && mSlidingPanel != null) {
            mArrivalsListHeader.setSlidingPanelCollapsed(isSlidingPanelCollapsed());
        }

        checkLeftHandMode();
        updateLayersFab();
        mFabMyLocation.requestLayout();
    }

    @Override
    protected void onPause() {
        ShowcaseViewUtils.hideShowcaseView();
        super.onPause();
    }

    @Override
    public void onStop() {
        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        mLayersFab.closeSpeedDialMenu();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mFocusedStopId != null) {
            outState.putString(MapParams.STOP_ID, mFocusedStopId);

            if (mFocusedStop != null) {
                outState.putString(MapParams.STOP_CODE, mFocusedStop.getStopCode());
                outState.putString(MapParams.STOP_NAME, mFocusedStop.getName());
            }
        }
        if (mBikeRentalStationId != null) {
            outState.putString(MapParams.BIKE_STATION_ID, mBikeRentalStationId);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        goToNavDrawerItem(position);
    }

    private void goToNavDrawerItem(int item) {
        // Update the main content by replacing fragments
        switch (item) {
            case NAVDRAWER_ITEM_STARRED_STOPS:
                if (mCurrentNavDrawerPosition != NAVDRAWER_ITEM_STARRED_STOPS) {
                    showStarredStopsFragment();
                    mCurrentNavDrawerPosition = item;
                    ObaAnalytics.reportEventWithCategory(
                            ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getString(R.string.analytics_action_button_press),
                            getString(R.string.analytics_label_button_press_star));
                }
                break;
            case NAVDRAWER_ITEM_NEARBY:
                if (mCurrentNavDrawerPosition != NAVDRAWER_ITEM_NEARBY) {
                    showMapFragment();
                    mCurrentNavDrawerPosition = NAVDRAWER_ITEM_NEARBY;
                    ObaAnalytics.reportEventWithCategory(
                            ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getString(R.string.analytics_action_button_press),
                            getString(R.string.analytics_label_button_press_nearby));
                }
                break;
            case NAVDRAWER_ITEM_MY_REMINDERS:
                if (mCurrentNavDrawerPosition != NAVDRAWER_ITEM_MY_REMINDERS) {
                    showMyRemindersFragment();
                    mCurrentNavDrawerPosition = item;
                    ObaAnalytics.reportEventWithCategory(
                            ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getString(R.string.analytics_action_button_press),
                            getString(R.string.analytics_label_button_press_reminders));
                }
                break;
            case NAVDRAWER_ITEM_PLAN_TRIP:
                Intent planTrip = new Intent(HomeActivity.this, TripPlanActivity.class);
                startActivity(planTrip);
                ObaAnalytics
                        .reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                getString(R.string.analytics_action_button_press),
                                getString(R.string.analytics_label_button_press_trip_plan));
                break;
            case NAVDRAWER_ITEM_SIGN_IN:
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_social_sign_in));
                EmbeddedSocial.launchSignInActivity(this);
                break;
            case NAVDRAWER_ITEM_PROFILE:
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_social_profile));
                EmbeddedSocial.launchProfileActivity(this);
                break;
            case NAVDRAWER_ITEM_POPULAR:
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_social_popular));
                EmbeddedSocial.launchPopularActivity(this);
                break;
            case NAVDRAWER_ITEM_PINS:
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_social_pins));
                EmbeddedSocial.launchPinsActivity(this);
                break;
            case NAVDRAWER_ITEM_ACTIVITY_FEED:
                ObaAnalytics.reportEventWithCategory(
                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_social_activity_feed));
                EmbeddedSocial.launchActivityFeedActivity(this);
                break;
            case NAVDRAWER_ITEM_SETTINGS:
                Intent preferences = new Intent(HomeActivity.this, PreferencesActivity.class);
                startActivity(preferences);
                ObaAnalytics
                        .reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                getString(R.string.analytics_action_button_press),
                                getString(R.string.analytics_label_button_press_settings));
                break;
            case NAVDRAWER_ITEM_HELP:
                if (noActiveFragments()) {
                    showMapFragment();
                }
                showDialog(HELP_DIALOG);
                ObaAnalytics
                        .reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                getString(R.string.analytics_action_button_press),
                                getString(R.string.analytics_label_button_press_help));
                break;
            case NAVDRAWER_ITEM_SEND_FEEDBACK:
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                        getString(R.string.analytics_action_button_press),
                        getString(R.string.analytics_label_button_press_feedback));
                goToSendFeedBack();
                break;
        }
        invalidateOptionsMenu();
    }

    // Return true if this HomeActivity has no active content fragments
    private boolean noActiveFragments() {
        return mMapFragment == null && mMyStarredStopsFragment == null && mMyRemindersFragment == null;
    }

    private void handleNearbySelection() {
    }

    private void showMapFragment() {
        FragmentManager fm = getSupportFragmentManager();
        /**
         * Hide everything that shouldn't be shown
         */
        hideStarredStopsFragment();
        hideReminderFragment();
        mShowStarredStopsMenu = false;
        /**
         * Show fragment (we use show instead of replace to keep the map state)
         */
        if (mMapFragment == null) {
            // First check to see if an instance of BaseMapFragment already exists (see #356)
            mMapFragment = (BaseMapFragment) fm.findFragmentByTag(BaseMapFragment.TAG);

            if (mMapFragment == null) {
                // No existing fragment was found, so create a new one
                Log.d(TAG, "Creating new BaseMapFragment");
                mMapFragment = BaseMapFragment.newInstance();
                fm.beginTransaction()
                        .add(R.id.main_fragment_container, mMapFragment, BaseMapFragment.TAG)
                        .commit();
            }
        }

        // Register listener for map focus callbacks
        mMapFragment.setOnFocusChangeListener(this);
        mMapFragment.setOnProgressBarChangedListener(this);

        getSupportFragmentManager().beginTransaction().show(mMapFragment).commit();

        showFloatingActionButtons();
        if (mLastMapProgressBarState) {
            showMapProgressBar();
        }
        mShowArrivalsMenu = true;
        if (mFocusedStopId != null && mSlidingPanel != null) {
            // if we've focused on a stop, then show the panel that was previously hidden
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        }
        setTitle(getResources().getString(R.string.navdrawer_item_nearby));
    }

    private void showStarredStopsFragment() {
        FragmentManager fm = getSupportFragmentManager();
        /**
         * Hide everything that shouldn't be shown
         */
        hideFloatingActionButtons();
        hideMapProgressBar();
        hideMapFragment();
        hideReminderFragment();
        hideSlidingPanel();
        mShowArrivalsMenu = false;
        /**
         * Show fragment (we use show instead of replace to keep the map state)
         */
        mShowStarredStopsMenu = true;
        if (mMyStarredStopsFragment == null) {
            // First check to see if an instance of MyStarredStopsFragment already exists (see #356)
            mMyStarredStopsFragment = (MyStarredStopsFragment) fm
                    .findFragmentByTag(MyStarredStopsFragment.TAG);

            if (mMyStarredStopsFragment == null) {
                // No existing fragment was found, so create a new one
                Log.d(TAG, "Creating new MyStarredStopsFragment");
                mMyStarredStopsFragment = new MyStarredStopsFragment();
                fm.beginTransaction().add(R.id.main_fragment_container, mMyStarredStopsFragment,
                        MyStarredStopsFragment.TAG).commit();
            }
        }
        fm.beginTransaction().show(mMyStarredStopsFragment).commit();
        setTitle(getResources().getString(R.string.navdrawer_item_starred_stops));
    }

    private void showMyRemindersFragment() {
        FragmentManager fm = getSupportFragmentManager();
        /**
         * Hide everything that shouldn't be shown
         */
        hideFloatingActionButtons();
        hideMapProgressBar();
        hideStarredStopsFragment();
        hideMapFragment();
        hideSlidingPanel();
        mShowArrivalsMenu = false;
        mShowStarredStopsMenu = false;
        /**
         * Show fragment (we use show instead of replace to keep the map state)
         */
        if (mMyRemindersFragment == null) {
            // First check to see if an instance of MyRemindersFragment already exists (see #356)
            mMyRemindersFragment = (MyRemindersFragment) fm
                    .findFragmentByTag(MyRemindersFragment.TAG);

            if (mMyRemindersFragment == null) {
                // No existing fragment was found, so create a new one
                Log.d(TAG, "Creating new MyRemindersFragment");
                mMyRemindersFragment = new MyRemindersFragment();
                fm.beginTransaction().add(R.id.main_fragment_container, mMyRemindersFragment,
                        MyRemindersFragment.TAG).commit();
            }
        }
        fm.beginTransaction().show(mMyRemindersFragment).commit();
        setTitle(getResources().getString(R.string.navdrawer_item_my_reminders));
    }

    private void hideMapFragment() {
        FragmentManager fm = getSupportFragmentManager();
        mMapFragment = (BaseMapFragment) fm.findFragmentByTag(BaseMapFragment.TAG);
        if (mMapFragment != null && !mMapFragment.isHidden()) {
            fm.beginTransaction().hide(mMapFragment).commit();
        }
    }

    private void hideStarredStopsFragment() {
        FragmentManager fm = getSupportFragmentManager();
        mMyStarredStopsFragment = (MyStarredStopsFragment) fm.findFragmentByTag(
                MyStarredStopsFragment.TAG);
        if (mMyStarredStopsFragment != null && !mMyStarredStopsFragment.isHidden()) {
            fm.beginTransaction().hide(mMyStarredStopsFragment).commit();
        }
    }

    private void hideReminderFragment() {
        FragmentManager fm = getSupportFragmentManager();
        mMyRemindersFragment = (MyRemindersFragment) fm
                .findFragmentByTag(MyRemindersFragment.TAG);
        if (mMyRemindersFragment != null && !mMyRemindersFragment.isHidden()) {
            fm.beginTransaction().hide(mMyRemindersFragment).commit();
        }
    }

    private void hideSlidingPanel() {
        if (mSlidingPanel != null) {
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_options, menu);

        UIUtils.setupSearch(this, menu);

        // Initialize fragment menu visibility here, so we don't have overlap between the various fragments
        setupOptionsMenu(menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Manage fragment menu visibility here, so we don't have overlap between the various fragments
        setupOptionsMenu(menu);

        return true;
    }

    private void setupOptionsMenu(Menu menu) {
        menu.setGroupVisible(R.id.main_options_menu_group, true);
        menu.setGroupVisible(R.id.arrival_list_menu_group, mShowArrivalsMenu);
        menu.setGroupVisible(R.id.starred_stop_menu_group, mShowStarredStopsMenu);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        final int id = item.getItemId();
        if (id == R.id.search) {
            onSearchRequested();
            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_button_press_search_box));
            return true;
        } else if (id == R.id.recent_stops_routes) {
            ShowcaseViewUtils.doNotShowTutorial(ShowcaseViewUtils.TUTORIAL_RECENT_STOPS_ROUTES);
            Intent myIntent = new Intent(this, MyRecentStopsAndRoutesActivity.class);
            startActivity(myIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case HELP_DIALOG:
                return createHelpDialog();

            case WHATSNEW_DIALOG:
                return createWhatsNewDialog();

            case LEGEND_DIALOG:
                return createLegendDialog();
        }
        return super.onCreateDialog(id);
    }

    @SuppressWarnings("deprecation")
    private Dialog createHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_title);
        // If a custom API URL is set, hide Contact Us, as we don't have a contact email to use
        int options;
        if (TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
            options = R.array.main_help_options;
        } else {
            // Hide "Contact Us"
            options = R.array.main_help_options_no_contact_us;
        }
        builder.setItems(options,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                ShowcaseViewUtils.resetAllTutorials(HomeActivity.this);
                                mNavigationDrawerFragment.setSavedPosition(NAVDRAWER_ITEM_NEARBY);
                                NavHelp.goHome(HomeActivity.this, true);

                                break;
                            case 1:
                                showDialog(LEGEND_DIALOG);
                                break;
                            case 2:
                                showDialog(WHATSNEW_DIALOG);
                                break;
                            case 3:
                                AgenciesActivity.start(HomeActivity.this);
                                break;
                            case 4:
                                String twitterUrl = TWITTER_URL;
                                if (Application.get().getCurrentRegion() != null &&
                                        !TextUtils.isEmpty(Application.get().getCurrentRegion()
                                                .getTwitterUrl())) {
                                    twitterUrl = Application.get().getCurrentRegion()
                                            .getTwitterUrl();
                                }
                                UIUtils.goToUrl(HomeActivity.this, twitterUrl);
                                ObaAnalytics.reportEventWithCategory(
                                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                        getString(R.string.analytics_action_switch),
                                        getString(R.string.analytics_label_app_switch));
                                break;
                            case 5:
                                // Contact us
                                goToSendFeedBack();
                                break;
                        }
                    }
                }
        );
        return builder.create();
    }

    @SuppressWarnings("deprecation")
    private Dialog createWhatsNewDialog() {
        TextView textView = (TextView) getLayoutInflater().inflate(R.layout.whats_new_dialog, null);
        textView.setText(R.string.main_help_whatsnew);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_whatsnew_title);
        builder.setIcon(R.mipmap.ic_launcher);
        builder.setView(textView);
        builder.setNeutralButton(R.string.main_help_close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dismissDialog(WHATSNEW_DIALOG);
                    }
                }
        );
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                boolean showOptOut = Application.getPrefs()
                        .getBoolean(ShowcaseViewUtils.TUTORIAL_OPT_OUT_DIALOG, true);
                if (showOptOut) {
                    ShowcaseViewUtils.showOptOutDialog(HomeActivity.this);
                }
            }
        });
        return builder.create();
    }

    @SuppressWarnings("deprecation")
    private Dialog createLegendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_legend_title);

        Resources resources = getResources();
        LayoutInflater inflater = LayoutInflater.from(getApplicationContext());
        View legendDialogView = inflater.inflate(R.layout.legend_dialog, null);
        final float etaTextFontSize = 30;

        // On time view
        View etaAndMin = legendDialogView.findViewById(R.id.eta_view_ontime);
        GradientDrawable d1 = (GradientDrawable) etaAndMin.getBackground();
        d1.setColor(resources.getColor(R.color.stop_info_ontime));
        etaAndMin.findViewById(R.id.eta_realtime_indicator).setVisibility(View.VISIBLE);
        TextView etaTextView = ((TextView) etaAndMin.findViewById(R.id.eta));
        etaTextView.setTextSize(etaTextFontSize);
        etaTextView.setText("5");

        // Early View
        etaAndMin = legendDialogView.findViewById(R.id.eta_view_early);
        d1 = (GradientDrawable) etaAndMin.getBackground();
        d1.setColor(resources.getColor(R.color.stop_info_early));
        etaAndMin.findViewById(R.id.eta_realtime_indicator).setVisibility(View.VISIBLE);
        etaTextView = ((TextView) etaAndMin.findViewById(R.id.eta));
        etaTextView.setTextSize(etaTextFontSize);
        etaTextView.setText("5");

        // Delayed View
        etaAndMin = legendDialogView.findViewById(R.id.eta_view_delayed);
        d1 = (GradientDrawable) etaAndMin.getBackground();
        d1.setColor(resources.getColor(R.color.stop_info_delayed));
        etaAndMin.findViewById(R.id.eta_realtime_indicator).setVisibility(View.VISIBLE);
        etaTextView = ((TextView) etaAndMin.findViewById(R.id.eta));
        etaTextView.setTextSize(etaTextFontSize);
        etaTextView.setText("5");

        // Scheduled View
        etaAndMin = legendDialogView.findViewById(R.id.eta_view_scheduled);
        d1 = (GradientDrawable) etaAndMin.getBackground();
        d1.setColor(resources.getColor(R.color.stop_info_scheduled_time));
        etaAndMin.findViewById(R.id.eta_realtime_indicator).setVisibility(View.INVISIBLE);
        etaTextView = ((TextView) etaAndMin.findViewById(R.id.eta));
        etaTextView.setTextSize(etaTextFontSize);
        etaTextView.setText("5");

        builder.setView(legendDialogView);

        builder.setNeutralButton(R.string.main_help_close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dismissDialog(LEGEND_DIALOG);
                    }
                }
        );
        return builder.create();
    }

    /**
     * Show the "What's New" message if a new version was just installed
     *
     * @return true if a new version was just installed, false if not
     */
    @SuppressWarnings("deprecation")
    private boolean autoShowWhatsNew() {
        SharedPreferences settings = Application.getPrefs();

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return false;
        }

        final int oldVer = settings.getInt(WHATS_NEW_VER, 0);
        final int newVer = appInfo.versionCode;

        if (oldVer < newVer) {
            showDialog(WHATSNEW_DIALOG);

            // Updates will remove the alarms. This should put them back.
            // (Unfortunately I can't find a way to reschedule them without
            // having the app run again).
            TripService.scheduleAll(this);
            PreferenceUtils.saveInt(WHATS_NEW_VER, appInfo.versionCode);
            return true;
        }
        return false;
    }

    /**
     * Called by the BaseMapFragment when a stop obtains focus, or no stops have focus
     *
     * @param stop     the ObaStop that obtained focus, or null if no stop is in focus
     * @param routes   a HashMap of all route display names that serve this stop - key is routeId
     * @param location the user touch location on the map, or null if the focus was otherwise
     *                 cleared programmatically
     */
    @Override
    public void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location) {
        // Check to see if we're already focused on this same stop - if so, we shouldn't do anything
        if (mFocusedStopId != null && stop != null &&
                mFocusedStopId.equalsIgnoreCase(stop.getId())) {
            return;
        }

        mFocusedStop = stop;

        if (stop != null) {
            mBikeRentalStationId = null;
            mFocusedStopId = stop.getId();
            // A stop on the map was just tapped, show it in the sliding panel
            updateArrivalListFragment(stop.getId(), stop.getName(), stop.getStopCode(), stop,
                    routes);

            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_button_press_map_icon));
        } else {
            // No stop is in focus (e.g., user tapped on the map), so hide the panel
            // and clear the currently focused stopId
            mFocusedStopId = null;
            moveFabsLocation();
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
            if (mArrivalsListFragment != null) {
                FragmentManager fm = getSupportFragmentManager();
                fm.beginTransaction().remove(mArrivalsListFragment).commit();
            }
            mShowArrivalsMenu = false;
        }
    }

    /**
     * Called from the BaseMapFragment when a BikeRentalStation is clicked.
     *
     * @param bikeRentalStation the bike rental station that was clicked.
     */
    @Override
    public void onFocusChanged(BikeRentalStation bikeRentalStation) {
        Log.d(TAG, "Bike Station Clicked on map");

        // Check to see if we're already focused on this same bike rental station - if so, we shouldn't do anything
        if (mBikeRentalStationId != null && bikeRentalStation != null &&
                mBikeRentalStationId.equalsIgnoreCase(bikeRentalStation.id)) {
            return;
        }

        if (bikeRentalStation == null) {
            mBikeRentalStationId = null;
        } else {
            mBikeRentalStationId = bikeRentalStation.id;
        }
    }

    @Override
    public void onProgressBarChanged(boolean showProgressBar) {
        mLastMapProgressBarState = showProgressBar;
        if (showProgressBar) {
            showMapProgressBar();
        } else {
            hideMapProgressBar();
        }
    }

    /**
     * Called by the ArrivalsListFragment when the ListView is created
     *
     * @param listView the ListView that was just created
     */
    @Override
    public void onListViewCreated(ListView listView) {
        // Set the scrollable view in the sliding panel
        mSlidingPanel.setScrollableView(listView);
    }

    /**
     * Called by the ArrivalsListFragment when we have new updated arrival information
     *
     * @param response new arrival information
     */
    @Override
    public void onArrivalTimesUpdated(ObaArrivalInfoResponse response) {
        if (response == null || response.getStop() == null) {
            return;
        }

        // If we're missing any local references (e.g., if orientation just changed), store the values
        if (mFocusedStopId == null) {
            mFocusedStopId = response.getStop().getId();
        }
        if (mFocusedStop == null) {
            mFocusedStop = response.getStop();

            // Since mFocusedStop was null, the layout changed, and we should recenter map on stop
            if (mMapFragment != null && mSlidingPanel != null) {
                mMapFragment.setMapCenter(mFocusedStop.getLocation(), false,
                        mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED);
            }

            // ...and we should add a focus marker for this stop
            if (mMapFragment != null) {
                mMapFragment.setFocusStop(mFocusedStop, response.getRoutes());
            }
        }

        // Header might have changed height, so make sure my location button is set above the header
        moveFabsLocation();

        // Show arrival info related tutorials
        showArrivalInfoTutorials(response);
    }

    /**
     * Triggers the various tutorials related to arrival info and the sliding panel header
     *
     * @param response arrival info, which is required for some tutorials
     */
    private void showArrivalInfoTutorials(ObaArrivalInfoResponse response) {
        // If we're already showing a ShowcaseView, we don't want to stack another on top
        if (ShowcaseViewUtils.isShowcaseViewShowing()) {
            return;
        }

        // If we can't see the map or sliding panel, we can't see the arrival info, so return
        if (mMapFragment.isHidden() || !mMapFragment.isVisible() ||
                mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
            return;
        }

        /**
         * The ShowcaseViewUtils.showTutorial() method takes care of checking if a tutorial has
         * already been shown, so we can just list the tutorials in order of how they should be
         * shown to the user.
         */

        // Show the tutorial explaining arrival times
        ShowcaseViewUtils.showTutorial(ShowcaseViewUtils.TUTORIAL_ARRIVAL_HEADER_ARRIVAL_INFO, this,
                response);

        // Make sure the panel is stationary before showing the starred routes tutorial
        if (mSlidingPanel != null &&
                (isSlidingPanelCollapsed() ||
                        mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED ||
                        mSlidingPanel.getPanelState()
                                == SlidingUpPanelLayout.PanelState.EXPANDED)) {
            ShowcaseViewUtils
                    .showTutorial(ShowcaseViewUtils.TUTORIAL_ARRIVAL_HEADER_STAR_ROUTE, this,
                            response);
        }
        ShowcaseViewUtils.showTutorial(ShowcaseViewUtils.TUTORIAL_RECENT_STOPS_ROUTES, this, null);
    }

    /**
     * Called by the ArrivalListFragment when the user selects the "Show route on map" for a
     * particular route/trip
     *
     * @param arrivalInfo The arrival information for the route/trip that the user selected
     * @return true if the listener has consumed the event, false otherwise
     */
    @Override
    public boolean onShowRouteOnMapSelected(ArrivalInfo arrivalInfo) {
        // If the panel is fully expanded, change it to anchored so the user can see the map
        if (mSlidingPanel != null) {
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        }

        Bundle bundle = new Bundle();
        bundle.putBoolean(MapParams.ZOOM_TO_ROUTE, false);
        bundle.putBoolean(MapParams.ZOOM_INCLUDE_CLOSEST_VEHICLE, true);
        bundle.putString(MapParams.ROUTE_ID, arrivalInfo.getInfo().getRouteId());
        mMapFragment.setMapMode(MapParams.MODE_ROUTE, bundle);

        return true;
    }

    /**
     * Called when the user selects the "Sort by" option in ArrivalsListFragment
     */
    @Override
    public void onSortBySelected() {
        // If the sliding panel isn't open, then open it to show what we're sorting
        if (mSlidingPanel != null) {
            if (isSlidingPanelCollapsed()) {
                mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.ANCHORED);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Collapse the panel when the user presses the back button
        if (mSlidingPanel != null) {
            // Collapse the sliding panel if its anchored or expanded
            if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED
                    || mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED) {
                mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                return;
            }
            // Clear focused stop and close the sliding panel if its collapsed
            if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                // Clear the stop focus in map fragment, which will trigger a callback to close the
                // panel via BaseMapFragment.OnFocusChangedListener in this.onFocusChanged()
                mMapFragment.setFocusStop(null, null);
                return;
            }
        }
        super.onBackPressed();
    }

    /**
     * Redraw navigation drawer. This is necessary because we do not know whether to draw the
     * "Plan A Trip" option until a region is selected.
     */
    private void redrawNavigationDrawerFragment() {
        if (mNavigationDrawerFragment != null) {
            mNavigationDrawerFragment.populateNavDrawer();
        }
    }

    /**
     * Create a new fragment to show the arrivals list for the given stop.  An ObaStop object
     * should
     * be passed in if available.  In all cases a stopId, stopName, and stopCode must be provided.
     *
     * @param stopId   Stop ID of the stop to show arrivals for
     * @param stopName Stop name of the stop to show arrivals for
     * @param stopCode Stop Code (rider-facing ID) of the stop to show arrivals for
     * @param stop     The ObaStop object for the stop to show arrivals for, or null if we don't
     *                 have
     *                 this yet.
     * @param routes   A HashMap of all route display names that serve this stop - key is routeId,
     *                 or
     *                 null if we don't have this yet.
     */
    private void updateArrivalListFragment(@NonNull String stopId, @NonNull String stopName,
                                           @NonNull String stopCode, ObaStop stop, HashMap<String, ObaRoute> routes) {
        FragmentManager fm = getSupportFragmentManager();
        Intent intent;

        mArrivalsListFragment = new ArrivalsListFragment();
        mArrivalsListFragment.setListener(this);

        // Set the header for the arrival list to be the top of the sliding panel
        mArrivalsListHeader = new ArrivalsListHeader(this, mArrivalsListFragment,
                getSupportFragmentManager());
        mArrivalsListFragment.setHeader(mArrivalsListHeader, mArrivalsListHeaderView);
        mArrivalsListHeader.setSlidingPanelController(mSlidingPanelController);
        mArrivalsListHeader.setSlidingPanelCollapsed(isSlidingPanelCollapsed());
        mShowArrivalsMenu = true;
        mExpandCollapse = (ImageView) mArrivalsListHeaderView.findViewById(R.id.expand_collapse);

        if (stop != null && routes != null) {
            // Use ObaStop and ObaRoute objects, since we can pre-populate some of the fields
            // before getting an API response
            intent = new ArrivalsListFragment.IntentBuilder(this, stop, routes).build();
        } else {
            // We don't have an ObaStop (likely started from Intent or after rotating device)
            // Some fields will be blank until we get an API response
            intent = new ArrivalsListFragment.IntentBuilder(this, stopId)
                    .setStopName(stopName)
                    .setStopCode(stopCode)
                    .build();
        }

        mArrivalsListFragment.setArguments(FragmentUtils.getIntentArgs(intent));
        fm.beginTransaction().replace(R.id.slidingFragment, mArrivalsListFragment).commit();
        showSlidingPanel();
        moveFabsLocation();
    }

    private void showSlidingPanel() {
        if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
        }

    }

    private void goToSendFeedBack() {
        if (mFocusedStop != null) {
            ReportActivity.start(this, mFocusedStopId, mFocusedStop.getName(), mFocusedStop.getStopCode(),
                    mFocusedStop.getLatitude(), mFocusedStop.getLongitude(), mGoogleApiClient);
        } else {
            Location loc = Application.getLastKnownLocation(this, mGoogleApiClient);
            if (loc != null) {
                ReportActivity.start(this, loc.getLatitude(), loc.getLongitude(), mGoogleApiClient);
            } else {
                ReportActivity.start(this, mGoogleApiClient);
            }
        }
    }

    /**
     * Checks region status, which can potentially including forcing a reload of region
     * info from the server.  Also includes auto-selection of closest region.
     */
    private void checkRegionStatus() {
        //First check for custom API URL set by user via Preferences, since if that is set we don't need region info from the REST API
        if (!TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
            return;
        }

        // Check if region is hard-coded for this build flavor
        if (BuildConfig.USE_FIXED_REGION) {
            ObaRegion r = RegionUtils.getRegionFromBuildFlavor();
            // Set the hard-coded region
            RegionUtils.saveToProvider(this, Collections.singletonList(r));
            Application.get().setCurrentRegion(r);
            // Disable any region auto-selection in preferences
            PreferenceUtils
                    .saveBoolean(getString(R.string.preference_key_auto_select_region), false);
            return;
        }

        boolean forceReload = false;
        boolean showProgressDialog = true;

        //If we don't have region info selected, or if enough time has passed since last region info update,
        //force contacting the server again
        if (Application.get().getCurrentRegion() == null ||
                new Date().getTime() - Application.get().getLastRegionUpdateDate()
                        > REGION_UPDATE_THRESHOLD) {
            forceReload = true;
            Log.d(TAG,
                    "Region info has expired (or does not exist), forcing a reload from the server...");
        }

        if (Application.get().getCurrentRegion() != null) {
            //We already have region info locally, so just check current region status quietly in the background
            showProgressDialog = false;
        }

        try {
            PackageInfo appInfo = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            SharedPreferences settings = Application.getPrefs();
            final int oldVer = settings.getInt(CHECK_REGION_VER, 0);
            final int newVer = appInfo.versionCode;

            if (oldVer < newVer) {
                forceReload = true;
            }
            PreferenceUtils.saveInt(CHECK_REGION_VER, appInfo.versionCode);
        } catch (NameNotFoundException e) {
            // Do nothing
        }

        //Check region status, possibly forcing a reload from server and checking proximity to current region
        List<ObaRegionsTask.Callback> callbacks = new ArrayList<>();
        callbacks.add(mMapFragment);
        callbacks.add(this);
        ObaRegionsTask task = new ObaRegionsTask(this, callbacks, forceReload, showProgressDialog);
        task.execute();
    }

    //
    // Region Task Callback
    //
    @Override
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        // Show "What's New" (which might need refreshed Regions API contents)
        boolean update = autoShowWhatsNew();

        // Redraw nav drawer if the region changed, or if we just installed a new version
        if (currentRegionChanged || update) {
            redrawNavigationDrawerFragment();
        }

        // If region changed and was auto-selected, show user what region we're using
        if (currentRegionChanged
                && Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                && Application.get().getCurrentRegion() != null
                && UIUtils.canManageDialog(this)) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.region_region_found,
                            Application.get().getCurrentRegion().getName()),
                    Toast.LENGTH_LONG
            ).show();
        }
        updateLayersFab();
    }

    private void setupMyLocationButton() {
        // Initialize the My Location button
        mFabMyLocation = (FloatingActionButton) findViewById(R.id.btnMyLocation);
        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mMapFragment != null) {
                    // Reset the preference to ask user to enable location
                    PreferenceUtils.saveBoolean(getString(R.string.preference_key_never_show_location_dialog), false);

                    mMapFragment.setMyLocation(true, true);
                    ObaAnalytics.reportEventWithCategory(
                            ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getString(R.string.analytics_action_button_press),
                            getString(R.string.analytics_label_button_press_location));
                }
            }
        });
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) mFabMyLocation
                .getLayoutParams();
        MY_LOC_DEFAULT_BOTTOM_MARGIN = p.bottomMargin;
        checkLeftHandMode();
        if (mCurrentNavDrawerPosition == NAVDRAWER_ITEM_NEARBY) {
            showFloatingActionButtons();
            showMapProgressBar();
        } else {
            hideFloatingActionButtons();
            hideMapProgressBar();
        }
    }

    private void checkLeftHandMode() {
        if (mFabMyLocation == null) {
            return;
        }
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mFabMyLocation
                .getLayoutParams();

        boolean leftHandMode = Application.getPrefs().getBoolean(
                getString(R.string.preference_key_left_hand_mode), false);
        if (leftHandMode) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            }
        } else {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
            }
        }
    }

    /**
     * Moves both Floating Action Buttons as response to sliding panel height changes.
     * <p>
     * Currently there are two FAB that can be moved, the My location button and the Layers button.
     */
    synchronized private void moveFabsLocation() {
        moveFabLocation(mFabMyLocation, MY_LOC_DEFAULT_BOTTOM_MARGIN);
        moveFabLocation(mLayersFab, LAYERS_FAB_DEFAULT_BOTTOM_MARGIN);
    }

    private void moveFabLocation(final View fab, final int initialMargin) {
        if (fab == null) {
            return;
        }
        if (mMyLocationAnimation != null &&
                (mMyLocationAnimation.hasStarted() && !mMyLocationAnimation.hasEnded())) {
            // We're already animating - do nothing

            //return;
        }

        if (mMyLocationAnimation != null) {
            mMyLocationAnimation.reset();
        }

        // Post this to a handler to allow the header to settle before animating the button
        final Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                final ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) fab
                        .getLayoutParams();

                int tempMargin = initialMargin;

                if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    tempMargin += mSlidingPanel.getPanelHeight();
                    if (p.bottomMargin == tempMargin) {
                        // Button is already in the right position, do nothing
                        return;
                    }
                } else {
                    if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
                        if (p.bottomMargin == tempMargin) {
                            // Button is already in the right position, do nothing
                            return;
                        }
                    }
                }

                final int goalMargin = tempMargin;
                final int currentMargin = p.bottomMargin;

                // TODO - this doesn't seem to be animating?? Why not?  Or is it just my device...
                mMyLocationAnimation = new Animation() {
                    @Override
                    protected void applyTransformation(float interpolatedTime, Transformation t) {
                        int bottom;
                        if (goalMargin > currentMargin) {
                            bottom = currentMargin + (int) (Math.abs(currentMargin - goalMargin)
                                    * interpolatedTime);
                        } else {
                            bottom = currentMargin - (int) (Math.abs(currentMargin - goalMargin)
                                    * interpolatedTime);
                        }
                        UIUtils.setMargins(fab,
                                p.leftMargin,
                                p.topMargin,
                                p.rightMargin,
                                bottom);
                    }
                };
                mMyLocationAnimation.setDuration(MY_LOC_BTN_ANIM_DURATION);
                fab.startAnimation(mMyLocationAnimation);
            }
        }, 100);
    }

    private void showFloatingActionButtons() {
        if (mFabMyLocation == null && mLayersFab == null) {
            return;
        }
        if (mFabMyLocation != null && mFabMyLocation.getVisibility() != View.VISIBLE) {
            mFabMyLocation.setVisibility(View.VISIBLE);
        }
        if (mLayersFab != null && mLayersFab.getVisibility() != View.VISIBLE) {
            if (Application.isBikeshareEnabled()) {
                mLayersFab.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideFloatingActionButtons() {
        if (mFabMyLocation == null && mLayersFab == null) {
            return;
        }
        if (mFabMyLocation != null && mFabMyLocation.getVisibility() != View.GONE) {
            mFabMyLocation.setVisibility(View.GONE);
        }
        if (mLayersFab != null && mLayersFab.getVisibility() != View.GONE) {
            mLayersFab.setVisibility(View.GONE);
        }
    }

    private void showMapProgressBar() {
        if (mMapProgressBar == null) {
            return;
        }
        if (mMapProgressBar.getVisibility() != View.VISIBLE) {
            mMapProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideMapProgressBar() {
        if (mMapProgressBar == null) {
            return;
        }
        if (mMapProgressBar.getVisibility() != View.GONE) {
            mMapProgressBar.setVisibility(View.GONE);
        }
    }

    private void setupNavigationDrawer() {
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.nav_drawer_left_pane));

        // Was this activity started to show a route or stop on the map? If so, switch to MapFragment
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            String routeId = bundle.getString(MapParams.ROUTE_ID);
            String stopId = bundle.getString(MapParams.STOP_ID);
            if (routeId != null || stopId != null) {
                mNavigationDrawerFragment.selectItem(NAVDRAWER_ITEM_NEARBY);
            }
        }
    }

    private void setupGooglePlayServices() {
        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(this)
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(this);
            mGoogleApiClient.connect();
        }
    }

    private void setupLayersSpeedDial() {
        mLayersFab = (uk.co.markormesher.android_fab.FloatingActionButton) findViewById(R.id.layersSpeedDial);

        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) mLayersFab
                .getLayoutParams();
        LAYERS_FAB_DEFAULT_BOTTOM_MARGIN = p.bottomMargin;

        mLayersFab.setIcon(R.drawable.ic_layers_white_24dp);
        mLayersFab.setBackgroundColour(ContextCompat.getColor(this, R.color.theme_accent));

        LayersSpeedDialAdapter adapter = new LayersSpeedDialAdapter(this);
        // Add the BaseMapFragment listener to activate the layer on the map
        adapter.addLayerActivationListener(mMapFragment);

        // Add another listener to rebuild the menu options after selection. This other listener
        // was added here because the call to rebuildSpeedDialMenu exists on the FAB and we have a
        // reference to it only in the main activity.
        adapter.addLayerActivationListener(new LayersSpeedDialAdapter.LayerActivationListener() {
            @Override
            public void onActivateLayer(LayerInfo layer) {
                Handler h = new Handler(getMainLooper());
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mLayersFab.rebuildSpeedDialMenu();
                    }
                }, 100);
            }

            @Override
            public void onDeactivateLayer(LayerInfo layer) {
                Handler h = new Handler(getMainLooper());
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mLayersFab.rebuildSpeedDialMenu();
                    }
                }, 100);
            }
        });
        mLayersFab.setMenuAdapter(adapter);
        mLayersFab.setOnSpeedDialOpenListener(
                new uk.co.markormesher.android_fab.FloatingActionButton.OnSpeedDialOpenListener() {
                    @Override
                    public void onOpen(uk.co.markormesher.android_fab.FloatingActionButton v) {
                        mLayersFab.setIcon(R.drawable.ic_add_white_24dp);
                    }
                });
        mLayersFab.setOnSpeedDialCloseListener(
                new uk.co.markormesher.android_fab.FloatingActionButton.OnSpeedDialCloseListener() {
                    @Override
                    public void onClose(uk.co.markormesher.android_fab.FloatingActionButton v) {
                        mLayersFab.setIcon(R.drawable.ic_layers_white_24dp);
                    }
                });
        mLayersFab.setContentCoverEnabled(false);
    }

    /**
     * Method used to (re)display the layers FAB button when the activity restarts or regions data
     * is updated
     */
    private void updateLayersFab() {
        if (Application.isBikeshareEnabled()
                && mCurrentNavDrawerPosition == NAVDRAWER_ITEM_NEARBY) {
            mLayersFab.setVisibility(View.VISIBLE);
        } else {
            mLayersFab.setVisibility(View.GONE);
        }
        mLayersFab.rebuildSpeedDialMenu();
    }


    private void setupSlidingPanel() {
        mSlidingPanel = (SlidingUpPanelLayout) findViewById(R.id.bottom_sliding_layout);
        mArrivalsListHeaderView = findViewById(R.id.arrivals_list_header);
        mArrivalsListHeaderSubView = mArrivalsListHeaderView.findViewById(R.id.main_header_content);

        mSlidingPanel.setPanelState(
                SlidingUpPanelLayout.PanelState.HIDDEN);  // Don't show the panel until we have content
        mSlidingPanel.setOverlayed(true);
        mSlidingPanel.setAnchorPoint(MapModeController.OVERLAY_PERCENTAGE);
        mSlidingPanel.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {

                if (previousState == SlidingUpPanelLayout.PanelState.HIDDEN) {
                    return;
                }

                switch (newState) {
                    case EXPANDED:
                        onPanelExpanded(panel);
                        break;
                    case COLLAPSED:
                        onPanelCollapsed(panel);
                        break;
                    case ANCHORED:
                        onPanelAnchored(panel);
                        break;
                    case HIDDEN:
                        onPanelHidden(panel);
                        break;
                }
            }

            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                Log.d(TAG, "onPanelSlide, offset " + slideOffset);
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.closeStatusPopups();
                }
            }

            public void onPanelExpanded(View panel) {
                Log.d(TAG, "onPanelExpanded");
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.setSlidingPanelCollapsed(false);
                    mArrivalsListHeader.refresh();
                }

                // Accessibility
                if (mExpandCollapse != null) {
                    mExpandCollapse.setContentDescription(mContext.getResources()
                            .getString(R.string.stop_header_sliding_panel_open));
                }
            }

            public void onPanelCollapsed(View panel) {
                Log.d(TAG, "onPanelCollapsed");
                if (mMapFragment != null) {
                    mMapFragment.getMapView()
                            .setPadding(null, null, null, mSlidingPanel.getPanelHeight());
                }
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.setSlidingPanelCollapsed(true);
                    mArrivalsListHeader.refresh();
                }
                moveFabsLocation();

                // Accessibility
                if (mExpandCollapse != null) {
                    mExpandCollapse.setContentDescription(mContext.getResources()
                            .getString(R.string.stop_header_sliding_panel_collapsed));
                }
            }

            public void onPanelAnchored(View panel) {
                Log.d(TAG, "onPanelAnchored");
                if (mMapFragment != null) {
                    mMapFragment.getMapView()
                            .setPadding(null, null, null, mSlidingPanel.getPanelHeight());
                }
                if (mFocusedStop != null && mMapFragment != null) {
                    mMapFragment.setMapCenter(mFocusedStop.getLocation(), true, true);
                }
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.setSlidingPanelCollapsed(false);
                    mArrivalsListHeader.refresh();
                }

                // Accessibility
                if (mExpandCollapse != null) {
                    mExpandCollapse.setContentDescription(mContext.getResources()
                            .getString(R.string.stop_header_sliding_panel_open));
                }
            }

            public void onPanelHidden(View panel) {
                Log.d(TAG, "onPanelHidden");
                // We need to hide the panel when switching between fragments via the navdrawer,
                // so we shouldn't put anything here that causes us to lose the state of the
                // MapFragment or the ArrivalListFragment (e.g., removing the ArrivalListFragment)
                if (mMapFragment != null) {
                    mMapFragment.getMapView().setPadding(null, null, null, 0);
                }

                // Accessibility - reset it here so its ready for next showing
                if (mExpandCollapse != null) {
                    mExpandCollapse.setContentDescription(mContext.getResources()
                            .getString(R.string.stop_header_sliding_panel_collapsed));
                }
            }
        });

        mSlidingPanelController = new SlidingPanelController() {
            @Override
            public void setPanelHeightPixels(int heightInPixels) {
                if (mSlidingPanel != null) {
                    if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.DRAGGING ||
                            mSlidingPanel.getPanelState()
                                    == SlidingUpPanelLayout.PanelState.HIDDEN) {
                        // Don't resize header yet - see #294 - header size will be refreshed on panel state change
                        return;
                    }
                    if (mSlidingPanel.getPanelHeight() != heightInPixels) {
                        mSlidingPanel.setPanelHeight(heightInPixels);
                        mArrivalsListHeaderView.getLayoutParams().height = heightInPixels;
                        mArrivalsListHeaderSubView.getLayoutParams().height = heightInPixels;
                    }
                }
            }

            @Override
            public int getPanelHeightPixels() {
                if (mSlidingPanel != null) {
                    return mSlidingPanel.getPanelHeight();
                }
                return -1;
            }
        };
    }

    /**
     * Sets up the initial map state, based on a previous savedInstanceState for this activity,
     * or an Intent that was passed into this activity
     */
    private void setupMapState(Bundle savedInstanceState) {
        String stopId;
        String stopName;
        String stopCode;
        // Check savedInstanceState to see if there is a previous state for this activity
        if (savedInstanceState != null) {
            // We're recreating an instance with a previous state, so show the focused stop in panel
            stopId = savedInstanceState.getString(MapParams.STOP_ID);
            stopName = savedInstanceState.getString(MapParams.STOP_NAME);
            stopCode = savedInstanceState.getString(MapParams.STOP_CODE);

            if (stopId != null) {
                mFocusedStopId = stopId;
                // We don't have an ObaStop or ObaRoute mapping, so just pass in null for those
                updateArrivalListFragment(stopId, stopName, stopCode, null, null);
            }
        } else {
            // Check intent passed into Activity
            Bundle bundle = getIntent().getExtras();
            if (bundle != null) {
                // Did this activity start to focus on a stop?  If so, set focus and show arrival info
                stopId = bundle.getString(MapParams.STOP_ID);
                stopName = bundle.getString(MapParams.STOP_NAME);
                stopCode = bundle.getString(MapParams.STOP_CODE);
                double lat = bundle.getDouble(MapParams.CENTER_LAT);
                double lon = bundle.getDouble(MapParams.CENTER_LON);

                if (stopId != null && lat != 0.0 && lon != 0.0) {
                    mFocusedStopId = stopId;
                    updateArrivalListFragment(stopId, stopName, stopCode, null, null);
                }
            }
        }
        mMapProgressBar = (ProgressBar) findViewById(R.id.progress_horizontal);
    }

    /**
     * Our definition of collapsed is consistent with SlidingPanel pre-v3.0.0 definition - we don't
     * consider the panel changing from the hidden state to the collapsed state to be a "collapsed"
     * event.  v3.0.0 and higher fire the "collapsed" event when coming from the hidden state.
     * This method provides us with a collapsed state that is consistent with the pre-v3.0.0
     * definition
     * of a collapse event, to make our event model consistent with post v3.0.0 SlidingPanel.
     *
     * @return true if the panel isn't expanded or anchored, false if it is not
     */
    private boolean isSlidingPanelCollapsed() {
        return !(mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED ||
                mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED);
    }

    public ArrivalsListFragment getArrivalsListFragment() {
        return mArrivalsListFragment;
    }
}
