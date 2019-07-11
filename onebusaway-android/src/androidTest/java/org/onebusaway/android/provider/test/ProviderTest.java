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
package org.onebusaway.android.provider.test;

import org.junit.Test;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.provider.ObaProvider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

/**
 * Tests the provider that stores and reads persistent OBA data on the device
 */
public class ProviderTest extends ProviderTestCase2<ObaProvider> {

    public ProviderTest() {
        super(ObaProvider.class, ObaContract.AUTHORITY);
    }

    public void testStops() {
        ContentResolver cr = getMockContentResolver();
        //
        // Create
        //
        final String stopId = "1_11060-TEST";
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops._ID, stopId);
        values.put(ObaContract.Stops.CODE, "11060");
        values.put(ObaContract.Stops.NAME, "Broadway & E Denny Way");
        values.put(ObaContract.Stops.DIRECTION, "S");
        values.put(ObaContract.Stops.USE_COUNT, 0);
        values.put(ObaContract.Stops.LATITUDE, 47.617676);
        values.put(ObaContract.Stops.LONGITUDE, -122.314523);

        Uri uri = cr.insert(ObaContract.Stops.CONTENT_URI, values);
        assertNotNull(uri);
        assertEquals(uri, Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stopId));

        //
        // Read
        //
        Cursor c = cr.query(ObaContract.Stops.CONTENT_URI,
                new String[]{ObaContract.Stops._ID, ObaContract.Stops.DIRECTION},
                null, null, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.moveToNext();
        assertEquals(stopId, c.getString(0));
        c.close();

        // Test counting
        c = cr.query(ObaContract.Stops.CONTENT_URI,
                new String[]{ObaContract.Stops._COUNT},
                null, null, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.moveToNext();
        assertEquals(1, c.getInt(0));
        c.close();
        // Get the one that we care about
        c = cr.query(uri, new String[]{ObaContract.Stops.CODE}, null, null, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.moveToNext();
        assertEquals("11060", c.getString(0));
        c.close();

        //
        // Update
        //
        values = new ContentValues();
        values.put(ObaContract.Stops.USE_COUNT, 1);
        int result = cr.update(uri, values, null, null);
        assertEquals(1, result);

        //
        // Delete
        //
        result = cr.delete(uri, null, null);
        assertEquals(1, result);
        result = cr.delete(uri, null, null);
        assertEquals(0, result);
    }

    @Test
    public void testLimit() {
        ContentResolver cr = getMockContentResolver();
        //
        // Create
        //
        final String stopId = "1_11060-TEST";
        ContentValues values = new ContentValues();
        values.put(ObaContract.Stops._ID, stopId);
        values.put(ObaContract.Stops.CODE, "11060");
        values.put(ObaContract.Stops.NAME, "Broadway & E Denny Way");
        values.put(ObaContract.Stops.DIRECTION, "S");
        values.put(ObaContract.Stops.USE_COUNT, 0);
        values.put(ObaContract.Stops.LATITUDE, 47.617676);
        values.put(ObaContract.Stops.LONGITUDE, -122.314523);

        Uri uri = cr.insert(ObaContract.Stops.CONTENT_URI, values);
        assertNotNull(uri);

        final String stopId2 = "1_1010101-TEST";
        values.put(ObaContract.Stops._ID, stopId2);
        uri = cr.insert(ObaContract.Stops.CONTENT_URI, values);
        assertNotNull(uri);

        Cursor c = cr.query(ObaContract.Stops.CONTENT_URI,
                new String[]{ObaContract.Stops._COUNT},
                null, null, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.moveToNext();
        assertEquals(2, c.getInt(0));
        c.close();

        c = cr.query(ObaContract.Stops.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter("limit", "1")
                        .build(),
                new String[]{ObaContract.Stops._ID},
                null, null, null
        );
        assertNotNull(c);
        assertEquals(1, c.getCount());

        // Delete
        //
        int result = cr.delete(uri, null, null);
        assertEquals(1, result);

        c.close();
    }
}
