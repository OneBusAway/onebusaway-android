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
   
    public static final int DAY_SUN = 0x1;
    public static final int DAY_MON = 0x2;
    public static final int DAY_TUE = 0x4;
    public static final int DAY_WED	= 0x8;
    public static final int DAY_THU = 0x10;
    public static final int DAY_FRI = 0x20;
    public static final int DAY_SAT = 0x40;
    public static final int DAY_WEEKDAY = DAY_MON|DAY_TUE|DAY_WED|DAY_THU|DAY_FRI;
    public static final int DAY_ALL = DAY_WEEKDAY|DAY_SUN|DAY_SAT;
   
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
			DbHelper.KEY_DAYS
 	};
	
	public static final int TRIP_COL_TRIPID = 0;
	public static final int TRIP_COL_STOPID = 1;
	public static final int TRIP_COL_ROUTEID = 2;
	public static final int TRIP_COL_DEPARTURE = 3;
	public static final int TRIP_COL_HEADSIGN = 4;
	public static final int TRIP_COL_NAME = 5;
	public static final int TRIP_COL_REMINDER = 6;
	public static final int TRIP_COL_DAYS = 7;	
	
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
    		int days) {
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
    	args.put(DbHelper.KEY_DAYS, days);

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
