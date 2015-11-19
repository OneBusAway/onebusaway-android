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
import org.onebusaway.android.report.connection.ServiceListTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.adapter.EntrySpinnerAdapter;
import org.onebusaway.android.report.ui.adapter.SectionItem;
import org.onebusaway.android.report.ui.adapter.ServiceSpinnerItem;
import org.onebusaway.android.report.ui.adapter.SpinnerItem;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;
import org.onebusaway.android.util.LocationUtil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        SimpleArrivalListFragment.Callback {

    private static final String SELECTED_SERVICE = "selectedService";

    private static final String SHOW_CATEGORIES = ".showCategories";

    private static final String SHOW_INFO = ".showInfo";

    private static final String SHOW_STOP_MARKER = ".showMarker";

    private static final String STOP_NAME = ".stopName";
    /**
     * UI Elements
     */
    private EditText mAddressEditText;

    private Spinner mServicesSpinner;

    private RelativeLayout mBusStopHeader;

    //Map Fragment
    private BaseMapFragment mMapFragment;

    //Location helper for tracking the issue location
    private IssueLocationHelper issueLocationHelper;

    /**
     * Open311 client
     */
    private Open311 mOpen311;

    /**
     * Selected static issue type
     */
    private String mStaticIssuetype;

    /**
     * Select this issue type when the activity starts
     */
    private String mDefaultIssueType;

    /**
     * Save instance state vars
     * Restores old selected categories and marker position
     */
    private boolean mBundleShowCategories = false;

    private boolean mBundleShowStopMarker = false;

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
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param intent  The Intent containing focusId, lat, lon of the map
     */
    public static Intent makeIntent(Context context, Intent intent) {
        Intent myIntent = new Intent(context, InfrastructureIssueActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, intent.getStringExtra(MapParams.STOP_ID));
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

        setupLocationHelper();

        setupViews();

        setupIconColors();

        initLocation();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObaAnalytics.reportActivityStart(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ObaStop obaStop = issueLocationHelper.getObaStop();
        if (obaStop != null) {
            String stopId = obaStop.getId();
            getIntent().putExtra(MapParams.STOP_ID, stopId);
            outState.putString(MapParams.STOP_ID, stopId);
            outState.putBoolean(SHOW_STOP_MARKER, true);
            outState.putString(STOP_NAME, obaStop.getName());
        }
        getIntent().putExtra(MapParams.CENTER_LAT, issueLocationHelper.getIssueLocation().getLatitude());
        getIntent().putExtra(MapParams.CENTER_LON, issueLocationHelper.getIssueLocation().getLongitude());

        SpinnerItem spinnerItem = (SpinnerItem) mServicesSpinner.getSelectedItem();
        if (!spinnerItem.isHint() && !spinnerItem.isSection()) {
            Service service = ((ServiceSpinnerItem) spinnerItem).getService();
            getIntent().putExtra(SELECTED_SERVICE, service.getService_name());
            outState.putBoolean(SHOW_CATEGORIES, true);
        }

        if (isInfoVisiable()) {
            String infoText = ((TextView) mInfoHeader.findViewById(R.id.ri_info_text)).getText().
                    toString();
            outState.putString(SHOW_INFO, infoText);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            mBundleShowCategories = savedInstanceState.getBoolean(SHOW_CATEGORIES, false);
            mBundleShowStopMarker = savedInstanceState.getBoolean(SHOW_STOP_MARKER, false);
            String bundleStopId = savedInstanceState.getString(MapParams.STOP_ID);
            String stopName = savedInstanceState.getString(STOP_NAME);

            if (bundleStopId != null) {
                double lat = getIntent().getDoubleExtra(MapParams.CENTER_LAT, 0);
                double lon = getIntent().getDoubleExtra(MapParams.CENTER_LON, 0);
                Location location = LocationUtil.makeLocation(lat, lon);
                issueLocationHelper.updateMarkerPosition(location, new ObaStopElement(bundleStopId,
                        lat, lon, stopName));
            }
            String infoText = savedInstanceState.getString(SHOW_INFO);
            if (infoText != null) {
                addInfoText(infoText);
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
        if (mMapFragment == null) {
            mMapFragment = new BaseMapFragment();
            mMapFragment.setArguments(bundle);
            // Register listener for map focus callbacks
            mMapFragment.setOnFocusChangeListener(this);
            mMapFragment.setZoom(15);
            fm.beginTransaction().add(R.id.ri_frame_map_view, mMapFragment).commit();
        }
        fm.beginTransaction().show(mMapFragment).commit();
    }

    /**
     * Setting up the location helper
     * IssueLocationHelper helps tracking the issue location and issue stop
     */
    private void setupLocationHelper() {
        double lat = getIntent().getDoubleExtra(MapParams.CENTER_LAT, 0);
        double lon = getIntent().getDoubleExtra(MapParams.CENTER_LON, 0);

        Location mapCenterLocation = LocationUtil.makeLocation(lat, lon);
        issueLocationHelper = new IssueLocationHelper(mapCenterLocation, this);

        // Set map center location
        mMapFragment.setMapCenter(mapCenterLocation, true, false);
    }

    /**
     * Initialize UI components
     */
    private void setupViews() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle(getString(R.string.rt_infrastructure_problem_header));

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
                SpinnerItem spinnerItem = (SpinnerItem) mServicesSpinner.getSelectedItem();
                if (!spinnerItem.isHint() && !spinnerItem.isSection()) {
                    if (!mBundleShowCategories) {
                        Service service = ((ServiceSpinnerItem) spinnerItem).getService();
                        onSpinnerItemSelected(service);
                    } else {
                        // Don't update the ui if the orientation change
                        mBundleShowCategories = false;
                    }
                } else if (spinnerItem.isHint()) {
                    clearReportingFragments(false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mDefaultIssueType = getIntent().getStringExtra(SELECTED_SERVICE);

        mBusStopHeader = (RelativeLayout) findViewById(R.id.bus_stop_header);
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
        syncAddress(issueLocationHelper.getIssueLocation());
    }

    private void onSpinnerItemSelected(Service service) {
        // Set static issue type to null
        mStaticIssuetype = null;

        if (service.getService_code() != null) {
            showOpen311Reporting(service);
        } else if (!ReportConstants.DEFAULT_SERVICE.equalsIgnoreCase(service.getType())) {
            //Remove the info text for select category
            removeInfoText();

            showStaticIssueReporting(service.getType());
        }
    }

    /**
     * Places a marker into the map from given Location
     *
     * @param location position for marker
     */
    private void updateMarkerPosition(Location location) {
        int markerId = mMapFragment.addMarker(location);
        issueLocationHelper.handleMarkerUpdate(markerId);
    }

    /**
     * Called when the issue location changes
     * Retrieves Open311 services from current address
     *
     * @param location current issue location
     */
    private void syncAddress(Location location) {
        String address = getAddressByLocation(location);
        mAddressEditText.setText(address);

        showProgress(Boolean.TRUE);

        ServiceListRequest slr = new ServiceListRequest(location.getLatitude(), location.getLongitude());
        slr.setAddress(address);

        List<Open311> open311List = Open311Manager.getAllOpen311();
        ServiceListTask serviceListTask = new ServiceListTask(slr, open311List, this);
        serviceListTask.execute();
    }

    private void syncAddressString(Location location) {
        String address = getAddressByLocation(location);
        mAddressEditText.setText(address);
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
     * @return the current address in String
     */
    private String getAddressByLocation(Location location) {
        showProgress(Boolean.TRUE);
        String address = null;
        try {
            Geocoder geo = new Geocoder(getApplicationContext(), Locale.getDefault());
            List<Address> addresses = geo.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.isEmpty()) {
                address = getString(R.string.ri_location_problem);
                addInfoText(getString(R.string.ri_location_problem_info));
            } else if (addresses.size() > 0) {
                StringBuilder sb = new StringBuilder();
                int addressLine = addresses.get(0).getMaxAddressLineIndex();
                for (int i = 0; i < addressLine - 1; i++) {
                    sb.append(addresses.get(0).getAddressLine(i)).append(", ");
                }
                sb.append(addresses.get(0).getAddressLine(addressLine - 1)).append(".");
                address = sb.toString();
            }
        } catch (Exception e) {
            address = getString(R.string.ri_location_problem);
            addInfoText(getString(R.string.ri_location_problem_info));
            e.printStackTrace();
        }
        showProgress(Boolean.FALSE);
        return address;
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
        issueLocationHelper.updateMarkerPosition(location, stop);

        // Don't call syncAddress if you are restoring the instance
        if (mBundleShowStopMarker) {
            if (stop != null) {
                // Show bus stop name on the header
                showBusStopHeader(stop.getName());
            }
            // Restore marker position
            mBundleShowStopMarker = false;
            return;
        }

        // Clear all reporting fragments
        clearReportingFragments();

        if (stop != null) {
            // Clear manually added markers
            issueLocationHelper.clearMarkers();

            // Show bus stop name on the header
            showBusStopHeader(stop.getName());

            showServicesSpinner();

            if (mStaticIssuetype != null) {
                showStaticIssueReporting(mStaticIssuetype);
                syncAddressString(location);
            } else {
                syncAddress(stop.getLocation());
            }
            // Clear static issue type
            mStaticIssuetype = null;
        } else if (location != null) {
            hideBusStopHeader();

            syncAddress(location);
        }
    }

    @Override
    public void onSendReport() {
        finishActivityWithResult();
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
     * Prepares the service lists and shows as categories in the screen
     * Adds static service categories (stop and trip problems) if they are
     * not specified by open311 endpoint
     */
    private void prepareServiceList(ServiceListResponse services) {
        // Create the service list
        List<Service> serviceList = new ArrayList<>();

        if (services != null && services.isSuccess()) {
            serviceList.addAll(services.getServiceList());
        }

        // Set marker on the map if there are open311 services
        if (Open311Manager.isAreaManagedByOpen311(serviceList)) {
            updateMarkerPosition(issueLocationHelper.getIssueLocation());
            showServicesSpinner();
        } else {
            // If there is no open311 services return and there is no stop selected
            // then hide the categories
            if (issueLocationHelper.getObaStop() == null) {
                hideServicesSpinner();
                // Show information to the user if there is no error on location
                String infoText = ((TextView) findViewById(R.id.ri_info_text)).getText().toString();
                if (!getString(R.string.ri_location_problem_info).equalsIgnoreCase(infoText)) {
                    addInfoText(getString(R.string.report_dialog_out_of_region_message));
                }
            } else if (mBundleShowStopMarker) {
                mMapFragment.setFocusStop(issueLocationHelper.getObaStop(), new ArrayList<ObaRoute>());
            }
        }

        boolean isStaticServicesProvided = checkTransitCategories(serviceList);

        // Set static service
        if (!isStaticServicesProvided) {
            serviceList.addAll(createStaticServices());
        }

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

        // Create Transit categories first
        spinnerItems.add(new SectionItem(ReportConstants.ISSUE_GROUP_TRANSIT));
        for (Service s : serviceListMap.get(ReportConstants.ISSUE_GROUP_TRANSIT)) {
            spinnerItems.add(new ServiceSpinnerItem(s));
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
            selectDefaultCategory(spinnerItems);
        }

    }

    private void selectDefaultCategory(ArrayList<SpinnerItem> spinnerItems) {
        int selectPosition = -1;
        for (int i = 0; i < spinnerItems.size(); i++) {
            SpinnerItem item = spinnerItems.get(i);
            if (item instanceof ServiceSpinnerItem) {
                if (((ServiceSpinnerItem) item).getService().getService_name().
                        equalsIgnoreCase(mDefaultIssueType)) {
                    selectPosition = i;
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

    private boolean checkTransitCategories(List<Service> serviceList) {
        String[] transitKeywords = getResources().getStringArray(R.array.report_transit_category_keywords);

        for (Service s : serviceList) {
            for (int i = 0; i < transitKeywords.length; i++) {
                String keyword = transitKeywords[i];
                if (keyword.equalsIgnoreCase(s.getGroup())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void hideServicesSpinner() {
        findViewById(R.id.ri_spinnerView).setVisibility(View.GONE);
    }

    private void showServicesSpinner() {
        findViewById(R.id.ri_spinnerView).setVisibility(View.VISIBLE);
    }

    private List<Service> createStaticServices() {
        Service s1 = new Service(getString(R.string.ri_service_stop),
                ReportConstants.STATIC_SERVICE_STOP);
        s1.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);
        Service s2 = new Service(getString(R.string.ri_service_trip),
                ReportConstants.STATIC_SERVICE_TRIP);
        s2.setGroup(ReportConstants.ISSUE_GROUP_TRANSIT);

        List<Service> staticServiceList = new ArrayList<>();
        staticServiceList.add(s1);
        staticServiceList.add(s2);

        return staticServiceList;
    }

    /**
     * Disables open311 reporting if there is no category for region
     * Show static services
     */
    public void showStaticIssueReporting(String issueType) {

        issueLocationHelper.clearMarkers();

        clearReportingFragments();

        ObaStop obaStop = issueLocationHelper.getObaStop();
        if (obaStop != null) {
            if (ReportConstants.STATIC_SERVICE_STOP.equals(issueType)) {
                showStopProblemFragment(obaStop);
            } else if (ReportConstants.STATIC_SERVICE_TRIP.equals(issueType)) {
                showTripProblemFragment(obaStop);
            }
        } else {
            addInfoText(getString(R.string.report_dialog_out_of_region_message));
            mStaticIssuetype = issueType;
        }
    }

    /**
     * Enables open311 reporting if there is no category for region
     */
    private void showOpen311Reporting(Service service) {

        clearReportingFragments();

        showOpen311ProblemFragment(service);

        updateMarkerPosition(issueLocationHelper.getIssueLocation());
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
        showArrivalListFragment(obaStop);
    }

    private void showArrivalListFragment(ObaStop obaStop) {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View v = layoutInflater.inflate(R.layout.arrivals_list_header, null);
        v.setVisibility(View.GONE);

        SimpleArrivalListFragment.show(this, R.id.ri_report_stop_problem, obaStop, this);
    }

    @Override
    public void onArrivalItemClicked(ObaArrivalInfo obaArrivalInfo) {
        removeFragmentByTag(SimpleArrivalListFragment.TAG);

        addTripName(obaArrivalInfo.getHeadsign());

        ReportTripProblemFragment.show(this, obaArrivalInfo, R.id.ri_report_stop_problem, false);
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

    private void removeOpen311ProblemFragment() {
        removeFragmentByTag(Open311ProblemFragment.TAG);
    }

    private void removeStopProblemFragment() {
        removeFragmentByTag(ReportStopProblemFragment.TAG);
    }

    private void removeTripProblemFragment() {
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
        return issueLocationHelper;
    }

    public String getCurrentAddress() {
        return mAddressEditText.getText().toString();
    }

    private void finishActivityWithResult() {
        Intent returnIntent = new Intent();
        returnIntent.putExtra(BaseReportActivity.CLOSE_REQUEST, true);
        setResult(RESULT_OK, returnIntent);
        finish();
    }
}
