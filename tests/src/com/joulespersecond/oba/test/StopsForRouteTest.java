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
import com.joulespersecond.oba.ObaPolyline;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;
import com.joulespersecond.oba.ObaStopGroup;
import com.joulespersecond.oba.ObaStopGrouping;

public class StopsForRouteTest extends ObaTestCase {
    private int mStopCount = -1;

    public void testV1() {
        ObaApi.setVersion(ObaApi.VERSION1);
        doTest1(ObaApi.getStopsForRoute(getContext(), "1_43"));
    }

    public void testV2() {
        ObaApi.setVersion(ObaApi.VERSION2);
        doTest1(ObaApi.getStopsForRoute(getContext(), "1_43"));
    }

    public void testPolylines() {
        doTestPolylines(true);
    }

    public void testNoPolylines() {
        doTestPolylines(false);
    }

    public void doTestPolylines(boolean includePolys) {
        ObaApi.setVersion(ObaApi.VERSION2);
        ObaResponse response = ObaApi.getStopsForRoute(getContext(), "1_43", includePolys);
        assertOK(response);
        ObaData data = response.getData();
        assertNotNull(data);
        ObaArray<ObaPolyline> polylines = data.getPolylines();
        if (includePolys) {
            assert(polylines.length() > 0);
        }
        else {
            assertEquals(0, polylines.length());
        }


        ObaArray<ObaStopGrouping> groupings = data.getStopGroupings();
        assert(groupings.length() > 0);
        ObaArray<ObaStopGroup> groups = groupings.get(0).getStopGroups();
        assert(groups.length() > 0);
        ObaArray<ObaPolyline> polys2 = groups.get(0).getPolylines();
        if (includePolys) {
            assert(polys2.length() > 0);
        }
        else {
            assertEquals(0, polys2.length());
        }
    }

    private void doTest1(ObaResponse response) {
        assertOK(response);

        ObaData data = response.getData();
        assertNotNull(data);
        ObaArray<ObaStop> stops = data.getStops();
        assertNotNull(stops);
        if (mStopCount == -1) {
            mStopCount = stops.length();
        }
        else {
            assertEquals(stops.length(), mStopCount);
        }
        // Ensure the first stop has routes and the routes have an agency.
        assert(stops.length() > 0);
        ObaStop stop = stops.get(0);
        // Obviously all if this can change.
        assertEquals(stop.getId(), "1_1085");
        ObaArray<ObaRoute> routes = stop.getRoutes();
        assertNotNull(routes);
        assert(routes.length() > 0);
        checkRoute(routes.get(0), "1_10", "10", "1", "Metro Transit");
    }
}
