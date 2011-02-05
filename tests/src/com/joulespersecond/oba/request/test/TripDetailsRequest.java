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

import com.joulespersecond.oba.elements.ObaTrip;
import com.joulespersecond.oba.elements.ObaTripSchedule;
import com.joulespersecond.oba.elements.ObaTripStatus;
import com.joulespersecond.oba.request.ObaTripDetailsRequest;
import com.joulespersecond.oba.request.ObaTripDetailsResponse;

public class TripDetailsRequest extends ObaTestCase {

    public void testKCMTrip() {
        ObaTripDetailsRequest.Builder builder =
                new ObaTripDetailsRequest.Builder(getContext(), "1_15551350");
        ObaTripDetailsRequest request = builder.build();
        ObaTripDetailsResponse response = request.call();
        assertOK(response);
        assertEquals("1_15551350", response.getId());

        ObaTripSchedule schedule = response.getSchedule();
        assertNotNull(schedule);

        ObaTripStatus status = response.getStatus();
        assertNotNull(status);

        // Make sure the trip exists
        ObaTrip trip = response.getTrip(response.getId());
        assertNotNull(trip);
    }

    public void testNoTrips() {
        ObaTripDetailsResponse response =
            new ObaTripDetailsRequest.Builder(getContext(), "1_15551350")
                .setIncludeTrip(false)
                .build()
                .call();
        assertOK(response);
        assertEquals("1_15551350", response.getId());
        // Make sure the trip exists
        ObaTrip trip = response.getTrip(response.getId());
        assertNull(trip);
    }

    public void testNoSchedule() {
        ObaTripDetailsResponse response =
            new ObaTripDetailsRequest.Builder(getContext(), "1_15551350")
                .setIncludeSchedule(false)
                .build()
                .call();
        assertOK(response);
        assertEquals("1_15551350", response.getId());

        ObaTripSchedule schedule = response.getSchedule();
        assertNull(schedule);
    }

    public void testNoStatus() {
        ObaTripDetailsResponse response =
            new ObaTripDetailsRequest.Builder(getContext(), "1_15551350")
                .setIncludeStatus(false)
                .build()
                .call();
        assertOK(response);
        assertEquals("1_15551350", response.getId());

        ObaTripStatus status = response.getStatus();
        assertNull(status);
    }

    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaTripDetailsRequest request =
                ObaTripDetailsRequest.newRequest(getContext(), "1_15551350");
        assertNotNull(request);
    }
}
