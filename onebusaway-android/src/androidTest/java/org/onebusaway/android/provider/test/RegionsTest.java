/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), 
 * Microsoft Corporation
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
package org.onebusaway.android.provider.test;

import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.provider.ObaContract.RegionBounds;
import org.onebusaway.android.provider.ObaContract.Regions;
import org.onebusaway.android.provider.ObaProvider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

public class RegionsTest extends ProviderTestCase2<ObaProvider> {

    public RegionsTest() {
        super(ObaProvider.class, ObaContract.AUTHORITY);
    }

    public void testInsertOrUpdate() {
        ContentResolver cr = getMockContentResolver();
        ContentValues values = new ContentValues();
        values.put(Regions.NAME, "Test region");
        values.put(Regions.OBA_BASE_URL, "http://test.onebusaway.org/");
        values.put(Regions.SIRI_BASE_URL, "");
        values.put(Regions.LANGUAGE, "en_US");
        values.put(Regions.CONTACT_EMAIL, "contact@onebusaway.org");
        values.put(Regions.SUPPORTS_OBA_DISCOVERY, true);
        values.put(Regions.SUPPORTS_OBA_REALTIME, false);
        values.put(Regions.SUPPORTS_SIRI_REALTIME, false);
        values.put(Regions.SUPPORTS_EMBEDDED_SOCIAL, false);

        Uri uri1 = cr.insert(Regions.CONTENT_URI, values);

        final String[] PROJECTION = {
                ObaContract.Regions._ID,
                ObaContract.Regions.NAME,
                ObaContract.Regions.SUPPORTS_OBA_DISCOVERY,
                ObaContract.Regions.SUPPORTS_SIRI_REALTIME,
                ObaContract.Regions.SUPPORTS_EMBEDDED_SOCIAL
        };

        // Query
        Cursor c1 = cr.query(uri1, PROJECTION, null, null, null);
        assertNotNull(c1);
        assertEquals(1, c1.getCount());
        c1.moveToFirst();
        int id = c1.getInt(0);
        c1.close();

        values = new ContentValues();
        values.put(Regions.SUPPORTS_SIRI_REALTIME, 1);
        Uri uri2 = Regions.insertOrUpdate(cr, id, values);

        assertEquals(uri1, uri2);

        Cursor c2 = cr.query(uri2, PROJECTION, null, null, null);
        assertNotNull(c2);
        assertEquals(1, c2.getCount());
        c2.moveToFirst();
        assertEquals(id, c2.getInt(0));
        assertEquals("Test region", c2.getString(1));
        assertEquals(1, c2.getInt(2));
        assertEquals(1, c2.getInt(3));
        c2.close();

        // Delete this alert
        cr.delete(uri1, null, null);
    }

    public void testBounds() {
        ContentResolver cr = getMockContentResolver();
        ContentValues values = new ContentValues();
        values.put(Regions.NAME, "Test region");
        values.put(Regions.OBA_BASE_URL, "http://test.onebusaway.org/");
        values.put(Regions.SIRI_BASE_URL, "");
        values.put(Regions.LANGUAGE, "en_US");
        values.put(Regions.CONTACT_EMAIL, "contact@onebusaway.org");
        values.put(Regions.SUPPORTS_OBA_DISCOVERY, true);
        values.put(Regions.SUPPORTS_OBA_REALTIME, false);
        values.put(Regions.SUPPORTS_SIRI_REALTIME, false);
        values.put(Regions.SUPPORTS_EMBEDDED_SOCIAL, false);

        Uri uri1 = cr.insert(Regions.CONTENT_URI, values);
        long regionId = ContentUris.parseId(uri1);

        // Insert bounds
        values = new ContentValues();
        values.put(RegionBounds.REGION_ID, regionId);
        values.put(RegionBounds.LATITUDE, 47.5607395);
        values.put(RegionBounds.LONGITUDE, -122.1462785);
        values.put(RegionBounds.LAT_SPAN, 0.7432510000000008);
        values.put(RegionBounds.LON_SPAN, 0.720901000000012);
        cr.insert(RegionBounds.CONTENT_URI, values);

        // Get the bounds count
        Cursor c1 = cr.query(RegionBounds.CONTENT_URI, null, null, null, null);
        assertNotNull(c1);
        assertEquals(1, c1.getCount());

        cr.delete(uri1, null, null);

        // Make sure there are no regions
        Cursor c2 = cr.query(RegionBounds.CONTENT_URI, null, null, null, null);
        assertNotNull(c2);
        assertEquals(0, c2.getCount());
    }
}
