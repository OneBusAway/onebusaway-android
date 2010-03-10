package com.joulespersecond.seattlebusbot.tests;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;

import com.joulespersecond.oba.provider.ObaContract;

public class ProviderTest extends AndroidTestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testStops() {
        ContentResolver cr = getContext().getContentResolver();
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
                new String[] { ObaContract.Stops._ID, ObaContract.Stops.DIRECTION },
                null, null, null);
        assertNotNull(c);
        assertTrue(c.getCount() >= 1);
        //assertEquals(c.getString(0), stopId);
        c.close();
        
        // Test counting
        c = cr.query(ObaContract.Stops.CONTENT_URI, 
                new String[] { ObaContract.Stops._COUNT },
                null, null, null);
        assertNotNull(c);
        assertEquals(c.getCount(), 1);
        c.moveToNext();
        assertTrue(c.getInt(0) >= 1);
        // Get the one that we care about
        c = cr.query(uri, new String[] { ObaContract.Stops.CODE }, null, null, null);
        assertNotNull(c);
        assertEquals(c.getCount(), 1);
        c.moveToNext();
        assertEquals(c.getString(0), "11060");
        
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
        
        //
        // TODO: We need to test Where clauses.
        //
        
    }
    
}
