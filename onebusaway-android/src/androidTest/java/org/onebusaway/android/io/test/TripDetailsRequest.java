/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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

import org.onebusaway.android.UriAssert;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.request.ObaTripDetailsRequest;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.mock.MockRegion;

import java.util.HashMap;

@SuppressWarnings("serial")
public class TripDetailsRequest extends ObaTestCase {

    protected final String TEST_TRIP_ID = "1_18196913";

    public void testKCMTripRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMTripRequest();
    }

    public void testKCMTripRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMTripRequest();
    }

    private void _assertKCMTripRequest() {
        ObaTripDetailsRequest.Builder builder =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID);
        ObaTripDetailsRequest request = builder.build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testKCMTripResponseUsingCustomUrl() throws Exception {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMTripResponse();
    }

    public void testKCMTripResponseUsingRegion() throws Exception {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMTripResponse();
    }

    private void _assertKCMTripResponse() {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());

        ObaTripSchedule schedule = response.getSchedule();
        assertNotNull(schedule);

        //ObaTripStatus status = response.getStatus();
        //assertNotNull(status);

        // Make sure the trip exists
        ObaTrip trip = response.getTrip(response.getId());
        assertNotNull(trip);
    }

    public void testNoTripsRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNoTripsRequest();
    }

    public void testNoTripsRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNoTripsRequest();
    }

    private void _assertNoTripsRequest() {
        ObaTripDetailsRequest request = new ObaTripDetailsRequest.Builder(getContext(),
                TEST_TRIP_ID)
                .setIncludeTrip(false)
                .build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("includeTrip", "false");
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    // TODO: API response includes the trip anyway
    public void testNoTripsResponse() throws Exception {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID)
                        .setIncludeTrip(false)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());
        // Make sure the trip exists
        ObaTrip trip = response.getTrip(response.getId());
        assertNull("Expected failure / TODO: report as API bug?", trip);
    }

    public void testNoScheduleRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNoScheduleRequest();
    }

    public void testNoScheduleRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNoScheduleRequest();
    }

    private void _assertNoScheduleRequest() {
        ObaTripDetailsRequest request = new ObaTripDetailsRequest.Builder(getContext(),
                TEST_TRIP_ID)
                .setIncludeSchedule(false)
                .build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("includeSchedule", "false");
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testNoScheduleResponse() throws Exception {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID)
                        .setIncludeSchedule(false)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());

        ObaTripSchedule schedule = response.getSchedule();
        assertNull(schedule);
    }

    public void testNoStatusRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNoStatusRequest();
    }

    public void testNoStatusRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNoStatusRequest();
    }

    private void _assertNoStatusRequest() {
        ObaTripDetailsRequest request =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID)
                        .setIncludeStatus(false)
                        .build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("includeStatus", "false");
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testNoStatus() throws Exception {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID)
                        .setIncludeStatus(false)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());

        ObaTripStatus status = response.getStatus();
        assertNull(status);
    }

    public void testNewRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNewRequest();
    }

    public void testNewRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNewRequest();
    }

    private void _assertNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaTripDetailsRequest request =
                ObaTripDetailsRequest.newRequest(getContext(), TEST_TRIP_ID);
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }
}
