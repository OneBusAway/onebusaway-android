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

import android.test.AndroidTestCase;

import com.joulespersecond.oba.ObaAgency;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.ObaResponse;
import com.joulespersecond.oba.ObaRoute;

class ObaTestCase extends AndroidTestCase {
    static final String PROD = "api.onebusaway.org";
    static final String SOAK = "soak-api.onebusaway.org";

    static void assertOK(ObaResponse response) {
        assertNotNull(response);
        assertEquals(response.getCode(), ObaApi.OBA_OK);
    }

    static void checkRoute(ObaRoute route,
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
