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

import org.junit.Test;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaAgencyWithCoverage;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageResponse;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Tests requests and parsing JSON responses from /res/raw for the OBA server API
 * to get transit agencies that are included in the OBA server
 */
public class AgenciesWithCoverageTest extends ObaTestCase {

    @Test
    public void testRequest() {
        ObaAgenciesWithCoverageRequest request =
                ObaAgenciesWithCoverageRequest.newRequest(getTargetContext());
        ObaAgenciesWithCoverageResponse response = request.call();
        assertOK(response);
        final ObaAgencyWithCoverage[] list = response.getAgencies();
        assertTrue(list.length > 0);
        for (ObaAgencyWithCoverage agency : list) {
            final ObaAgency a = response.getAgency(agency.getId());
            assertNotNull(a);
        }
    }

    @Test
    public void testBuilder() {
        ObaAgenciesWithCoverageRequest.Builder builder =
                new ObaAgenciesWithCoverageRequest.Builder(getTargetContext());
        ObaAgenciesWithCoverageRequest request = builder.build();
        assertNotNull(request);
    }
}
