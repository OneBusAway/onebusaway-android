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

import org.junit.Test;
import org.onebusaway.android.UriAssert;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.Occupancy;
import org.onebusaway.android.io.request.ObaTripDetailsRequest;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.mock.MockRegion;

import java.util.HashMap;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * Tests requests and parsing JSON responses from /res/raw for the OBA server API
 * to get information about specific trip details
 */
@SuppressWarnings("serial")
public class TripDetailsRequest extends ObaTestCase {

    protected final String TEST_TRIP_ID = "1_18196913";

    @Test
    public void testKCMTripRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMTripRequest();
    }

    @Test
    public void testKCMTripRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMTripRequest();
    }

    private void _assertKCMTripRequest() {
        ObaTripDetailsRequest.Builder builder =
                new ObaTripDetailsRequest.Builder(getTargetContext(), TEST_TRIP_ID);
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

    @Test
    public void testKCMTripResponseUsingCustomUrl() throws Exception {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMTripResponse();
    }

    @Test
    public void testKCMTripResponseUsingRegion() throws Exception {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMTripResponse();
    }

    private void _assertKCMTripResponse() {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getTargetContext(), TEST_TRIP_ID)
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

    @Test
    public void testNoTripsRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNoTripsRequest();
    }

    @Test
    public void testNoTripsRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNoTripsRequest();
    }

    private void _assertNoTripsRequest() {
        ObaTripDetailsRequest request = new ObaTripDetailsRequest.Builder(getTargetContext(),
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
    @Test
    public void testNoTripsResponse() throws Exception {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getTargetContext(), TEST_TRIP_ID)
                        .setIncludeTrip(false)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());
        // Make sure the trip exists
        ObaTrip trip = response.getTrip(response.getId());
        assertNull("Expected failure / TODO: report as API bug?", trip);
    }

    @Test
    public void testNoScheduleRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNoScheduleRequest();
    }

    @Test
    public void testNoScheduleRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNoScheduleRequest();
    }

    private void _assertNoScheduleRequest() {
        ObaTripDetailsRequest request = new ObaTripDetailsRequest.Builder(getTargetContext(),
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

    @Test
    public void testNoScheduleResponse() {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getTargetContext(), TEST_TRIP_ID)
                        .setIncludeSchedule(false)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());

        ObaTripSchedule schedule = response.getSchedule();
        assertNull(schedule);
    }

    @Test
    public void testNoStatusRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNoStatusRequest();
    }

    @Test
    public void testNoStatusRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNoStatusRequest();
    }

    private void _assertNoStatusRequest() {
        ObaTripDetailsRequest request =
                new ObaTripDetailsRequest.Builder(getTargetContext(), TEST_TRIP_ID)
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

    @Test
    public void testNoStatus() {
        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getTargetContext(), TEST_TRIP_ID)
                        .setIncludeStatus(false)
                        .build()
                        .call();
        assertOK(response);
        assertEquals(TEST_TRIP_ID, response.getId());

        ObaTripStatus status = response.getStatus();
        assertNull(status);
    }

    @Test
    public void testNewRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNewRequest();
    }

    @Test
    public void testNewRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNewRequest();
    }

    private void _assertNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaTripDetailsRequest request =
                ObaTripDetailsRequest.newRequest(getTargetContext(), TEST_TRIP_ID);
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

    @Test
    public void testTripResponseOccupancy() {
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        ObaTripDetailsResponse response =
                new ObaTripDetailsRequest.Builder(getTargetContext(), "Hillsborough Area Regional Transit_1389962")
                        .build()
                        .call();
        assertOK(response);
        assertEquals("Hillsborough Area Regional Transit_1389962", response.getId());

        ObaTripSchedule schedule = response.getSchedule();
        assertNotNull(schedule);

        ObaTripSchedule.StopTime[] stopTimes = schedule.getStopTimes();

        // Occupancy - EMPTY
        assertEquals(Occupancy.EMPTY, stopTimes[0].getHistoricalOccupancy());

        // Occupancy - MANY_SEATS_AVAILABLE
        assertEquals(Occupancy.MANY_SEATS_AVAILABLE, stopTimes[1].getHistoricalOccupancy());

        // Occupancy - FEW_SEATS_AVAILABLE
        assertEquals(Occupancy.FEW_SEATS_AVAILABLE, stopTimes[2].getHistoricalOccupancy());

        // Occupancy - STANDING_ROOM_ONLY
        assertEquals(Occupancy.STANDING_ROOM_ONLY, stopTimes[3].getHistoricalOccupancy());

        // Occupancy - CRUSHED_STANDING_ROOM_ONLY
        assertEquals(Occupancy.CRUSHED_STANDING_ROOM_ONLY, stopTimes[4].getHistoricalOccupancy());

        // Occupancy - FULL
        assertEquals(Occupancy.FULL, stopTimes[5].getHistoricalOccupancy());

        // Occupancy - NOT_ACCEPTING_PASSENGERS
        assertEquals(Occupancy.NOT_ACCEPTING_PASSENGERS, stopTimes[6].getHistoricalOccupancy());

        // Occupancy - Empty string
        assertNull(stopTimes[7].getHistoricalOccupancy());

        // Occupancy - Missing field
        assertNull(stopTimes[8].getHistoricalOccupancy());
    }
}
