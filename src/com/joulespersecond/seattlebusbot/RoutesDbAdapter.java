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
    	final String where = DbHelper.KEY_ROUTEID + " = '" + routeId + "'";
        Cursor cursor =
        	mDb.query(DbHelper.ROUTES_TABLE, 
        				new String[] { DbHelper.KEY_USECOUNT }, // rows
        				where, // selection (where)
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
        	mDb.update(DbHelper.ROUTES_TABLE, args, where, null);
        }
        else {
        	// Insert a new entry
        	args.put(DbHelper.KEY_ROUTEID, routeId);
        	args.put(DbHelper.KEY_USECOUNT, 1);
        	mDb.insert(DbHelper.ROUTES_TABLE, null, args);
        }
    	// Make sure the cursor is closed if it exists
    	if (cursor != null) {
    		cursor.close();
    	}
    }
    public static void addRoute(Context ctx, String routeId, String shortName, 
    		String longName) {
    	RoutesDbAdapter adapter = new RoutesDbAdapter(ctx);
    	adapter.open();
    	adapter.addRoute(routeId, shortName, longName);
    	adapter.close();
    }
    public static void clearFavorites(Context ctx) {
    	RoutesDbAdapter adapter = new RoutesDbAdapter(ctx);
    	adapter.open();
    	adapter.clearFavorites();
    	adapter.close();
    }
    
    public static final int FAVORITE_COL_ROUTEID = 0;
    public static final int FAVORITE_COL_SHORTNAME = 1;
    public static final int FAVORITE_COL_LONGNAME = 2;
    public static final int FAVORITE_COL_USECOUNT = 3;
    
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
    public void clearFavorites() {
    	mDb.execSQL("delete from " + DbHelper.ROUTES_TABLE);
    }
}
