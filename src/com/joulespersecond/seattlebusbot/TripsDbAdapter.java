package com.joulespersecond.seattlebusbot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.format.Time;

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
    public static final int REPEAT_DAILY = -1;
    public static final int REPEAT_WEEKDAY = -2;
    // Positive values are days of the week (starting at 1)
   
	private static final String WHERE = String.format("%s=? and %s=?", 
			DbHelper.KEY_TRIPID,
			DbHelper.KEY_STOP);
	private static final String[] COLS = new String[] { 
			DbHelper.KEY_TRIPID,
			DbHelper.KEY_STOP,
			DbHelper.KEY_ROUTE,
			DbHelper.KEY_DEPARTURE,
			DbHelper.KEY_HEADSIGN,
			DbHelper.KEY_NAME, 
			DbHelper.KEY_REMINDER, 
			DbHelper.KEY_REPEAT
 	};
	
	public static final int TRIP_COL_TRIPID = 0;
	public static final int TRIP_COL_STOPID = 1;
	public static final int TRIP_COL_ROUTEID = 2;
	public static final int TRIP_COL_DEPARTURE = 3;
	public static final int TRIP_COL_HEADSIGN = 4;
	public static final int TRIP_COL_NAME = 5;
	public static final int TRIP_COL_REMINDER = 6;
	public static final int TRIP_COL_REPEAT = 7;	
	
    public Cursor getTrip(String tripId, String stopId) {
        Cursor cursor =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				COLS,
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

    public Cursor getTrips() {
        Cursor cursor =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				COLS,
        				null, // where
        				null, // selectionArgs
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
    		long departure,
    		String headsign,
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
    	args.put(DbHelper.KEY_DEPARTURE, convertTimeToDB(departure));
    	args.put(DbHelper.KEY_NAME, name);
    	args.put(DbHelper.KEY_REMINDER, reminder);
    	args.put(DbHelper.KEY_REPEAT, repeat);

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
    
    // Helper functions to convert the DB DepartureTime value 
    public static long convertDBToTime(int minutes) {
    	// This converts the minutes-to-midnight to a time of the current day.
    	Time t = new Time();
    	t.setToNow();
    	t.set(0, minutes, 0, t.monthDay, t.month, t.year);
    	return t.toMillis(false);
    }
    public static int convertTimeToDB(long departureTime) {
    	// This converts a time_t to minutes-to-midnight.
    	Time t = new Time();
    	t.set(departureTime);
    	return t.hour*60 + t.minute;
    }
}
