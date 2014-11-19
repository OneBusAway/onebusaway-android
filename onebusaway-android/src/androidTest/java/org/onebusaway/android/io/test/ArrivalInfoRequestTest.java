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
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaSituation;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.mock.MockRegion;

import java.util.HashMap;
import java.util.List;

@SuppressWarnings("serial")
public class ArrivalInfoRequestTest extends ObaTestCase {

    public void testKCMStopRequestUsingCustomUrl() {
        // Test by setting URL directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMStopRequest();
    }

    public void testKCMStopRequestUsingRegion() {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMStopRequest();
    }

    private void _assertKCMStopRequest() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getContext(), "1_29261");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_29261.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testHARTStopRequestUsingCustomUrl() {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        _assertHARTStopRequest();
    }

    public void testHARTStopRequestUsingRegion() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        _assertHARTStopRequest();
    }

    private void _assertHARTStopRequest() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_3105");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "http://api.tampa.onebusaway.org/api/api/where/arrivals-and-departures-for-stop/Hillsborough%20Area%20Regional%20Transit_3105.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    public void testKCMStopResponseUsingCustomUrl() throws Exception {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertKCMStopResponse();
    }

    public void testKCMStopResponseUsingRegion() throws Exception {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertKCMStopResponse();
    }

    private void _assertKCMStopResponse() {
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(), "1_29261").build().call();
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

    public void testHARTStopResponseUsingCustomUrl() throws Exception {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        _assertHARTStopResponse();
    }

    public void testHARTStopResponseUsingRegion() throws Exception {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        _assertHARTStopResponse();
    }

    private void _assertHARTStopResponse() {
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_3105").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_3105", stop.getId());
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        // Uhh, this will vary considerably depending on when this is run.
        assertNotNull(arrivals);

        final List<ObaStop> nearbyStops = response.getNearbyStops();
        assertTrue(nearbyStops.size() > 0);
    }

    public void testNewRequestUsingCustomUrl() throws Exception {
        // Test by setting API directly
        Application.get().setCustomApiUrl("api.pugetsound.onebusaway.org");
        _assertNewRequest();
    }

    public void testNewRequestUsingRegion() throws Exception {
        // Test by setting region
        ObaRegion ps = MockRegion.getPugetSound(getContext());
        assertNotNull(ps);
        Application.get().setCurrentRegion(ps);
        _assertNewRequest();
    }

    private void _assertNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaArrivalInfoRequest request = ObaArrivalInfoRequest.newRequest(getContext(), "1_10");
        assertNotNull(request);
        UriAssert.assertUriMatch(
                "http://api.pugetsound.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_10.json",
                new HashMap<String, String>() {{
                    put("key", "*");
                    put("version", "2");
                }},
                request
        );
    }

    // TODO: get/create situation response (not much of a test, otherwise)
    public void testStopSituation() throws Exception {
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(), "1_75403").build().call();
        assertOK(response);
        List<ObaSituation> situations = response.getSituations();
        assertNotNull(situations);

        // This is all test data, we really shouldn't depend on it.
        // This is why we need a way of inserting canned data for testing.
        //ObaSituation situation = situations.get(0);
        //assertEquals("Stop Moved", situation.getSummary());
        //assertEquals("", situation.getReason());
        //assertEquals(ObaSituation.REASON_TYPE_UNDEFINED, situation.getReasonType());

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

    // TODO: get/create situation response
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
        List<ObaSituation> situations = response.getSituations(situationIds);
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
}
