/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.provider;

import org.onebusaway.android.BuildConfig;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.List;

public class ObaProvider extends ContentProvider {

    public static final String TAG = "ObaProvider";

    /**
     * The database name cannot be changed.  It needs to remain the same to support backwards
     * compatibility with existing installed apps
     */
    private static final String DATABASE_NAME = BuildConfig.APPLICATION_ID + ".db";

    private class OpenHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 27;

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
            Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
            if (oldVersion < 12) {
                dropTables(db);
                bootstrapDatabase(db);
                oldVersion = 12;
            }
            if (oldVersion == 12) {
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.StopRouteFilters.PATH + " (" +
                                ObaContract.StopRouteFilters.STOP_ID + " VARCHAR NOT NULL, " +
                                ObaContract.StopRouteFilters.ROUTE_ID + " VARCHAR NOT NULL" +
                                ");");
                ++oldVersion;
            }
            if (oldVersion == 13) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Stops.PATH +
                                " ADD COLUMN " + ObaContract.Stops.USER_NAME);
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Stops.PATH +
                                " ADD COLUMN " + ObaContract.Stops.ACCESS_TIME);
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Stops.PATH +
                                " ADD COLUMN " + ObaContract.Stops.FAVORITE);
                // These are being added to the routes database as well,
                // even though some of them aren't accessible though the UI yet
                // (we don't allow people to rename routes)
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Routes.PATH +
                                " ADD COLUMN " + ObaContract.Routes.USER_NAME);
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Routes.PATH +
                                " ADD COLUMN " + ObaContract.Routes.ACCESS_TIME);
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Routes.PATH +
                                " ADD COLUMN " + ObaContract.Routes.FAVORITE);
                ++oldVersion;
            }
            if (oldVersion == 14) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Routes.PATH +
                                " ADD COLUMN " + ObaContract.Routes.URL);
                ++oldVersion;
            }
            if (oldVersion == 15) {
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.TripAlerts.PATH + " (" +
                                ObaContract.TripAlerts._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                                +
                                ObaContract.TripAlerts.TRIP_ID + " VARCHAR NOT NULL, " +
                                ObaContract.TripAlerts.STOP_ID + " VARCHAR NOT NULL, " +
                                ObaContract.TripAlerts.START_TIME + " INTEGER NOT NULL, " +
                                ObaContract.TripAlerts.STATE + " INTEGER NOT NULL DEFAULT " +
                                ObaContract.TripAlerts.STATE_SCHEDULED +
                                ");");

                db.execSQL("DROP TRIGGER IF EXISTS trip_alerts_cleanup");
                db.execSQL(
                        "CREATE TRIGGER trip_alerts_cleanup DELETE ON " + ObaContract.Trips.PATH +
                                " BEGIN " +
                                "DELETE FROM " + ObaContract.TripAlerts.PATH +
                                " WHERE " + ObaContract.TripAlerts.TRIP_ID + " = old."
                                + ObaContract.Trips._ID +
                                " AND " + ObaContract.TripAlerts.STOP_ID + " = old."
                                + ObaContract.Trips.STOP_ID +
                                ";" +
                                "END");
                ++oldVersion;
            }
            if (oldVersion == 16) {
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.ServiceAlerts.PATH + " (" +
                                ObaContract.ServiceAlerts._ID + " VARCHAR PRIMARY KEY, " +
                                ObaContract.ServiceAlerts.MARKED_READ_TIME + " INTEGER " +
                                ");");
                ++oldVersion;
            }
            if (oldVersion == 17) {
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.Regions.PATH + " (" +
                                ObaContract.Regions._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                ObaContract.Regions.NAME + " VARCHAR NOT NULL, " +
                                ObaContract.Regions.OBA_BASE_URL + " VARCHAR NOT NULL, " +
                                ObaContract.Regions.SIRI_BASE_URL + " VARCHAR NOT NULL, " +
                                ObaContract.Regions.LANGUAGE + " VARCHAR NOT NULL, " +
                                ObaContract.Regions.CONTACT_EMAIL + " VARCHAR NOT NULL, " +
                                ObaContract.Regions.SUPPORTS_OBA_DISCOVERY + " INTEGER NOT NULL, " +
                                ObaContract.Regions.SUPPORTS_OBA_REALTIME + " INTEGER NOT NULL, " +
                                ObaContract.Regions.SUPPORTS_SIRI_REALTIME + " INTEGER NOT NULL " +
                                ");");
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.RegionBounds.PATH + " (" +
                                ObaContract.RegionBounds._ID
                                + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                ObaContract.RegionBounds.REGION_ID + " INTEGER NOT NULL, " +
                                ObaContract.RegionBounds.LATITUDE + " REAL NOT NULL, " +
                                ObaContract.RegionBounds.LONGITUDE + " REAL NOT NULL, " +
                                ObaContract.RegionBounds.LAT_SPAN + " REAL NOT NULL, " +
                                ObaContract.RegionBounds.LON_SPAN + " REAL NOT NULL " +
                                ");");
                db.execSQL("DROP TRIGGER IF EXISTS region_bounds_cleanup");
                db.execSQL(
                        "CREATE TRIGGER region_bounds_cleanup DELETE ON " + ObaContract.Regions.PATH
                                +
                                " BEGIN " +
                                "DELETE FROM " + ObaContract.RegionBounds.PATH +
                                " WHERE " + ObaContract.RegionBounds.REGION_ID + " = old."
                                + ObaContract.Regions._ID +
                                ";" +
                                "END");
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Stops.PATH +
                                " ADD COLUMN " + ObaContract.Stops.REGION_ID + " INTEGER");
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Routes.PATH +
                                " ADD COLUMN " + ObaContract.Routes.REGION_ID + " INTEGER");
                ++oldVersion;
            }
            if (oldVersion == 18) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Regions.PATH +
                                " ADD COLUMN " + ObaContract.Regions.TWITTER_URL + " VARCHAR");
                ++oldVersion;
            }
            if (oldVersion == 19) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Regions.PATH +
                                " ADD COLUMN " + ObaContract.Regions.EXPERIMENTAL + " INTEGER");
                ++oldVersion;
            }
            if (oldVersion == 20) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Regions.PATH +
                                " ADD COLUMN " + ObaContract.Regions.STOP_INFO_URL + " VARCHAR");
                ++oldVersion;
            }
            if (oldVersion == 21) {
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.RouteHeadsignFavorites.PATH + " (" +
                                ObaContract.RouteHeadsignFavorites.ROUTE_ID + " VARCHAR NOT NULL, "
                                +
                                ObaContract.RouteHeadsignFavorites.HEADSIGN + " VARCHAR NOT NULL, "
                                +
                                ObaContract.RouteHeadsignFavorites.STOP_ID + " VARCHAR NOT NULL, " +
                                ObaContract.RouteHeadsignFavorites.EXCLUDE + " INTEGER NOT NULL " +
                                ");");
                ++oldVersion;
            }
            if (oldVersion == 22) {
                db.execSQL(
                        "CREATE TABLE " +
                                ObaContract.RegionOpen311Servers.PATH + " (" +
                                ObaContract.RegionOpen311Servers._ID
                                + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                ObaContract.RegionOpen311Servers.REGION_ID + " INTEGER NOT NULL, " +
                                ObaContract.RegionOpen311Servers.JURISDICTION + " VARCHAR, " +
                                ObaContract.RegionOpen311Servers.API_KEY + " VARCHAR NOT NULL, " +
                                ObaContract.RegionOpen311Servers.BASE_URL + " VARCHAR NOT NULL " +
                                ");");
                ++oldVersion;
            }
            if (oldVersion == 23) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Regions.PATH +
                                " ADD COLUMN " + ObaContract.Regions.OTP_BASE_URL + " VARCHAR");
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Regions.PATH +
                                " ADD COLUMN " + ObaContract.Regions.OTP_CONTACT_EMAIL + " VARCHAR");
                ++oldVersion;
            }
            if (oldVersion == 24) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.ServiceAlerts.PATH +
                                " ADD COLUMN " + ObaContract.ServiceAlerts.HIDDEN + " INTEGER");
                ++oldVersion;
            }
            if (oldVersion == 25) {
                db.execSQL(
                        "ALTER TABLE " + ObaContract.Regions.PATH +
                                " ADD COLUMN " + ObaContract.Regions.SUPPORTS_OTP_BIKESHARE + " INTEGER");
                ++oldVersion;
            }
            if (oldVersion == 26) {
                /**
                 * Bike share and Embedded Social both added columns in version 26;
                 * Bike share in Beta, Embedded Social in alpha
                 * We are adding extra logic here to prevent either group from breaking on update
                 */
                try {
                    db.execSQL(
                            "ALTER TABLE " + ObaContract.Regions.PATH +
                                    " ADD COLUMN " + ObaContract.Regions.SUPPORTS_OTP_BIKESHARE + " INTEGER");
                } catch (SQLiteException e) {
                    Log.w(TAG, "Database already has bike share column - " + e);
                }

                try {
                    db.execSQL(
                            "ALTER TABLE " + ObaContract.Regions.PATH +
                                    " ADD COLUMN " + ObaContract.Regions.SUPPORTS_EMBEDDED_SOCIAL + " INTEGER");
                } catch (SQLiteException e) {
                    Log.w(TAG, "Database already has embedded social column - " + e);
                }
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            /**
             * See #473 - This can happen if the user does a backup of stops/routes, updates to a
             * new version of the app that includes a database upgrade, and then tries to restore
             * the backup.  This is because our current backup/restore mechanism is simply copying
             * the database. In this case, we should try to make sure the schema is current so no
             * code fails on missing tables or columns.
             */
            Log.d(TAG, "Downgrading database from version " + oldVersion + " to " + newVersion);
            onUpgrade(db, newVersion, newVersion);
        }

        private void bootstrapDatabase(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE " +
                            ObaContract.Stops.PATH + " (" +
                            ObaContract.Stops._ID + " VARCHAR PRIMARY KEY, " +
                            ObaContract.Stops.CODE + " VARCHAR NOT NULL, " +
                            ObaContract.Stops.NAME + " VARCHAR NOT NULL, " +
                            ObaContract.Stops.DIRECTION + " CHAR[2] NOT NULL," +
                            ObaContract.Stops.USE_COUNT + " INTEGER NOT NULL," +
                            ObaContract.Stops.LATITUDE + " DOUBLE NOT NULL," +
                            ObaContract.Stops.LONGITUDE + " DOUBLE NOT NULL" +
                            ");");
            db.execSQL(
                    "CREATE TABLE " +
                            ObaContract.Routes.PATH + " (" +
                            ObaContract.Routes._ID + " VARCHAR PRIMARY KEY, " +
                            ObaContract.Routes.SHORTNAME + " VARCHAR NOT NULL, " +
                            ObaContract.Routes.LONGNAME + " VARCHAR, " +
                            ObaContract.Routes.USE_COUNT + " INTEGER NOT NULL" +
                            ");");
            db.execSQL(
                    "CREATE TABLE " +
                            ObaContract.Trips.PATH + " (" +
                            ObaContract.Trips._ID + " VARCHAR NOT NULL, " +
                            ObaContract.Trips.STOP_ID + " VARCHAR NOT NULL, " +
                            ObaContract.Trips.ROUTE_ID + " VARCHAR NOT NULL, " +
                            ObaContract.Trips.DEPARTURE + " INTEGER NOT NULL, " +
                            ObaContract.Trips.HEADSIGN + " VARCHAR NOT NULL, " +
                            ObaContract.Trips.NAME + " VARCHAR NOT NULL, " +
                            ObaContract.Trips.REMINDER + " INTEGER NOT NULL, " +
                            ObaContract.Trips.DAYS + " INTEGER NOT NULL" +
                            ");");
        }

        private void dropTables(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.StopRouteFilters.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Routes.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Stops.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Trips.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.TripAlerts.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.ServiceAlerts.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.Regions.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.RegionBounds.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.RegionOpen311Servers.PATH);
            db.execSQL("DROP TABLE IF EXISTS " + ObaContract.RouteHeadsignFavorites.PATH);
        }
    }

    private static final int STOPS = 1;

    private static final int STOPS_ID = 2;

    private static final int ROUTES = 3;

    private static final int ROUTES_ID = 4;

    private static final int TRIPS = 5;

    private static final int TRIPS_ID = 6;

    private static final int TRIP_ALERTS = 7;

    private static final int TRIP_ALERTS_ID = 8;

    private static final int STOP_ROUTE_FILTERS = 9;

    private static final int SERVICE_ALERTS = 10;

    private static final int SERVICE_ALERTS_ID = 11;

    private static final int REGIONS = 12;

    private static final int REGIONS_ID = 13;

    private static final int REGION_BOUNDS = 14;

    private static final int REGION_BOUNDS_ID = 15;

    private static final int ROUTE_HEADSIGN_FAVORITES = 16;

    private static final int REGION_OPEN311_SERVERS = 17;

    private static final int REGION_OPEN311_SERVERS_ID = 18;

    private static final UriMatcher sUriMatcher;

    private static final HashMap<String, String> sStopsProjectionMap;

    private static final HashMap<String, String> sRoutesProjectionMap;

    private static final HashMap<String, String> sTripsProjectionMap;

    private static final HashMap<String, String> sTripAlertsProjectionMap;

    private static final HashMap<String, String> sServiceAlertsProjectionMap;

    private static final HashMap<String, String> sRegionsProjectionMap;

    private static final HashMap<String, String> sRegionBoundsProjectionMap;

    private static final HashMap<String, String> sRegionOpen311ProjectionMap;

    // Insert helpers are useful.
    private DatabaseUtils.InsertHelper mStopsInserter;

    private DatabaseUtils.InsertHelper mRoutesInserter;

    private DatabaseUtils.InsertHelper mTripsInserter;

    private DatabaseUtils.InsertHelper mTripAlertsInserter;

    private DatabaseUtils.InsertHelper mFilterInserter;

    private DatabaseUtils.InsertHelper mServiceAlertsInserter;

    private DatabaseUtils.InsertHelper mRegionsInserter;

    private DatabaseUtils.InsertHelper mRegionBoundsInserter;

    private DatabaseUtils.InsertHelper mRegionOpen311ServersInserter;

    private DatabaseUtils.InsertHelper mRouteHeadsignFavoritesInserter;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Stops.PATH, STOPS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Stops.PATH + "/*", STOPS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Routes.PATH, ROUTES);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Routes.PATH + "/*", ROUTES_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Trips.PATH, TRIPS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Trips.PATH + "/*/*", TRIPS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.TripAlerts.PATH, TRIP_ALERTS);
        sUriMatcher
                .addURI(ObaContract.AUTHORITY, ObaContract.TripAlerts.PATH + "/#", TRIP_ALERTS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.StopRouteFilters.PATH,
                STOP_ROUTE_FILTERS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.ServiceAlerts.PATH, SERVICE_ALERTS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.ServiceAlerts.PATH + "/*",
                SERVICE_ALERTS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Regions.PATH, REGIONS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.Regions.PATH + "/#", REGIONS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.RegionBounds.PATH, REGION_BOUNDS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.RegionBounds.PATH + "/#",
                REGION_BOUNDS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.RegionOpen311Servers.PATH, REGION_OPEN311_SERVERS);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.RegionOpen311Servers.PATH + "/#",
                REGION_OPEN311_SERVERS_ID);
        sUriMatcher.addURI(ObaContract.AUTHORITY, ObaContract.RouteHeadsignFavorites.PATH,
                ROUTE_HEADSIGN_FAVORITES);

        sStopsProjectionMap = new HashMap<String, String>();
        sStopsProjectionMap.put(ObaContract.Stops._ID, ObaContract.Stops._ID);
        sStopsProjectionMap.put(ObaContract.Stops.CODE, ObaContract.Stops.CODE);
        sStopsProjectionMap.put(ObaContract.Stops.NAME, ObaContract.Stops.NAME);
        sStopsProjectionMap.put(ObaContract.Stops.DIRECTION, ObaContract.Stops.DIRECTION);
        sStopsProjectionMap.put(ObaContract.Stops.USE_COUNT, ObaContract.Stops.USE_COUNT);
        sStopsProjectionMap.put(ObaContract.Stops.LATITUDE, ObaContract.Stops.LATITUDE);
        sStopsProjectionMap.put(ObaContract.Stops.LONGITUDE, ObaContract.Stops.LONGITUDE);
        sStopsProjectionMap.put(ObaContract.Stops.USER_NAME, ObaContract.Stops.USER_NAME);
        sStopsProjectionMap.put(ObaContract.Stops.ACCESS_TIME, ObaContract.Stops.ACCESS_TIME);
        sStopsProjectionMap.put(ObaContract.Stops.FAVORITE, ObaContract.Stops.FAVORITE);
        sStopsProjectionMap.put(ObaContract.Stops._COUNT, "count(*)");
        sStopsProjectionMap.put(ObaContract.Stops.UI_NAME,
                "CASE WHEN " + ObaContract.Stops.USER_NAME + " IS NOT NULL THEN " +
                        ObaContract.Stops.USER_NAME + " ELSE " +
                        ObaContract.Stops.NAME + " END AS " +
                        ObaContract.Stops.UI_NAME);

        sRoutesProjectionMap = new HashMap<String, String>();
        sRoutesProjectionMap.put(ObaContract.Routes._ID, ObaContract.Routes._ID);
        sRoutesProjectionMap.put(ObaContract.Routes.SHORTNAME, ObaContract.Routes.SHORTNAME);
        sRoutesProjectionMap.put(ObaContract.Routes.LONGNAME, ObaContract.Routes.LONGNAME);
        sRoutesProjectionMap.put(ObaContract.Routes.USE_COUNT, ObaContract.Routes.USE_COUNT);
        sRoutesProjectionMap.put(ObaContract.Routes.USER_NAME, ObaContract.Routes.USER_NAME);
        sRoutesProjectionMap.put(ObaContract.Routes.ACCESS_TIME, ObaContract.Routes.ACCESS_TIME);
        sRoutesProjectionMap.put(ObaContract.Routes.FAVORITE, ObaContract.Routes.FAVORITE);
        sRoutesProjectionMap.put(ObaContract.Routes.URL, ObaContract.Routes.URL);
        sRoutesProjectionMap.put(ObaContract.Routes._COUNT, "count(*)");

        sTripsProjectionMap = new HashMap<String, String>();
        sTripsProjectionMap.put(ObaContract.Trips._ID, ObaContract.Trips._ID);
        sTripsProjectionMap.put(ObaContract.Trips.STOP_ID, ObaContract.Trips.STOP_ID);
        sTripsProjectionMap.put(ObaContract.Trips.ROUTE_ID, ObaContract.Trips.ROUTE_ID);
        sTripsProjectionMap.put(ObaContract.Trips.DEPARTURE, ObaContract.Trips.DEPARTURE);
        sTripsProjectionMap.put(ObaContract.Trips.HEADSIGN, ObaContract.Trips.HEADSIGN);
        sTripsProjectionMap.put(ObaContract.Trips.NAME, ObaContract.Trips.NAME);
        sTripsProjectionMap.put(ObaContract.Trips.REMINDER, ObaContract.Trips.REMINDER);
        sTripsProjectionMap.put(ObaContract.Trips.DAYS, ObaContract.Trips.DAYS);
        sTripsProjectionMap.put(ObaContract.Trips._COUNT, "count(*)");

        sTripAlertsProjectionMap = new HashMap<String, String>();
        sTripAlertsProjectionMap.put(ObaContract.TripAlerts._ID, ObaContract.TripAlerts._ID);
        sTripAlertsProjectionMap
                .put(ObaContract.TripAlerts.TRIP_ID, ObaContract.TripAlerts.TRIP_ID);
        sTripAlertsProjectionMap
                .put(ObaContract.TripAlerts.STOP_ID, ObaContract.TripAlerts.STOP_ID);
        sTripAlertsProjectionMap
                .put(ObaContract.TripAlerts.START_TIME, ObaContract.TripAlerts.START_TIME);
        sTripAlertsProjectionMap.put(ObaContract.TripAlerts.STATE, ObaContract.TripAlerts.STATE);
        sTripAlertsProjectionMap.put(ObaContract.TripAlerts._COUNT, "count(*)");

        sServiceAlertsProjectionMap = new HashMap<String, String>();
        sServiceAlertsProjectionMap
                .put(ObaContract.ServiceAlerts._ID, ObaContract.ServiceAlerts._ID);
        sServiceAlertsProjectionMap.put(ObaContract.ServiceAlerts.MARKED_READ_TIME,
                ObaContract.ServiceAlerts.MARKED_READ_TIME);
        sServiceAlertsProjectionMap.put(ObaContract.ServiceAlerts.HIDDEN,
                ObaContract.ServiceAlerts.HIDDEN);

        sRegionsProjectionMap = new HashMap<String, String>();
        sRegionsProjectionMap.put(ObaContract.Regions._ID, ObaContract.Regions._ID);
        sRegionsProjectionMap.put(ObaContract.Regions.NAME, ObaContract.Regions.NAME);
        sRegionsProjectionMap
                .put(ObaContract.Regions.OBA_BASE_URL, ObaContract.Regions.OBA_BASE_URL);
        sRegionsProjectionMap
                .put(ObaContract.Regions.SIRI_BASE_URL, ObaContract.Regions.SIRI_BASE_URL);
        sRegionsProjectionMap.put(ObaContract.Regions.LANGUAGE, ObaContract.Regions.LANGUAGE);
        sRegionsProjectionMap
                .put(ObaContract.Regions.CONTACT_EMAIL, ObaContract.Regions.CONTACT_EMAIL);
        sRegionsProjectionMap.put(ObaContract.Regions.SUPPORTS_OBA_DISCOVERY,
                ObaContract.Regions.SUPPORTS_OBA_DISCOVERY);
        sRegionsProjectionMap.put(ObaContract.Regions.SUPPORTS_OBA_REALTIME,
                ObaContract.Regions.SUPPORTS_OBA_REALTIME);
        sRegionsProjectionMap.put(ObaContract.Regions.SUPPORTS_SIRI_REALTIME,
                ObaContract.Regions.SUPPORTS_SIRI_REALTIME);
        sRegionsProjectionMap.put(ObaContract.Regions.SUPPORTS_EMBEDDED_SOCIAL,
                ObaContract.Regions.SUPPORTS_EMBEDDED_SOCIAL);
        sRegionsProjectionMap.put(ObaContract.Regions.TWITTER_URL, ObaContract.Regions.TWITTER_URL);
        sRegionsProjectionMap
                .put(ObaContract.Regions.EXPERIMENTAL, ObaContract.Regions.EXPERIMENTAL);
        sRegionsProjectionMap
                .put(ObaContract.Regions.STOP_INFO_URL, ObaContract.Regions.STOP_INFO_URL);
        sRegionsProjectionMap
                .put(ObaContract.Regions.OTP_BASE_URL, ObaContract.Regions.OTP_BASE_URL);
        sRegionsProjectionMap
                .put(ObaContract.Regions.OTP_CONTACT_EMAIL, ObaContract.Regions.OTP_CONTACT_EMAIL);
        sRegionsProjectionMap
                .put(ObaContract.Regions.SUPPORTS_OTP_BIKESHARE, ObaContract.Regions.SUPPORTS_OTP_BIKESHARE);

        sRegionBoundsProjectionMap = new HashMap<String, String>();
        sRegionBoundsProjectionMap.put(ObaContract.RegionBounds._ID, ObaContract.RegionBounds._ID);
        sRegionBoundsProjectionMap
                .put(ObaContract.RegionBounds.REGION_ID, ObaContract.RegionBounds.REGION_ID);
        sRegionBoundsProjectionMap
                .put(ObaContract.RegionBounds.LATITUDE, ObaContract.RegionBounds.LATITUDE);
        sRegionBoundsProjectionMap
                .put(ObaContract.RegionBounds.LONGITUDE, ObaContract.RegionBounds.LONGITUDE);
        sRegionBoundsProjectionMap
                .put(ObaContract.RegionBounds.LAT_SPAN, ObaContract.RegionBounds.LAT_SPAN);
        sRegionBoundsProjectionMap
                .put(ObaContract.RegionBounds.LON_SPAN, ObaContract.RegionBounds.LON_SPAN);

        sRegionOpen311ProjectionMap = new HashMap<String, String>();
        sRegionOpen311ProjectionMap
                .put(ObaContract.RegionOpen311Servers._ID, ObaContract.RegionOpen311Servers._ID);
        sRegionOpen311ProjectionMap
                .put(ObaContract.RegionOpen311Servers.REGION_ID, ObaContract.RegionOpen311Servers.REGION_ID);
        sRegionOpen311ProjectionMap
                .put(ObaContract.RegionOpen311Servers.JURISDICTION, ObaContract.RegionOpen311Servers.JURISDICTION);
        sRegionOpen311ProjectionMap
                .put(ObaContract.RegionOpen311Servers.API_KEY, ObaContract.RegionOpen311Servers.API_KEY);
        sRegionOpen311ProjectionMap
                .put(ObaContract.RegionOpen311Servers.BASE_URL, ObaContract.RegionOpen311Servers.BASE_URL);
    }

    private SQLiteDatabase mDb;

    private OpenHelper mOpenHelper;

    public static File getDatabasePath(Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }

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
            case TRIP_ALERTS:
                return ObaContract.TripAlerts.CONTENT_DIR_TYPE;
            case TRIP_ALERTS_ID:
                return ObaContract.TripAlerts.CONTENT_TYPE;
            case STOP_ROUTE_FILTERS:
                return ObaContract.StopRouteFilters.CONTENT_DIR_TYPE;
            case SERVICE_ALERTS:
                return ObaContract.ServiceAlerts.CONTENT_DIR_TYPE;
            case SERVICE_ALERTS_ID:
                return ObaContract.ServiceAlerts.CONTENT_TYPE;
            case REGIONS:
                return ObaContract.Regions.CONTENT_DIR_TYPE;
            case REGIONS_ID:
                return ObaContract.Regions.CONTENT_TYPE;
            case REGION_BOUNDS:
                return ObaContract.RegionBounds.CONTENT_DIR_TYPE;
            case REGION_BOUNDS_ID:
                return ObaContract.RegionBounds.CONTENT_TYPE;
            case REGION_OPEN311_SERVERS:
                return ObaContract.RegionOpen311Servers.CONTENT_DIR_TYPE;
            case REGION_OPEN311_SERVERS_ID:
                return ObaContract.RegionOpen311Servers.CONTENT_TYPE;
            case ROUTE_HEADSIGN_FAVORITES:
                return ObaContract.RouteHeadsignFavorites.CONTENT_DIR_TYPE;
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
        } finally {
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
        } finally {
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
        } finally {
            db.endTransaction();
        }
    }

    private Uri insertInternal(SQLiteDatabase db, Uri uri, ContentValues values) {
        final int match = sUriMatcher.match(uri);
        String id;
        Uri result;
        long longId;

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

            case TRIP_ALERTS:
                longId = mTripAlertsInserter.insert(values);
                result = ContentUris.withAppendedId(ObaContract.TripAlerts.CONTENT_URI, longId);
                return result;

            case STOP_ROUTE_FILTERS:
                // TODO: We should provide a "virtual" column that is an array,
                // so clients don't have to call the content provider for each item to insert.
                // Pull out the stop ID from the values to construct the new URI
                // (And we'd better have a stop ID)
                id = values.getAsString(ObaContract.StopRouteFilters.STOP_ID);
                if (id == null) {
                    throw new IllegalArgumentException("Need a stop ID to insert! " + uri);
                }
                result = Uri.withAppendedPath(ObaContract.StopRouteFilters.CONTENT_URI, id);
                mFilterInserter.insert(values);
                return result;

            case SERVICE_ALERTS:
                // Pull out the Situation ID from the values to construct the new URI
                // (And we'd better have a situation ID)
                id = values.getAsString(ObaContract.ServiceAlerts._ID);
                if (id == null) {
                    throw new IllegalArgumentException("Need a situation ID to insert! " + uri);
                }
                result = Uri.withAppendedPath(ObaContract.ServiceAlerts.CONTENT_URI, id);
                mServiceAlertsInserter.insert(values);
                return result;

            case REGIONS:
                longId = mRegionsInserter.insert(values);
                result = ContentUris.withAppendedId(ObaContract.Regions.CONTENT_URI, longId);
                return result;

            case REGION_BOUNDS:
                longId = mRegionBoundsInserter.insert(values);
                result = ContentUris.withAppendedId(ObaContract.RegionBounds.CONTENT_URI, longId);
                return result;

            case REGION_OPEN311_SERVERS:
                longId = mRegionOpen311ServersInserter.insert(values);
                result = ContentUris.withAppendedId(ObaContract.RegionOpen311Servers.CONTENT_URI, longId);
                return result;

            case ROUTE_HEADSIGN_FAVORITES:
                id = values.getAsString(ObaContract.RouteHeadsignFavorites.ROUTE_ID);
                if (id == null) {
                    throw new IllegalArgumentException("Need a route ID to insert! " + uri);
                }
                result = Uri.withAppendedPath(ObaContract.RouteHeadsignFavorites.CONTENT_URI, id);
                mRouteHeadsignFavoritesInserter.insert(values);
                return result;

            // What would these mean, anyway??
            case STOPS_ID:
            case ROUTES_ID:
            case TRIPS_ID:
            case TRIP_ALERTS_ID:
            case SERVICE_ALERTS_ID:
            case REGIONS_ID:
            case REGION_BOUNDS_ID:
                throw new UnsupportedOperationException("Cannot insert to this URI: " + uri);
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    private Cursor queryInternal(SQLiteDatabase db,
                                 Uri uri, String[] projection, String selection,
                                 String[] selectionArgs, String sortOrder) {
        final int match = sUriMatcher.match(uri);
        final String limit = uri.getQueryParameter("limit");

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (match) {
            case STOPS:
                qb.setTables(ObaContract.Stops.PATH);
                qb.setProjectionMap(sStopsProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case STOPS_ID:
                qb.setTables(ObaContract.Stops.PATH);
                qb.setProjectionMap(sStopsProjectionMap);
                qb.appendWhere(ObaContract.Stops._ID);
                qb.appendWhere("=");
                qb.appendWhereEscapeString(uri.getLastPathSegment());
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case ROUTES:
                qb.setTables(ObaContract.Routes.PATH);
                qb.setProjectionMap(sRoutesProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case ROUTES_ID:
                qb.setTables(ObaContract.Routes.PATH);
                qb.setProjectionMap(sRoutesProjectionMap);
                qb.appendWhere(ObaContract.Routes._ID);
                qb.appendWhere("=");
                qb.appendWhereEscapeString(uri.getLastPathSegment());
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case TRIPS:
                qb.setTables(ObaContract.Trips.PATH);
                qb.setProjectionMap(sTripsProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case TRIPS_ID:
                qb.setTables(ObaContract.Trips.PATH);
                qb.setProjectionMap(sTripsProjectionMap);
                qb.appendWhere(tripWhere(uri));
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case TRIP_ALERTS:
                qb.setTables(ObaContract.TripAlerts.PATH);
                qb.setProjectionMap(sTripAlertsProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case TRIP_ALERTS_ID:
                qb.setTables(ObaContract.TripAlerts.PATH);
                qb.setProjectionMap(sTripAlertsProjectionMap);
                qb.appendWhere(ObaContract.TripAlerts._ID);
                qb.appendWhere("=");
                qb.appendWhere(String.valueOf(ContentUris.parseId(uri)));
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case STOP_ROUTE_FILTERS:
                qb.setTables(ObaContract.StopRouteFilters.PATH);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case SERVICE_ALERTS:
                qb.setTables(ObaContract.ServiceAlerts.PATH);
                qb.setProjectionMap(sServiceAlertsProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case SERVICE_ALERTS_ID:
                qb.setTables(ObaContract.ServiceAlerts.PATH);
                qb.setProjectionMap(sServiceAlertsProjectionMap);
                qb.appendWhere(ObaContract.ServiceAlerts._ID);
                qb.appendWhere("=");
                qb.appendWhereEscapeString(uri.getLastPathSegment());
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case REGIONS:
                qb.setTables(ObaContract.Regions.PATH);
                qb.setProjectionMap(sRegionsProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case REGIONS_ID:
                qb.setTables(ObaContract.Regions.PATH);
                qb.setProjectionMap(sRegionsProjectionMap);
                qb.appendWhere(ObaContract.Regions._ID);
                qb.appendWhere("=");
                qb.appendWhere(String.valueOf(ContentUris.parseId(uri)));
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case REGION_BOUNDS:
                qb.setTables(ObaContract.RegionBounds.PATH);
                qb.setProjectionMap(sRegionBoundsProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case REGION_BOUNDS_ID:
                qb.setTables(ObaContract.RegionBounds.PATH);
                qb.setProjectionMap(sRegionBoundsProjectionMap);
                qb.appendWhere(ObaContract.RegionBounds._ID);
                qb.appendWhere("=");
                qb.appendWhere(String.valueOf(ContentUris.parseId(uri)));
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case REGION_OPEN311_SERVERS:
                qb.setTables(ObaContract.RegionOpen311Servers.PATH);
                qb.setProjectionMap(sRegionOpen311ProjectionMap);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case REGION_OPEN311_SERVERS_ID:
                qb.setTables(ObaContract.RegionOpen311Servers.PATH);
                qb.setProjectionMap(sRegionOpen311ProjectionMap);
                qb.appendWhere(ObaContract.RegionOpen311Servers._ID);
                qb.appendWhere("=");
                qb.appendWhere(String.valueOf(ContentUris.parseId(uri)));
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);

            case ROUTE_HEADSIGN_FAVORITES:
                qb.setTables(ObaContract.RouteHeadsignFavorites.PATH);
                return qb.query(mDb, projection, selection, selectionArgs,
                        null, null, sortOrder, limit);
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

            case TRIP_ALERTS:
                return db.update(ObaContract.TripAlerts.PATH, values, selection, selectionArgs);

            case TRIP_ALERTS_ID:
                return db.update(ObaContract.TripAlerts.PATH, values,
                        whereLong(ObaContract.TripAlerts._ID, uri), selectionArgs);

            // Can we do anything here??
            case STOP_ROUTE_FILTERS:
                return 0;

            case SERVICE_ALERTS:
                return db.update(ObaContract.ServiceAlerts.PATH, values, selection, selectionArgs);

            case SERVICE_ALERTS_ID:
                return db.update(ObaContract.ServiceAlerts.PATH, values,
                        where(ObaContract.ServiceAlerts._ID, uri), selectionArgs);

            case REGIONS:
                return db.update(ObaContract.Regions.PATH, values, selection, selectionArgs);

            case REGIONS_ID:
                return db.update(ObaContract.Regions.PATH, values,
                        whereLong(ObaContract.Regions._ID, uri), selectionArgs);

            case REGION_BOUNDS:
                return db.update(ObaContract.RegionBounds.PATH, values, selection, selectionArgs);

            case REGION_BOUNDS_ID:
                return db.update(ObaContract.RegionBounds.PATH, values,
                        whereLong(ObaContract.RegionBounds._ID, uri), selectionArgs);

            case REGION_OPEN311_SERVERS:
                return db.update(ObaContract.RegionOpen311Servers.PATH, values, selection, selectionArgs);

            case REGION_OPEN311_SERVERS_ID:
                return db.update(ObaContract.RegionOpen311Servers.PATH, values,
                        whereLong(ObaContract.RegionOpen311Servers._ID, uri), selectionArgs);

            case ROUTE_HEADSIGN_FAVORITES:
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

            case TRIP_ALERTS:
                return db.delete(ObaContract.TripAlerts.PATH, selection, selectionArgs);

            case TRIP_ALERTS_ID:
                return db.delete(ObaContract.TripAlerts.PATH,
                        whereLong(ObaContract.TripAlerts._ID, uri), selectionArgs);

            case STOP_ROUTE_FILTERS:
                return db.delete(ObaContract.StopRouteFilters.PATH, selection, selectionArgs);

            case SERVICE_ALERTS:
                return db.delete(ObaContract.ServiceAlerts.PATH, selection, selectionArgs);

            case SERVICE_ALERTS_ID:
                return db.delete(ObaContract.ServiceAlerts.PATH,
                        where(ObaContract.ServiceAlerts._ID, uri), selectionArgs);

            case REGIONS:
                return db.delete(ObaContract.Regions.PATH, selection, selectionArgs);

            case REGIONS_ID:
                return db.delete(ObaContract.Regions.PATH,
                        whereLong(ObaContract.Regions._ID, uri), selectionArgs);

            case REGION_BOUNDS:
                return db.delete(ObaContract.RegionBounds.PATH, selection, selectionArgs);

            case REGION_BOUNDS_ID:
                return db.delete(ObaContract.RegionBounds.PATH,
                        whereLong(ObaContract.RegionBounds._ID, uri), selectionArgs);

            case REGION_OPEN311_SERVERS:
                return db.delete(ObaContract.RegionOpen311Servers.PATH, selection, selectionArgs);

            case REGION_OPEN311_SERVERS_ID:
                return db.delete(ObaContract.RegionOpen311Servers.PATH,
                        whereLong(ObaContract.RegionOpen311Servers._ID, uri), selectionArgs);

            case ROUTE_HEADSIGN_FAVORITES:
                return db.delete(ObaContract.RouteHeadsignFavorites.PATH, selection, selectionArgs);

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

    private String whereLong(String column, Uri uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(column);
        sb.append('=');
        sb.append(String.valueOf(ContentUris.parseId(uri)));
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
            mTripAlertsInserter = new DatabaseUtils.InsertHelper(mDb, ObaContract.TripAlerts.PATH);
            mFilterInserter = new DatabaseUtils.InsertHelper(mDb,
                    ObaContract.StopRouteFilters.PATH);
            mServiceAlertsInserter = new DatabaseUtils.InsertHelper(mDb,
                    ObaContract.ServiceAlerts.PATH);
            mRegionsInserter = new DatabaseUtils.InsertHelper(mDb, ObaContract.Regions.PATH);
            mRegionBoundsInserter = new DatabaseUtils.InsertHelper(mDb,
                    ObaContract.RegionBounds.PATH);
            mRegionOpen311ServersInserter = new DatabaseUtils.InsertHelper(mDb,
                    ObaContract.RegionOpen311Servers.PATH);
            mRouteHeadsignFavoritesInserter = new DatabaseUtils.InsertHelper(mDb,
                    ObaContract.RouteHeadsignFavorites.PATH);
        }
        return mDb;
    }

    //
    // Closes the database
    //
    public void closeDB() {
        mOpenHelper.close();
        mDb = null;
    }
}
