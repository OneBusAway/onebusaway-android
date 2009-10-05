package com.joulespersecond.seattlebusbot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TripsDbAdapter {
    private DbHelper mDbHelper;
    private SQLiteDatabase mDb;
    
    private final Context mCtx;
    
    public TripsDbAdapter(Context ctx) {
        mCtx = ctx;
    }
    
    public TripsDbAdapter open() /*throws SQLException*/ {
        mDbHelper = new DbHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }
    
    public void close() {
        mDbHelper.close();
    }
   
    public static final int REPEAT_ONETIME = 0; // necessary?
    public static final int REPEAT_DAILY = 1;
    public static final int REPEAT_WEEKDAY = 2;
    public static final int REPEAT_WEEKLY = 3;
    
	public static final int TRIP_COL_NAME = 0;
	public static final int TRIP_COL_REMINDER = 1;
	public static final int TRIP_COL_REPEAT = 2;
    
	private static final String WHERE = String.format("%s=? and %s=?", 
			DbHelper.KEY_TRIPID,
			DbHelper.KEY_STOP);
	
    public Cursor getTrip(String tripId, String stopId) {
        Cursor cursor =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				new String[] { 
        					DbHelper.KEY_NAME, 
        					DbHelper.KEY_REMINDER, 
        					DbHelper.KEY_REPEAT }, // rows
        				WHERE, // selection (where)
        				new String[] {
        					tripId,
        					stopId
        				}, // selectionArgs
        				null, // groupBy
        				null, // having
        				null); // order by
        if (cursor != null) {
        	cursor.moveToFirst();
        }
        return cursor;
    }
	
    public void addTrip(String tripId, 
    		String stopId, 
    		String routeId,
    		String headsign,
    		long departure,
    		String name,
    		int reminder,
    		int repeat) {
    	final String[] whereArgs = new String[] { tripId, stopId };
    	
    	Cursor cursor =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				new String[] { DbHelper.KEY_TRIPID }, // rows
        				WHERE, // selection (where)
        				whereArgs, // selectionArgs
        				null, // groupBy
        				null, // having
        				null); // order by
    	
    	ContentValues args = new ContentValues();
    	args.put(DbHelper.KEY_ROUTE, routeId);
    	args.put(DbHelper.KEY_HEADSIGN, headsign);
    	args.put(DbHelper.KEY_DEPARTURE, departure);
    	args.put(DbHelper.KEY_NAME, name);
    	args.put(DbHelper.KEY_REMINDER, reminder);
    	if (repeat >= 0) {
    		args.put(DbHelper.KEY_REPEAT, repeat);
    	} 
    	else {
    		args.putNull(DbHelper.KEY_REPEAT);
    	}
    	if (cursor != null && cursor.getCount() > 0) {
    		// update
    		mDb.update(DbHelper.TRIPS_TABLE, args, WHERE, whereArgs);
    	}
    	else {
    		// Insert a new row
    		args.put(DbHelper.KEY_TRIPID, tripId);
    		args.put(DbHelper.KEY_STOP, stopId);
    		mDb.insert(DbHelper.TRIPS_TABLE, null, args);
    	}
    	// Make sure the cursor is closed if it exists
    	if (cursor != null) {
    		cursor.close();
    	}
	}
}
