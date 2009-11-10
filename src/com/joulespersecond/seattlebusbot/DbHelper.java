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
    public static final String STOP_ROUTES_FILTER_TABLE = "stop_routes_filter";
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
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";
    
    public static final String KEY_STOP = "stop_id";
    public static final String KEY_ROUTE = "route_id";
    public static final String KEY_DEPARTURE = "departure";
    public static final String KEY_HEADSIGN = "headsign";
    public static final String KEY_REMINDER = "reminder";
    public static final String KEY_DAYS = "days";
    
    /**
     * Database creation sql statement
     */
    private static final String DATABASE_NAME = "com.joulespersecond.seattlebusbot.db";
    private static final int DATABASE_VERSION_V1 = 12;
    private static final int DATABASE_VERSION = 13;
    
    // Yes, I know this is inefficient. 
    // It only happens once, and it is much more readable than 
    // a long string of append()s
    //
    private static final String CREATE_STOPS = 
        "create table " +
            STOPS_TABLE      + " (" + 
            KEY_STOPID       + " varchar primary key, " + 
            KEY_CODE         + " varchar not null, " +
            KEY_NAME         + " varchar not null, " +
            KEY_DIRECTION    + " char[2] not null," +
            KEY_USECOUNT     + " integer not null," +
            KEY_LATITUDE     + " double not null," +
            KEY_LONGITUDE    + " double not null" +
            ");";
    private static final String CREATE_ROUTES = 
        "create table " +
            ROUTES_TABLE     + " (" + 
            KEY_ROUTEID      + " varchar primary key, " + 
            KEY_SHORTNAME    + " varchar not null, " +
            KEY_LONGNAME     + " varchar, " +
            KEY_USECOUNT     + " integer not null" +
            ");"; 
    private static final String CREATE_STOP_ROUTES_FILTER = 
        "create table " +
            STOP_ROUTES_FILTER_TABLE     + " (" +
            KEY_STOP                    + " varchar not null, " +
            KEY_ROUTE                   + " varchar not null" +
            ");";

    //
    // Note, in our case "trips" does not mean the same as what GTFS
    // means as "trips". A GTFS trip is one complete run of a particular route,
    // whereas our trip is a user's notion of frequently used routes,
    // stops, and departure times (I get on a particular bus at a particular stop
    // at a particular time every day.)
    //
    // TripID cannot be a primary key because one can imagine having two
    // "trips" where the user may want to be reminded of the same
    // GTFS trip at multiple stops.
    // 
    // There may not be rows in the stops and routes tables that correspond
    // to the KEY_STOP and KEY_ROUTE IDs. In this case, it should either
    // ask the server for the info, or use some defaults.
    //
    // Departure := The number of minutes from midnight of the scheduled departure
    // Reminder  := The number of minutes before PredictedDeparture to notify the user.
    // Days      := Bitmask value defined in TripDbAdapter
    //
    private static final String CREATE_TRIPS = 
        "create table " +
            TRIPS_TABLE     + " (" +
            KEY_TRIPID      + " varchar not null, " +
            KEY_STOP        + " varchar not null, " +
            KEY_ROUTE       + " varchar not null, " +
            KEY_DEPARTURE   + " integer not null, " + 
            KEY_HEADSIGN    + " varchar not null, " +
            KEY_NAME        + " varchar not null, " +
            KEY_REMINDER    + " integer not null, " + 
            KEY_DAYS        + " integer not null" +
            ");";
    
    DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_STOPS);
        db.execSQL(CREATE_ROUTES);
        db.execSQL(CREATE_TRIPS);
        db.execSQL(CREATE_STOP_ROUTES_FILTER);
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < DATABASE_VERSION_V1) {
            destructiveUpgrade(db);
        }
        else if (oldVersion == DATABASE_VERSION_V1) {
            upgradeV1ToCurrent(db);
        }
        else {
            // Nothing to do...
        }
    }
    void upgradeV1ToCurrent(SQLiteDatabase db) {
        db.execSQL(CREATE_STOP_ROUTES_FILTER);        
    }
    void destructiveUpgrade(SQLiteDatabase db) {
        Log.w(TAG, "Destructive DB update");
        db.execSQL("DROP TABLE IF EXISTS " + STOPS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + ROUTES_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + TRIPS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + STOP_ROUTES_FILTER_TABLE);
        onCreate(db);        
    }
}
