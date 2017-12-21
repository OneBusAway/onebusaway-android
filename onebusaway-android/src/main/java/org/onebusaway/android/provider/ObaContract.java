/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Benjamin Du (bendu@me.com),
 * Microsoft Corporation.
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
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRegionElement;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.Time;

import java.util.ArrayList;

/**
 * The contract between clients and the ObaProvider.
 *
 * This really needs to be documented better.
 *
 * NOTE: The AUTHORITY names in this class cannot be changed.  They need to stay under the
 * BuildConfig.DATABASE_AUTHORITY namespace (for the original OBA brand, "com.joulespersecond.oba")
 * namespace to support backwards compatibility with existing installed apps
 *
 * @author paulw
 */
public final class ObaContract {

    public static final String TAG = "ObaContract";

    /** The authority portion of the URI - defined in build.gradle */
    public static final String AUTHORITY = BuildConfig.DATABASE_AUTHORITY;

    /** The base URI for the Oba provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    protected interface StopsColumns {

        /**
         * The code for the stop, e.g. 001_123
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String CODE = "code";

        /**
         * The user specified name of the stop, e.g. "13th Ave E & John St"
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String NAME = "name";

        /**
         * The stop direction, one of: N, NE, NW, E, SE, SW, S, W or null
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String DIRECTION = "direction";

        /**
         * The latitude of the stop location.
         * <P>
         * Type: DOUBLE
         * </P>
         */
        public static final String LATITUDE = "latitude";

        /**
         * The longitude of the stop location.
         * <P>
         * Type: DOUBLE
         * </P>
         */
        public static final String LONGITUDE = "longitude";

        /**
         * The region ID
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String REGION_ID = "region_id";
    }

    protected interface RoutesColumns {

        /**
         * The short name of the route, e.g. "10"
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String SHORTNAME = "short_name";

        /**
         * The long name of the route, e.g "Downtown to U-District"
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String LONGNAME = "long_name";

        /**
         * Returns the URL of the route schedule.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String URL = "url";

        /**
         * The region ID
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String REGION_ID = "region_id";
    }

    protected interface StopRouteKeyColumns {

        /**
         * The referenced Stop ID. This may or may not represent a key in the
         * Stops table.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String STOP_ID = "stop_id";

        /**
         * The referenced Route ID. This may or may not represent a key in the
         * Routes table.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String ROUTE_ID = "route_id";
    }

    protected interface StopRouteFilterColumns extends StopRouteKeyColumns {
        // No additional columns
    }

    protected interface TripsColumns extends StopRouteKeyColumns {

        /**
         * The scheduled departure time for the trip in milliseconds.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String DEPARTURE = "departure";

        /**
         * The headsign of the trip, e.g., "Capitol Hill"
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String HEADSIGN = "headsign";

        /**
         * The user specified name of the trip.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String NAME = "name";

        /**
         * The number of minutes before the arrival to notify the user
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String REMINDER = "reminder";

        /**
         * A bitmask representing the days the reminder should be used.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String DAYS = "days";
    }

    protected interface TripAlertsColumns {

        /**
         * The trip_id key of the corresponding trip.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String TRIP_ID = "trip_id";
        /**
         * The stop_id key of the corresponding trip.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String STOP_ID = "stop_id";

        /**
         * The time in milliseconds to begin the polling. Unlike the "reminder"
         * time in the Trips columns, this represents a specific time.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String START_TIME = "start_time";

        /**
         * The state of the the alert. Can be SCHEDULED, POLLING, NOTIFY,
         * CANCELED
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String STATE = "state";
    }

    protected interface UserColumns {

        /**
         * The number of times this resource has been accessed by the user.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String USE_COUNT = "use_count";

        /**
         * The user specified name given to this resource.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String USER_NAME = "user_name";

        /**
         * The last time the user accessed the resource.
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String ACCESS_TIME = "access_time";

        /**
         * Whether or not the resource is marked as a favorite (starred)
         * <P>
         * Type: INTEGER (1 or 0)
         * </P>
         */
        public static final String FAVORITE = "favorite";

