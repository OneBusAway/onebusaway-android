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

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaAgency;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaStopsForLocationRequest;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;


public class StopsForLocationTest extends ObaTestCase {
    public void testDowntownSeattle1() {
        final GeoPoint pt = ObaApi.makeGeoPoint(47.610980, -122.33845);

        ObaStopsForLocationRequest.Builder builder =
                new ObaStopsForLocationRequest.Builder(getContext(), pt);
        ObaStopsForLocationRequest request = builder.build();
        ObaStopsForLocationResponse response = request.call();
        assertOK(response);

        final ObaStop[] list = response.getStops();
        assertTrue(list.length > 0);
        //assertFalse(response.getLimitExceeded());

        final ObaStop first = list[0];
        // This may not always be true, but it is now.
        assertEquals("1_1030", first.getId());
        final ObaRoute route = response.getRoute(first.getRouteIds()[0]);
        assertEquals("1_25", route.getId());
        final ObaAgency agency = response.getAgency(route.getAgencyId());
        assertEquals("1", agency.getId());
        assertEquals("Metro Transit", agency.getName());
    }

    public void testQuery() {
        final GeoPoint pt = ObaApi.makeGeoPoint(47.25331, -122.44040);

        ObaStopsForLocationResponse response =
            new ObaStopsForLocationRequest.Builder(getContext(), pt)
                .setQuery("26")
                .build()
                .call();
        assertOK(response);
        final ObaStop[] list = response.getStops();
        assertTrue(list.length > 0);
        assertFalse(response.getLimitExceeded());
        assertFalse(response.getOutOfRange());

        final ObaStop first = list[0];
        // This may not always be true, but it is now.
        assertEquals("3_26", first.getId());
        final ObaRoute route = response.getRoute(first.getRouteIds()[0]);
        assertEquals("3", route.getAgencyId());
    }

    public void testQueryFail() {
        final GeoPoint pt = ObaApi.makeGeoPoint(47.25331, -122.44040);

        ObaStopsForLocationResponse response =
            new ObaStopsForLocationRequest.Builder(getContext(), pt)
                .setQuery("112423")
                .build()
                .call();
        assertOK(response);
        final ObaStop[] list = response.getStops();
        assertEquals(0, list.length);
        assertFalse(response.getLimitExceeded());
        assertFalse(response.getOutOfRange());
    }

    public void testOutOfRange() {
        // This is just to make sure we copy and call newRequest() at least once
        final GeoPoint pt = ObaApi.makeGeoPoint(48.85808, 2.29498);

        ObaStopsForLocationRequest request =
                new ObaStopsForLocationRequest.Builder(getContext(), pt).build();
        ObaStopsForLocationResponse response = request.call();
        assertOK(response);
        assertTrue(response.getOutOfRange());
    }

    // TODO: Span & radius
}
