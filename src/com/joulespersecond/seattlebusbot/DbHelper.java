package com.joulespersecond.seattlebusbot;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbHelper extends SQLiteOpenHelper {
	public static final String TAG = "DbHelper";
	
    public static final String STOPS_TABLE = "favorite_stops";
    public static final String ROUTES_TABLE = "favorite_routes";
	// We will cache a bunch of useful data so we don't have to constantly
	// look it up OTA -- in particular, data that we display to the user
	// in the Favorites lists.
    public static final String KEY_STOPID = "_id";
    public static final String KEY_ROUTEID = "_id";
    public static final String KEY_CODE = "code";
    public static final String KEY_NAME = "name";
    public static final String KEY_DIRECTION = "direction";
    public static final String KEY_USECOUNT = "use_count";
    public static final String KEY_SHORTNAME = "short_name";
    public static final String KEY_LONGNAME = "long_name";
	
    /**
     * Database creation sql statement
     */
    private static final String DATABASE_NAME = "com.joulespersecond.seattlebusbot.db";
    private static final int DATABASE_VERSION = 4;
	
    private static final String CREATE_STOPS = 
        "create table " +
        	STOPS_TABLE + " (" + 
        	KEY_STOPID + " varchar primary key, " + 
            KEY_CODE + " varchar not null, " +
            KEY_NAME + " varchar not null, " +
            KEY_DIRECTION + " direction char[2] not null," +
            KEY_USECOUNT + " integer not null" +
            ");";
    private static final String CREATE_ROUTES = 
        "create table " +
    		ROUTES_TABLE + " (" + 
    		KEY_ROUTEID + " varchar primary key, " + 
    		KEY_SHORTNAME + " varchar not null, " +
    		KEY_LONGNAME + " varchar not null, " +
    		KEY_USECOUNT + " integer not null" +
    		");";    	
    
    DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_STOPS);
        db.execSQL(CREATE_ROUTES);
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + STOPS_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + ROUTES_TABLE);
        onCreate(db);
    }
}