        /**
         * This returns the user specified name, or the default name.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String UI_NAME = "ui_name";
    }

    protected interface ServiceAlertsColumns {

        /**
         * The time it was marked as read.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String MARKED_READ_TIME = "marked_read_time";

        /**
         * Whether or not the alert has been hidden by the user.
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String HIDDEN = "hidden";
    }

    protected interface RegionsColumns {

        /**
         * The name of the region.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String NAME = "name";

        /**
         * The base OBA URL.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String OBA_BASE_URL = "oba_base_url";

        /**
         * The base SIRI URL.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String SIRI_BASE_URL = "siri_base_url";

        /**
         * The locale of the API server.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String LANGUAGE = "lang";

        /**
         * The email of the person responsible for this server.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String CONTACT_EMAIL = "contact_email";

        /**
         * Whether or not the server supports OBA discovery APIs.
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String SUPPORTS_OBA_DISCOVERY = "supports_api_discovery";

        /**
         * Whether or not the server supports OBA realtime APIs.
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String SUPPORTS_OBA_REALTIME = "supports_api_realtime";

        /**
         * Whether or not the server supports SIRI realtime APIs.
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String SUPPORTS_SIRI_REALTIME = "supports_siri_realtime";

        /**
         * The Twitter URL for the region.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String TWITTER_URL = "twitter_url";

        /**
         * Whether or not the server is experimental (i.e., not production).
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String EXPERIMENTAL = "experimental";

        /**
         * The StopInfo URL for the region (see #103)
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String STOP_INFO_URL = "stop_info_url";

        /**
         * The OpenTripPlanner URL for the region
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String OTP_BASE_URL = "otp_base_url";

        /**
         * The email of the person responsible for the OTP server.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String OTP_CONTACT_EMAIL = "otp_contact_email";

        /**
         * Whether or not the region supports bikeshare information through integration with OTP
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String SUPPORTS_OTP_BIKESHARE = "supports_otp_bikeshare";

        /**
         * Whether or not the server supports Embedded Social
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String SUPPORTS_EMBEDDED_SOCIAL = "supports_embedded_social";
    }

    protected interface RegionBoundsColumns {

        /**
         * The region ID
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String REGION_ID = "region_id";

        /**
         * The latitude center of the agencies coverage area
         * <P>
         * Type: REAL
         * </P>
         */
        public static final String LATITUDE = "lat";

        /**
         * The longitude center of the agencies coverage area
         * <P>
         * Type: REAL
         * </P>
         */
        public static final String LONGITUDE = "lon";

        /**
         * The height of the agencies bounding box
         * <P>
         * Type: REAL
         * </P>
         */
        public static final String LAT_SPAN = "lat_span";

