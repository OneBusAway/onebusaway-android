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
import org.onebusaway.android.provider.ObaContract.TripAlerts;
import org.onebusaway.android.provider.ObaProvider;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

public class TripAlertsTest extends ProviderTestCase2<ObaProvider> {

    public TripAlertsTest() {
        super(ObaProvider.class, ObaContract.AUTHORITY);
    }

    public void testInsertIfNotExists() {
        ContentResolver cr = getMockContentResolver();
        final Uri uri1 = TripAlerts.insertIfNotExists(cr, "1_12345", "1_STOP", 1000);
        assertNotNull(uri1);

        Cursor c1 = cr.query(ObaContract.TripAlerts.CONTENT_URI,
                new String[]{ObaContract.TripAlerts._ID},
                null, null, null);
        assertNotNull(c1);
        assertEquals(1, c1.getCount());

        final Uri uri2 = TripAlerts.insertIfNotExists(cr, "1_12345", "1_STOP", 1000);
        assertNotNull(uri2);
        c1.close();

        Cursor c2 = cr.query(ObaContract.TripAlerts.CONTENT_URI,
                new String[]{ObaContract.TripAlerts._ID},
                null, null, null);
        assertNotNull(c2);
        assertEquals(1, c2.getCount());
        assertEquals(uri1, uri2);
        c2.close();

        // Delete this alert
        cr.delete(uri1, null, null);
    }

    public void testSetState() {
        ContentResolver cr = getMockContentResolver();
        final Uri uri = TripAlerts.insertIfNotExists(cr, "1_12345", "1_STOP", 1000);
        assertNotNull(uri);

        final String[] PROJECTION = {TripAlerts.STATE};

        // Ensure it's created with a *scheduled* state
        Cursor c = cr.query(uri, PROJECTION, null, null, null);
        assertNotNull(c);
        c.moveToNext();
        assertEquals(TripAlerts.STATE_SCHEDULED, c.getInt(0));
        c.close();

        TripAlerts.setState(cr, uri, TripAlerts.STATE_POLLING);

        c = cr.query(uri, PROJECTION, null, null, null);
        assertNotNull(c);
        c.moveToNext();
        assertEquals(TripAlerts.STATE_POLLING, c.getInt(0));
        c.close();

        cr.delete(uri, null, null);
    }
}
