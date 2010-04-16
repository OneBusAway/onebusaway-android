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

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaStop;

public class StopsByLocationTest extends ObaTestCase {
    private int mStopCount = -1;
    private static final GeoPoint CENTER = ObaApi.makeGeoPoint(47.653435, -122.305641);

    public void testV1() {
        ObaApi.setVersion(ObaApi.VERSION1);
        doTest1(ObaApi.getStopsByLocation(getContext(), CENTER, 0, 0, 0, null, 0));
    }

    public void testV2() {
        ObaApi.setVersion(ObaApi.VERSION2);
        doTest1(ObaApi.getStopsByLocation(getContext(), CENTER, 0, 0, 0, null, 0));
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
    }
}
