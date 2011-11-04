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

import com.joulespersecond.oba.elements.ObaAgency;
import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.elements.ObaSituation;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaArrivalInfoRequest;
import com.joulespersecond.oba.request.ObaArrivalInfoResponse;
import com.joulespersecond.seattlebusbot.test.UriAssert;

import java.util.HashMap;
import java.util.List;

@SuppressWarnings("serial")
public class ArrivalInfoRequestTest extends ObaTestCase {
    public void testKCMStopRequest() {
        ObaArrivalInfoRequest.Builder builder =
                new ObaArrivalInfoRequest.Builder(getContext(), "1_29261");
        ObaArrivalInfoRequest request = builder.build();
        UriAssert.assertUriMatch(
                "http://api.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_29261.json",
                new HashMap<String, String>() {{ put("key", "*"); put("version", "2"); }},
                request);
    }

    public void testKCMStopResponse() throws Exception {
        ObaArrivalInfoResponse response =
                getRawResourceAs("arrivals_and_departures_for_stop_1_29261", ObaArrivalInfoResponse.class);
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

    public void testNewRequest() throws Exception {
        // This is just to make sure we copy and call newRequest() at least once
        ObaArrivalInfoRequest request = ObaArrivalInfoRequest.newRequest(getContext(), "1_10");
        assertNotNull(request);
        UriAssert.assertUriMatch(
                "http://api.onebusaway.org/api/where/arrivals-and-departures-for-stop/1_10.json",
                new HashMap<String, String>() {{ put("key", "*"); put("version", "2"); }},
                request);
    }

    // TODO: get/create situation response (not much of a test, otherwise)
    public void testStopSituation() throws Exception {
        ObaArrivalInfoResponse response = getRawResourceAs("arrivals_and_departures_for_stop_1_75403", ObaArrivalInfoResponse.class);
        assertOK(response);
        List<ObaSituation> situations = response.getSituations();
        assertNotNull(situations);

        assertTrue("Expected failure / TODO: add situation", situations.size() > 0);
        ObaSituation situation = situations.get(0);
        assertEquals("Stop Moved", situation.getSummary());
        assertEquals("", situation.getReason());
        assertEquals(ObaSituation.REASON_TYPE_UNDEFINED, situation.getReasonType());

        ObaSituation.Affects affects = situation.getAffects();
        assertNotNull(affects);
        List<String> affectedStops = affects.getStopIds();
        assertNotNull(affectedStops);
        assertEquals(1, affectedStops.size());
        assertEquals("1_75403", affectedStops.get(0));

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
    public void testTripSituation() throws Exception {
        ObaArrivalInfoResponse response = getRawResourceAs("arrivals_and_departures_for_stop_1_10020", ObaArrivalInfoResponse.class);
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
        assertEquals(ObaSituation.REASON_TYPE_ENVIRONMENT,
                situation.getReasonType());

        ObaSituation.Affects affects = situation.getAffects();
        assertNotNull(affects);
        ObaSituation.VehicleJourney[] journeys = affects.getVehicleJourneys();
        assertNotNull(journeys);
        ObaSituation.VehicleJourney journey = journeys[0];
        assertNotNull("1", journey.getDirection());
        assertNotNull("1_30", journey.getRouteId());
        List<String> journeyStops = journey.getStopIds();
        assertNotNull(journeyStops);
        assertTrue(journeyStops.size() > 0);
        assertEquals("1_9980", journeyStops.get(0));

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
}
