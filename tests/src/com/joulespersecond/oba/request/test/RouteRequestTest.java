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

import java.io.IOException;

import android.graphics.Color;

import com.joulespersecond.oba.elements.ObaAgency;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.request.ObaRouteRequest;
import com.joulespersecond.oba.request.ObaRouteResponse;


public class RouteRequestTest extends ObaTestCase {
    public void testKCMRoute() throws IOException {
        ObaRouteRequest.Builder builder = new ObaRouteRequest.Builder(getContext(), "1_10");
        ObaRouteRequest request = builder.build();
        ObaRouteResponse response = request.call();
        assertOK(response);
        assertEquals(response.getId(), "1_10");
        assertEquals(response.getShortName(), "10");
        assertEquals(response.getAgencyId(), "1");
        assertEquals(response.getType(), ObaRoute.TYPE_BUS);
        assertEquals(response.getColor(), Color.WHITE);
        assertEquals(response.getTextColor(), Color.BLACK);

        ObaAgency agency = response.getAgency();
        assertNotNull(agency);
        assertEquals(agency.getId(), "1");
        assertEquals(agency.getName(), "Metro Transit");
    }

    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaRouteRequest request = ObaRouteRequest.newRequest(getContext(), "1_10");
        assertNotNull(request);
    }
}
