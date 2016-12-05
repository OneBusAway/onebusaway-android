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

import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.provider.ObaProvider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

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
        assertTrue(c.getCount() == 1);
        c.moveToNext();
        assertEquals(c.getString(0), stopId);
        c.close();

        // Test counting
        c = cr.query(ObaContract.Stops.CONTENT_URI,
                new String[]{ObaContract.Stops._COUNT},
                null, null, null);
        assertNotNull(c);
        assertEquals(c.getCount(), 1);
        c.moveToNext();
        assertTrue(c.getInt(0) == 1);
        c.close();
        // Get the one that we care about
        c = cr.query(uri, new String[]{ObaContract.Stops.CODE}, null, null, null);
        assertNotNull(c);
        assertEquals(c.getCount(), 1);
        c.moveToNext();
        assertEquals(c.getString(0), "11060");
        c.close();

        //
        // Update
        //
        values = new ContentValues();
        values.put(ObaContract.Stops.USE_COUNT, 1);
        int result = cr.update(uri, values, null, null);
        assertEquals(result, 1);

        //
        // Delete
        //
        result = cr.delete(uri, null, null);
        assertEquals(result, 1);
        result = cr.delete(uri, null, null);
        assertEquals(result, 0);
    }

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

        final String stopId2 = "1_1010101";
        values.put(ObaContract.Stops._ID, stopId2);
        uri = cr.insert(ObaContract.Stops.CONTENT_URI, values);
        assertNotNull(uri);

        Cursor c = cr.query(ObaContract.Stops.CONTENT_URI,
                new String[]{ObaContract.Stops._COUNT},
                null, null, null);
        assertNotNull(c);
        assertEquals(c.getCount(), 1);
        c.moveToNext();
        assertTrue(c.getInt(0) == 2);
        c.close();

        c = cr.query(ObaContract.Stops.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter("limit", "1")
                        .build(),
                new String[]{ObaContract.Stops._ID},
                null, null, null
        );
        assertNotNull(c);
        assertEquals(c.getCount(), 1);
        c.close();
    }

}
