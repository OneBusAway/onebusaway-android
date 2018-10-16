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
import org.onebusaway.android.io.request.ObaAgencyRequest;
import org.onebusaway.android.io.request.ObaAgencyResponse;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * Tests requests and parsing JSON responses from /res/raw for the OBA server API
 * to get information about a specific transit agency
 */
public class AgencyRequestTest extends ObaTestCase {

    @Test
    public void testKCMAgency() {
        ObaAgencyRequest.Builder builder = new ObaAgencyRequest.Builder(getTargetContext(), "1");
        ObaAgencyRequest request = builder.build();
        ObaAgencyResponse response = request.call();
        assertOK(response);

        assertEquals("1", response.getId());
        assertEquals("Metro Transit", response.getName());
    }

    @Test
    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaAgencyRequest request = ObaAgencyRequest.newRequest(getTargetContext(), "1");
        assertNotNull(request);
    }
}
