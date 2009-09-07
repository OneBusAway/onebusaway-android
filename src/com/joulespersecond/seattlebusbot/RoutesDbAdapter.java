package com.joulespersecond.seattlebusbot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class RoutesDbAdapter {
    private DbHelper mDbHelper;
    private SQLiteDatabase mDb;
    
    private final Context mCtx;
    
    public RoutesDbAdapter(Context ctx) {
        mCtx = ctx;
    }
    
    public RoutesDbAdapter open() /*throws SQLException*/ {
        mDbHelper = new DbHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }
    
    public void close() {
        mDbHelper.close();
    }
    
    public void addRoute(String routeId, String shortName, String longName) {
        Cursor cursor =
        	mDb.query(DbHelper.ROUTES_TABLE, 
        				new String[] { DbHelper.KEY_USECOUNT }, // rows
        				DbHelper.KEY_ROUTEID + " = '" + routeId + "'", // selection (where)
        				null, // selectionArgs
        				null, // groupBy
        				null, // having
        				null, // order by
        				null); // limit
        
        ContentValues args = new ContentValues();
        args.put(DbHelper.KEY_SHORTNAME, shortName);
        args.put(DbHelper.KEY_LONGNAME, longName);
       
        if (cursor != null && cursor.getCount() > 0) {
        	cursor.moveToFirst();
        	args.put(DbHelper.KEY_USECOUNT, cursor.getInt(0) + 1);
        	cursor.close();
        }
        else {
        	// Insert a new entry
        	args.put(DbHelper.KEY_ROUTEID, routeId);
        	args.put(DbHelper.KEY_USECOUNT, 1);
        	mDb.insert(DbHelper.ROUTES_TABLE, null, args);
        }
    }
    public static void addRoute(Context ctx, String routeId, String shortName, 
    		String longName) {
    	RoutesDbAdapter adapter = new RoutesDbAdapter(ctx);
    	adapter.open();
    	adapter.addRoute(routeId, shortName, longName);
    	adapter.close();
    }
    
    public Cursor getFavoriteRoutes() {
        Cursor cursor =
        	mDb.query(DbHelper.ROUTES_TABLE, 
        				new String[] { 
        					DbHelper.KEY_ROUTEID, 
        					DbHelper.KEY_SHORTNAME, 
        					DbHelper.KEY_LONGNAME, 
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
