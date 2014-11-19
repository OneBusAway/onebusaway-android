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

import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaStopRequest;
import org.onebusaway.android.io.request.ObaStopResponse;

import android.test.MoreAsserts;

import java.util.Arrays;
import java.util.List;

public class StopRequestTest extends ObaTestCase {

    public void testKCMStop() {
        ObaStopRequest.Builder builder = new ObaStopRequest.Builder(getContext(), "1_29261");
        ObaStopRequest request = builder.build();
        ObaStopResponse response = request.call();
        assertOK(response);
        assertEquals("1_29261", response.getId());
        assertEquals("29261", response.getStopCode());
        assertEquals(ObaStop.LOCATION_STOP, response.getLocationType());
        final String[] routeIds = response.getRouteIds();
        assertNotNull(routeIds);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(routeIds), "1_8", "1_10", "1_43");

        final List<ObaRoute> routes = response.getRoutes();
        assertNotNull(routes);
        assertEquals(routes.size(), routeIds.length);
    }

    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaStopRequest request = ObaStopRequest.newRequest(getContext(), "1_29261");
        assertNotNull(request);
    }
}
