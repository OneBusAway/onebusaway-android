package org.onebusaway.android.report.ui;

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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.map.googlemapsv2.BaseMapFragment;
import org.onebusaway.android.report.connection.ServicesTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.open311.models.Service;
import org.onebusaway.android.report.open311.models.ServiceListResponse;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;
import org.onebusaway.android.ui.ReportProblemFragmentBase;
import org.onebusaway.android.ui.ReportStopProblemFragment;
import org.onebusaway.android.util.LocationUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class InfrastructureIssueActivity extends BaseReportActivity implements View.OnClickListener,
        BaseMapFragment.OnFocusChangedListener, ServicesTask.Callback,
        ReportProblemFragmentBase.OnProblemReportedListener, IssueLocationHelper.Callback {

    /**
     * UI Elements
     */
    private EditText addressEditText;

    private ImageButton addressSearchButton;

    private ImageButton addressRefreshButton;

    private CustomScrollView mainScrollView;

    //Map Fragment
    private BaseMapFragment mMapFragment;

    //Open311 Issue report Fragment

    //Location helper for tracking the issue location
    private IssueLocationHelper issueLocationHelper;

    /**
     * Stores the serviceList Results from ServicesTask
     * This list will be used from Open311ProblemFragment
     */
    private List<Service> serviceList;

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param intent  The Intent containing focusId, lat, lon of the map
     */
    public static final void start(Context context, Intent intent) {
        context.startActivity(makeIntent(context, intent));
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param intent  The Intent containing focusId, lat, lon of the map
     */
    public static final Intent makeIntent(Context context, Intent intent) {
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

        setUpProgressBar();

        setupMapFragment();

        setupLocationHelper();

        setupViews();

        initLocation();
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

        mainScrollView = (CustomScrollView) findViewById(R.id.ri_scrollView);
        mainScrollView.addInterceptScrollView(findViewById(R.id.ri_frame_map_view));
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
     * Called by the BaseMapFragment when a stop obtains focus, or no stops have focus
     *
     * @param stop   the ObaStop that obtained focus, or null if no stop is in focus
     * @param routes a HashMap of all route display names that serve this stop - key is routeId
     */
    @Override
    public void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location) {
        issueLocationHelper.updateMarkerPosition(location, stop);
        if (stop != null) {
            //Clear manually added markers
            issueLocationHelper.clearMarkers();
            syncAddress(stop.getLocation());
        } else if (location != null) {
            syncAddress(location);
        }
    }

    /**
     * Called by the ServicesTask when Open311 endpoint returns ServiceList
     *
     * @param services ServiceListResponse
     */
    @Override
    public void onServicesTaskCompleted(ServiceListResponse services) {
        //Close progress
        showProgress(Boolean.FALSE);

        List<Service> serviceList = new ArrayList<Service>();
        serviceList.add(new Service("Issue Category", ReportConstants.DEFAULT_SERVICE));
        if (services != null && services.isSuccess()) {
            serviceList.addAll(services.getServiceList());
            if (isCategoryDefinedForRegion(serviceList)) {
                enableOpen311Reporting(serviceList);
            } else {
                disableOpen311Reporting();
            }
        } else {
            Toast.makeText(this, services.getResultDescription(), Toast.LENGTH_LONG).show();
        }

    }

    /**
     * If problem was sent from ReportStopProblemFragment then close also this Activity
     */
    @Override
    public void onSendReport() {
        finish();
    }

    @Override
    public void onClearMarker(int markerId) {
        mMapFragment.removeMarker(markerId);
    }

    /**
     * Called when the issue location changes
     * Retries Open311 services from current address
     *
     * @param location current issue location
     */
    private void syncAddress(Location location) {
        String address = getAddressByLocation(location);
        addressEditText.setText(address);

        showProgress(Boolean.TRUE);
        ServicesTask servicesTask = new ServicesTask(location, this);
        servicesTask.execute();
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
     * @param isVisible
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

    /**
     * Returns if Category defined by Open311 endpoint for selected region
     *
     * @param serviceList Response from the open311
     * @return true if there is category for region
     */
    public Boolean isCategoryDefinedForRegion(List<Service> serviceList) {
        return (serviceList.size() > 2);
    }

    /**
     * Disables open311 reporting if there is no category for region
     */
    public void disableOpen311Reporting() {

        issueLocationHelper.clearMarkers();

        removeInfoText(R.id.ri_custom_info_layout);

        removeOpen311ProblemFragment();

        removeStopProblemFragment();

        ObaStop obaStop = issueLocationHelper.getObaStop();
        if (obaStop != null) {
            showStopProblemFragment(obaStop);
        } else {
            addInfoText(getString(R.string.report_dialog_out_of_region_message), R.id.ri_custom_info_layout);
        }

    }

    /**
     * Enables open311 reporting if there is no category for region
     */
    private void enableOpen311Reporting(List<Service> serviceList) {

        setServiceList(serviceList);

        removeInfoText(R.id.ri_custom_info_layout);

        removeStopProblemFragment();

        showOpen311ProblemFragment();

        updateMarkerPosition(issueLocationHelper.getIssueLocation());
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

    private void removeStopProblemFragment() {
        removeFragmentByTag(ReportStopProblemFragment.TAG);
    }

    private void showOpen311ProblemFragment() {
        Open311ProblemFragment.show(this, R.id.ri_report_stop_problem);
    }

    private void removeOpen311ProblemFragment() {
        removeFragmentByTag(Open311ProblemFragment.TAG);
    }

    public IssueLocationHelper getIssueLocationHelper() {
        return issueLocationHelper;
    }

    public String getCurrentAddress() {
        return addressEditText.getText().toString();
    }

    public List<Service> getServiceList() {
        return serviceList;
    }

    public void setServiceList(List<Service> serviceList) {
        this.serviceList = serviceList;
    }
}
