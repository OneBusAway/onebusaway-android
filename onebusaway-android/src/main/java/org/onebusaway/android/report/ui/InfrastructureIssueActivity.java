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
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.report.connection.ServiceListTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;
import org.onebusaway.android.ui.ReportProblemFragmentBase;
import org.onebusaway.android.ui.ReportStopProblemFragment;
import org.onebusaway.android.ui.ReportTripProblemFragment;
import org.onebusaway.android.util.LocationUtil;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import edu.usf.cutr.open311client.Open311;
import edu.usf.cutr.open311client.Open311Manager;
import edu.usf.cutr.open311client.models.Service;
import edu.usf.cutr.open311client.models.ServiceListRequest;
import edu.usf.cutr.open311client.models.ServiceListResponse;

public class InfrastructureIssueActivity extends BaseReportActivity implements View.OnClickListener,
        BaseMapFragment.OnFocusChangedListener, ServiceListTask.Callback,
        ReportProblemFragmentBase.OnProblemReportedListener, IssueLocationHelper.Callback {

    /**
     * UI Elements
     */
    private EditText addressEditText;

    private ImageButton addressSearchButton;

    private ImageButton addressRefreshButton;

    private Spinner servicesSpinner;

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
    private String staticIssuetype;

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param intent  The Intent containing focusId, lat, lon of the map
     */
    public static void start(Context context, Intent intent) {
        context.startActivity(makeIntent(context, intent));
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

        setupMapFragment();

        setupLocationHelper();

        setupViews();

        initLocation();
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
    private void setupMapFragment() {
        FragmentManager fm = getSupportFragmentManager();
        if (mMapFragment == null) {
            mMapFragment = new BaseMapFragment();
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
        Location mapCenterLocation = LocationUtil.makeLocation(
                getIntent().getDoubleExtra(MapParams.CENTER_LAT, 0),
                getIntent().getDoubleExtra(MapParams.CENTER_LON, 0));

        issueLocationHelper = new IssueLocationHelper(mapCenterLocation, this);
    }

    /**
     * Initialize UI components
     */
    private void setupViews() {
        addressEditText = (EditText) findViewById(R.id.ri_address_editText);

        addressSearchButton = (ImageButton) findViewById(R.id.ri_search_address_button);
        addressSearchButton.setOnClickListener(this);

        addressRefreshButton = (ImageButton) findViewById(R.id.ri_refresh_address_button);
        addressRefreshButton.setOnClickListener(this);

        CustomScrollView mainScrollView = (CustomScrollView) findViewById(R.id.ri_scrollView);
        mainScrollView.addInterceptScrollView(findViewById(R.id.ri_frame_map_view));

        servicesSpinner = (Spinner) findViewById(R.id.ri_spinnerServices);
        servicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Service service = (Service) servicesSpinner.getSelectedItem();
                onSpinnerItemSelected(service);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }

    /**
     * Show the current location on the map
     */
    private void initLocation() {
        syncAddress(issueLocationHelper.getIssueLocation());
    }

    @Override
    public void onClick(View v) {
        if (v == addressSearchButton) {
            searchAddress();
        } else if (v == addressRefreshButton) {
            getAddressByLocation(issueLocationHelper.getIssueLocation());
        }
    }

    private void onSpinnerItemSelected(Service service) {

        if (!ReportConstants.DEFAULT_SERVICE.equalsIgnoreCase(service.getType())) {
            //Remove the info text for select category
            removeInfoText(R.id.ri_custom_info_layout);
        }

        staticIssuetype = null;

        if (service.getService_code() != null) {
            showOpen311Reporting(service);
        } else if (!ReportConstants.DEFAULT_SERVICE.equals(service.getType())) {
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
        addressEditText.setText(address);

        showProgress(Boolean.TRUE);

        ServiceListRequest slr = new ServiceListRequest(location.getLatitude(), location.getLongitude());
        slr.setAddress(address);

        List<Open311> open311List = Open311Manager.getAllOpen311();
        ServiceListTask serviceListTask = new ServiceListTask(slr, open311List, this);
        serviceListTask.execute();
    }

    private void syncAddressString(Location location) {
        String address = getAddressByLocation(location);
        addressEditText.setText(address);
    }

    /**
     * Search address string from addressEditText
     */
    private void searchAddress() {
        showProgress(Boolean.TRUE);
        String addressString = addressEditText.getText().toString();
        Location location = getLocationByAddress(addressString);
        if (location != null) {
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
                showRefreshButton(Boolean.TRUE);
            } else if (addresses.size() > 0) {
                StringBuilder sb = new StringBuilder();
                int addressLine = addresses.get(0).getMaxAddressLineIndex();
                for (int i = 0; i < addressLine - 1; i++) {
                    sb.append(addresses.get(0).getAddressLine(i)).append(", ");
                }
                sb.append(addresses.get(0).getAddressLine(addressLine - 1)).append(".");
                address = sb.toString();
                showRefreshButton(Boolean.FALSE);
            }
        } catch (Exception e) {
            address = getString(R.string.ri_location_problem);
            showRefreshButton(Boolean.TRUE);
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

    /**
     * Shows or hides refresh button for address bar
     *
     * @param isVisible true if you want to show refresh button
     */
    private void showRefreshButton(Boolean isVisible) {
        if (isVisible) {
            addressSearchButton.setVisibility(View.GONE);
            addressRefreshButton.setVisibility(View.VISIBLE);
        } else {
            addressSearchButton.setVisibility(View.VISIBLE);
            addressRefreshButton.setVisibility(View.GONE);
        }
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
        // Clear all reporting fragments
        clearReportingFragments();

        if (stop != null) {
            //Clear manually added markers
            issueLocationHelper.clearMarkers();

            if (staticIssuetype != null) {
                showStaticIssueReporting(staticIssuetype);
                syncAddressString(location);
            } else {
                syncAddress(stop.getLocation());
            }
            // clear static issue type
            staticIssuetype = null;
        } else if (location != null) {
            syncAddress(location);
        }
    }

    @Override
    public void onSendReport() {
        finish();
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

        // Show information to the user
        addInfoText(getString(R.string.report_dialog_categories), R.id.ri_custom_info_layout);

        // Create the service list
        List<Service> serviceList = new ArrayList<>();
        serviceList.add(0, new Service(getString(R.string.ri_service_default),
                ReportConstants.DEFAULT_SERVICE));

        if (services != null && services.isSuccess()) {
            serviceList.addAll(services.getServiceList());
        }

        // Set marker on the map if there are open311 services
        if (Open311Manager.isAreaManagedByOpen311(serviceList)) {
            updateMarkerPosition(issueLocationHelper.getIssueLocation());
        }

        boolean isStaticServicesProvided = false;
        for (Service s : serviceList) {
            if (ReportConstants.ISSUE_GROUP_TRANSIT.equalsIgnoreCase(s.getGroup())) {
                isStaticServicesProvided = true;
                break;
                // TODO: better manage static services
            }
        }

        // Set static service
        if (!isStaticServicesProvided) {
            serviceList.add(new Service(getString(R.string.ri_service_stop),
                    ReportConstants.STATIC_SERVICE_STOP));
            serviceList.add(new Service(getString(R.string.ri_service_trip),
                    ReportConstants.STATIC_SERVICE_TRIP));
        }

        ArrayAdapter<Service> adapter = new ArrayAdapter<>(this,
                android.support.v7.appcompat.R.layout.support_simple_spinner_dropdown_item,
                serviceList);
        servicesSpinner.setAdapter(adapter);
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
            } else if (ReportConstants.STATIC_SERVICE_STOP.equals(issueType)) {
                showTripProblemFragment(obaStop);
            }
        } else {
            addInfoText(getString(R.string.report_dialog_out_of_region_message),
                    R.id.ri_custom_info_layout);
            staticIssuetype = issueType;
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
        removeInfoText(R.id.ri_custom_info_layout);

        removeOpen311ProblemFragment();

        removeStopProblemFragment();

        removeTripProblemFragment();
    }

    public void createContactInfoFragment() {
        Fragment fragment = getSupportFragmentManager().
                findFragmentByTag(ReportConstants.TAG_CONTACT_INFO_FRAGMENT);

        if (fragment instanceof ContactInfoFragment) {
            removeFragment(fragment);
        } else {
            addFragment(new ContactInfoFragment(),
                    ReportConstants.TAG_CONTACT_INFO_FRAGMENT, R.id.ri_contact_info).
                    addToBackStack(ReportConstants.TAG_CONTACT_INFO_FRAGMENT).commit();
        }
    }

    private void showStopProblemFragment(ObaStop obaStop) {
        ReportStopProblemFragment.show(this, obaStop, R.id.ri_report_stop_problem);
    }

    private void showTripProblemFragment(ObaStop obaStop) {
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
    }

    public IssueLocationHelper getIssueLocationHelper() {
        return issueLocationHelper;
    }

    public String getCurrentAddress() {
        return addressEditText.getText().toString();
    }
}
