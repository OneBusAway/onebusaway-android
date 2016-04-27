/*
* Copyright (C) 2014-2015 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaStopElement;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.report.connection.GeocoderTask;
import org.onebusaway.android.report.connection.ServiceListTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.adapter.EntrySpinnerAdapter;
import org.onebusaway.android.report.ui.adapter.SectionItem;
import org.onebusaway.android.report.ui.adapter.ServiceSpinnerItem;
import org.onebusaway.android.report.ui.adapter.SpinnerItem;
import org.onebusaway.android.report.ui.dialog.ReportSuccessDialog;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;
import org.onebusaway.android.report.ui.util.ServiceUtils;
import org.onebusaway.android.util.LocationUtils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.usf.cutr.open311client.Open311;
import edu.usf.cutr.open311client.Open311Manager;
import edu.usf.cutr.open311client.models.Service;
import edu.usf.cutr.open311client.models.ServiceListRequest;
import edu.usf.cutr.open311client.models.ServiceListResponse;

public class InfrastructureIssueActivity extends BaseReportActivity implements
        BaseMapFragment.OnFocusChangedListener, ServiceListTask.Callback,
        ReportProblemFragmentBase.OnProblemReportedListener, IssueLocationHelper.Callback,
        SimpleArrivalListFragment.Callback, GeocoderTask.Callback {

    private static final String SHOW_STOP_MARKER = ".showMarker";

    private static final String SELECTED_SERVICE = ".selectedService";

    private static final String SELECTED_SERVICE_TYPE = ".selectedServiceType";

    private static final String RESTORED_SERVICE = ".restoredService";

    private static final String SHOW_CATEGORIES = ".showCategories";

    private static final String SHOW_INFO = ".showInfo";

    private static final String ARRIVAL_LIST = ".arrivalList";

    private static final String TRIP_INFO = ".tripInfo";

    private static final String AGENCY_NAME = ".agencyName";

    private static final String ACTION_BAR_TITLE = ".actionBarTitle";

    /**
     * UI Elements
     */
    private EditText mAddressEditText;

    private Spinner mServicesSpinner;

    private RelativeLayout mBusStopHeader;

    //Map Fragment
    private BaseMapFragment mMapFragment;

    // Services spinner container
    private FrameLayout mServicesSpinnerFrameLayout;

    //Location helper for tracking the issue location
    private IssueLocationHelper mIssueLocationHelper;

    /**
     * Open311 client
     */
    private Open311 mOpen311;

    /**
     * Store the transit service type if there is no bus stop selected
     * Then, when a user selects a bus stop, find the transit service by type and show
     */
    private String mTransitServiceIssueTypeWithoutStop;

    /**
     * Selected transit service object
     */
    private Service mSelectedTransitService;

    /**
     * Select this issue type when the activity starts
     * If a user selects "report stop or trip problem" from the main activity, set the default
     * value to this variable
     */
    private String mDefaultIssueType;

    /**
     * Restore this issue on rotation
     */
    private String mRestoredServiceName;

    /**
     * Selected arrival information for trip problem reporting
     */
    private ObaArrivalInfo mArrivalInfo;

    /**
     * Agency name for trip problem reporting
     */
    private String mAgencyName;

    /**
     * For rotation changes:
     * Save instance state vars
     * Restores old selected categories and marker position
     */
    private boolean mShowCategories = false;

    private boolean mShowStopMarker = false;

    private boolean mShowArrivalListFragment = false;

    /**
     * Starts the InfrastructureIssueActivity.
     *
     * @param activity The context of the activity.
     * @param intent   The Intent containing focusId, lat, lon of the map
     */
    public static void start(Activity activity, Intent intent) {
        activity.startActivityForResult(makeIntent(activity, intent), 0);
    }

    /**
     * Starts the InfrastructureIssueActivity with a given open311 service category selected
     *
     * @param activity The context of the activity.
     * @param intent   The Intent containing focusId, lat, lon of the map
     */
    public static void startWithService(Activity activity, Intent intent, String serviceKeyword) {
        intent = makeIntent(activity, intent);
        intent.putExtra(SELECTED_SERVICE, serviceKeyword);
        activity.startActivityForResult(intent, 0);
    }

    /**
     * Starts the InfrastructureIssueActivity with a given open311 service category selected
     *
     * @param activity       The context of the activity.
     * @param intent         The Intent containing focusId, lat, lon of the map
     * @param obaArrivalInfo Arrival info for trip problems
     */
    public static void startWithService(Activity activity, Intent intent, String serviceKeyword
            , ObaArrivalInfo obaArrivalInfo, String agencyName) {
        intent = makeIntent(activity, intent);
        // Put trip issue specific data
        intent.putExtra(SELECTED_SERVICE, serviceKeyword);
        intent.putExtra(TRIP_INFO, obaArrivalInfo);
        intent.putExtra(AGENCY_NAME, agencyName);

        activity.startActivityForResult(intent, 0);
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param intent  The Intent containing focusId, lat, lon of the map
     */
    public static Intent makeIntent(Context context, Intent intent) {
        Intent myIntent = new Intent(context, InfrastructureIssueActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, intent.getStringExtra(MapParams.STOP_ID));
        myIntent.putExtra(MapParams.STOP_NAME, intent.getStringExtra(MapParams.STOP_NAME));
        myIntent.putExtra(MapParams.STOP_CODE, intent.getStringExtra(MapParams.STOP_CODE));
        myIntent.putExtra(MapParams.CENTER_LAT, intent.getDoubleExtra(MapParams.CENTER_LAT, 0));
        myIntent.putExtra(MapParams.CENTER_LON, intent.getDoubleExtra(MapParams.CENTER_LON, 0));
        return myIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.infrastructure_issue);

        setUpOpen311();

        setUpProgressBar();

        setupMapFragment(savedInstanceState);

        setupLocationHelper(savedInstanceState);

        setupViews();

        setupIntentData(savedInstanceState);

        setupIconColors();

        initLocation();

        setActionBarTitle(savedInstanceState);
    }

    /**
     * Set the intent parameters when the activity starts
     * @param savedInstanceState
     */
    private void setupIntentData(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            double lat = getIntent().getDoubleExtra(MapParams.CENTER_LAT, 0);
            double lon = getIntent().getDoubleExtra(MapParams.CENTER_LON, 0);
            // If a bus stop selected in the main screen
            // Set the focus to the current stop
            String stopId = getIntent().getStringExtra(MapParams.STOP_ID);
            String stopName = getIntent().getStringExtra(MapParams.STOP_NAME);
            String stopCode = getIntent().getStringExtra(MapParams.STOP_CODE);
            if (stopId != null) {
                mIssueLocationHelper.setObaStop(new ObaStopElement(stopId, lat, lon, stopName, stopCode));
                showBusStopHeader(stopName);
                mShowStopMarker = true;
            }
            mArrivalInfo = (ObaArrivalInfo) getIntent().getSerializableExtra(TRIP_INFO);
            mAgencyName = getIntent().getStringExtra(AGENCY_NAME);
            mDefaultIssueType = getIntent().getStringExtra(SELECTED_SERVICE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObaAnalytics.reportActivityStart(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloat(MapParams.ZOOM, mMapFragment.getMapView().getZoomLevelAsFloat());
        ObaStop obaStop = mIssueLocationHelper.getObaStop();
        if (obaStop != null) {
            String stopId = obaStop.getId();
            getIntent().putExtra(MapParams.STOP_ID, stopId);
            outState.putString(MapParams.STOP_ID, stopId);
            outState.putString(MapParams.STOP_NAME, obaStop.getName());
            outState.putString(MapParams.STOP_CODE, obaStop.getStopCode());
            outState.putBoolean(SHOW_STOP_MARKER, true);
        }

        outState.putDouble(MapParams.CENTER_LAT, mIssueLocationHelper.getIssueLocation().getLatitude());
        outState.putDouble(MapParams.CENTER_LON, mIssueLocationHelper.getIssueLocation().getLongitude());

        SpinnerItem spinnerItem = (SpinnerItem) mServicesSpinner.getSelectedItem();
        if (spinnerItem != null && (!spinnerItem.isHint() && !spinnerItem.isSection() ||
                (spinnerItem.isHint() && mServicesSpinnerFrameLayout.getVisibility() == View.VISIBLE))) {
            Service service = ((ServiceSpinnerItem) spinnerItem).getService();
            outState.putString(RESTORED_SERVICE, service.getService_name());
            outState.putBoolean(SHOW_CATEGORIES, true);
        }

        if (isInfoVisiable()) {
            String infoText = ((TextView) mInfoHeader.findViewById(R.id.ri_info_text)).getText().
                    toString();
            outState.putString(SHOW_INFO, infoText);
        }

        outState.putBoolean(ARRIVAL_LIST, mShowArrivalListFragment);
        outState.putString(AGENCY_NAME, mAgencyName);
        outState.putString(SELECTED_SERVICE_TYPE, mTransitServiceIssueTypeWithoutStop);
        outState.putString(ACTION_BAR_TITLE, getTitle().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            mShowCategories = savedInstanceState.getBoolean(SHOW_CATEGORIES, false);
            mShowStopMarker = savedInstanceState.getBoolean(SHOW_STOP_MARKER, false);
            mShowArrivalListFragment = savedInstanceState.getBoolean(ARRIVAL_LIST, false);
            mRestoredServiceName = savedInstanceState.getString(RESTORED_SERVICE);
            mAgencyName = savedInstanceState.getString(AGENCY_NAME);
            mTransitServiceIssueTypeWithoutStop = savedInstanceState.getString(SELECTED_SERVICE_TYPE);

            String bundleStopId = savedInstanceState.getString(MapParams.STOP_ID);
            String stopName = savedInstanceState.getString(MapParams.STOP_NAME);
            String stopCode = savedInstanceState.getString(MapParams.STOP_CODE);

            if (bundleStopId != null) {
                double lat = savedInstanceState.getDouble(MapParams.CENTER_LAT, 0);
                double lon = savedInstanceState.getDouble(MapParams.CENTER_LON, 0);
                Location location = LocationUtils.makeLocation(lat, lon);
                mIssueLocationHelper.updateMarkerPosition(location, new ObaStopElement(bundleStopId,
                        lat, lon, stopName, stopCode));
            }
            String infoText = savedInstanceState.getString(SHOW_INFO);
            if (infoText != null) {
                addInfoText(infoText);
            }

            if (mShowArrivalListFragment) {
                removeTripProblemFragment();
                showArrivalListFragment(mIssueLocationHelper.getObaStop());
            }
        }
    }

    /**
     * Set default open311 client
     */
    private void setUpOpen311() {
        mOpen311 = Open311Manager.getDefaultOpen311();
    }

    /**
     * Setting up the BaseMapFragment
     * BaseMapFragment was used to implement a map.
     */
    private void setupMapFragment(Bundle bundle) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(BaseMapFragment.TAG);
        if (fragment != null) {
            mMapFragment = (BaseMapFragment) fragment;
            mMapFragment.setOnFocusChangeListener(this);
        }
        if (mMapFragment == null) {

            mMapFragment = new BaseMapFragment();
            mMapFragment.setArguments(bundle);
            // Register listener for map focus callbacks
            mMapFragment.setOnFocusChangeListener(this);

            fm.beginTransaction().add(R.id.ri_frame_map_view, mMapFragment,
                    BaseMapFragment.TAG).commit();
        }
        fm.beginTransaction().show(mMapFragment).commit();
    }

    /**
     * Setting up the location helper
     * IssueLocationHelper helps tracking the issue location and issue stop
     */
    private void setupLocationHelper(Bundle savedInstanceState) {

        double lat;
        double lon;
        if (savedInstanceState == null) {
            lat = getIntent().getDoubleExtra(MapParams.CENTER_LAT, 0);
            lon = getIntent().getDoubleExtra(MapParams.CENTER_LON, 0);
        } else {
            lat = savedInstanceState.getDouble(MapParams.CENTER_LAT, 0);
            lon = savedInstanceState.getDouble(MapParams.CENTER_LON, 0);
        }

        Location mapCenterLocation = LocationUtils.makeLocation(lat, lon);
        mIssueLocationHelper = new IssueLocationHelper(mapCenterLocation, this);

        // Set map center location
        mMapFragment.setMapCenter(mapCenterLocation, true, false);
    }

    /**
     * Initialize UI components
     */
    private void setupViews() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mServicesSpinnerFrameLayout = (FrameLayout) findViewById(R.id.ri_spinnerView);

        mAddressEditText = (EditText) findViewById(R.id.ri_address_editText);
        mAddressEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchAddress();
                    return true;
                }
                return false;
            }
        });

        CustomScrollView mainScrollView = (CustomScrollView) findViewById(R.id.ri_scrollView);
        mainScrollView.addInterceptScrollView(findViewById(R.id.ri_frame_map_view));

        mServicesSpinner = (Spinner) findViewById(R.id.ri_spinnerServices);
        mServicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView,
                                       int position, long id) {

                if (mShowCategories) {
                    mShowCategories = false;
                    return;
                }

                SpinnerItem spinnerItem = (SpinnerItem) mServicesSpinner.getSelectedItem();
                if (!spinnerItem.isHint() && !spinnerItem.isSection()) {

                    Service service = ((ServiceSpinnerItem) spinnerItem).getService();
                    onSpinnerItemSelected(service);
                } else if (spinnerItem.isHint()) {
                    clearReportingFragments(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mBusStopHeader = (RelativeLayout) findViewById(R.id.bus_stop_header);
    }

    private void setActionBarTitle(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            setActionBarTitle(mDefaultIssueType);
        } else {
            setTitle(savedInstanceState.getString(ACTION_BAR_TITLE));
        }
    }

    /**
     * Set action bar title by issue type
     *
     * @param issueType could be stop, trip, dynamic_stop or dynamic_trip
     */
    private void setActionBarTitle(String issueType) {
        if (ServiceUtils.isTransitStopServiceByType(issueType)) {
            setTitle(getString(R.string.rt_stop_problem_title));
        } else if (ServiceUtils.isTransitTripServiceByType(issueType)) {
            setTitle(getString(R.string.rt_arrival_problem_title));
        } else {
            setTitle(getString(R.string.rt_infrastructure_problem_title));
        }
    }

    private void setupIconColors() {
        ((ImageView) findViewById(R.id.ri_ic_location)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_ic_category)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_header_location)).setColorFilter(
                getResources().getColor(android.R.color.white));
    }

    /**
     * Show the current location on the map
     */
    private void initLocation() {
        syncAddress(mIssueLocationHelper.getIssueLocation());
    }

    private void onSpinnerItemSelected(Service service) {
        // Set static issue type to null
        mTransitServiceIssueTypeWithoutStop = null;
        mSelectedTransitService = null;

        if (service.getService_code() != null &&
                !(service.getType() != null && service.getType().contains(ReportConstants.DYNAMIC_SERVICE))) {
            showOpen311Reporting(service);
        } else if (!ReportConstants.DEFAULT_SERVICE.equalsIgnoreCase(service.getType())) {
            mSelectedTransitService = service;
            //Remove the info text for select category
            removeInfoText();
            showTransitService(service.getType());
        }

        // Set action bar title based on the selected issue
        setActionBarTitle(service.getType());
    }

    /**
     * Places a marker into the map from given Location
     *
     * @param location position for marker
     */
    private void updateMarkerPosition(Location location) {
        int markerId = mMapFragment.addMarker(location);
        mIssueLocationHelper.handleMarkerUpdate(markerId);
    }

    /**
     * Called when the issue location changes
     * Retrieves Open311 services from current address
     *
     * @param location current issue location
     */
    private void syncAddress(Location location) {
        syncAddressString(location);

        showProgress(Boolean.TRUE);

        ServiceListRequest slr = new ServiceListRequest(location.getLatitude(), location.getLongitude());

        List<Open311> open311List = Open311Manager.getAllOpen311();
        ServiceListTask serviceListTask = new ServiceListTask(slr, open311List, this);
        serviceListTask.execute();
    }

    /**
     * Search address string from mAddressEditText
     */
    private void searchAddress() {
        showProgress(Boolean.TRUE);
        String addressString = mAddressEditText.getText().toString();
        Location location = getLocationByAddress(addressString);
        if (location != null) {
            mMapFragment.setMapCenter(location, true, true);
            syncAddress(location);
        } else {
            String message = getResources().getString(R.string.ri_address_not_found);
            createToastMessage(message);
        }
        showProgress(Boolean.FALSE);
    }

    /**
     * Calculates the address of a given location
     *
     * @param location takes location object
     */
    private void syncAddressString(Location location) {
        GeocoderTask gct = new GeocoderTask(this, location, this);
        gct.execute();
    }

    /**
     * Converts plain address string to Location object
     *
     * @param addressString takes address string
     * @return Location of given address String
     */
    public Location getLocationByAddress(String addressString) {
        Geocoder coder = new Geocoder(this);
        List<Address> addressList;
        Location location;

        try {
            addressList = coder.getFromLocationName(addressString, 3);
            if (addressList == null || addressList.size() == 0) {
                return null;
            }
            Address address = addressList.get(0);

            location = new Location("");
            location.setLatitude(address.getLatitude());
            location.setLongitude(address.getLongitude());

            return location;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onClearMarker(int markerId) {
        mMapFragment.removeMarker(markerId);
    }

    /**
     * Called by the BaseMapFragment when a stop obtains focus, or no stops have focus
     *
     * @param stop   the ObaStop that obtained focus, or null if no stop is in focus
     * @param routes a HashMap of all route display names that serve this stop - key is routeId
     */
    @Override
    public void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location) {
        mIssueLocationHelper.updateMarkerPosition(location, stop);

        // Don't call syncAddress if you are restoring the instance
        if (mShowStopMarker) {
            if (stop != null) {
                // Show bus stop name on the header
                showBusStopHeader(stop.getName());
            }
            mShowStopMarker = false;
            return;
        }

        // Clear all reporting fragments
        clearReportingFragments();

        if (stop != null) {
            // Clear manually added markers
            mIssueLocationHelper.clearMarkers();

            // Show bus stop name on the header
            showBusStopHeader(stop.getName());

            showServicesSpinner();

            if (mTransitServiceIssueTypeWithoutStop != null) {
                showTransitService(mTransitServiceIssueTypeWithoutStop);
                syncAddressString(location);
            } else {
                syncAddress(stop.getLocation());
            }
            // Clear static issue type
            mTransitServiceIssueTypeWithoutStop = null;
        } else if (location != null) {
            hideBusStopHeader();

            syncAddress(location);
        }

        // Set action bar title based on the selected issue
        setActionBarTitle("");
    }

    @Override
    public void onSendReport() {
        (new ReportSuccessDialog()).show(getSupportFragmentManager(), ReportSuccessDialog.TAG);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finishActivityWithResult();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Called by the ServicesTask when Open311 endpoint returns ServiceList
     *
     * @param services ServiceListResponse
     * @param open311  returns active open311
     */
    @Override
    public void onServicesTaskCompleted(ServiceListResponse services, Open311 open311) {
        // Close progress
        showProgress(Boolean.FALSE);

        // Set main open311
        this.mOpen311 = open311;

        prepareServiceList(services);
    }

    /**
     * Called when geocoder converts the location to an address string
     *
     * @param address the address string from given location
     */
    @Override
    public void onGeocoderTaskCompleted(String address) {
        mAddressEditText.setText(address);
    }

    /**
     * Prepares the service lists and shows as categories in the screen
     * Adds static service categories (stop and trip problems) if they are
     * not specified by open311 endpoint
     */
    private void prepareServiceList(ServiceListResponse services) {
        // Create the service list
        List<Service> serviceList = new ArrayList<>();

        // Add services to list if service response is successful
        if (services != null && services.isSuccess() &&
                Open311Manager.isAreaManagedByOpen311(services.getServiceList())) {
            for (Service s : services.getServiceList()) {
                if (s.getService_name() != null && s.getService_code() != null) {
                    serviceList.add(s);
                }
            }
        }

        if (mShowStopMarker) {
            mMapFragment.setFocusStop(mIssueLocationHelper.getObaStop(), new ArrayList<ObaRoute>());
            showServicesSpinner();
        } else if (Open311Manager.isAreaManagedByOpen311(serviceList)) {
            // Set marker on the map if there are open311 services
            updateMarkerPosition(mIssueLocationHelper.getIssueLocation());
            showServicesSpinner();
        } else {
            // If there is no open311 services return and there is no stop selected
            // then hide the categories
            if (mIssueLocationHelper.getObaStop() == null) {
                hideServicesSpinner();
                // Show information to the user if there is no error on location
                addInfoText(getString(R.string.report_dialog_out_of_region_message));
            }
        }

        // Set transit services
        createTransitServices(serviceList);

        /**
         * Map the group names with service list
         */
        Map<String, List<Service>> serviceListMap = new TreeMap<>();

        for (Service s : serviceList) {
            String groupName = s.getGroup() == null ? "Others" : s.getGroup();
            List<Service> mappedList = serviceListMap.get(groupName);
            if (mappedList != null) {
                mappedList.add(s);
            } else {
                mappedList = new ArrayList<>();
                mappedList.add(s);
                serviceListMap.put(groupName, mappedList);
            }
        }

        /**
         * Create Ordered Spinner items
         */
        ArrayList<SpinnerItem> spinnerItems = new ArrayList<>();
        ServiceSpinnerItem hintServiceSpinnerItem = new ServiceSpinnerItem(
                new Service(getString(R.string.ri_service_default),
                        ReportConstants.DEFAULT_SERVICE));
        hintServiceSpinnerItem.setHint(true);
        spinnerItems.add(hintServiceSpinnerItem);

        if (serviceListMap.get(ReportConstants.ISSUE_GROUP_TRANSIT) != null) {
            // Create Transit categories first
            spinnerItems.add(new SectionItem(ReportConstants.ISSUE_GROUP_TRANSIT));

            for (Service s : serviceListMap.get(ReportConstants.ISSUE_GROUP_TRANSIT)) {
                spinnerItems.add(new ServiceSpinnerItem(s));
            }
        }

        // Create the rest of the categories
        for (String key : serviceListMap.keySet()) {
            // Skip if it is transit category
            if (ReportConstants.ISSUE_GROUP_TRANSIT.equals(key)) {
                continue;
            }
            spinnerItems.add(new SectionItem(key));
            for (Service s : serviceListMap.get(key)) {
                spinnerItems.add(new ServiceSpinnerItem(s));
            }
        }

        EntrySpinnerAdapter adapter = new EntrySpinnerAdapter(this, spinnerItems);
        mServicesSpinner.setAdapter(adapter);

        if (mDefaultIssueType != null) {
            // Select an default issue category programmatically
            selectDefaultTransitCategory(spinnerItems);
        } else if (mRestoredServiceName != null) {
            restoreSelection(spinnerItems);
        }
    }

    /**
     * Programmatically select a issue category on rotation
     *
     * @param spinnerItems the issue services loaded into the services spinner
     */
    private void restoreSelection(ArrayList<SpinnerItem> spinnerItems) {
        for (int i = 0; i < spinnerItems.size(); i++) {
            SpinnerItem item = spinnerItems.get(i);
            if (item instanceof ServiceSpinnerItem) {
                Service service = ((ServiceSpinnerItem) item).getService();
                if (service.getService_name().equals(mRestoredServiceName)) {
                    mServicesSpinner.setSelection(i, true);
                    mRestoredServiceName = null;
                    if (ServiceUtils.isTransitServiceByType(service.getType())) {
                        mSelectedTransitService = service;
                    }
                    break;
                }
            }
        }
    }

    /**
     * Programmatically select a default issue category from the spinner
     *
     * @param spinnerItems the issue services loaded into the services spinner
     */
    private void selectDefaultTransitCategory(ArrayList<SpinnerItem> spinnerItems) {
        int selectPosition = -1;
        for (int i = 0; i < spinnerItems.size(); i++) {
            SpinnerItem item = spinnerItems.get(i);
            if (item instanceof ServiceSpinnerItem) {

                Service service = ((ServiceSpinnerItem) item).getService();

                boolean transitServiceFound = false;

                if (getString(R.string.ri_selected_service_stop).equals(mDefaultIssueType) &&
                        ServiceUtils.isTransitStopServiceByType(service.getType())) {
                    transitServiceFound = true;
                } else if (getString(R.string.ri_selected_service_trip).equals(mDefaultIssueType) &&
                        ServiceUtils.isTransitTripServiceByType(service.getType())) {
                    transitServiceFound = true;
                }

                if (transitServiceFound) {
                    selectPosition = i;
                    // If transit service selected and no bus stop selected remove markers
                    if (ServiceUtils.isTransitServiceByType(service.getType()) &&
                            mIssueLocationHelper.getObaStop() == null) {
                        mIssueLocationHelper.clearMarkers();
                    }
                    break;
                }
            }
        }

        // Set mDefaultIssueType = null to prevent auto selections
        mDefaultIssueType = null;

        // Set selected category if it is in the spinner items list
        if (selectPosition != -1) {
            showServicesSpinner();
            mServicesSpinner.setSelection(selectPosition, true);
        }
    }

    private void hideServicesSpinner() {
        mServicesSpinnerFrameLayout.setVisibility(View.GONE);
    }

    private void showServicesSpinner() {
        mServicesSpinnerFrameLayout.setVisibility(View.VISIBLE);
    }

    private void createTransitServices(List<Service> serviceList) {

        boolean stopProblemFound = false;
        boolean tripProblemFound = false;

        // Search transit services by keywords
        for (Service s : serviceList) {
            if (ServiceUtils.isTransitStopServiceByKey(s.getKeywords()) && !stopProblemFound) {
                s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                stopProblemFound = true;
            } else if (ServiceUtils.isTransitTripServiceByKey(s.getKeywords()) && !tripProblemFound) {
                s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP);
                tripProblemFound = true;
            }
        }

        // Search transit services by groups
        if (!stopProblemFound || !tripProblemFound) {
            for (Service s : serviceList) {
                if (ServiceUtils.isTransitStopServiceByKey(s.getGroup()) && !stopProblemFound) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                    stopProblemFound = true;
                } else if (ServiceUtils.isTransitTripServiceByKey(s.getGroup()) && !tripProblemFound) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP);
                    tripProblemFound = true;
                }
            }
        }

        // Search transit services by name
        if (!stopProblemFound || !tripProblemFound) {
            for (Service s : serviceList) {
                if (ServiceUtils.isTransitStopServiceByKey(s.getService_name()) && !stopProblemFound) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP);
                    stopProblemFound = true;
                } else if (ServiceUtils.isTransitTripServiceByKey(s.getService_name()) && !tripProblemFound) {
                    s.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
                    s.setType(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP);
                    tripProblemFound = true;
                }
            }
        }

        if (!stopProblemFound) {
            Service s1 = new Service(getString(R.string.ri_service_stop),
                    ReportConstants.STATIC_TRANSIT_SERVICE_STOP);
            s1.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
            serviceList.add(s1);
        }

        if (!tripProblemFound) {
            Service s2 = new Service(getString(R.string.ri_service_trip),
                    ReportConstants.STATIC_TRANSIT_SERVICE_TRIP);
            s2.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
            serviceList.add(s2);
        }
    }

    /**
     * Disables open311 reporting if there is no category for region
     * Show static services
     */
    public void showTransitService(String issueType) {

        clearReportingFragments();

        ObaStop obaStop = mIssueLocationHelper.getObaStop();
        if (obaStop != null) {
            if (ReportConstants.STATIC_TRANSIT_SERVICE_STOP.equals(issueType)) {
                showStopProblemFragment(obaStop);
            } else if (ReportConstants.STATIC_TRANSIT_SERVICE_TRIP.equals(issueType)) {
                showTripProblemFragment(obaStop);
            } else if (ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP.equals(issueType)) {
                showOpen311Reporting(mSelectedTransitService);
            } else if (ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP.equals(issueType)) {
                if (mArrivalInfo != null) {
                    showOpen311Reporting(mSelectedTransitService, mArrivalInfo);
                    mArrivalInfo = null;
                } else {
                    showTripProblemFragment(obaStop);
                }
            }
        } else {
            addInfoText(getString(R.string.report_dialog_out_of_region_message));
            mTransitServiceIssueTypeWithoutStop = issueType;

            mIssueLocationHelper.clearMarkers();
        }
    }

    private void showOpen311Reporting(Service service) {

        clearReportingFragments();

        showOpen311ProblemFragment(service);

        updateMarkerPosition(mIssueLocationHelper.getIssueLocation());
    }

    private void showOpen311Reporting(Service service, ObaArrivalInfo obaArrivalInfo) {

        clearReportingFragments();

        showOpen311ProblemFragment(service, obaArrivalInfo);

        updateMarkerPosition(mIssueLocationHelper.getIssueLocation());
    }

    private void clearReportingFragments() {
        clearReportingFragments(true);
    }

    private void clearReportingFragments(boolean removeInfo) {
        if (removeInfo) {
            removeInfoText();
        }
        removeOpen311ProblemFragment();

        removeStopProblemFragment();

        removeTripProblemFragment();
    }

    private void showStopProblemFragment(ObaStop obaStop) {
        ReportStopProblemFragment.show(this, obaStop, R.id.ri_report_stop_problem, false);
    }

    private void showTripProblemFragment(ObaStop obaStop) {
        if (mArrivalInfo == null) {
            showArrivalListFragment(obaStop);
        } else {
            // Show default trip problem issue reporting
            ReportTripProblemFragment.show(this, mArrivalInfo, R.id.ri_report_stop_problem, false);
            mArrivalInfo = null;
        }
    }

    private void showArrivalListFragment(ObaStop obaStop) {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View v = layoutInflater.inflate(R.layout.arrivals_list_header, null);
        v.setVisibility(View.GONE);

        mShowArrivalListFragment = true;

        SimpleArrivalListFragment.show(this, R.id.ri_report_stop_problem, obaStop, this);
    }

    @Override
    public void onArrivalItemClicked(ObaArrivalInfo obaArrivalInfo, String agencyName) {
        mShowArrivalListFragment = false;

        mAgencyName = agencyName;

        removeFragmentByTag(SimpleArrivalListFragment.TAG);

        addTripName(obaArrivalInfo.getHeadsign());

        if (mSelectedTransitService != null &&
                ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP.equals(mSelectedTransitService.getType())) {
            // Show open311 defined issue reporting service
            showOpen311ProblemFragment(mSelectedTransitService, obaArrivalInfo);
        } else {
            // Show default trip problem issue reporting
            ReportTripProblemFragment.show(this, obaArrivalInfo, R.id.ri_report_stop_problem, false);
        }
    }

    private void showBusStopHeader(String text) {
        // First remove info text
        removeInfoText();

        ((TextView) findViewById(R.id.ri_bus_stop_text)).setText(text);
        if (mBusStopHeader.getVisibility() != View.VISIBLE) {
            mBusStopHeader.setVisibility(View.VISIBLE);
        }
    }

    public void hideBusStopHeader() {
        findViewById(R.id.bus_stop_header).setVisibility(View.GONE);
    }

    private void showOpen311ProblemFragment(Service service) {
        Open311ProblemFragment.show(this, R.id.ri_report_stop_problem, mOpen311, service);
    }

    private void showOpen311ProblemFragment(Service service, ObaArrivalInfo obaArrivalInfo) {
        Open311ProblemFragment.show(this, R.id.ri_report_stop_problem, mOpen311, service,
                obaArrivalInfo, mAgencyName);
    }

    public void removeOpen311ProblemFragment() {
        removeFragmentByTag(Open311ProblemFragment.TAG);
    }

    private void removeStopProblemFragment() {
        removeFragmentByTag(ReportStopProblemFragment.TAG);
    }

    private void removeTripProblemFragment() {
        mShowArrivalListFragment = false;

        removeFragmentByTag(ReportTripProblemFragment.TAG);

        removeFragmentByTag(SimpleArrivalListFragment.TAG);

        ((LinearLayout) findViewById(R.id.ri_report_stop_problem)).removeAllViews();
    }

    private void addTripName(String text) {
        LayoutInflater inflater = LayoutInflater.from(this);
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_description_item, null, false);

        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_report_stop_problem);
        TextView tv = ((TextView) layout.findViewById(R.id.riii_textView));
        tv.setText(text);
        tv.setTypeface(null, Typeface.NORMAL);

        linear.addView(layout);

        ((ImageView) layout.findViewById(R.id.ic_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    public IssueLocationHelper getIssueLocationHelper() {
        return mIssueLocationHelper;
    }

    public String getCurrentAddress() {
        return mAddressEditText.getText().toString();
    }

    public void finishActivityWithResult() {
        Intent returnIntent = new Intent();
        returnIntent.putExtra(BaseReportActivity.CLOSE_REQUEST, true);
        setResult(RESULT_OK, returnIntent);
        finish();
    }
}
