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

package org.onebusaway.android.api.test;

import org.junit.Test;
import org.onebusaway.android.region.Region;
import org.onebusaway.android.provider.ObaContract;

import android.content.ContentResolver;
import android.content.ContentValues;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * Instrumented coverage for region ContentProvider persistence. The wire parsing of the `regions`
 * endpoint moved to the JVM RegionsDecodeTest when the endpoint migrated to the Retrofit client;
 * what remains here is the DB round-trip, which genuinely needs the provider.
 */
public class RegionsTest extends ObaTestCase {

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

        Region region = ObaContract.Regions.get(cr, id);
        assertNotNull(region);
        assertEquals("https://umami.example.com", region.getUmamiAnalyticsUrl());
        assertEquals("uuid-persist-1", region.getUmamiAnalyticsId());

        cr.delete(ObaContract.Regions.buildUri(id), null, null);
    }
}
