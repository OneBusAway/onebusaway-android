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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;

import com.joulespersecond.oba.ObaAgency;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaArray;
import com.joulespersecond.oba.ObaData;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;
import com.joulespersecond.oba.ObaStop;

public class StopByIdTest extends AndroidTestCase {
    protected void setUp() throws Exception {
        super.setUp();
        // We need to explicitly set our server to soak to test V2 APIs.
        SharedPreferences.Editor edit =
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        edit.putString("preferences_oba_api_servername", "soak-api.onebusaway.org");
        edit.commit();
    }


    public void testV1() {
        ObaApi.setVersion(ObaApi.VERSION1);
        doTest1(ObaApi.getStopById(getContext(), "1_29261"));
    }

    public void testV2() {
        ObaApi.setVersion(ObaApi.VERSION2);
        doTest1(ObaApi.getStopById(getContext(), "1_29261"));
    }

    private void doTest1(ObaResponse response) {
        assertNotNull(response);
        assertEquals(response.getCode(), ObaApi.OBA_OK);

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
        checkRoute(routes.get(0), "1_8",  "8",  "1", "Metro Transit");
        checkRoute(routes.get(1), "1_10", "10", "1", "Metro Transit");
        checkRoute(routes.get(2), "1_43", "43", "1", "Metro Transit");
    }

    private static void checkRoute(ObaRoute route,
            String id,
            String name,
            String agencyId,
            String agencyName) {
        assertEquals(route.getId(), id);
        assertEquals(route.getShortName(), name);
        ObaAgency agency = route.getAgency();
        assertNotNull(agency);
        assertEquals(agency.getId(), agencyId);
        assertEquals(agency.getName(), agencyName);
        assertEquals(route.getAgencyId(), agencyId);
        assertEquals(route.getAgencyName(), agencyName);
    }
}