        /**
         * The width of the agencies bounding box
         * <P>
         * Type: REAL
         * </P>
         */
        public static final String LON_SPAN = "lon_span";

    }

    protected interface RegionOpen311ServersColumns {

        /**
         * The region ID
         * <P>
         * Type: INTEGER
         * </P>
         */
        public static final String REGION_ID = "region_id";

        /**
         * The jurisdiction id of the open311 server
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String JURISDICTION = "jurisdiction";

        /**
         * The api key of the open311 server
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String API_KEY = "api_key";

        /**
         * The url of the open311 server
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String BASE_URL = "open311_base_url";

    }

    protected interface RouteHeadsignKeyColumns extends StopRouteKeyColumns {

        /**
         * The referenced headsign. This may or may not represent a value in the
         * Trips table.
         * <P>
         * Type: TEXT
         * </P>
         */
        public static final String HEADSIGN = "headsign";

        /**
         * Whether or not this stop should be excluded as a favorite.  This is to allow a user to
         * star a route/headsign for all stops, and then remove the star from selected stops.
         * <P>
         * Type: BOOLEAN
         * </P>
         */
        public static final String EXCLUDE = "exclude";
    }

    public static class Stops implements BaseColumns, StopsColumns, UserColumns {

        // Cannot be instantiated
        private Stops() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "stops";

        /** The content:// style URI for this table */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".stop";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".stop";

        public static Uri insertOrUpdate(String id,
                ContentValues values,
                boolean markAsUsed) {
            ContentResolver cr = Application.get().getContentResolver();
            final Uri uri = Uri.withAppendedPath(CONTENT_URI, id);
            Cursor c = cr.query(uri, new String[]{USE_COUNT}, null, null,
                    null);
            Uri result;
            if (c != null && c.getCount() > 0) {
                // Update
                if (markAsUsed) {
                    c.moveToFirst();
                    int count = c.getInt(0);
                    values.put(USE_COUNT, count + 1);
                    values.put(ACCESS_TIME, System.currentTimeMillis());
                }
                cr.update(uri, values, null, null);
                result = uri;
            } else {
                // Insert
                if (markAsUsed) {
                    values.put(USE_COUNT, 1);
                    values.put(ACCESS_TIME, System.currentTimeMillis());
                } else {
                    values.put(USE_COUNT, 0);
                }
                values.put(_ID, id);
                result = cr.insert(CONTENT_URI, values);
            }
            if (c != null) {
                c.close();
            }
            return result;
        }

        public static boolean markAsFavorite(Context context,
                Uri uri,
                boolean favorite) {
            ContentResolver cr = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(ObaContract.Stops.FAVORITE, favorite ? 1 : 0);
            return cr.update(uri, values, null, null) > 0;
        }

        public static boolean markAsUnused(Context context, Uri uri) {
            ContentResolver cr = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(ObaContract.Stops.USE_COUNT, 0);
            values.putNull(ObaContract.Stops.ACCESS_TIME);
            return cr.update(uri, values, null, null) > 0;
        }
    }

    public static class Routes implements BaseColumns, RoutesColumns,
            UserColumns {

        // Cannot be instantiated
        private Routes() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "routes";

        /** The content:// style URI for this table */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".route";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".route";

        public static Uri insertOrUpdate(Context context,
                String id,
                ContentValues values,
                boolean markAsUsed) {
            ContentResolver cr = context.getContentResolver();
            final Uri uri = Uri.withAppendedPath(CONTENT_URI, id);
            Cursor c = cr.query(uri, new String[]{USE_COUNT}, null, null,
                    null);
            Uri result;
            if (c != null && c.getCount() > 0) {
                // Update
                if (markAsUsed) {
                    c.moveToFirst();
                    int count = c.getInt(0);
                    values.put(USE_COUNT, count + 1);
                    values.put(ACCESS_TIME, System.currentTimeMillis());
                }
                cr.update(uri, values, null, null);
                result = uri;
            } else {
                // Insert
                if (markAsUsed) {
                    values.put(USE_COUNT, 1);
                    values.put(ACCESS_TIME, System.currentTimeMillis());
                } else {
                    values.put(USE_COUNT, 0);
                }
                values.put(_ID, id);
                result = cr.insert(CONTENT_URI, values);
            }
            if (c != null) {
                c.close();
            }
            return result;
        }

        protected static boolean markAsFavorite(Context context,
                Uri uri,
                boolean favorite) {
            ContentResolver cr = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(ObaContract.Routes.FAVORITE, favorite ? 1 : 0);
            return cr.update(uri, values, null, null) > 0;
        }

        public static boolean markAsUnused(Context context, Uri uri) {
            ContentResolver cr = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(ObaContract.Routes.USE_COUNT, 0);
            values.putNull(ObaContract.Routes.ACCESS_TIME);
            return cr.update(uri, values, null, null) > 0;
        }

        /**
         * Returns true if this route is a favorite, false if it does not
         *
         * Note that this is NOT specific to headsign.  If you want to know if a combination of a
         * routeId and headsign is a user favorite, see
         * RouteHeadsignFavorites.isFavorite(context, routeId, headsign).
         *
         * @param routeUri Uri for a route
         * @return true if this route is a favorite, false if it does not
         */
        public static boolean isFavorite(Context context, Uri routeUri) {
            ContentResolver cr = context.getContentResolver();
            String[] ROUTE_USER_PROJECTION = {ObaContract.Routes.FAVORITE};
            Cursor c = cr.query(routeUri, ROUTE_USER_PROJECTION, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToNext()) {
                        return (c.getInt(0) == 1);
                    }
                } finally {
                    c.close();
                }
            }
            // If we get this far, assume its not
            return false;
        }
    }

    public static class StopRouteFilters implements StopRouteFilterColumns {

        // Cannot be instantiated
        private StopRouteFilters() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "stop_routes_filter";

        /** The content:// style URI for this table */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".stoproutefilter";

        private static final String FILTER_WHERE = STOP_ID + "=?";

        /**
         * Gets the filter for the specified Stop ID.
         *
         * @param context The context.
         * @param stopId  The stop ID.
         * @return The filter. If there is no filter (or on error), it returns
         * an empty list.
         */
        public static ArrayList<String> get(Context context, String stopId) {
            final String[] selection = {ROUTE_ID};
            final String[] selectionArgs = {stopId};
            ContentResolver cr = context.getContentResolver();
            Cursor c = cr.query(CONTENT_URI, selection, FILTER_WHERE,
                    selectionArgs, null);
            ArrayList<String> result = new ArrayList<String>();
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        result.add(c.getString(0));
                    }
                } finally {
                    c.close();
                }
            }
            return result;
        }

        /**
         * Sets the filter for the particular stop ID.
         *
         * @param context The context.
         * @param stopId  The stop ID.
         * @param filter  An array of route IDs to filter.
         */
        public static void set(Context context,
                String stopId,
                ArrayList<String> filter) {
            if (context == null) {
                return;
            }
            // First, delete any existing rows for this stop.
            // Then, insert all of these rows.
            // Should we put this in a transaction? We could,
            // but it's not terribly important.
            final String[] selectionArgs = {stopId};
            ContentResolver cr = context.getContentResolver();
            cr.delete(CONTENT_URI, FILTER_WHERE, selectionArgs);

            ContentValues args = new ContentValues();
            args.put(STOP_ID, stopId);
            final int len = filter.size();
            for (int i = 0; i < len; ++i) {
                args.put(ROUTE_ID, filter.get(i));
                cr.insert(CONTENT_URI, args);
            }
        }
    }

    public static class Trips implements BaseColumns, StopRouteKeyColumns,
            TripsColumns {

        // Cannot be instantiated
        private Trips() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "trips";

        /**
         * The content:// style URI for this table URI is of the form
         * content://<authority>/trips/<tripId>/<stopId>
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".trip";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".trip";

        public static final int DAY_MON = 0x1;

        public static final int DAY_TUE = 0x2;

        public static final int DAY_WED = 0x4;

        public static final int DAY_THU = 0x8;

        public static final int DAY_FRI = 0x10;

        public static final int DAY_SAT = 0x20;

        public static final int DAY_SUN = 0x40;

        public static final int DAY_WEEKDAY = DAY_MON | DAY_TUE | DAY_WED
                | DAY_THU | DAY_FRI;

        public static final int DAY_ALL = DAY_WEEKDAY | DAY_SUN | DAY_SAT;

        public static Uri buildUri(String tripId, String stopId) {
            return CONTENT_URI.buildUpon().appendPath(tripId)
                    .appendPath(stopId).build();
        }

        /**
         * Converts a days bitmask into a boolean[] array
         *
         * @param days A DB compatible days bitmask.
         * @return A boolean array representing the days set in the bitmask,
         * Mon=0 to Sun=6
         */
        public static boolean[] daysToArray(int days) {
            final boolean[] result = {
                    (days & ObaContract.Trips.DAY_MON) == ObaContract.Trips.DAY_MON,
                    (days & ObaContract.Trips.DAY_TUE) == ObaContract.Trips.DAY_TUE,
                    (days & ObaContract.Trips.DAY_WED) == ObaContract.Trips.DAY_WED,
                    (days & ObaContract.Trips.DAY_THU) == ObaContract.Trips.DAY_THU,
                    (days & ObaContract.Trips.DAY_FRI) == ObaContract.Trips.DAY_FRI,
                    (days & ObaContract.Trips.DAY_SAT) == ObaContract.Trips.DAY_SAT,
                    (days & ObaContract.Trips.DAY_SUN) == ObaContract.Trips.DAY_SUN,};
            return result;
        }

        /**
         * Converts a boolean[] array to a DB compatible days bitmask
         *
         * @param days boolean array as returned by daysToArray
         * @return A DB compatible days bitmask
         */
        public static int arrayToDays(boolean[] days) {
            if (days.length != 7) {
                throw new IllegalArgumentException("days.length must be 7");
            }
            int result = 0;
            for (int i = 0; i < days.length; ++i) {
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
            // This converts the minutes-to-midnight to a time of the current
            // day.
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
            return t.hour * 60 + t.minute;
        }

        /**
         * Converts a weekday value from a android.text.format.Time to a bit.
         *
         * @param weekday The weekDay value from android.text.format.Time
         * @return A DB compatible bit.
         */
        public static int getDayBit(int weekday) {
            switch (weekday) {
                case Time.MONDAY:
                    return ObaContract.Trips.DAY_MON;
                case Time.TUESDAY:
                    return ObaContract.Trips.DAY_TUE;
                case Time.WEDNESDAY:
                    return ObaContract.Trips.DAY_WED;
                case Time.THURSDAY:
                    return ObaContract.Trips.DAY_THU;
                case Time.FRIDAY:
                    return ObaContract.Trips.DAY_FRI;
                case Time.SATURDAY:
                    return ObaContract.Trips.DAY_SAT;
                case Time.SUNDAY:
                    return ObaContract.Trips.DAY_SUN;
            }
            return 0;
        }
    }

    public static class TripAlerts implements BaseColumns, TripAlertsColumns {

        // Cannot be instantiated
        private TripAlerts() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "trip_alerts";

        /**
         * The content:// style URI for this table URI is of the form
         * content://<authority>/trip_alerts/<id>
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".trip_alert";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".trip_alert";

        public static final int STATE_SCHEDULED = 0;

        public static final int STATE_POLLING = 1;

        public static final int STATE_NOTIFY = 2;

        public static final int STATE_CANCELLED = 3;

        public static Uri buildUri(int id) {
            return CONTENT_URI.buildUpon().appendPath(String.valueOf(id))
                    .build();
        }

        public static Uri insertIfNotExists(Context context,
                String tripId,
                String stopId,
                long startTime) {
            return insertIfNotExists(context.getContentResolver(), tripId,
                    stopId, startTime);
        }

        public static Uri insertIfNotExists(ContentResolver cr,
                String tripId,
                String stopId,
                long startTime) {
            Uri result;
            Cursor c = cr.query(CONTENT_URI,
                    new String[]{_ID},
                    String.format("%s=? AND %s=? AND %s=?",
                            TRIP_ID, STOP_ID, START_TIME),
                    new String[]{tripId, stopId, String.valueOf(startTime)},
                    null);
            if (c != null && c.moveToNext()) {
                result = buildUri(c.getInt(0));
            } else {
                ContentValues values = new ContentValues();
                values.put(TRIP_ID, tripId);
                values.put(STOP_ID, stopId);
                values.put(START_TIME, startTime);
                result = cr.insert(CONTENT_URI, values);
            }
            if (c != null) {
                c.close();
            }
            return result;
        }

        public static void setState(Context context, Uri uri, int state) {
            setState(context.getContentResolver(), uri, state);
        }

        public static void setState(ContentResolver cr, Uri uri, int state) {
            ContentValues values = new ContentValues();
            values.put(STATE, state);
            cr.update(uri, values, null, null);
        }
    }

    public static class ServiceAlerts implements BaseColumns, ServiceAlertsColumns {

        // Cannot be instantiated
        private ServiceAlerts() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "service_alerts";

        /**
         * The content:// style URI for this table URI is of the form
         * content://<authority>/service_alerts/<id>
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".service_alert";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".service_alert";

        /**
         * @param markAsRead true if this alert should be marked as read with the timestamp of
         *                   System.currentTimeMillis(),
         *                   false if the alert should not be marked as read with the timestamp of
         *                   System.currentTimeMillis()
         * @param hidden  true if this alert should be marked as hidden by the user, false if
         *                   it should be marked as not
         *                   hidden by the user, or null if the hidden value shouldn't be
         *                   changed
         */
        public static Uri insertOrUpdate(String id,
                ContentValues values,
                boolean markAsRead,
                Boolean hidden) {
            if (id == null) {
                return null;
            }
            if (values == null) {
                values = new ContentValues();
            }
            ContentResolver cr = Application.get().getContentResolver();
            final Uri uri = Uri.withAppendedPath(CONTENT_URI, id);
            Cursor c = cr.query(uri, new String[]{}, null, null, null);
            Uri result;
            if (c != null && c.getCount() > 0) {
                // Update
                if (markAsRead) {
                    c.moveToFirst();
                    values.put(MARKED_READ_TIME, System.currentTimeMillis());
                }
                if (hidden != null) {
                    c.moveToFirst();
                    if (hidden) {
                        values.put(HIDDEN, 1);
                    } else {
                        values.put(HIDDEN, 0);
                    }
                }
                if (values.size() != 0) {
                    cr.update(uri, values, null, null);
                }
                result = uri;
            } else {
                // Insert
                if (markAsRead) {
                    values.put(MARKED_READ_TIME, System.currentTimeMillis());
                }
                if (hidden != null) {
                    if (hidden) {
                        values.put(HIDDEN, 1);
                    } else {
                        values.put(HIDDEN, 0);
                    }
                }
                values.put(_ID, id);
                result = cr.insert(CONTENT_URI, values);
            }
            if (c != null) {
                c.close();
            }
            return result;
        }

        /**
         * Returns true if this service alert (situation) has been previously hidden by the
         * user, false it if has not
         *
         * @param situationId The ID of the situation (service alert)
         * @return true if this service alert (situation) has been previously hidden by the user,
         * false it if has not
         */
        public static boolean isHidden(String situationId) {
            final String[] selection = {_ID, HIDDEN};
            final String[] selectionArgs = {situationId, Integer.toString(1)};
            final String WHERE = _ID + "=? AND " + HIDDEN + "=?";
            ContentResolver cr = Application.get().getContentResolver();
            Cursor c = cr.query(CONTENT_URI, selection, WHERE, selectionArgs, null);
            boolean hidden;
            if (c != null && c.getCount() > 0) {
                hidden = true;
            } else {
                hidden = false;
            }
            if (c != null) {
                c.close();
            }
            return hidden;
        }

        /**
         * Marks all alerts as not hidden, and therefore visible
         *
         * @return the number of rows updated
         */
        public static int showAllAlerts() {
            ContentResolver cr = Application.get().getContentResolver();
            ContentValues values = new ContentValues();
            values.put(HIDDEN, 0);
            return cr.update(CONTENT_URI, values, null, null);
        }
    }

    public static class Regions implements BaseColumns, RegionsColumns {

        // Cannot be instantiated
        private Regions() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "regions";

        /**
         * The content:// style URI for this table URI is of the form
         * content://<authority>/regions/<id>
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".region";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".region";

        public static Uri buildUri(int id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }

        public static Uri insertOrUpdate(Context context,
                int id,
                ContentValues values) {
            return insertOrUpdate(context.getContentResolver(), id, values);
        }

        public static Uri insertOrUpdate(ContentResolver cr,
                int id,
                ContentValues values) {
            final Uri uri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(id));
            Cursor c = cr.query(uri, new String[]{}, null, null, null);
            Uri result;
            if (c != null && c.getCount() > 0) {
                cr.update(uri, values, null, null);
                result = uri;
            } else {
                values.put(_ID, id);
                result = cr.insert(CONTENT_URI, values);
            }
            if (c != null) {
                c.close();
            }
            return result;
        }

        public static ObaRegion get(Context context, int id) {
            return get(context.getContentResolver(), id);
        }

        public static ObaRegion get(ContentResolver cr, int id) {
            final String[] PROJECTION = {
                    _ID,
                    NAME,
                    OBA_BASE_URL,
                    SIRI_BASE_URL,
                    LANGUAGE,
                    CONTACT_EMAIL,
                    SUPPORTS_OBA_DISCOVERY,
                    SUPPORTS_OBA_REALTIME,
                    SUPPORTS_SIRI_REALTIME,
                    TWITTER_URL,
                    EXPERIMENTAL,
                    STOP_INFO_URL,
                    OTP_BASE_URL,
                    OTP_CONTACT_EMAIL,
                    SUPPORTS_OTP_BIKESHARE,
                    SUPPORTS_EMBEDDED_SOCIAL
            };

            Cursor c = cr.query(buildUri((int) id), PROJECTION, null, null, null);
            if (c != null) {
                try {
                    if (c.getCount() == 0) {
                        return null;
                    }
                    c.moveToFirst();
                    return new ObaRegionElement(id,   // id
                            c.getString(1),             // Name
                            true,                       // Active
                            c.getString(2),             // OBA Base URL
                            c.getString(3),             // SIRI Base URL
                            RegionBounds.getRegion(cr, id), // Bounds
                            RegionOpen311Servers.getOpen311Server(cr, id), // Open311 servers
                            c.getString(4),             // Lang
                            c.getString(5),             // Contact Email
                            c.getInt(6) > 0,            // Supports Oba Discovery
                            c.getInt(7) > 0,            // Supports Oba Realtime
                            c.getInt(8) > 0,            // Supports Siri Realtime
                            c.getString(9),              // Twitter URL
                            c.getInt(10) > 0,               // Experimental
                            c.getString(11),              // StopInfoUrl
                            c.getString(12),              // OtpBaseUrl
                            c.getString(13),               // OtpContactEmail
                            c.getInt(14) > 0,           // Supports OTP Bikeshare
                            c.getInt(15) > 0            // Supports Embedded Social
                    );
                } finally {
                    c.close();
                }
            }
            return null;
        }
    }

    public static class RegionBounds implements BaseColumns, RegionBoundsColumns {

        // Cannot be instantiated
        private RegionBounds() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "region_bounds";

        /**
         * The content:// style URI for this table URI is of the form
         * content://<authority>/region_bounds/<id>
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".region_bounds";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".region_bounds";

        public static Uri buildUri(int id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }

        public static ObaRegionElement.Bounds[] getRegion(ContentResolver cr, int regionId) {
            final String[] PROJECTION = {
                    LATITUDE,
                    LONGITUDE,
                    LAT_SPAN,
                    LON_SPAN
            };
            Cursor c = cr.query(CONTENT_URI, PROJECTION,
                    "(" + RegionBounds.REGION_ID + " = " + regionId + ")",
                    null, null);
            if (c != null) {
                try {
                    ObaRegionElement.Bounds[] results = new ObaRegionElement.Bounds[c.getCount()];
                    if (c.getCount() == 0) {
                        return results;
                    }

                    int i = 0;
                    c.moveToFirst();
                    do {
                        results[i] = new ObaRegionElement.Bounds(
                                c.getDouble(0),
                                c.getDouble(1),
                                c.getDouble(2),
                                c.getDouble(3));
                        i++;
                    } while (c.moveToNext());

                    return results;
                } finally {
                    c.close();
                }
            }
            return null;
        }
    }

    public static class RegionOpen311Servers implements BaseColumns, RegionOpen311ServersColumns {

        // Cannot be instantiated
        private RegionOpen311Servers() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "open311_servers";

        /**
         * The content:// style URI for this table URI is of the form
         * content://<authority>/region_open311_servers/<id>
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.item/" + BuildConfig.DATABASE_AUTHORITY + ".open311_servers";

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".open311_servers";

        public static Uri buildUri(int id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }

        public static ObaRegionElement.Open311Server[] getOpen311Server
                (ContentResolver cr, int regionId) {
            final String[] PROJECTION = {
                    JURISDICTION,
                    API_KEY,
                    BASE_URL
            };
            Cursor c = cr.query(CONTENT_URI, PROJECTION,
                    "(" + RegionOpen311Servers.REGION_ID + " = " + regionId + ")",
                    null, null);
            if (c != null) {
                try {
                    ObaRegionElement.Open311Server[] results = new ObaRegionElement.Open311Server[c.getCount()];
                    if (c.getCount() == 0) {
                        return results;
                    }

                    int i = 0;
                    c.moveToFirst();
                    do {
                        results[i] = new ObaRegionElement.Open311Server(
                                c.getString(0),
                                c.getString(1),
                                c.getString(2));
                        i++;
                    } while (c.moveToNext());

                    return results;
                } finally {
                    c.close();
                }
            }
            return null;
        }
    }

    /**
     * Supports storing user-defined favorites for route/headsign/stop combinations.  This is
     * currently implemented without requiring knowledge of a full set of stops for a route.  This
     * allows some flexibility in terms of changes server-side without invalidating user's
     * favorites - as long as the routeId/headsign combination remains consistent (and stopId,
     * when a particular stop is referenced), then user favorites should survive changes in the
     * composition of stops for a route.
     *
     * When the user favorites a route/headsign combination in the ArrivalsListFragment/Header,
     * they are prompted if they would like to make it a favorite for the current stop, or for all
     * stops.  If they make it a favorite for the current stop, a record with
     * routeId/headsign/stopId is created, with "exclude" value of false (0).  If they make it a
     * favorite for all stops, a record with routeId/headsign/ALL_STOPS is created with exclude
     * value of false.  When arrival times are displayed for a given stopId, if a record in the
     * database with routeId/headsign/ALL_STOPS or routeId?headsign/stopId matches AND exclude is
     * set to false, then it is shown as a favorite.  Otherwise, it is not shown as a favorite.
     * If the user unstars a stop, then routeId/headsign/stopId is inserted with an exclude value of
     * true (1).S
     */
    public static class RouteHeadsignFavorites implements RouteHeadsignKeyColumns, UserColumns {

        // Cannot be instantiated
        private RouteHeadsignFavorites() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "route_headsign_favorites";

        /** The content:// style URI for this table */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_DIR_TYPE
                = "vnd.android.dir/" + BuildConfig.DATABASE_AUTHORITY + ".routeheadsignfavorites";

        // String used to indicate that a route/headsign combination is a favorite for all stops
        private static final String ALL_STOPS = "all";

        /**
         * Set the specified route and headsign combination as a favorite, optionally for a specific
         * stop.  Note that this will also handle the marking/unmarking of the designated route as
         * the favorite as well.  The route is marked as not a favorite when no more
         * routeId/headsign combinations remain.  If marking the route/headsign as favorite for
         * all stops, then stopId should be null.
         *
         * @param routeId  routeId to be marked as favorite, in combination with headsign
         * @param headsign headsign to be marked as favorite, in combination with routeId
         * @param stopId stopId to be marked as a favorite, or null if all stopIds should be marked
         *               for this routeId/headsign combo.
         * @param favorite true if this route and headsign combination should be marked as a
         *                 favorite, false if it should not
         */
        public static void markAsFavorite(Context context, String routeId, String headsign, String
                stopId, boolean favorite) {
            if (context == null) {
                return;
            }
            if (headsign == null) {
                headsign = "";
            }

            ContentResolver cr = context.getContentResolver();
            Uri routeUri = Uri.withAppendedPath(ObaContract.Routes.CONTENT_URI, routeId);

            String stopIdInternal;
            if (stopId != null) {
                stopIdInternal = stopId;
            } else {
                stopIdInternal = ALL_STOPS;
            }

            final String WHERE = ROUTE_ID + "=? AND " + HEADSIGN + "=? AND " + STOP_ID + "=?";
            final String[] selectionArgs = {routeId, headsign, stopIdInternal};
            if (favorite) {
                if (stopIdInternal != ALL_STOPS) {
                    // First, delete any potential exclusion records for this stop by removing all records
                    cr.delete(CONTENT_URI, WHERE, selectionArgs);
                }

                // Mark as favorite by inserting a record for this route/headsign combo
                ContentValues values = new ContentValues();
                values.put(ROUTE_ID, routeId);
                values.put(HEADSIGN, headsign);
                values.put(STOP_ID, stopIdInternal);
                values.put(EXCLUDE, 0);
                cr.insert(CONTENT_URI, values);

                // Mark the route as a favorite also in the routes table
                Routes.markAsFavorite(context, routeUri, true);
            } else {
                // Deselect it as favorite by deleting all records for this route/headsign/stopId combo
                cr.delete(CONTENT_URI, WHERE, selectionArgs);
                if (stopIdInternal == ALL_STOPS) {
                    // Also make sure we've deleted the single record for this specific stop, if it exists
                    // We don't have the stopId here, so we can just delete all records for this routeId/headsign
                    final String[] selectionArgs2 = {routeId, headsign};
                    final String WHERE2 = ROUTE_ID + "=? AND " + HEADSIGN + "=?";
                    cr.delete(CONTENT_URI, WHERE2, selectionArgs2);
                }

                // If there are no more route/headsign combinations that are favorites for this route,
                // then mark the route as not a favorite
                if (!isFavorite(context, routeId)) {
                    Routes.markAsFavorite(context, routeUri, false);
                }

                // If a single stop is unstarred, but isFavorite(...) == true due to starring all
                // stops, insert exclusion record
                if (stopIdInternal != ALL_STOPS && isFavorite(routeId, headsign, stopId)) {
                    // Insert an exclusion record for this single stop, in case the user is unstarring it
                    // after starring the entire route
                    ContentValues values = new ContentValues();
                    values.put(ROUTE_ID, routeId);
                    values.put(HEADSIGN, headsign);
                    values.put(STOP_ID, stopIdInternal);
                    values.put(EXCLUDE, 1);
                    cr.insert(CONTENT_URI, values);
                }
            }

            StringBuilder analyicsLabel = new StringBuilder();
            if (favorite) {
                analyicsLabel.append(context.getString(R.string.analytics_label_star_route));
            } else {
                analyicsLabel.append(context.getString(R.string.analytics_label_unstar_route));
            }
            analyicsLabel.append(" ").append(routeId).append("_").append(headsign).append(" for ");
            if (stopId != null) {
                analyicsLabel.append(stopId);
            } else {
                analyicsLabel.append("all stops");
            }
            ObaAnalytics.reportEventWithCategory(
                    ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    context.getString(R.string.analytics_action_edit_field),
                    analyicsLabel.toString());
        }

        /**
         * Returns true if this combination of routeId and headsign is a favorite for this stop
         * or all stops (and that stop is not excluded as a favorite), false if it is not
         *
         * @param routeId  The routeId to check for favorite
         * @param headsign The headsign to check for favorite
         * @param stopId The stopId to check for favorite
         * @return true if this combination of routeId and headsign is a favorite for this stop
         * or all stops (and that stop is not excluded as a favorite), false if it is not
         */
        public static boolean isFavorite(String routeId, String headsign,
                String stopId) {
            if (headsign == null) {
                headsign = "";
            }
            final String[] selection = {ROUTE_ID, HEADSIGN, STOP_ID, EXCLUDE};
            final String[] selectionArgs = {routeId, headsign, stopId, Integer.toString(0)};
            ContentResolver cr = Application.get().getContentResolver();
            final String FILTER_WHERE_ALL_FIELDS = ROUTE_ID + "=? AND " + HEADSIGN + "=? AND "
                    + STOP_ID + "=? AND " + EXCLUDE + "=?";
            Cursor c = cr.query(CONTENT_URI, selection, FILTER_WHERE_ALL_FIELDS,
                    selectionArgs, null);
            boolean favorite;
            if (c != null && c.getCount() > 0) {
                favorite = true;
            } else {
                // Check again to see if the user has favorited this route/headsign combo for all stops
                final String[] selectionArgs2 = {routeId, headsign, ALL_STOPS};
                String WHERE_PARTIAL = ROUTE_ID + "=? AND " + HEADSIGN + "=? AND "
                        + STOP_ID + "=?";
                Cursor c2 = cr.query(CONTENT_URI, selection, WHERE_PARTIAL,
                        selectionArgs2, null);
                favorite = c2 != null && c2.getCount() > 0;
                if (c2 != null) {
                    c2.close();
                }

                if (favorite) {
                    // Finally, make sure the user hasn't excluded this stop as a favorite
                    final String[] selectionArgs3 = {routeId, headsign, stopId,
                            Integer.toString(1)};
                    Cursor c3 = cr.query(CONTENT_URI, selection, FILTER_WHERE_ALL_FIELDS,
                            selectionArgs3, null);
                    // If this query returns at least one record, it means the stop has been excluded as
                    // a favorite (i.e., the user explicitly de-selected it)
                    boolean isStopExcluded = c3 != null && c3.getCount() > 0;
                    favorite = !isStopExcluded;
                    if (c3 != null) {
                        c3.close();
                    }
                }
            }
            if (c != null) {
                c.close();
            }
            return favorite;
        }

        /**
         * Returns true if this routeId is listed as a favorite for at least one headsign with
         * EXCLUDED set to false, or false if it is not
         *
         * @param routeId The routeId to check for favorite
         * @return true if this routeId is listed as a favorite for at least one headsign without
         * EXCLUDE being set to true, or false if it is not
         */
        private static boolean isFavorite(Context context, String routeId) {
            final String[] selection = {ROUTE_ID, EXCLUDE};
            final String[] selectionArgs = {routeId, Integer.toString(0)};
            final String WHERE = ROUTE_ID + "=? AND " + EXCLUDE + "=?";
            ContentResolver cr = context.getContentResolver();
            Cursor c = cr.query(CONTENT_URI, selection, WHERE,
                    selectionArgs, null);
            boolean favorite = false;
            if (c != null && c.getCount() > 0) {
                favorite = true;
            } else {
                favorite = false;
            }
            if (c != null) {
                c.close();
            }
            return favorite;
        }
    }
}
