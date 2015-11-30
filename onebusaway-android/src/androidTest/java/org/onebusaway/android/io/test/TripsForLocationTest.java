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

import org.onebusaway.android.io.elements.ObaTripDetails;
import org.onebusaway.android.io.request.ObaTripsForLocationRequest;
import org.onebusaway.android.io.request.ObaTripsForLocationResponse;
import org.onebusaway.android.util.LocationUtils;

import android.location.Location;


public class TripsForLocationTest extends ObaTestCase {

    public void test1() {
        final Location pt = LocationUtils.makeLocation(47.653, -122.307);

        ObaTripsForLocationRequest.Builder builder =
                new ObaTripsForLocationRequest.Builder(getContext(), pt);
        ObaTripsForLocationRequest request = builder.build();
        ObaTripsForLocationResponse response = request.call();
        assertOK(response);

        // Unfortunately there really isn't much we can assume here...
        final ObaTripDetails[] list = response.getTrips();
        assertNotNull(list);
    }

    public void testOutOfRange() {
        // This is just to make sure we copy and call newRequest() at least once
        final Location pt = LocationUtils.makeLocation(48.85808, 2.29498);

        ObaTripsForLocationRequest request =
                new ObaTripsForLocationRequest.Builder(getContext(), pt).build();
        ObaTripsForLocationResponse response = request.call();
        assertOK(response);
        assertTrue(response.getOutOfRange());
    }
}
