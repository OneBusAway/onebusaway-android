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

import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaAgencyWithCoverage;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageRequest;
import org.onebusaway.android.io.request.ObaAgenciesWithCoverageResponse;


public class AgenciesWithCoverageTest extends ObaTestCase {

    public void testRequest() {
        ObaAgenciesWithCoverageRequest request =
                ObaAgenciesWithCoverageRequest.newRequest(getContext());
        ObaAgenciesWithCoverageResponse response = request.call();
        assertOK(response);
        final ObaAgencyWithCoverage[] list = response.getAgencies();
        assertTrue(list.length > 0);
        for (ObaAgencyWithCoverage agency : list) {
            final ObaAgency a = response.getAgency(agency.getId());
            assertNotNull(a);
        }
    }

    public void testBuilder() {
        ObaAgenciesWithCoverageRequest.Builder builder =
                new ObaAgenciesWithCoverageRequest.Builder(getContext());
        ObaAgenciesWithCoverageRequest request = builder.build();
        assertNotNull(request);
    }
}
