package com.joulespersecond.seattlebusbot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class StopsDbAdapter {
    private DbHelper mDbHelper;
    private SQLiteDatabase mDb;
    
    private final Context mCtx;
    
    public StopsDbAdapter(Context ctx) {
        mCtx = ctx;
    }
    
    public StopsDbAdapter open() /*throws SQLException*/ {
        mDbHelper = new DbHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }
    
    public void close() {
        mDbHelper.close();
    }
    
    /**
     * addStop will either add a stop to the database, or update the data and increment 
     * its use count.
     * 
     * @param stopId
     * @param code
     * @param name
     */
    public void addStop(String stopId, String code, String name, String direction) {
        Cursor cursor =
        	mDb.query(DbHelper.STOPS_TABLE, 
        				new String[] { DbHelper.KEY_USECOUNT }, // rows
        				DbHelper.KEY_STOPID + " = '" + stopId + "'", // selection (where)
        				null, // selectionArgs
        				null, // groupBy
        				null, // having
        				null, // order by
        				null); // limit
        
        ContentValues args = new ContentValues();
        args.put(DbHelper.KEY_CODE, code);
        args.put(DbHelper.KEY_NAME, name);
        args.put(DbHelper.KEY_DIRECTION, direction);
       
        if (cursor != null && cursor.getCount() > 0) {
        	cursor.moveToFirst();
        	args.put(DbHelper.KEY_USECOUNT, cursor.getInt(0) + 1);
        	cursor.close();
        }
        else {
        	// Insert a new entry
        	args.put(DbHelper.KEY_STOPID, stopId);
        	args.put(DbHelper.KEY_USECOUNT, 1);
        	mDb.insert(DbHelper.STOPS_TABLE, null, args);
        }
    }
    public static void addStop(Context ctx, String stopId, String code, 
    		String name, String direction) {
    	StopsDbAdapter adapter = new StopsDbAdapter(ctx);
    	adapter.open();
    	adapter.addStop(stopId, code, name, direction);
    	adapter.close();
    }
    
    public static final int FAVORITE_COL_STOPID = 0;
    public static final int FAVORITE_COL_CODE = 1;
    public static final int FAVORITE_COL_NAME = 2;
    public static final int FAVORITE_COL_DIRECTION = 3;
    public static final int FAVORITE_COL_USECOUNT = 4;
    
    public Cursor getFavoriteStops() {
        Cursor cursor =
        	mDb.query(DbHelper.STOPS_TABLE, 
        				new String[] { 
        					DbHelper.KEY_STOPID, 
        					DbHelper.KEY_CODE, 
        					DbHelper.KEY_NAME, 
        					DbHelper.KEY_DIRECTION, 
        					DbHelper.KEY_USECOUNT }, // rows
        				DbHelper.KEY_USECOUNT + " > 0", // selection (where)
        				null, // selectionArgs
        				null, // groupBy
        				null, // having
        				DbHelper.KEY_USECOUNT + " desc", // order by
        				"20"); // limit
        if (cursor != null) {
        	cursor.moveToFirst();
        }
        return cursor;
    }
}
