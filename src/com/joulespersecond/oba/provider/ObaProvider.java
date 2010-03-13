package com.joulespersecond.oba.provider;

import java.util.HashMap;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class ObaProvider extends ContentProvider {
    private class OpenHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "com.joulespersecond.seattlebusbot.db";
        private static final int DATABASE_VERSION = 14;

        public OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            bootstrapDatabase(db);
            onUpgrade(db, 12, DATABASE_VERSION);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 12) {
                dropTables(db);
                bootstrapDatabase(db);
                oldVersion = 12;
            }
            if (oldVersion == 12) {
                db.execSQL(
                    "create table " +
                        ObaContract.StopRouteFilters.PATH     + " (" +
                        ObaContract.StopRouteFilters.STOP_ID  + " varchar not null, " +
                        ObaContract.StopRouteFilters.ROUTE_ID + " varchar not null" +
                        ");");
                ++oldVersion;
            }
            if (oldVersion == 13) {
                db.execSQL(
                    "alter table " + ObaContract.Stops.PATH +
                        " add column " + ObaContract.Stops.USER_NAME);
                db.execSQL(
                    "alter table " + ObaContract.Stops.PATH +
                        " add column " + ObaContract.Stops.ACCESS_TIME);
                db.execSQL(
                    "alter table " + ObaContract.Stops.PATH +
                        " add column " + ObaContract.Stops.FAVORITE);
                // These are being added to the routes database as well,
                // even though some of them aren't accessible though the UI yet
                // (we don't allow people to rename routes)
                db.execSQL(
                    "alter table " + ObaContract.Routes.PATH +
                        " add column " + ObaContract.Routes.USER_NAME);
                db.execSQL(
                    "alter table " + ObaContract.Routes.PATH +
                        " add column " + ObaContract.Routes.ACCESS_TIME);
                db.execSQL(
                    "alter table " + ObaContract.Routes.PATH +
                        " add column " + ObaContract.Routes.FAVORITE);
                ++oldVersion;
            }
        }

        private void bootstrapDatabase(SQLiteDatabase db) {
            db.execSQL(
                "create table " +
                    ObaContract.Stops.PATH      + " (" +
                    ObaContract.Stops._ID       + " varchar primary key, " +
                    ObaContract.Stops.CODE      + " varchar not null, " +
                    ObaContract.Stops.NAME      + " varchar not null, " +
                    ObaContract.Stops.DIRECTION + " char[2] not null," +
                    ObaContract.Stops.USE_COUNT + " integer not null," +
                    ObaContract.Stops.LATITUDE  + " double not null," +
                    ObaContract.Stops.LONGITUDE + " double not null" +
                    ");");
            db.execSQL(
                "create table " +
                    ObaContract.Routes.PATH         + " (" +
                    ObaContract.Routes._ID          + " varchar primary key, " +
                    ObaContract.Routes.SHORTNAME    + " varchar not null, " +
                    ObaContract.Routes.LONGNAME     + " varchar, " +
                    ObaContract.Routes.USE_COUNT    + " integer not null" +
                    ");");
            db.execSQL(
                "create table " +
                    ObaContract.Trips.PATH          + " (" +
                    ObaContract.Trips._ID           + " varchar not null, " +
                    ObaContract.Trips.STOP_ID       + " varchar not null, " +
                    ObaContract.Trips.ROUTE_ID      + " varchar not null, " +
                    ObaContract.Trips.DEPARTURE     + " integer not null, " +
                    ObaContract.Trips.HEADSIGN      + " varchar not null, " +
                    ObaContract.Trips.NAME          + " varchar not null, " +
                    ObaContract.Trips.REMINDER      + " integer not null, " +
                    ObaContract.Trips.DAYS          + " integer not null" +
                    ");");
        }

        private void dropTables(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.StopRouteFilters.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Routes.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Stops.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Trips.PATH);
        }
    }

    private static final int STOPS      = 1;
    private static final int STOPS_ID   = 2;
    private static final int ROUTES     = 3;
    private static final int ROUTES_ID  = 4;
    private static final int TRIPS      = 5;
    private static final int TRIPS_ID   = 6;
    private static final int STOP_ROUTE_FILTERS = 7;

    private static final UriMatcher sUriMatcher;
    private static final HashMap<String,String> sStopsProjectionMap;
    private static final HashMap<String,String> sRoutesProjectionMap;
    private static final HashMap<String,String> sTripsProjectionMap;

    // Insert helpers are useful.
    private DatabaseUtils.InsertHelper mStopsInserter;
    private DatabaseUtils.InsertHelper mRoutesInserter;
    private DatabaseUtils.InsertHelper mTripsInserter;
    private DatabaseUtils.InsertHelper mFilterInserter;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Stops.PATH, STOPS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Stops.PATH + "/*", STOPS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Routes.PATH, ROUTES);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Routes.PATH + "/*", ROUTES_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Trips.PATH, TRIPS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Trips.PATH + "/*/*", TRIPS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.StopRouteFilters.PATH, STOP_ROUTE_FILTERS);

        sStopsProjectionMap = new HashMap<String,String>();
        sStopsProjectionMap.put(ObaContract.Stops._ID,      ObaContract.Stops._ID);
        sStopsProjectionMap.put(ObaContract.Stops.CODE,     ObaContract.Stops.CODE);
        sStopsProjectionMap.put(ObaContract.Stops.NAME,     ObaContract.Stops.NAME);
        sStopsProjectionMap.put(ObaContract.Stops.DIRECTION,ObaContract.Stops.DIRECTION);
        sStopsProjectionMap.put(ObaContract.Stops.USE_COUNT,ObaContract.Stops.USE_COUNT);
        sStopsProjectionMap.put(ObaContract.Stops.LATITUDE, ObaContract.Stops.LATITUDE);
        sStopsProjectionMap.put(ObaContract.Stops.LONGITUDE,ObaContract.Stops.LONGITUDE);
        sStopsProjectionMap.put(ObaContract.Stops.USER_NAME,ObaContract.Stops.USER_NAME);
        sStopsProjectionMap.put(ObaContract.Stops.ACCESS_TIME,ObaContract.Stops.ACCESS_TIME);
        sStopsProjectionMap.put(ObaContract.Stops.FAVORITE, ObaContract.Stops.FAVORITE);
        sStopsProjectionMap.put(ObaContract.Stops._COUNT, "count(*)");

        sRoutesProjectionMap = new HashMap<String,String>();
        sRoutesProjectionMap.put(ObaContract.Routes._ID,        ObaContract.Routes._ID);
        sRoutesProjectionMap.put(ObaContract.Routes.SHORTNAME,  ObaContract.Routes.SHORTNAME);
        sRoutesProjectionMap.put(ObaContract.Routes.LONGNAME,   ObaContract.Routes.LONGNAME);
        sRoutesProjectionMap.put(ObaContract.Routes.USE_COUNT,  ObaContract.Routes.USE_COUNT);
        sRoutesProjectionMap.put(ObaContract.Routes.USER_NAME,	ObaContract.Routes.USER_NAME);
        sRoutesProjectionMap.put(ObaContract.Routes.ACCESS_TIME,ObaContract.Routes.ACCESS_TIME);
        sRoutesProjectionMap.put(ObaContract.Routes.FAVORITE, 	ObaContract.Routes.FAVORITE);
        sRoutesProjectionMap.put(ObaContract.Routes._COUNT,     "count(*)");

        sTripsProjectionMap = new HashMap<String,String>();
        sTripsProjectionMap.put(ObaContract.Trips._ID,      ObaContract.Trips._ID);
        sTripsProjectionMap.put(ObaContract.Trips.STOP_ID,  ObaContract.Trips.STOP_ID);
        sTripsProjectionMap.put(ObaContract.Trips.ROUTE_ID, ObaContract.Trips.ROUTE_ID);
        sTripsProjectionMap.put(ObaContract.Trips.DEPARTURE,ObaContract.Trips.DEPARTURE);
        sTripsProjectionMap.put(ObaContract.Trips.HEADSIGN, ObaContract.Trips.HEADSIGN);
        sTripsProjectionMap.put(ObaContract.Trips.NAME,     ObaContract.Trips.NAME);
        sTripsProjectionMap.put(ObaContract.Trips.REMINDER, ObaContract.Trips.REMINDER);
        sTripsProjectionMap.put(ObaContract.Trips.DAYS,     ObaContract.Trips.DAYS);
        sTripsProjectionMap.put(ObaContract.Trips._COUNT,   "count(*)");
    }

    private SQLiteDatabase mDb;
    private OpenHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new OpenHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        switch (match) {
        case STOPS:
            return ObaContract.Stops.CONTENT_DIR_TYPE;
        case STOPS_ID:
            return ObaContract.Stops.CONTENT_TYPE;
        case ROUTES:
            return ObaContract.Routes.CONTENT_DIR_TYPE;
        case ROUTES_ID:
            return ObaContract.Routes.CONTENT_TYPE;
        case TRIPS:
            return ObaContract.Trips.CONTENT_DIR_TYPE;
        case TRIPS_ID:
            return ObaContract.Trips.CONTENT_TYPE;
        case STOP_ROUTE_FILTERS:
            return ObaContract.StopRouteFilters.CONTENT_DIR_TYPE;
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            Uri result = insertInternal(db, uri, values);
            getContext().getContentResolver().notifyChange(uri, null);
            db.setTransactionSuccessful();
            return result;
        }
        finally {
            db.endTransaction();
        }
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final SQLiteDatabase db = getDatabase();
        return queryInternal(db, uri, projection, selection, selectionArgs, sortOrder);
    }
    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        final SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            int result = updateInternal(db, uri, values, selection, selectionArgs);
            if (result > 0) {
                getContext().getContentResolver().notifyChange(uri, null);
            }
            db.setTransactionSuccessful();
            return result;
        }
        finally {
            db.endTransaction();
        }
    }
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            int result = deleteInternal(db, uri, selection, selectionArgs);
            if (result > 0) {
                getContext().getContentResolver().notifyChange(uri, null);
            }
            db.setTransactionSuccessful();
            return result;
        }
        finally {
            db.endTransaction();
        }
    }

    private Uri insertInternal(SQLiteDatabase db, Uri uri, ContentValues values) {
        final int match = sUriMatcher.match(uri);
        String id;
        Uri result;

        switch (match) {
        case STOPS:
            // Pull out the Stop ID from the values to construct the new URI
            // (And we'd better have a stop ID)
            id = values.getAsString(ObaContract.Stops._ID);
            if (id == null) {
                throw new IllegalArgumentException("Need a stop ID to insert! " + uri);
            }
            result = Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, id);
            mStopsInserter.insert(values);
            return result;

        case ROUTES:
            // Pull out the Route ID from the values to construct the new URI
            // (And we'd better have a route ID)
            id = values.getAsString(ObaContract.Routes._ID);
            if (id == null) {
                throw new IllegalArgumentException("Need a routes ID to insert! " + uri);
            }
            result = Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, id);
            mRoutesInserter.insert(values);
            return result;

        case TRIPS:
            // Pull out the Trip ID from the values to construct the new URI
            // (And we'd better have a trip ID)
            id = values.getAsString(ObaContract.Trips._ID);
            if (id == null) {
                throw new IllegalArgumentException("Need a trip ID to insert! " + uri);
            }
            result = Uri.withAppendedPath(ObaContract.Trips.CONTENT_URI, id);
            mTripsInserter.insert(values);
            return result;

        case STOP_ROUTE_FILTERS:
            // TODO: We should provide a "virtual" column that is an array,
            // so clients don't have to call the content provider for each item to insert.
            // Pull out the Trip ID from the values to construct the new URI
            // (And we'd better have a route ID)
            id = values.getAsString(ObaContract.StopRouteFilters.STOP_ID);
            if (id == null) {
                throw new IllegalArgumentException("Need a stop ID to insert! " + uri);
            }
            result = Uri.withAppendedPath(ObaContract.StopRouteFilters.CONTENT_URI, id);
            mFilterInserter.insert(values);
            return result;

        // What would these mean, anyway??
        case STOPS_ID:
        case ROUTES_ID:
        case TRIPS_ID:
            throw new UnsupportedOperationException("Cannot insert to this URI: " + uri);
        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }
    private Cursor queryInternal(SQLiteDatabase db,
            Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final int match = sUriMatcher.match(uri);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (match) {
        case STOPS:
            qb.setTables(ObaContract.Stops.PATH);
            qb.setProjectionMap(sStopsProjectionMap);
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        case STOPS_ID:
            qb.setTables(ObaContract.Stops.PATH);
            qb.setProjectionMap(sStopsProjectionMap);
            qb.appendWhere(ObaContract.Stops._ID);
            qb.appendWhere("=");
            qb.appendWhereEscapeString(uri.getLastPathSegment());
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        case ROUTES:
            qb.setTables(ObaContract.Routes.PATH);
            qb.setProjectionMap(sRoutesProjectionMap);
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        case ROUTES_ID:
            qb.setTables(ObaContract.Routes.PATH);
            qb.setProjectionMap(sRoutesProjectionMap);
            qb.appendWhere(ObaContract.Routes._ID);
            qb.appendWhere("=");
            qb.appendWhereEscapeString(uri.getLastPathSegment());
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        case TRIPS:
            qb.setTables(ObaContract.Trips.PATH);
            qb.setProjectionMap(sTripsProjectionMap);
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        case TRIPS_ID:
            qb.setTables(ObaContract.Trips.PATH);
            qb.setProjectionMap(sTripsProjectionMap);
            qb.appendWhere(tripWhere(uri));
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        case STOP_ROUTE_FILTERS:
            qb.setTables(ObaContract.StopRouteFilters.PATH);
            return qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);

        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }
    private int updateInternal(SQLiteDatabase db,
            Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
        case STOPS:
            return db.update(ObaContract.Stops.PATH, values, selection, selectionArgs);

        case STOPS_ID:
            return db.update(ObaContract.Stops.PATH, values,
                    where(ObaContract.Stops._ID, uri), selectionArgs);

        case ROUTES:
            return db.update(ObaContract.Routes.PATH, values, selection, selectionArgs);

        case ROUTES_ID:
            return db.update(ObaContract.Routes.PATH, values,
                    where(ObaContract.Routes._ID, uri), selectionArgs);

        case TRIPS:
            return db.update(ObaContract.Trips.PATH, values, selection, selectionArgs);

        case TRIPS_ID:
            return db.update(ObaContract.Trips.PATH, values, tripWhere(uri), selectionArgs);

        // Can we do anything here??
        case STOP_ROUTE_FILTERS:
            return 0;

        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }
    private int deleteInternal(SQLiteDatabase db,
            Uri uri, String selection, String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
        case STOPS:
            return db.delete(ObaContract.Stops.PATH, selection, selectionArgs);

        case STOPS_ID:
            return db.delete(ObaContract.Stops.PATH,
                    where(ObaContract.Stops._ID, uri), selectionArgs);

        case ROUTES:
            return db.delete(ObaContract.Routes.PATH, selection, selectionArgs);

        case ROUTES_ID:
            return db.delete(ObaContract.Routes.PATH,
                    where(ObaContract.Routes._ID, uri), selectionArgs);

        case TRIPS:
            return db.delete(ObaContract.Trips.PATH, selection, selectionArgs);

        case TRIPS_ID:
            return db.delete(ObaContract.Trips.PATH, tripWhere(uri), selectionArgs);

        case STOP_ROUTE_FILTERS:
            return db.delete(ObaContract.StopRouteFilters.PATH, selection, selectionArgs);

        default:
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    private String where(String column, Uri uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(column);
        sb.append('=');
        DatabaseUtils.appendValueToSql(sb, uri.getLastPathSegment());
        return sb.toString();
    }
    private String tripWhere(Uri uri) {
        List<String> segments = uri.getPathSegments();
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(ObaContract.Trips._ID);
        sb.append("=");
        DatabaseUtils.appendValueToSql(sb, segments.get(1));
        sb.append(" AND ");
        sb.append(ObaContract.Trips.STOP_ID);
        sb.append("=");
        DatabaseUtils.appendValueToSql(sb, segments.get(2));
        sb.append(")");
        return sb.toString();
    }

    private SQLiteDatabase getDatabase() {
        if (mDb == null) {
            mDb = mOpenHelper.getWritableDatabase();
            // Initialize the insert helpers
            mStopsInserter = new DatabaseUtils.InsertHelper(mDb, ObaContract.Stops.PATH);
            mRoutesInserter = new DatabaseUtils.InsertHelper(mDb, ObaContract.Routes.PATH);
            mTripsInserter = new DatabaseUtils.InsertHelper(mDb, ObaContract.Trips.PATH);
            mFilterInserter = new DatabaseUtils.InsertHelper(mDb, ObaContract.StopRouteFilters.PATH);
        }
        return mDb;
    }
}
