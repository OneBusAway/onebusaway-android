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
import java.util.Arrays;
import java.util.List;

import android.test.MoreAsserts;

import com.joulespersecond.oba.elements.ObaRouteElement;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaStopRequest;
import com.joulespersecond.oba.request.ObaStopResponse;

public class StopRequestTest extends ObaTestCase {

    public void testKCMStop() throws IOException {
        ObaStopRequest.Builder builder = new ObaStopRequest.Builder(getContext(), "1_29261");
        ObaStopRequest request = builder.build();
        ObaStopResponse response = request.call();
        assertOK(response);
        assertEquals(response.getId(), "1_29261");
        assertEquals(response.getStopCode(), "29261");
        assertEquals(response.getLocationType(), ObaStop.LOCATION_STOP);
        final String[] routeIds = response.getRouteIds();
        assertNotNull(routeIds);
        MoreAsserts.assertContentsInAnyOrder(Arrays.asList(routeIds), "1_8", "1_10", "1_43");

        final List<ObaRouteElement> routes = response.getRoutes();
        assertNotNull(routes);
        assertEquals(routes.size(), routeIds.length);
    }

}
