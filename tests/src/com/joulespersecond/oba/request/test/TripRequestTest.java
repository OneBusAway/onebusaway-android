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

import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.request.ObaTripRequest;
import com.joulespersecond.oba.request.ObaTripResponse;

public class TripRequestTest extends ObaTestCase {

    public void testKCMTrip() {
        ObaTripRequest.Builder builder = new ObaTripRequest.Builder(getContext(), "1_15551350");
        ObaTripRequest request = builder.build();
        ObaTripResponse response = request.call();
        assertOK(response);
        assertEquals(response.getId(), "1_15551350");
        assertEquals(response.getRouteId(), "1_65");

        final ObaRoute route = response.getRoute();
        assertNotNull(route);
        assertEquals(route.getId(), "1_65");
        assertEquals(route.getAgencyId(), "1");
    }

    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaTripRequest request = ObaTripRequest.newRequest(getContext(), "1_15551350");
        assertNotNull(request);
    }
}
