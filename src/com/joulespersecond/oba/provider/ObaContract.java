/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.provider;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.Time;

/**
 * The contract between clients and the ObaProvider.
 *
 * This really needs to be documented better.
 *
 * @author paulw
 *
 */
public final class ObaContract {
    /** The authority portion of the URI for the Oba provider */
    public static final String AUTHORITY = "com.joulespersecond.oba";
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

    public static class Stops implements BaseColumns, StopsColumns, UserColumns {
        // Cannot be instantiated
        private Stops() {
        }

        /** The URI path portion for this table */
        public static final String PATH = "stops";
        /** The content:// style URI for this table */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(
                AUTHORITY_URI, PATH);

        public static final String CONTENT_TYPE = "vnd.android.cursor.item/com.joulespersecond.oba.stop";
        public static final String CONTENT_DIR_TYPE = "vnd.android.dir/com.joulespersecond.oba.stop";

        public static Uri insertOrUpdate(Context context,
                String id,
                ContentValues values,
                boolean markAsUsed) {
            ContentResolver cr = context.getContentResolver();
            final Uri uri = Uri.withAppendedPath(CONTENT_URI, id);
            Cursor c = cr.query(uri, new String[] { USE_COUNT }, null, null,
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

        public static final String CONTENT_TYPE = "vnd.android.cursor.item/com.joulespersecond.oba.route";
        public static final String CONTENT_DIR_TYPE = "vnd.android.dir/com.joulespersecond.oba.route";

        public static Uri insertOrUpdate(Context context,
                String id,
                ContentValues values,
                boolean markAsUsed) {
            ContentResolver cr = context.getContentResolver();
            final Uri uri = Uri.withAppendedPath(CONTENT_URI, id);
            Cursor c = cr.query(uri, new String[] { USE_COUNT }, null, null,
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

        public static boolean markAsUnused(Context context, Uri uri) {
            ContentResolver cr = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(ObaContract.Routes.USE_COUNT, 0);
            values.putNull(ObaContract.Routes.ACCESS_TIME);
            return cr.update(uri, values, null, null) > 0;
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

        public static final String CONTENT_DIR_TYPE = "vnd.android.dir/com.joulespersecond.oba.stoproutefilter";

        private static final String FILTER_WHERE = STOP_ID + "=?";

        /**
         * Gets the filter for the specified Stop ID.
         *
         * @param context
         *            The context.
         * @param stopId
         *            The stop ID.
         * @return The filter. If there is no filter (or on error), it returns
         *         an empty list.
         */
        public static ArrayList<String> get(Context context, String stopId) {
            final String[] selection = { ROUTE_ID };
            final String[] selectionArgs = { stopId };
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
         * @param context
         *            The context.
         * @param stopId
         *            The stop ID.
         * @param filter
         *            An array of route IDs to filter.
         */
        public static void set(Context context,
                String stopId,
                ArrayList<String> filter) {
            // First, delete any existing rows for this stop.
            // Then, insert all of these rows.
            // Should we put this in a transaction? We could,
            // but it's not terribly important.
            final String[] selectionArgs = { stopId };
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

        public static final String CONTENT_TYPE = "vnd.android.cursor.item/com.joulespersecond.oba.trip";
        public static final String CONTENT_DIR_TYPE = "vnd.android.dir/com.joulespersecond.oba.trip";

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

        public static final Uri buildUri(String tripId, String stopId) {
            return CONTENT_URI.buildUpon().appendPath(tripId)
                    .appendPath(stopId).build();
        }

        /**
         * Converts a days bitmask into a boolean[] array
         *
         * @param days
         *            A DB compatible days bitmask.
         * @return A boolean array representing the days set in the bitmask,
         *         Mon=0 to Sun=6
         */
        public static boolean[] daysToArray(int days) {
            final boolean[] result = {
                    (days & ObaContract.Trips.DAY_MON) == ObaContract.Trips.DAY_MON,
                    (days & ObaContract.Trips.DAY_TUE) == ObaContract.Trips.DAY_TUE,
                    (days & ObaContract.Trips.DAY_WED) == ObaContract.Trips.DAY_WED,
                    (days & ObaContract.Trips.DAY_THU) == ObaContract.Trips.DAY_THU,
                    (days & ObaContract.Trips.DAY_FRI) == ObaContract.Trips.DAY_FRI,
                    (days & ObaContract.Trips.DAY_SAT) == ObaContract.Trips.DAY_SAT,
                    (days & ObaContract.Trips.DAY_SUN) == ObaContract.Trips.DAY_SUN, };
            return result;
        }

        /**
         * Converts a boolean[] array to a DB compatible days bitmask
         *
         * @param A
         *            boolean array as returned by daysToArray
         * @return A DB compatible days bitmask
         */
        public static int arrayToDays(boolean[] days) {
            int result = 0;
            assert (days.length == 7);
            for (int i = 0; i < days.length; ++i) {
                final int bit = days[i] ? 1 : 0;
                result |= bit << i;
            }
            return result;
        }

        /**
         * Converts a 'minutes-to-midnight' value into a Unix time.
         *
         * @param minutes
         *            from midnight in UTC.
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
         * @param departureTime
         *            A Unix time.
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
         * @param weekday
         *            The weekDay value from android.text.format.Time
         * @return A DB compatible bit.
         */
        public static int getDayBit(int weekday) {
            switch (weekday) {
            case Time.MONDAY:       return ObaContract.Trips.DAY_MON;
            case Time.TUESDAY:      return ObaContract.Trips.DAY_TUE;
            case Time.WEDNESDAY:    return ObaContract.Trips.DAY_WED;
            case Time.THURSDAY:     return ObaContract.Trips.DAY_THU;
            case Time.FRIDAY:       return ObaContract.Trips.DAY_FRI;
            case Time.SATURDAY:     return ObaContract.Trips.DAY_SAT;
            case Time.SUNDAY:       return ObaContract.Trips.DAY_SUN;
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

        public static final String CONTENT_TYPE = "vnd.android.cursor.item/com.joulespersecond.oba.trip_alert";
        public static final String CONTENT_DIR_TYPE = "vnd.android.dir/com.joulespersecond.oba.trip_alert";

        public static final int STATE_SCHEDULED = 0;
        public static final int STATE_POLLING = 1;
        public static final int STATE_NOTIFY = 2;
        public static final int STATE_CANCELLED = 3;

        public static final Uri buildUri(int id) {
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
                    new String[] { _ID },
                    String.format("%s=? AND %s=? AND %s=?",
                            TRIP_ID, STOP_ID, START_TIME),
                    new String[] { tripId, stopId, String.valueOf(startTime) },
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
}
