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

import org.junit.Test;
import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.request.ObaRegionsRequest;
import org.onebusaway.android.io.request.ObaRegionsResponse;

import android.content.ContentResolver;
import android.net.Uri;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Tests requests and parsing JSON responses from /res/raw for the OBA Regions API
 * to get available regions
 */
public class RegionsTest extends ObaTestCase {

    @Test
    public void testRequest() {
        final Uri.Builder builder = new Uri.Builder();
        builder.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE);
        builder.authority(getTargetContext().getPackageName());
        builder.path(String.valueOf(R.raw.regions_v3));

        ObaRegionsRequest request =
                ObaRegionsRequest.newRequest(getTargetContext(), builder.build());
        ObaRegionsResponse response = request.call();
        assertOK(response);
        final ObaRegion[] list = response.getRegions();
        assertTrue(list.length > 0);
        for (ObaRegion region : list) {
            assertNotNull(region.getName());
        }
    }

    @Test
    public void testBuilder() {
        ObaRegionsRequest.Builder builder =
                new ObaRegionsRequest.Builder(getTargetContext());
        ObaRegionsRequest request = builder.build();
        assertNotNull(request);
    }
}
