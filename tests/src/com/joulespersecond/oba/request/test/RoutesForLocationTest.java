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
import com.joulespersecond.oba.request.ObaRoutesForLocationRequest;
import com.joulespersecond.oba.request.ObaRoutesForLocationResponse;


public class RoutesForLocationTest extends ObaTestCase {
    public void testDowntownSeattle1() {
        final GeoPoint pt = ObaApi.makeGeoPoint(47.610980, -122.33845);

        ObaRoutesForLocationRequest.Builder builder =
                new ObaRoutesForLocationRequest.Builder(getContext(), pt);
        ObaRoutesForLocationRequest request = builder.build();
        ObaRoutesForLocationResponse response = request.call();
        assertOK(response);

        final ObaRoute[] list = response.getRoutes();
        assertTrue(list.length > 0);
        assertTrue(response.getLimitExceeded());

        final ObaRoute first = list[0];
        // This may not always be true, but it is now.
        assertEquals(ObaRoute.TYPE_BUS, first.getType());
        final ObaAgency agency = response.getAgency(first.getAgencyId());
        assertEquals("1", agency.getId());
    }

    public void testQuery() {
        final GeoPoint pt = ObaApi.makeGeoPoint(47.25331, -122.44040);

        ObaRoutesForLocationResponse response =
            new ObaRoutesForLocationRequest.Builder(getContext(), pt)
                .setQuery("11")
                .build()
                .call();
        assertOK(response);
        final ObaRoute[] list = response.getRoutes();
        assertTrue(list.length > 0);
        assertFalse(response.getLimitExceeded());
        assertFalse(response.getOutOfRange());

        final ObaRoute first = list[0];
        // This may not always be true, but it is now.
        assertEquals(first.getType(), ObaRoute.TYPE_BUS);
        final ObaAgency agency = response.getAgency(first.getAgencyId());
        assertEquals("3", agency.getId());
    }

    public void testQueryFail() {
        final GeoPoint pt = ObaApi.makeGeoPoint(47.25331, -122.44040);

        ObaRoutesForLocationResponse response =
            new ObaRoutesForLocationRequest.Builder(getContext(), pt)
                .setQuery("112423")
                .build()
                .call();
        assertOK(response);
        final ObaRoute[] list = response.getRoutes();
        assertEquals(0, list.length);
        assertFalse(response.getLimitExceeded());
        assertFalse(response.getOutOfRange());
    }

    public void testOutOfRange() {
        // This is just to make sure we copy and call newRequest() at least once
        final GeoPoint pt = ObaApi.makeGeoPoint(48.85808, 2.29498);

        ObaRoutesForLocationRequest request =
                new ObaRoutesForLocationRequest.Builder(getContext(), pt).build();
        ObaRoutesForLocationResponse response = request.call();
        assertOK(response);
        assertTrue(response.getOutOfRange());
    }

    // TODO: Span & radius
}
