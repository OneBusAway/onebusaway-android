/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.io.test;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.report.connection.ServiceListTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.util.ServiceUtils;
import org.onebusaway.android.util.LocationUtils;

import android.location.Location;
import android.test.AndroidTestCase;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import edu.usf.cutr.open311client.Open311;
import edu.usf.cutr.open311client.Open311Manager;
import edu.usf.cutr.open311client.models.Open311Option;
import edu.usf.cutr.open311client.models.Service;
import edu.usf.cutr.open311client.models.ServiceListRequest;
import edu.usf.cutr.open311client.models.ServiceListResponse;

/**
 * Tests to evaluate interactions with Open311 system for regions that use Open311.
 *
 * NOTE: These tests make actual HTTP requests to live Open311 servers (SeeClickFix), because we
 * make some assumptions required for text heuristic matching to identify transit-related services.
 * If there are changes in the live server (e.g., new services added than aren't transit-related
 * but include some of the text we're using to identify transit services), these could break our
 * assumptions, and we want to know about that.
 */
public class ReportProblemOpen311Test extends AndroidTestCase {

    // Mock region to use in tests
    ObaRegion mTampaRegion;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTampaRegion = MockRegion.getTampa(getContext());

        // Clear all open311 endpoints
        Open311Manager.clearOpen311();

        assertNotNull(mTampaRegion.getOpen311Servers());

        // Read the open311 preferences from the region and set
        if (mTampaRegion.getOpen311Servers() != null) {
            for (ObaRegion.Open311Server open311Server : mTampaRegion.getOpen311Servers()) {
                String jurisdictionId = open311Server.getJuridisctionId();

                Open311Option option = new Open311Option(open311Server.getBaseUrl(),
                        open311Server.getApiKey(),
                        TextUtils.isEmpty(jurisdictionId) ? null : jurisdictionId);
                Open311Manager.initOpen311WithOption(option);
            }
        }
    }

    /**
     * Tests locations in Hillsborough County for services at that location.  There should be at
     * least
     * ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD services marked as transit services, as HART
     * is the primary SeeClickFix account holder and therefore all services are transit-related.
     * As of Dec. 2, 2016 services are heuristically matched based on text in the service name
     * (SeeClickFix does not support the group or keyword Open311 elements for explicit matching).
     */
    public void testHillsboroughCounty() {
        List<Location> locations = new ArrayList<>();

        // USF area
        locations.add(LocationUtils.makeLocation(28.0612088, -82.415747));
        // Downtown
        locations.add(LocationUtils.makeLocation(27.9577463, -82.4559472));
        // Brandon
        locations.add(LocationUtils.makeLocation(27.9520925, -82.3039963));

        List<Service> serviceList = _testOpen311AtLocation(locations);

        // Make sure we get a valid response here - a failure could be intermittent network issues
        assertNotNull(serviceList);

        // Mark the services that are transit-related
        boolean mIsAllTransitHeuristicMatch = ServiceUtils
                .markTransitServices(getContext(), serviceList);
        assertTrue(mIsAllTransitHeuristicMatch);

        int countGroupTransit = 0;
        int countDynamicStop = 0;
        int countDynamicTrip = 0;

        for (Service s : serviceList) {
            if (s.getGroup().equals(ReportConstants.ISSUE_GROUP_TRANSIT)) {
                countGroupTransit++;
            }
            if (s.getType().equals(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP)) {
                countDynamicStop++;
            }
            if (s.getType().equals(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP)) {
                countDynamicTrip++;
            }
        }

        // We should be over the threshold of assuming that all services (request types) are transit-related
        assertTrue(countGroupTransit >= ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD);

        // Everything not arrival times related should be marked as dynamic stop
        assertTrue(countDynamicStop >= ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD);

        // There should only be one arrival times (trip) request type, because we treat that differently (show arrivals to pick from)
        assertTrue(countDynamicTrip == 1);
    }

    /**
     * Tests locations in Pinellas County for services at that location.  There should be less than
     * ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD services marked as transit services, because
     * PSTA has only 2 (stop problem and arrival time problem) of the many services maintained
     * by Pinellas County.  As of Dec. 2, 2016 services are heuristically matched based on text in
     * the service name (SeeClickFix does not support the group or keyword Open311 elements for
     * explicit matching).
     */
    public void testPinellasCounty() {
        List<Location> locations = new ArrayList<>();

        // Mid County
        locations.add(LocationUtils.makeLocation(27.8435877, -82.7109945));
        // St. Pete
        locations.add(LocationUtils.makeLocation(27.7626097, -82.6436986));
        // Largo
        locations.add(LocationUtils.makeLocation(27.9118406, -82.7879116));
        // Clearwater
        locations.add(LocationUtils.makeLocation(27.9632576, -82.7885631));

        List<Service> serviceList = _testOpen311AtLocation(locations);

        // Make sure we get a valid response here - a failure could be intermittent network issues
        assertNotNull(serviceList);

        // Mark the services that are transit-related
        boolean mIsAllTransitHeuristicMatch = ServiceUtils
                .markTransitServices(getContext(), serviceList);
        assertFalse(mIsAllTransitHeuristicMatch);

        // There should be less than ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD services
        // marked as transit services in Pinellas County
        int countGroupTransit = 0;
        int countDynamicStop = 0;
        int countDynamicTrip = 0;

        for (Service s : serviceList) {
            if (s.getGroup() != null && s.getGroup().equals(ReportConstants.ISSUE_GROUP_TRANSIT)) {
                countGroupTransit++;
            }
            if (s.getType().equals(ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP)) {
                countDynamicStop++;
            }
            if (s.getType().equals(ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP)) {
                countDynamicTrip++;
            }
        }

        // We should be under the threshold for assuming that all services as transit-related
        assertTrue(countGroupTransit < ReportConstants.NUM_TRANSIT_SERVICES_THRESHOLD);

        // We should only recognize two transit request types (services) - stop and trip
        assertTrue(countDynamicStop == 1);
        assertTrue(countDynamicTrip == 1);
    }

    private List<Service> _testOpen311AtLocation(List<Location> locations) {
        for (Location l : locations) {
            ServiceListRequest slr = new ServiceListRequest(l.getLatitude(), l.getLongitude());
            List<Open311> open311List = Open311Manager.getAllOpen311();
            ServiceListTask.Callback callback = new ServiceListTask.Callback() {
                @Override
                public void onServicesTaskCompleted(ServiceListResponse services, Open311 open311) {
                }
            };

            ServiceListTask serviceListTask = new ServiceListTask(slr, open311List, callback);

            try {
                // Execute the AsyncTask synchronously
                ServiceListResponse services = serviceListTask.execute().get();
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
                return serviceList;
            } catch (InterruptedException | ExecutionException e) {
                // Print any network errors
                e.printStackTrace();
            }
        }
        return null;
    }
}
