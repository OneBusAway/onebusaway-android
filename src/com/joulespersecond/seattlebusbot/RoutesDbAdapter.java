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
    
    private static final String WhereRouteId = DbHelper.KEY_ROUTEID + "=?";
    
    public void addRoute(String routeId, 
            String shortName, 
            String longName,
            boolean markAsUsed) {
        final String[] rows = { DbHelper.KEY_USECOUNT };
        final String where = DbHelper.KEY_ROUTEID + "=?";
        final String[] whereArgs = { routeId };
        Cursor cursor =
            mDb.query(DbHelper.ROUTES_TABLE, 
                        rows, // rows
                        WhereRouteId, // selection (where)
                        whereArgs, // selectionArgs
                        null, // groupBy
                        null, // having
                        null, // order by
                        null); // limit
        
        ContentValues args = new ContentValues();
       
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            // Only update the values if they are valid (no nulls)
            if (shortName != null) {
                args.put(DbHelper.KEY_SHORTNAME, shortName);                
            }
            if (longName != null) {
                args.put(DbHelper.KEY_LONGNAME, longName);                
            }
            
            if (markAsUsed) {
                args.put(DbHelper.KEY_USECOUNT, cursor.getInt(0) + 1);
            }
            mDb.update(DbHelper.ROUTES_TABLE, args, where, whereArgs);
        }
        else {
            // Insert a new entry
            args.put(DbHelper.KEY_ROUTEID, routeId);
            args.put(DbHelper.KEY_SHORTNAME, shortName);
            args.put(DbHelper.KEY_LONGNAME, longName);
            if (markAsUsed) {
                args.put(DbHelper.KEY_USECOUNT, 1);                
            }
            else {
                args.put(DbHelper.KEY_USECOUNT, 0);
            }
            mDb.insert(DbHelper.ROUTES_TABLE, null, args);
        }
        // Make sure the cursor is closed if it exists
        if (cursor != null) {
            cursor.close();
        }
    }
    public static void addRoute(Context ctx, String routeId, String shortName, 
            String longName, boolean markAsUsed) {
        RoutesDbAdapter adapter = new RoutesDbAdapter(ctx);
        adapter.open();
        adapter.addRoute(routeId, shortName, longName, markAsUsed);
        adapter.close();
    }
    public static void clearFavorites(Context ctx) {
        RoutesDbAdapter adapter = new RoutesDbAdapter(ctx);
        adapter.open();
        adapter.clearFavorites();
        adapter.close();
    }
    
    private static final String WHERE = String.format("%s=?", 
            DbHelper.KEY_ROUTEID);
    private static final String[] COLS = { 
            DbHelper.KEY_ROUTEID, 
            DbHelper.KEY_SHORTNAME, 
            DbHelper.KEY_LONGNAME, 
            DbHelper.KEY_USECOUNT
         };
    
    public static final int ROUTE_COL_ROUTEID = 0;
    public static final int ROUTE_COL_SHORTNAME = 1;
    public static final int ROUTE_COL_LONGNAME = 2;
    public static final int ROUTE_COL_USECOUNT = 3;
    
    public Cursor getRoute(String routeId) {
        final String[] whereArgs = { routeId };
        Cursor cursor =
            mDb.query(DbHelper.ROUTES_TABLE, 
                        COLS, // rows
                        WHERE, // selection (where)
                        whereArgs, // selectionArgs
                        null, // groupBy
                        null, // having
                        null, // order by
                        null); // limit
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;        
    }
    public String getRouteShortName(String routeId) {
        String result = null;
        final String[] rows = { DbHelper.KEY_SHORTNAME };
        final String[] whereArgs = { routeId };
        Cursor cursor =
            mDb.query(DbHelper.ROUTES_TABLE, 
                        rows, // rows
                        WHERE, // selection (where)
                        whereArgs, // selectionArgs
                        null, // groupBy
                        null, // having
                        null, // order by
                        null); // limit
        
        if (cursor != null && cursor.getCount() >= 1) {
            cursor.moveToFirst();
            result = cursor.getString(0);
        }
        if (cursor != null) {
            cursor.close();
        }
        return result;
    }
    
    // A more efficient helper when all you want is the short name of the route.
    static public String getRouteShortName(Context context, String routeId) {
        RoutesDbAdapter adapter = new RoutesDbAdapter(context);
        adapter.open();
        String result = adapter.getRouteShortName(routeId);
        adapter.close();
        return result;
    }
    
    private static final String FavoriteWhere = DbHelper.KEY_USECOUNT + " > 0";
    private static final String FavoriteOrderBy = DbHelper.KEY_USECOUNT + " desc";
    
    public Cursor getFavoriteRoutes() {
        Cursor cursor =
            mDb.query(DbHelper.ROUTES_TABLE, 
                        COLS, // rows
                        FavoriteWhere, // selection (where)
                        null, // selectionArgs
                        null, // groupBy
                        null, // having
                        FavoriteOrderBy, // order by
                        "20"); // limit
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }
    public void removeFavorite(String routeID) {
        ContentValues args = new ContentValues();
        args.put(DbHelper.KEY_USECOUNT, 0);
        mDb.update(DbHelper.ROUTES_TABLE, args, WHERE, new String[] { routeID });        
    }
    public void clearFavorites() {
        ContentValues args = new ContentValues();
        args.put(DbHelper.KEY_USECOUNT, 0);
        mDb.update(DbHelper.ROUTES_TABLE, args, null, null);
    }
}
