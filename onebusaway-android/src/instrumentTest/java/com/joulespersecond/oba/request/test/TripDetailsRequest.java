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
package com.joulespersecond.oba.request.test;

import com.joulespersecond.oba.elements.ObaTrip;
import com.joulespersecond.oba.elements.ObaTripSchedule;
import com.joulespersecond.oba.elements.ObaTripStatus;
import com.joulespersecond.oba.request.ObaTripDetailsRequest;
import com.joulespersecond.oba.request.ObaTripDetailsResponse;
import com.joulespersecond.seattlebusbot.test.UriAssert;

import java.util.HashMap;

@SuppressWarnings("serial")
public class TripDetailsRequest extends ObaTestCase {

    protected final String TEST_TRIP_ID = "1_18196913";

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testKCMTripRequest() {
        ObaTripDetailsRequest.Builder builder =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID);
        ObaTripDetailsRequest request = builder.build();
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request);
    }

    public void testKCMTripResponse() throws Exception {
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

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testNoTripsRequest() {
        ObaTripDetailsRequest request = new ObaTripDetailsRequest.Builder(getContext(),
                TEST_TRIP_ID)
                .setIncludeTrip(false)
                .build();
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("includeTrip", "false");
                    put("key", "*");
                    put("version", "2");
                }},
                request);
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

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testNoScheduleRequest() {
        ObaTripDetailsRequest request = new ObaTripDetailsRequest.Builder(getContext(),
                TEST_TRIP_ID)
                .setIncludeSchedule(false)
                .build();
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("includeSchedule", "false");
                    put("key", "*");
                    put("version", "2");
                }},
                request);
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

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testNoStatusRequest() {
        ObaTripDetailsRequest request =
                new ObaTripDetailsRequest.Builder(getContext(), TEST_TRIP_ID)
                        .setIncludeStatus(false)
                        .build();
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("includeStatus", "false");
                    put("key", "*");
                    put("version", "2");
                }},
                request);
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

    // TODO - fix this test in context of regions and loading multiple URLs
    // Currently mixes Tampa URL with KCM data
    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaTripDetailsRequest request =
                ObaTripDetailsRequest.newRequest(getContext(), TEST_TRIP_ID);
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trip-details/" + TEST_TRIP_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request);
    }
}
