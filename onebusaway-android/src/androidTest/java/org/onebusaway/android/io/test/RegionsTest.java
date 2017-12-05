/*
 * Copyright (C) 2014-2017 Paul Watts,
 * University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.io.test;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.request.ObaRegionsRequest;
import org.onebusaway.android.io.request.ObaRegionsResponse;

/**
 * Tests Regions API requests and responses
 */
public class RegionsTest extends ObaTestCase {

    public void testRequest() {
        ObaRegionsRequest request =
                ObaRegionsRequest.newRequest(getContext());
        ObaRegionsResponse response = request.call();
        assertOK(response);
        final ObaRegion[] list = response.getRegions();
        assertTrue(list.length > 0);
        for (ObaRegion region : list) {
            assertNotNull(region.getName());
        }
    }

    public void testBuilder() {
        ObaRegionsRequest.Builder builder =
                new ObaRegionsRequest.Builder(getContext());
        ObaRegionsRequest request = builder.build();
        assertNotNull(request);
    }
}
