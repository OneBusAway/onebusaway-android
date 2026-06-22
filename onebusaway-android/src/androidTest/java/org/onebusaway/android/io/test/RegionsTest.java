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
import org.onebusaway.android.mock.Resources;
import org.onebusaway.android.provider.ObaContract;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
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

    @Test
    public void testUmamiAnalyticsParsing() throws Exception {
        ObaRegionsResponse response = Resources.readAs(getTargetContext(),
                Resources.getTestUri("regions_umami_test"), ObaRegionsResponse.class);
        ObaRegion[] regions = response.getRegions();

        ObaRegion withUmami = regions[0];
        assertEquals("https://umami.example.com", withUmami.getUmamiAnalyticsUrl());
        assertEquals("abc-123-uuid", withUmami.getUmamiAnalyticsId());

        ObaRegion withoutUmami = regions[1];
        assertNull(withoutUmami.getUmamiAnalyticsUrl());
        assertNull(withoutUmami.getUmamiAnalyticsId());
    }

    @Test
    public void testUmamiAnalyticsPersistenceRoundTrip() {
        ContentResolver cr = getTargetContext().getContentResolver();
        int id = 987654;

        ContentValues values = new ContentValues();
        values.put(ObaContract.Regions._ID, id);
        values.put(ObaContract.Regions.NAME, "Umami Persist Region");
        values.put(ObaContract.Regions.OBA_BASE_URL, "https://api.example.com/");
        values.put(ObaContract.Regions.SIRI_BASE_URL, "");
        values.put(ObaContract.Regions.LANGUAGE, "en_US");
        values.put(ObaContract.Regions.CONTACT_EMAIL, "test@example.com");
        values.put(ObaContract.Regions.SUPPORTS_OBA_DISCOVERY, 1);
        values.put(ObaContract.Regions.SUPPORTS_OBA_REALTIME, 1);
        values.put(ObaContract.Regions.SUPPORTS_SIRI_REALTIME, 0);
        values.put(ObaContract.Regions.UMAMI_ANALYTICS_URL, "https://umami.example.com");
        values.put(ObaContract.Regions.UMAMI_ANALYTICS_ID, "uuid-persist-1");
        ObaContract.Regions.insertOrUpdate(getTargetContext(), id, values);

        ObaRegion region = ObaContract.Regions.get(cr, id);
        assertNotNull(region);
        assertEquals("https://umami.example.com", region.getUmamiAnalyticsUrl());
        assertEquals("uuid-persist-1", region.getUmamiAnalyticsId());

        cr.delete(ObaContract.Regions.buildUri(id), null, null);
    }
}
