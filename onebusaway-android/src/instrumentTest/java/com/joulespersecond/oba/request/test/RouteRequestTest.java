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

import android.graphics.Color;

import com.joulespersecond.oba.elements.ObaAgency;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.request.ObaRouteRequest;
import com.joulespersecond.oba.request.ObaRouteResponse;


public class RouteRequestTest extends ObaTestCase {
    public void testKCMRoute() {
        ObaRouteRequest.Builder builder = new ObaRouteRequest.Builder(getContext(), "1_10");
        ObaRouteRequest request = builder.build();
        ObaRouteResponse response = request.call();
        assertOK(response);
        assertEquals("1_10", response.getId());
        assertEquals("10", response.getShortName());
        assertEquals("1", response.getAgencyId());
        assertEquals(ObaRoute.TYPE_BUS, response.getType());
        assertEquals(Color.WHITE, response.getColor());
        assertEquals(Color.BLACK, response.getTextColor());

        ObaAgency agency = response.getAgency();
        assertNotNull(agency);
        assertEquals("1", agency.getId());
        assertEquals("Metro Transit", agency.getName());
    }

    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaRouteRequest request = ObaRouteRequest.newRequest(getContext(), "1_10");
        assertNotNull(request);
    }
}
