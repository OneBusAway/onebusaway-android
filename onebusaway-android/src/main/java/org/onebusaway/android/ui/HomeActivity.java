/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;

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
import org.onebusaway.android.region.ObaRegionsTask;
import org.onebusaway.android.tripservice.TripService;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.PreferenceHelp;
import org.onebusaway.android.util.RegionUtils;
import org.onebusaway.android.util.UIHelp;

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
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_HELP;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_MY_REMINDERS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_NEARBY;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_SEND_FEEDBACK;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_SETTINGS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NAVDRAWER_ITEM_STARRED_STOPS;
import static org.onebusaway.android.ui.NavigationDrawerFragment.NavigationDrawerCallbacks;

public class HomeActivity extends ActionBarActivity
        implements BaseMapFragment.OnFocusChangedListener, ArrivalsListFragment.Listener,
        NavigationDrawerCallbacks {

    interface SlidingPanelController {

        /**
         * Sets the height of the sliding panel in pixels
         *
         * @param heightInPixels height of panel in pixels
         */
        void setPanelHeightPixels(int heightInPixels);
    }

    public static final String TWITTER_URL = "http://mobile.twitter.com/onebusaway";

    public static final String STOP_ID = ".StopId";

    private static final int HELP_DIALOG = 1;

    private static final int WHATSNEW_DIALOG = 2;

    //One week, in milliseconds
    private static final long REGION_UPDATE_THRESHOLD = 1000 * 60 * 60 * 24 * 7;

    private static final String TAG = "HomeActivity";

    Context mContext;

    ArrivalsListFragment mArrivalsListFragment;

    ArrivalsListHeader mArrivalsListHeader;

    View mArrivalsListHeaderView;

    private ImageButton mBtnMyLocation;

    private static int MY_LOC_DEFAULT_BOTTOM_MARGIN;

    private static final int MY_LOC_BTN_ANIM_DURATION = 100;  // ms

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
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    /**
     * Stop that has current focus on the map.  We retain a reference to the StopId,
     * since during rapid rotations its possible that a reference to a ObaStop object in
     * mFocusedStop can still be null, and we don't want to lose the state of which stopId is in
     * focus.  We also need access to the focused stop properties, hence why we also have
     * mFocusedStop
     */
    String mFocusedStopId = null;

    ObaStop mFocusedStop = null;

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static final void start(Context context,
            String focusId,
            double lat,
            double lon) {
        context.startActivity(makeIntent(context, focusId, lat, lon));
    }

    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     */
    public static final void start(Context context, String routeId) {
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
    public static final Intent makeIntent(Context context,
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
     * Returns an intent that starts the MapActivity in "RouteMode", which shows
     * stops along a route, and does not get new stops when the user pans the
     * map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     */
    public static final Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.MODE, MapParams.MODE_ROUTE);
        myIntent.putExtra(MapParams.ZOOM_TO_ROUTE, true);
        myIntent.putExtra(MapParams.ROUTE_ID, routeId);
        return myIntent;
    }

    private int mWhatsNewMessage = R.string.main_help_whatsnew;

    SlidingPanelController mSlidingPanelController;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mContext = this;

        setupNavigationDrawer();

        setupSlidingPanel(savedInstanceState);

        setupMyLocationButton();

        setupGooglePlayServices();

        UIHelp.setupActionBar(this);

        autoShowWhatsNew();

        checkRegionStatus();
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
    }

    @Override
    public void onStop() {
        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mFocusedStopId != null) {
            outState.putString(STOP_ID, mFocusedStopId);
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
                    mCurrentNavDrawerPosition = item;
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
            case NAVDRAWER_ITEM_SETTINGS:
                Intent preferences = new Intent(HomeActivity.this, PreferencesActivity.class);
                startActivity(preferences);
                ObaAnalytics
                        .reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                getString(R.string.analytics_action_button_press),
                                getString(R.string.analytics_label_button_press_settings));
                break;
            case NAVDRAWER_ITEM_HELP:
                showDialog(HELP_DIALOG);
                ObaAnalytics
                        .reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                getString(R.string.analytics_action_button_press),
                                getString(R.string.analytics_label_button_press_help));
                break;
            case NAVDRAWER_ITEM_SEND_FEEDBACK:
                Log.d(TAG, "TODO - show send feedback fragment");
                ObaAnalytics
                        .reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                getString(R.string.analytics_action_button_press),
                                getString(R.string.analytics_label_button_press_feedback));
                break;
        }
    }

    private void showMapFragment() {
        FragmentManager fm = getSupportFragmentManager();
        /**
         * Hide everything that shouldn't be shown
         */
        if (mMyStarredStopsFragment != null && !mMyStarredStopsFragment.isHidden()) {
            fm.beginTransaction().hide(mMyStarredStopsFragment).commit();
        }
        if (mMyRemindersFragment != null && !mMyRemindersFragment.isHidden()) {
            fm.beginTransaction().hide(mMyRemindersFragment).commit();
        }
        /**
         * Show fragment (we use show instead of replace to keep the map state)
         */
        if (mMapFragment == null) {
            mMapFragment = new BaseMapFragment();

            // Register listener for map focus callbacks
            mMapFragment.setOnFocusChangeListener(this);
            fm.beginTransaction().add(R.id.main_fragment_container, mMapFragment).commit();
        }
        getSupportFragmentManager().beginTransaction().show(mMapFragment).commit();
        showMyLocationButton();
        if (mFocusedStopId != null && mSlidingPanel != null) {
            // if we've focused on a stop, then show the panel that was previously hidden
            mSlidingPanel.showPanel();
        }
    }

    private void showStarredStopsFragment() {
        FragmentManager fm = getSupportFragmentManager();
        /**
         * Hide everything that shouldn't be shown
         */
        hideMyLocationButton();
        if (mMapFragment != null && !mMapFragment.isHidden()) {
            fm.beginTransaction().hide(mMapFragment).commit();
        }
        if (mMyRemindersFragment != null && !mMyRemindersFragment.isHidden()) {
            fm.beginTransaction().hide(mMyRemindersFragment).commit();
        }
        if (mSlidingPanel != null) {
            mSlidingPanel.hidePanel();
        }
        /**
         * Show fragment (we use show instead of replace to keep the map state)
         */
        if (mMyStarredStopsFragment == null) {
            mMyStarredStopsFragment = new MyStarredStopsFragment();
            fm.beginTransaction().add(R.id.main_fragment_container, mMyStarredStopsFragment)
                    .commit();
        }
        fm.beginTransaction().show(mMyStarredStopsFragment).commit();
    }

    private void showMyRemindersFragment() {
        FragmentManager fm = getSupportFragmentManager();
        /**
         * Hide everything that shouldn't be shown
         */
        hideMyLocationButton();
        if (mMyStarredStopsFragment != null && !mMyStarredStopsFragment.isHidden()) {
            fm.beginTransaction().hide(mMyStarredStopsFragment).commit();
        }
        if (mMapFragment != null && !mMapFragment.isHidden()) {
            fm.beginTransaction().hide(mMapFragment).commit();
        }
        if (mSlidingPanel != null) {
            mSlidingPanel.hidePanel();
        }
        /**
         * Show fragment (we use show instead of replace to keep the map state)
         */
        if (mMyRemindersFragment == null) {
            mMyRemindersFragment = new MyRemindersFragment();
            fm.beginTransaction().add(R.id.main_fragment_container, mMyRemindersFragment).commit();
        }
        fm.beginTransaction().show(mMyRemindersFragment).commit();
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        MenuInflater inflater = getMenuInflater();
//        inflater.inflate(R.menu.main_options, menu);
//        return true;
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main_options, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
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
        } else if (id == R.id.find_route) {
            Intent myIntent = new Intent(this, MyRoutesActivity.class);
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
        }
        return super.onCreateDialog(id);
    }

    @SuppressWarnings("deprecation")
    private Dialog createHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_title);
        builder.setItems(R.array.main_help_options,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                String twitterUrl = TWITTER_URL;
                                if (Application.get().getCurrentRegion() != null &&
                                        !TextUtils.isEmpty(Application.get().getCurrentRegion()
                                                .getTwitterUrl())) {
                                    twitterUrl = Application.get().getCurrentRegion()
                                            .getTwitterUrl();
                                }
                                UIHelp.goToUrl(HomeActivity.this, twitterUrl);
                                ObaAnalytics.reportEventWithCategory(
                                        ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                                        getString(R.string.analytics_action_switch),
                                        getString(R.string.analytics_label_app_switch));
                                break;
                            case 1:
                                AgenciesActivity.start(HomeActivity.this);
                                break;
                            case 2:
                                showDialog(WHATSNEW_DIALOG);
                                break;
                            case 3:
                                goToContactEmail(HomeActivity.this);
                                break;
                        }
                    }
                }
        );
        return builder.create();
    }

    @SuppressWarnings("deprecation")
    private Dialog createWhatsNewDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_whatsnew_title);
        builder.setIcon(R.mipmap.ic_launcher);
        builder.setMessage(mWhatsNewMessage);
        builder.setNeutralButton(R.string.main_help_close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dismissDialog(WHATSNEW_DIALOG);
                    }
                }
        );
        return builder.create();
    }

    private static final String WHATS_NEW_VER = "whatsNewVer";

    @SuppressWarnings("deprecation")
    private void autoShowWhatsNew() {
        SharedPreferences settings = Application.getPrefs();

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }

        final int oldVer = settings.getInt(WHATS_NEW_VER, 0);
        final int newVer = appInfo.versionCode;

        if (oldVer < newVer) {
            mWhatsNewMessage = R.string.main_help_whatsnew;
            showDialog(WHATSNEW_DIALOG);

            // Updates will remove the alarms. This should put them back.
            // (Unfortunately I can't find a way to reschedule them without
            // having the app run again).
            TripService.scheduleAll(this);
            PreferenceHelp.saveInt(WHATS_NEW_VER, appInfo.versionCode);
        }
    }

    /**
     * Called by the BaseMapFragment when a stop obtains focus, or no stops have focus
     *
     * @param stop   the ObaStop that obtained focus, or null if no stop is in focus
     * @param routes a HashMap of all route display names that serve this stop - key is routeId
     * @param location the user touch location on the map
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
            mFocusedStopId = stop.getId();
            // A stop on the map was just tapped, show it in the sliding panel
            updateArrivalListFragment(stop.getId(), stop, routes);

            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_button_press_map_icon));
        } else {
            // No stop is in focus (e.g., user tapped on the map), so hide the panel
            // and clear the currently focused stopId
            mFocusedStopId = null;
            moveMyLocationButtonDown();
            mSlidingPanel.hidePanel();
            if (mArrivalsListFragment != null) {
                FragmentManager fm = getSupportFragmentManager();
                fm.beginTransaction().remove(mArrivalsListFragment).commit();
            }
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
        //mSlidingPanel.setScrollableView(listView);
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
                mMapFragment.setMapCenter(mFocusedStop.getLocation(), true,
                        mSlidingPanel.isPanelAnchored());
            }

            // ...and we should add a focus marker for this stop
            if (mMapFragment != null) {
                mMapFragment.setFocusStop(mFocusedStop, response.getRoutes());
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Collapse the panel when the user presses the back button
        if (mSlidingPanel != null) {
            if (mSlidingPanel.isPanelExpanded() || mSlidingPanel.isPanelAnchored()) {
                mSlidingPanel.collapsePanel();
                return;
            }
        }
        super.onBackPressed();
    }

    private void updateArrivalListFragment(String stopId, ObaStop stop,
            HashMap<String, ObaRoute> routes) {
        FragmentManager fm = getSupportFragmentManager();
        Intent intent;

        mArrivalsListFragment = new ArrivalsListFragment();
        mArrivalsListFragment.setListener(this);

        // Set the header for the arrival list to be the top of the sliding panel
        mArrivalsListHeader = new ArrivalsListHeader(this, mArrivalsListFragment);
        mArrivalsListFragment.setHeader(mArrivalsListHeader, mArrivalsListHeaderView);
        mArrivalsListHeader.setSlidingPanelController(mSlidingPanelController);
        mArrivalsListHeader.setSlidingPanelCollapsed(isSlidingPanelCollapsed());

        if (stop != null && routes != null) {
            // Use ObaStop and ObaRoute objects, since we can pre-populate some of the fields
            // before getting an API response
            intent = new ArrivalsListFragment.IntentBuilder(this, stop, routes).build();
        } else {
            // All we have is a stopId (likely started from Intent or after rotating device)
            // Some fields will be blank until we get an API response
            intent = new ArrivalsListFragment.IntentBuilder(this, stopId).build();
        }

        mArrivalsListFragment.setArguments(FragmentUtils.getIntentArgs(intent));
        fm.beginTransaction().replace(R.id.slidingFragment, mArrivalsListFragment).commit();
        mSlidingPanel.showPanel();
        moveMyLocationButtonUp();
    }

    private String getLocationString(Context context) {
        Location loc = LocationUtil.getLocation2(context, mGoogleApiClient);
        return LocationUtil.printLocationDetails(loc);
    }

    private void goToContactEmail(Context ctxt) {
        PackageManager pm = ctxt.getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(ctxt.getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        ObaRegion region = Application.get().getCurrentRegion();
        if (region == null) {
            return;
        }

        // appInfo.versionName
        // Build.MODEL
        // Build.VERSION.RELEASE
        // Build.VERSION.SDK
        // %s\nModel: %s\nOS Version: %s\nSDK Version: %s\
        final String body = ctxt.getString(R.string.bug_report_body,
                appInfo.versionName,
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                getLocationString(ctxt));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_EMAIL,
                new String[]{region.getContactEmail()});
        send.putExtra(Intent.EXTRA_SUBJECT,
                ctxt.getString(R.string.bug_report_subject));
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.setType("message/rfc822");
        try {
            ctxt.startActivity(Intent.createChooser(send,
                    ctxt.getString(R.string.bug_report_subject)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctxt, R.string.bug_report_error, Toast.LENGTH_LONG)
                    .show();
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
            PreferenceHelp
                    .saveBoolean(getString(R.string.preference_key_auto_select_region), false);
            return;
        }

        boolean forceReload = false;
        boolean showProgressDialog = true;

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
            //We already have region info locally, so just check current region status quietly in the background
            showProgressDialog = false;
        }

        //Check region status, possibly forcing a reload from server and checking proximity to current region
        ObaRegionsTask task = new ObaRegionsTask(this, mMapFragment, forceReload,
                showProgressDialog);
        task.execute();
    }

    private void setupMyLocationButton() {
        // Initialize the My Location button
        mBtnMyLocation = (ImageButton) findViewById(R.id.btnMyLocation);
        View.OnClickListener oclMyLocation = new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (mMapFragment != null) {
                    mMapFragment.setMyLocation(true, true);
                    ObaAnalytics.reportEventWithCategory(
                            ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getString(R.string.analytics_action_button_press),
                            getString(R.string.analytics_label_button_press_location));
                }
            }
        };
        mBtnMyLocation.setOnClickListener(oclMyLocation);
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) mBtnMyLocation
                .getLayoutParams();
        MY_LOC_DEFAULT_BOTTOM_MARGIN = p.bottomMargin;
        if (mCurrentNavDrawerPosition == NAVDRAWER_ITEM_NEARBY) {
            showMyLocationButton();
        } else {
            hideMyLocationButton();
        }
    }

    private void moveMyLocationButtonUp() {
        if (mBtnMyLocation == null) {
            return;
        }

        final ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) mBtnMyLocation
                .getLayoutParams();

        if (p.bottomMargin != MY_LOC_DEFAULT_BOTTOM_MARGIN) {
            // Button was already moved, do nothing
            return;
        }

        // Move My Location button above the sliding panel header, so its still visible
        final int bottomMargin = MY_LOC_DEFAULT_BOTTOM_MARGIN + mSlidingPanel.getPanelHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                UIHelp.setMargins(mBtnMyLocation,
                        p.leftMargin,
                        p.topMargin,
                        p.rightMargin,
                        (int) (bottomMargin * interpolatedTime));
            }
        };
        a.setDuration(MY_LOC_BTN_ANIM_DURATION);
        mBtnMyLocation.startAnimation(a);
    }

    private void moveMyLocationButtonDown() {
        if (mBtnMyLocation == null) {
            return;
        }

        final ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) mBtnMyLocation
                .getLayoutParams();

        if (p.bottomMargin == MY_LOC_DEFAULT_BOTTOM_MARGIN) {
            // Button is already in default state, do nothing
            return;
        }

        // Move My Location button back to its default position
        final int bottomMargin = MY_LOC_DEFAULT_BOTTOM_MARGIN;

        // TODO - this doesn't seem to be animating?? Why not?  Or is it just my device...
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                UIHelp.setMargins(mBtnMyLocation,
                        p.leftMargin,
                        p.topMargin,
                        p.rightMargin,
                        (int) (bottomMargin * interpolatedTime));
            }
        };
        a.setDuration(MY_LOC_BTN_ANIM_DURATION);
        mBtnMyLocation.startAnimation(a);
    }

    private void showMyLocationButton() {
        if (mBtnMyLocation == null) {
            return;
        }
        if (mBtnMyLocation.getVisibility() != View.VISIBLE) {
            mBtnMyLocation.setVisibility(View.VISIBLE);
        }
    }

    private void hideMyLocationButton() {
        if (mBtnMyLocation == null) {
            return;
        }
        if (mBtnMyLocation.getVisibility() != View.GONE) {
            mBtnMyLocation.setVisibility(View.GONE);
        }
    }

    private void setupNavigationDrawer() {
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.nav_drawer_left_pane));
    }

    private void setupGooglePlayServices() {
        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this)
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtil.getGoogleApiClientWithCallbacks(this);
            mGoogleApiClient.connect();
        }
    }

    private void setupSlidingPanel(Bundle bundle) {
        mSlidingPanel = (SlidingUpPanelLayout) findViewById(R.id.bottom_sliding_layout);
        mArrivalsListHeaderView = findViewById(R.id.arrivals_list_header);

        mSlidingPanel.hidePanel();  // Don't show the panel until we have content
        mSlidingPanel.setOverlayed(true);
        mSlidingPanel.setAnchorPoint(MapModeController.OVERLAY_PERCENTAGE);
        mSlidingPanel.setPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {
                Log.d(TAG, "onPanelSlide, offset " + slideOffset);
            }

            @Override
            public void onPanelExpanded(View panel) {
                Log.d(TAG, "onPanelExpanded");
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.setSlidingPanelCollapsed(false);
                    mArrivalsListHeader.refresh();
                }
            }

            @Override
            public void onPanelCollapsed(View panel) {
                Log.d(TAG, "onPanelCollapsed");
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.setSlidingPanelCollapsed(true);
                    mArrivalsListHeader.refresh();
                }
            }

            @Override
            public void onPanelAnchored(View panel) {
                Log.d(TAG, "onPanelAnchored");
                if (mFocusedStop != null && mMapFragment != null) {
                    mMapFragment.setMapCenter(mFocusedStop.getLocation(), true, true);
                }
                if (mArrivalsListHeader != null) {
                    mArrivalsListHeader.setSlidingPanelCollapsed(false);
                    mArrivalsListHeader.refresh();
                }
                moveMyLocationButtonUp();
            }

            @Override
            public void onPanelHidden(View panel) {
                Log.d(TAG, "onPanelHidden");
                // We need to hide the panel when switching between fragments via the navdrawer,
                // so we shouldn't put anything here that causes us to lose the state of the
                // MapFragment or the ArrivalListFragment (e.g., removing the ArrivalListFragment)
            }
        });

        mSlidingPanelController = new SlidingPanelController() {
            @Override
            public void setPanelHeightPixels(int heightInPixels) {
                if (mSlidingPanel != null) {
                    mSlidingPanel.setPanelHeight(heightInPixels);
                }
            }
        };

        String stopId;
        if (bundle != null) {
            stopId = bundle.getString(STOP_ID);

            if (stopId != null) {
                mFocusedStopId = stopId;
                // We're recreating an instance with a previous state, so show the focused stop in panel
                // We don't have an ObaStop or ObaRoute mapping, so just pass in null for those
                updateArrivalListFragment(stopId, null, null);
            }
        }
    }

    private boolean isSlidingPanelCollapsed() {
        return !(mSlidingPanel.isPanelExpanded() || mSlidingPanel.isPanelAnchored());
    }

    public ArrivalsListFragment getArrivalsListFragment() {
        return mArrivalsListFragment;
    }
}
