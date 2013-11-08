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

import java.util.List;

import com.joulespersecond.oba.elements.ObaShape;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.elements.ObaStopGrouping;
import com.joulespersecond.oba.request.ObaStopsForRouteRequest;
import com.joulespersecond.oba.request.ObaStopsForRouteResponse;

public class StopsForRouteRequestTest extends ObaTestCase {

    public void testKCMRoute() {
        ObaStopsForRouteRequest.Builder builder =
                new ObaStopsForRouteRequest.Builder(getContext(), "1_44");
        ObaStopsForRouteRequest request = builder.build();
        ObaStopsForRouteResponse response = request.call();
        assertOK(response);
        final List<ObaStop> stops = response.getStops();
        assertNotNull(stops);
        assertTrue(stops.size() > 0);
        // Check one
        final ObaStop stop = stops.get(0);
        assertEquals("1_10911", stop.getId());
        final ObaStopGrouping[] groupings = response.getStopGroupings();
        assertNotNull(groupings);

        final ObaShape[] shapes = response.getShapes();
        assertNotNull(shapes);
        assertTrue(shapes.length > 0);
    }

    public void testNoShapes() {
        ObaStopsForRouteResponse response =
            new ObaStopsForRouteRequest.Builder(getContext(), "1_44")
                .setIncludeShapes(false)
                .build()
                .call();
        assertOK(response);
        final ObaShape[] shapes = response.getShapes();
        assertNotNull(shapes);
        assertEquals(0, shapes.length);
    }
}
