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
package com.joulespersecond.oba.test;

import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;

public class StopByIdTest extends ObaTestCase {
    public void testV1() {
        ObaApi.setVersion(ObaApi.VERSION1);
        doTest1(ObaApi.getStopById(getContext(), "1_29261"));
    }

    public void testV2() {
        ObaApi.setVersion(ObaApi.VERSION2);
        doTest1(ObaApi.getStopById(getContext(), "1_29261"));
    }

    private void doTest1(ObaResponse response) {
        assertOK(response);

        ObaData data = response.getData();
        assertNotNull(data);
        ObaStop stop = data.getAsStop();
        assertNotNull(stop);
        assertEquals(stop.getId(), "1_29261");
        assertEquals(stop.getDirection(), "W");

        ObaArray<ObaRoute> routes = stop.getRoutes();
        assertNotNull(routes);
        assertTrue(routes.length() > 2);
        // TODO: Much of this is dependent on the *order* in which objects are returned,
        // in addition to the transit data itself, both of which are not guaranteed.
        // We should something better like write a function assertSet()
        ObaTestCase.checkRoute(routes.get(0), "1_8",  "8",  "1", "Metro Transit");
        ObaTestCase.checkRoute(routes.get(1), "1_10", "10", "1", "Metro Transit");
        ObaTestCase.checkRoute(routes.get(2), "1_43", "43", "1", "Metro Transit");
    }
}
