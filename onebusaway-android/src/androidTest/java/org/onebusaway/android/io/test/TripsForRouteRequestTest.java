/*
 * Copyright (C) 2015 University of South Florida (sjbarbeau@gmail.com)
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
import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.Occupancy;
import org.onebusaway.android.io.request.ObaTripsForRouteRequest;
import org.onebusaway.android.io.request.ObaTripsForRouteResponse;
import org.onebusaway.android.mock.MockRegion;

import java.util.HashMap;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * Tests requests and parsing JSON responses from /res/raw for the OBA server API
 * to get trips that belong to a particular route
 */
@SuppressWarnings("serial")
public class TripsForRouteRequestTest extends ObaTestCase {

    protected final String TEST_ROUTE_ID = "Hillsborough Area Regional Transit_5";

    @Test
    public void testHARTTripsForRouteRequest() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        callHARTTripsForRouteRequest();
        Application.get().setCustomApiUrl(null);

        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        callHARTTripsForRouteRequest();
        Application.get().setCurrentRegion(null);
    }

    private void callHARTTripsForRouteRequest() {
        ObaTripsForRouteRequest.Builder builder = new ObaTripsForRouteRequest.Builder(getTargetContext(),
                TEST_ROUTE_ID);
        ObaTripsForRouteRequest request = builder.build();
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trips-for-route/" + TEST_ROUTE_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testHARTTripsForRouteResponse() throws Exception {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        // No trip status included
        ObaTripsForRouteResponse response =
                new ObaTripsForRouteRequest.Builder(getTargetContext(), TEST_ROUTE_ID)
                        .build()
                        .call();
        assertOK(response);
        ObaTripDetails[] trips = response.getTrips();

        assertEquals("Hillsborough Area Regional Transit_98682", trips[0].getId());
        assertNull(trips[0].getStatus());

        // Include vehicles status
        response =
                new ObaTripsForRouteRequest.Builder(getTargetContext(), TEST_ROUTE_ID)
                        .setIncludeStatus(true)
                        .build()
                        .call();
        assertOK(response);
        trips = response.getTrips();

        assertEquals("Hillsborough Area Regional Transit_101446", trips[0].getId());
        assertEquals(1444073087126L, trips[0].getStatus().getLastUpdateTime());
        assertEquals("Hillsborough Area Regional Transit_2415",
                trips[0].getStatus().getVehicleId());
        assertEquals("Hillsborough Area Regional Transit_4707", trips[0].getStatus().getNextStop());
        assertEquals(420, trips[0].getStatus().getScheduleDeviation());
        // Potentially interpolated position
        assertEquals(28.063130557136404, trips[0].getStatus().getPosition().getLatitude());
        assertEquals(-82.43457, trips[0].getStatus().getPosition().getLongitude());
        // Last known location
        assertEquals(28.065561294555664, trips[0].getStatus().getLastKnownLocation().getLatitude());
        assertEquals(-82.4344711303711, trips[0].getStatus().getLastKnownLocation().getLongitude());
        assertEquals(0, trips[0].getStatus().getLastLocationUpdateTime());
    }

    @Test
    public void testNewRequest() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        callNewRequest();
        Application.get().setCustomApiUrl(null);

        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        callNewRequest();
        Application.get().setCurrentRegion(null);
    }

    private void callNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaTripsForRouteRequest request = ObaTripsForRouteRequest
                .newRequest(getTargetContext(), TEST_ROUTE_ID);
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/trips-for-route/" + TEST_ROUTE_ID
                        + ".json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testTripsForRouteOccupancyResponse() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        // Include vehicles status
        ObaTripsForRouteResponse response =
                new ObaTripsForRouteRequest.Builder(getTargetContext(), "Hillsborough Area Regional Transit_5_occupancy")
                        .setIncludeStatus(true)
                        .build()
                        .call();
        assertOK(response);
        ObaTripDetails[] trips = response.getTrips();

        assertEquals("Hillsborough Area Regional Transit_101446", trips[0].getId());
        assertEquals(1444073087126L, trips[0].getStatus().getLastUpdateTime());
        assertEquals("Hillsborough Area Regional Transit_2415",
                trips[0].getStatus().getVehicleId());
        assertEquals("Hillsborough Area Regional Transit_4707", trips[0].getStatus().getNextStop());
        assertEquals(420, trips[0].getStatus().getScheduleDeviation());
        // Potentially interpolated position
        assertEquals(28.063130557136404, trips[0].getStatus().getPosition().getLatitude());
        assertEquals(-82.43457, trips[0].getStatus().getPosition().getLongitude());
        // Last known location
        assertEquals(28.065561294555664, trips[0].getStatus().getLastKnownLocation().getLatitude());
        assertEquals(-82.4344711303711, trips[0].getStatus().getLastKnownLocation().getLongitude());
        assertEquals(0, trips[0].getStatus().getLastLocationUpdateTime());

        // Occupancy - EMPTY
        ObaTripStatus status = trips[0].getStatus();
        assertEquals(Occupancy.EMPTY, status.getRealtimeOccupancy());

        // Occupancy - MANY_SEATS_AVAILABLE
        status = trips[1].getStatus();
        assertEquals(Occupancy.MANY_SEATS_AVAILABLE, status.getRealtimeOccupancy());

        // Occupancy - FEW_SEATS_AVAILABLE
        status = trips[2].getStatus();
        assertEquals(Occupancy.FEW_SEATS_AVAILABLE, status.getRealtimeOccupancy());

        // Occupancy - STANDING_ROOM_ONLY
        status = trips[3].getStatus();
        assertEquals(Occupancy.STANDING_ROOM_ONLY, status.getRealtimeOccupancy());

        // Occupancy - CRUSHED_STANDING_ROOM_ONLY
        status = trips[4].getStatus();
        assertEquals(Occupancy.CRUSHED_STANDING_ROOM_ONLY, status.getRealtimeOccupancy());

        // Occupancy - FULL
        status = trips[5].getStatus();
        assertEquals(Occupancy.FULL, status.getRealtimeOccupancy());

        // Occupancy - NOT_ACCEPTING_PASSENGERS
        status = trips[6].getStatus();
        assertEquals(Occupancy.NOT_ACCEPTING_PASSENGERS, status.getRealtimeOccupancy());

        // Occupancy - Empty string
        status = trips[7].getStatus();
        assertNull(status.getRealtimeOccupancy());

        // Occupancy - Missing field
        status = trips[8].getStatus();
        assertNull(status.getRealtimeOccupancy());
    }
}
