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

import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.request.ObaRoutesForLocationRequest;
import org.onebusaway.android.io.request.ObaRoutesForLocationResponse;
import org.onebusaway.android.util.LocationUtils;

import android.location.Location;


public class RoutesForLocationTest extends ObaTestCase {

    public void testDowntownSeattle1() {
        final Location pt = LocationUtils.makeLocation(47.610980, -122.33845);

        ObaRoutesForLocationRequest.Builder builder =
                new ObaRoutesForLocationRequest.Builder(getContext(), pt);
        ObaRoutesForLocationRequest request = builder.build();
        ObaRoutesForLocationResponse response = request.call();
        assertOK(response);

        final ObaRoute[] list = response.getRoutesForLocation();
        assertTrue(list.length > 0);
        assertTrue(response.getLimitExceeded());

        final ObaRoute first = list[0];
        // This may not always be true, but it is now.
        assertEquals(ObaRoute.TYPE_BUS, first.getType());
        final ObaAgency agency = response.getAgency(first.getAgencyId());
        assertEquals("1", agency.getId());
    }

    public void testQuery() {
        final Location pt = LocationUtils.makeLocation(47.25331, -122.44040);

        ObaRoutesForLocationResponse response =
                new ObaRoutesForLocationRequest.Builder(getContext(), pt)
                        .setQuery("11")
                        .build()
                        .call();
        assertOK(response);
        final ObaRoute[] list = response.getRoutesForLocation();
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
        final Location pt = LocationUtils.makeLocation(47.25331, -122.44040);

        ObaRoutesForLocationResponse response =
                new ObaRoutesForLocationRequest.Builder(getContext(), pt)
                        .setQuery("112423")
                        .build()
                        .call();
        assertOK(response);
        final ObaRoute[] list = response.getRoutesForLocation();
        assertEquals(0, list.length);
        assertFalse(response.getLimitExceeded());
        assertFalse(response.getOutOfRange());
    }

    public void testOutOfRange() {
        // This is just to make sure we copy and call newRequest() at least once
        final Location pt = LocationUtils.makeLocation(48.85808, 2.29498);

        ObaRoutesForLocationRequest request =
                new ObaRoutesForLocationRequest.Builder(getContext(), pt).build();
        ObaRoutesForLocationResponse response = request.call();
        assertOK(response);
        assertTrue(response.getOutOfRange());
    }

    // TODO: Span & radius
}
