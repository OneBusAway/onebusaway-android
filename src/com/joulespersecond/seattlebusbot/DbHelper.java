package com.joulespersecond.seattlebusbot;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbHelper extends SQLiteOpenHelper {
	public static final String TAG = "DbHelper";
	
    public static final String STOPS_TABLE = "stops";
    public static final String ROUTES_TABLE = "routes";
    public static final String TRIPS_TABLE = "trips";
	// We will cache a bunch of useful data so we don't have to constantly
	// look it up OTA -- in particular, data that we display to the user
	// in the Favorites lists.
    public static final String KEY_STOPID = "_id";
    public static final String KEY_ROUTEID = "_id";
    public static final String KEY_TRIPID = "_id";
    public static final String KEY_CODE = "code";
    public static final String KEY_NAME = "name";
    public static final String KEY_DIRECTION = "direction";
    public static final String KEY_USECOUNT = "use_count";
    public static final String KEY_SHORTNAME = "short_name";
    public static final String KEY_LONGNAME = "long_name";
    public static final String KEY_STOP = "stop_id";
    public static final String KEY_ROUTE = "route_id";
    public static final String KEY_HEADSIGN = "headsign";
    public static final String KEY_REMINDER = "reminder";
    public static final String KEY_REPEAT = "repeat";
    public static final String KEY_DEPARTURE = "departure";
	
    /**
     * Database creation sql statement
     */
    private static final String DATABASE_NAME = "com.joulespersecond.seattlebusbot.db";
    private static final int DATABASE_VERSION = 7;
    
    private static final String CREATE_STOPS = 
        "create table " +
        	STOPS_TABLE    	+ " (" + 
        	KEY_STOPID 		+ " varchar primary key, " + 
            KEY_CODE 		+ " varchar not null, " +
            KEY_NAME 		+ " varchar not null, " +
            KEY_DIRECTION 	+ " char[2] not null," +
            KEY_USECOUNT 	+ " integer not null" +
            ");";
    private static final String CREATE_ROUTES = 
        "create table " +
    		ROUTES_TABLE 	+ " (" + 
    		KEY_ROUTEID 	+ " varchar primary key, " + 
    		KEY_SHORTNAME 	+ " varchar not null, " +
    		KEY_LONGNAME 	+ " varchar not null, " +
    		KEY_USECOUNT 	+ " integer not null" +
    		");";  
    // NOTE: The trip ID cannot be a primary key, because one 
    // could see a case where someone wants to be notified
    // for multiple stops along a single trip (very corner case, but possible).
    // When accessing this table, the caller needs to be aware that
    // there may not be rows in the stops and routes tables that correspond
    // to the KEY_STOP and KEY_ROUTE IDs. In this case, it should either
    // ask the server for the info, or use some defaults.
    private static final String CREATE_TRIPS = 
    	"create table " +
    		TRIPS_TABLE 	+ " (" +
    		KEY_TRIPID 		+ " varchar not null, " +
    		KEY_STOP 		+ " varchar not null, " +
    		KEY_ROUTE       + " varchar not null, " +
    		KEY_HEADSIGN    + " varchar not null, " +
    		KEY_DEPARTURE   + " time not null, " + 
    		KEY_NAME        + " varchar not null, " +
    		// Reminder is the number of minutes before, or null if there is no reminder
    		KEY_REMINDER 	+ " integer, " + 
    		// Repeat is a constant defined in TripsDbAdapter, or null if there is no reminder
    		KEY_REPEAT 		+ " integer" +
    		");";
    
    DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_STOPS);
        db.execSQL(CREATE_ROUTES);
        db.execSQL(CREATE_TRIPS);
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + STOPS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + ROUTES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + TRIPS_TABLE);
        onCreate(db);
    }
}
