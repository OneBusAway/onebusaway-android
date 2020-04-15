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
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.Occupancy;
import org.onebusaway.android.io.elements.Status;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.util.UIUtils;

import java.util.HashMap;
import java.util.List;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

/**
 * Tests requests and parsing JSON responses from /res/raw for the OBA server API
 * to get arrival times for a specific stop
 */
@SuppressWarnings("serial")
public class ArrivalInfoRequestTest extends ObaTestCase {

    @Test
    public void testKCMStopRequestUsingCustomUrl() {
        // Test by setting URL directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMStopRequest();
    }

    @Test
    public void testKCMStopRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMStopRequest();
    }

    private void _assertKCMStopRequest() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "1_29261");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.pugetsound.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_29261.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testHARTStopRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        _assertHARTStopRequest();
    }

    @Test
    public void testHARTStopRequestUsingRegion() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        _assertHARTStopRequest();
    }

    private void _assertHARTStopRequest() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "https://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testKCMStopResponseUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMStopResponse();
    }

    @Test
    public void testKCMStopResponseUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMStopResponse();
    }

    private void _assertKCMStopResponse() {
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "1_29261").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("1_29261", stop.getId());
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("1", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        // Uhh, this will vary considerably depending on when this is run.
        assertNotNull(arrivals);

        final List<ObaStop> nearbyStops = response.getNearbyStops();
        assertTrue(nearbyStops.size() > 0);
    }

    @Test
    public void testHARTStopResponseUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        _assertHARTStopResponse();
    }

    @Test
    public void testHARTStopResponseUsingRegion() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        _assertHARTStopResponse();
    }

    private void _assertHARTStopResponse() {
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_3105").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_3105", stop.getId());
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());
        ObaTrip trip = response.getTrip("Hillsborough Area Regional Transit_909841");
        assertEquals("Hillsborough Area Regional Transit_266684", trip.getBlockId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);
        assertEquals(27.982215585882088, arrivals[0].getTripStatus().getPosition().getLatitude());
        assertEquals(-82.4224, arrivals[0].getTripStatus().getPosition().getLongitude());
        assertEquals(Status.DEFAULT, arrivals[0].getTripStatus().getStatus());

        final List<ObaStop> nearbyStops = response.getNearbyStops();
        assertTrue(nearbyStops.size() > 0);
    }

    @Test
    public void testTotalStopsInTrip() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");

        /**
         * First test with a response from a server that supports the "totalStopsInTrip" field.
         * In this case, it's for a fake stop 10000, and we use data for actual stop 6497 from
         * the OBA Tampa server after it started supporting the field.
         */
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_10000").build().call();
        assertOK(response);

        ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);
        assertEquals(34, arrivals[0].getTotalStopsInTrip());
        assertEquals(34, arrivals[1].getTotalStopsInTrip());
        assertEquals(142, arrivals[2].getTotalStopsInTrip());
        assertEquals(71, arrivals[3].getTotalStopsInTrip());
        assertEquals(142, arrivals[4].getTotalStopsInTrip());
        assertEquals(34, arrivals[5].getTotalStopsInTrip());
        assertEquals(34, arrivals[6].getTotalStopsInTrip());
        assertEquals(142, arrivals[7].getTotalStopsInTrip());

        /**
         * Now test with a response from a server that doesn't support the "totalStopsInTrip" field.
         * In this case, it's a response from the OBA Tampa server prior to supporting the field.
         */
        response = new ObaArrivalInfoRequest.Builder(getTargetContext(),
                "Hillsborough Area Regional Transit_6497").build().call();
        assertOK(response);

        arrivals = response.getArrivalInfo();
        assertEquals(0, arrivals[0].getTotalStopsInTrip());
        assertEquals(0, arrivals[1].getTotalStopsInTrip());
        assertEquals(0, arrivals[2].getTotalStopsInTrip());
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
        ObaArrivalInfoRequest request = ObaArrivalInfoRequest.newRequest(getTargetContext(), "1_10");
        assertNotNull(request);
        UriAssert.assertUriMatch(
                "https://api.pugetsound.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_10.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    @Test
    public void testStopSituationPsta() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "PSTA_4077").build().call();
        assertOK(response);
        List<ObaSituation> situations = response.getSituations();
        assertNotNull(situations);

        ObaSituation situation = situations.get(0);
        assertEquals("PSTA_1", situation.getId());
        assertEquals("new 29: 29", situation.getSummary());
        assertEquals("[Low] : 29 updated", situation.getDescription());
        assertEquals("", situation.getReason());
        assertEquals(1424788240588l, situation.getCreationTime());
        assertEquals("", situation.getSeverity());
        assertEquals("PSTA", situation.getAllAffects()[0].getAgencyId());
        assertEquals("", situation.getAllAffects()[0].getApplicationId());
        assertEquals("", situation.getAllAffects()[0].getDirectionId());
        assertEquals("", situation.getAllAffects()[0].getRouteId());
        assertEquals("", situation.getAllAffects()[0].getStopId());
        assertEquals("", situation.getAllAffects()[0].getTripId());
        assertNull(situation.getUrl());

        // Check active windows
        ObaSituation.ActiveWindow[] windows = situation.getActiveWindows();
        assertEquals(0, windows.length);

        // No active window is included, so this should return true to assume the alert is active
        boolean result = UIUtils.isActiveWindowForSituation(situation, response.getCurrentTime());
        assertEquals(true, result);

        // TODO - we need valid test responses that include the below situation data
        //ObaSituation.Affects affects = situation.getAffects();
        //assertNotNull(affects);
        //List<String> affectedStops = affects.getStopIds();
        //assertNotNull(affectedStops);
        //assertEquals(1, affectedStops.size());
        //assertEquals("1_75403", affectedStops.get(0));
        //ObaSituation.VehicleJourney[] journeys = affects.getVehicleJourneys();
        //assertNotNull(journeys);
        //ObaSituation.VehicleJourney journey = journeys[0];
        //assertNotNull("1", journey.getDirection());
        //assertNotNull("1_30", journey.getRouteId());
        //List<String> journeyStops = journey.getStopIds();
        //assertNotNull(journeyStops);
        //assertTrue(journeyStops.size() > 0);
        //assertEquals("1_9980", journeyStops.get(0));

        //ObaSituation.Consequence[] consequences = situation.getConsequences();
        //assertNotNull(consequences);
        //assertEquals(1, consequences.length);
        //assertEquals("diversion", consequences[0].getCondition());
    }

    /**
     * Test stop-specific service alerts
     */
    @Test
    public void testStopSituationDart() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("dart.onebusaway.org/api");
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "DART_4041").build().call();
        assertOK(response);
        List<ObaSituation> situations = response.getSituations();
        assertNotNull(situations);

        ObaSituation situation = situations.get(0);
        assertEquals("DART_54", situation.getId());
        assertEquals("No DART bus service on Saturday, July 4, 2015", situation.getSummary());
        assertEquals(
                "DART will not operate bus service on Saturday, July 4, 2015 in observation of Independence Day.\n\nDART will operate Saturday service on Friday, July 3, 2015 for the day before the holiday. Hours for the customer service window and phones are 8 a.m. to 4 p.m. and Administrative offices are also closed on Friday, July 3, 2015.\n\nThe following routes will not operate on Friday, July 3, 2015:\n\n· Local Routes 5, 8, 11, 13, 51\n\n· Express Routes\n\n· Flex Routes 73, 74\n\n· D-Line Downtown Shuttle\n\n· The LINK Shuttle\n\n· On Call Services\n\nFor more information, contact DART Customer Service at 515-283-8100 or by email at dart@ridedart.com..",
                situation.getDescription());
        assertEquals("", situation.getReason());
        assertEquals(1436072383004L, situation.getCreationTime());
        assertEquals("", situation.getSeverity());
        assertEquals("DART", situation.getAllAffects()[0].getAgencyId());
        assertEquals("", situation.getAllAffects()[0].getApplicationId());
        assertEquals("", situation.getAllAffects()[0].getDirectionId());
        assertEquals("", situation.getAllAffects()[0].getRouteId());
        assertEquals("", situation.getAllAffects()[0].getStopId());
        assertEquals("", situation.getAllAffects()[0].getTripId());
        assertNull(situation.getUrl());

        // Check active windows
        ObaSituation.ActiveWindow[] windows = situation.getActiveWindows();
        assertEquals(1435005045, windows[0].getFrom());
        assertEquals(1436072372, windows[0].getTo());

        assertEquals(1436072374, windows[1].getFrom());
        assertEquals(1436073000, windows[1].getTo());

        long timeBeforeWindow0 = 0;
        boolean result = UIUtils.isActiveWindowForSituation(situation, timeBeforeWindow0);
        assertEquals(false, result);

        long timeWithinWindow0 = 1435005046000L;
        result = UIUtils.isActiveWindowForSituation(situation, timeWithinWindow0);
        assertEquals(true, result);

        long timeAfterWindow0 = 1436072373000L;
        result = UIUtils.isActiveWindowForSituation(situation, timeAfterWindow0);
        assertEquals(false, result);

        long timeBeforeWindow1 = 1436072373000L;
        result = UIUtils.isActiveWindowForSituation(situation, timeBeforeWindow1);
        assertEquals(false, result);

        long timeWithinWindow1 = 1436072375000L;
        result = UIUtils.isActiveWindowForSituation(situation, timeWithinWindow1);
        assertEquals(true, result);

        long timeAfterWindow1 = 1436073001000L;
        result = UIUtils.isActiveWindowForSituation(situation, timeAfterWindow1);
        assertEquals(false, result);
    }

    /**
     * Test route-specific service alerts
     *
     * @throws Exception
     */
    @Test
    public void testRouteSituationSdmts() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("sdmts.onebusway.org/api");
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "MTS_11670").build().call();
        assertOK(response);
        List<ObaSituation> situations = response.getSituations();
        assertNotNull(situations);
        // Route-specific situations don't appear in the main situations element - see #700
        assertEquals(0, situations.size());

        // They do appear, however, in the references list and are referenced by each arrival info
        // Scan through the arrivals and make sure we can pull a ObaSituation for all situationIds
        ObaArrivalInfo[] info = response.getArrivalInfo();
        for (ObaArrivalInfo i : info) {
            for (String situationId : i.getSituationIds()) {
                assertNotNull(response.getSituation(situationId));
            }
        }

        // Test pulling fields for a route-specific situation
        String[] ids = info[0].getSituationIds();
        ObaSituation situation = response.getSituation(ids[0]);

        assertEquals("MTS_38", situation.getId());
        assertEquals("Concrete Pour Impacting N/B RTS 11, 901 & 929", situation.getSummary());
        assertEquals(
                "Due to a three day concrete pour, northbound Park Blvd. will be closed from Imperial to 11th Ave. 9/21 - 9/23, from 5am - 4pm. Northbound routes 11, 901 and 929 will detour during construction hours. The northbound bus stop i.d 99010 on 11th at K (Library) will be temporarily discontinued. Connections can be made at 12th & Imperial.",
                situation.getDescription());
        assertEquals("CONSTRUCTION", situation.getReason());
        assertEquals(1474527588415L, situation.getCreationTime());
        assertEquals("", situation.getSeverity());

        assertEquals("", situation.getAllAffects()[0].getAgencyId());
        assertEquals("", situation.getAllAffects()[0].getApplicationId());
        assertEquals("", situation.getAllAffects()[0].getDirectionId());
        assertEquals("MTS_11", situation.getAllAffects()[0].getRouteId());
        assertEquals("", situation.getAllAffects()[0].getStopId());
        assertEquals("", situation.getAllAffects()[0].getTripId());

        ObaSituation.ActiveWindow[] windows = situation.getActiveWindows();
        assertEquals(1474434000, windows[0].getFrom());
        assertEquals(1474646400, windows[0].getTo());

        ObaSituation.Consequence[] consequences = situation.getConsequences();
        assertNotNull(consequences);
        assertEquals(1, consequences.length);
        ObaSituation.Consequence c = consequences[0];
        assertEquals(ObaSituation.Consequence.CONDITION_DETOUR,
                c.getCondition());
        ObaSituation.ConditionDetails details = c.getDetails();
        assertNull(details);
        assertEquals("http://www.sdmts.com/Planning/alerts_detours.asp", situation.getUrl());
    }

    /**
     * Test an alert with a beginning time but no end time
     *
     */
    @Test
    public void testSituationNoEndTimeSdmts() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("sdmts.onebusway.org/api");
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "MTS_9999").build().call();
        assertOK(response);
        List<ObaSituation> situations = response.getSituations();
        assertNotNull(situations);

        ObaSituation situation = situations.get(0);
        assertEquals("MTS_RTA:11955571", situation.getId());
        assertEquals("Bus Real Time System Outage", situation.getSummary());
        assertEquals(
                "Due to a communications outage, all bus real time information is unavailable. We apologize for the inconvenience and are working to resolve the issue as soon as possible.",
                situation.getDescription());
        assertEquals(1561496718329L, situation.getCreationTime());
        assertEquals("MTS", situation.getAllAffects()[0].getAgencyId());
        assertEquals("", situation.getAllAffects()[0].getApplicationId());
        assertEquals("", situation.getAllAffects()[0].getDirectionId());
        assertEquals("", situation.getAllAffects()[0].getRouteId());
        assertEquals("", situation.getAllAffects()[0].getStopId());
        assertEquals("", situation.getAllAffects()[0].getTripId());
        assertNull(situation.getUrl());

        // Check active window - there is no end time for this situation in the response
        ObaSituation.ActiveWindow[] windows = situation.getActiveWindows();
        assertEquals(1561491840, windows[0].getFrom());
        assertEquals(0, windows[0].getTo());

        long timeBeforeWindow0 = 0;
        boolean result = UIUtils.isActiveWindowForSituation(situation, timeBeforeWindow0);
        assertEquals(false, result);

        long timeWithinWindow0 = 1561494000000L;
        result = UIUtils.isActiveWindowForSituation(situation, timeWithinWindow0);
        assertEquals(true, result);
    }

    // TODO: get/create situation response that includes diversion path
    /*
    public void testTripSituation() throws Exception {
        ObaArrivalInfoResponse response =
                readResourceAs(Resources.getTestUri("arrivals_and_departures_for_stop_1_10020"),
                        ObaArrivalInfoResponse.class);
        assertOK(response);

        ObaArrivalInfo[] infoList = response.getArrivalInfo();
        assertNotNull(infoList);
        assertEquals(2, infoList.length);
        ObaArrivalInfo info = infoList[0];
        String[] situationIds = info.getSituationIds();
        assertNotNull(situationIds);
        List<ObaSituation> situations = response.getAllSituations(situationIds);
        assertNotNull(situations);

        assertEquals("Expected failure / TODO: add situation", 1, situations.size());
        ObaSituation situation = situations.get(0);
        assertEquals("Snow Reroute", situation.getSummary());
        assertEquals("heavySnowFall", situation.getReason());
        //assertEquals(ObaSituation.REASON_TYPE_ENVIRONMENT,
        //        situation.getReasonType());

        //ObaSituation.Affects affects = situation.getAffects();
        //assertNotNull(affects);

        ObaSituation.Consequence[] consequences = situation.getConsequences();
        assertNotNull(consequences);
        assertEquals(1, consequences.length);
        ObaSituation.Consequence c = consequences[0];
        assertEquals(ObaSituation.Consequence.CONDITION_DIVERSION,
                c.getCondition());
        ObaSituation.ConditionDetails details = c.getDetails();
        assertNotNull(details);
        List<String> stopIds = details.getDiversionStopIds();
        assertNotNull(stopIds);
        assertTrue(stopIds.size() > 0);
        assertEquals("1_9972", stopIds.get(0));
        ObaShape diversion = details.getDiversionPath();
        assertNotNull(diversion);
    }
    */

    /**
     * Test occupancy being parsed from a server reponse
     */
    @Test
    public void testOccupancy() {
        ObaRegion ps = MockRegion.getPugetSound(getTargetContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);

        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(), "1_10020_occupancy").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("1_10020", stop.getId());
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("1", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);

        final List<ObaStop> nearbyStops = response.getNearbyStops();
        assertTrue(nearbyStops.size() > 0);

        // EMPTY
        ObaArrivalInfo info = arrivals[0];
        assertEquals(Occupancy.EMPTY, info.getHistoricalOccupancy());
        assertEquals(Occupancy.EMPTY, info.getPredictedOccupancy());
        assertEquals(Occupancy.EMPTY, info.getTripStatus().getRealtimeOccupancy());

        // MANY_SEATS_AVAILABLE
        info = arrivals[1];
        assertEquals(Occupancy.MANY_SEATS_AVAILABLE, info.getHistoricalOccupancy());
        assertEquals(Occupancy.MANY_SEATS_AVAILABLE, info.getPredictedOccupancy());
        assertEquals(Occupancy.MANY_SEATS_AVAILABLE, info.getTripStatus().getRealtimeOccupancy());

        // FEW_SEATS_AVAILABLE
        info = arrivals[2];
        assertEquals(Occupancy.FEW_SEATS_AVAILABLE, info.getHistoricalOccupancy());
        assertEquals(Occupancy.FEW_SEATS_AVAILABLE, info.getPredictedOccupancy());
        assertEquals(Occupancy.FEW_SEATS_AVAILABLE, info.getTripStatus().getRealtimeOccupancy());

        // STANDING_ROOM_ONLY
        info = arrivals[3];
        assertEquals(Occupancy.STANDING_ROOM_ONLY, info.getHistoricalOccupancy());
        assertEquals(Occupancy.STANDING_ROOM_ONLY, info.getPredictedOccupancy());
        assertEquals(Occupancy.STANDING_ROOM_ONLY, info.getTripStatus().getRealtimeOccupancy());

        // CRUSHED_STANDING_ROOM_ONLY
        info = arrivals[4];
        assertEquals(Occupancy.CRUSHED_STANDING_ROOM_ONLY, info.getHistoricalOccupancy());
        assertEquals(Occupancy.CRUSHED_STANDING_ROOM_ONLY, info.getPredictedOccupancy());
        assertEquals(Occupancy.CRUSHED_STANDING_ROOM_ONLY, info.getTripStatus().getRealtimeOccupancy());

        // FULL
        info = arrivals[5];
        assertEquals(Occupancy.FULL, info.getHistoricalOccupancy());
        assertEquals(Occupancy.FULL, info.getPredictedOccupancy());
        assertEquals(Occupancy.FULL, info.getTripStatus().getRealtimeOccupancy());

        // NOT_ACCEPTING_PASSENGERS
        info = arrivals[6];
        assertEquals(Occupancy.NOT_ACCEPTING_PASSENGERS, info.getHistoricalOccupancy());
        assertEquals(Occupancy.NOT_ACCEPTING_PASSENGERS, info.getPredictedOccupancy());
        assertEquals(Occupancy.NOT_ACCEPTING_PASSENGERS, info.getTripStatus().getRealtimeOccupancy());

        // Empty string
        info = arrivals[7];
        assertNull(info.getHistoricalOccupancy());
        assertNull(info.getPredictedOccupancy());
        assertNull(info.getTripStatus().getRealtimeOccupancy());

        // Missing field
        info = arrivals[8];
        assertNull(info.getHistoricalOccupancy());
        assertNull(info.getPredictedOccupancy());
        assertNull(info.getTripStatus().getRealtimeOccupancy());
    }

    /**
     * Test canceled trips
     */
    @Test
    public void testCanceledTrips() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
                        "Hillsborough Area Regional Transit_9999").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());
        ObaTrip trip = response.getTrip("Hillsborough Area Regional Transit_909841");
        assertEquals("Hillsborough Area Regional Transit_266684", trip.getBlockId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);
        assertEquals(27.982215585882088, arrivals[0].getTripStatus().getPosition().getLatitude());
        assertEquals(-82.4224, arrivals[0].getTripStatus().getPosition().getLongitude());
        assertEquals(Status.CANCELED, arrivals[0].getTripStatus().getStatus());
    }
}
