package com.joulespersecond.seattlebusbot;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
   

    public static final int DAY_MON = 0x1;
    public static final int DAY_TUE = 0x2;
    public static final int DAY_WED	= 0x4;
    public static final int DAY_THU = 0x8;
    public static final int DAY_FRI = 0x10;
    public static final int DAY_SAT = 0x20;
    public static final int DAY_SUN = 0x40;
    public static final int DAY_WEEKDAY = DAY_MON|DAY_TUE|DAY_WED|DAY_THU|DAY_FRI;
    public static final int DAY_ALL = DAY_WEEKDAY|DAY_SUN|DAY_SAT;
   
	private static final String WHERE = String.format("%s=? and %s=?", 
			DbHelper.KEY_TRIPID,
			DbHelper.KEY_STOP);
	private static final String[] COLS = { 
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
    	final String[] whereArgs = { tripId, stopId };
        Cursor cursor =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				COLS,
        				WHERE, // selection (where)
        				whereArgs,  // selectionArgs
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
    
    public final class TripsForStopSet {
    	private final Cursor mCursor;
    	
    	TripsForStopSet(Cursor c) {
    		mCursor = c;
    	}
    	public void close() {
    		if (mCursor != null) {
    			mCursor.close();
    		}
    	}
    	public void refresh() {
    		if (mCursor != null) {
    			mCursor.requery();
    		}
    	}
    	// Returns null is there is no trip with this ID.
    	public String getTripName(String tripId) {
        	if (mCursor == null || !mCursor.moveToFirst()) {
        		return null;
        	}
        	do {
        		if (tripId.equals(mCursor.getString(0))) {
        			return mCursor.getString(1);
        		}
        	} while (mCursor.moveToNext());
        	return null;    		
    	}
    }
    public TripsForStopSet getTripsForStopId(String stopId) {
    	final String[] rows = { DbHelper.KEY_TRIPID, DbHelper.KEY_NAME };
    	final String[] whereArgs = { stopId };
    	Cursor c =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				rows, // rows
        				DbHelper.KEY_STOP + "=?", // selection (where)" +
        				whereArgs, // selectionArgs
        				null, // groupBy
        				null, // having
        				null); // order by
    	return new TripsForStopSet(c);
    }
	
    public void addTrip(String tripId,
    		String stopId, 
    		String routeId,
    		long departure,
    		String headsign,
    		String name,
    		int reminder,
    		int days) {
    	final String[] rows = { DbHelper.KEY_TRIPID };
    	final String[] whereArgs = { tripId, stopId };

    	Cursor cursor =
        	mDb.query(DbHelper.TRIPS_TABLE, 
        				rows, // rows
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
    	runTripService();
	}
    public void deleteTrip(String tripId, String stopId) {
    	final String[] whereArgs = { tripId, stopId };
    	mDb.delete(DbHelper.TRIPS_TABLE, WHERE, whereArgs);
    	runTripService();
    }
    
    private void runTripService() {
    	final Intent tripService = new Intent(mCtx, TripService.class);
    	tripService.setAction(TripService.ACTION_SCHEDULE_ALL);
    	mCtx.startService(tripService);
    }
    
    /**
     * Converts a days bitmask into a boolean[] array
     * 
     * @param days A DB compatible days bitmask.
     * @return A boolean array representing the days set in the bitmask, Mon=0 to Sun=6
     */
    public static boolean[] daysToArray(int days) {
    	final boolean[] result = {
        		(days & DAY_MON) == DAY_MON,
        		(days & DAY_TUE) == DAY_TUE,
        		(days & DAY_WED) == DAY_WED,
        		(days & DAY_THU) == DAY_THU,    		
        		(days & DAY_FRI) == DAY_FRI,   
        		(days & DAY_SAT) == DAY_SAT,      
        		(days & DAY_SUN) == DAY_SUN,   
    	};
    	return result;
    }
    /**
     * Converts a boolean[] array to a DB compatible days bitmask
     * 
     * @param A boolean array as returned by daysToArray
     * @return A DB compatible days bitmask
     */
    public static int arrayToDays(boolean[] days) {
    	int result = 0;
    	assert(days.length == 7);
    	for (int i=0; i < days.length; ++i) {
    		final int bit = days[i] ? 1 : 0;
    		result |= bit << i;
    	}
    	return result;
    }
    
    /**
     * Converts a 'minutes-to-midnight' value into a Unix time. 
     * 
     * @param minutes from midnight in UTC.
     * @return A Unix time representing the time in the current day.
     */
    // Helper functions to convert the DB DepartureTime value 
    public static long convertDBToTime(int minutes) {
    	// This converts the minutes-to-midnight to a time of the current day.
    	Time t = new Time();
    	t.setToNow();
    	t.set(0, minutes, 0, t.monthDay, t.month, t.year);
    	return t.toMillis(false);
    }
    /**
     * Converts a Unix time into a 'minutes-to-midnight' in UTC.
     * 
     * @param departureTime A Unix time.
     * @return minutes from midnight in UTC.
     */
    public static int convertTimeToDB(long departureTime) {
    	// This converts a time_t to minutes-to-midnight.
    	Time t = new Time();
    	t.set(departureTime);
    	return t.hour*60 + t.minute;
    }
    /**
     * Converts a weekday value from a android.text.format.Time to a bit.
     * 
     * @param weekday The weekDay value from android.text.format.Time
     * @return A DB compatible bit.
     */
    public static int getDayBit(int weekday) {
    	switch (weekday) {
    	case Time.MONDAY: 		return TripsDbAdapter.DAY_MON;
    	case Time.TUESDAY:		return TripsDbAdapter.DAY_TUE;
    	case Time.WEDNESDAY:	return TripsDbAdapter.DAY_WED;
    	case Time.THURSDAY:		return TripsDbAdapter.DAY_THU;
    	case Time.FRIDAY:		return TripsDbAdapter.DAY_FRI;
    	case Time.SATURDAY:		return TripsDbAdapter.DAY_SAT;
    	case Time.SUNDAY:		return TripsDbAdapter.DAY_SUN;
    	}
    	return 0;
    }
}
