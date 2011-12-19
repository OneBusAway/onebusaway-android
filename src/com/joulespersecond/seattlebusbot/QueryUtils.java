/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.provider.ObaContract;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Utilities mainly to support queries for the Stops and Routes lists
 * @author paulw
 *
 */
final class QueryUtils {
    static protected CursorLoader newRecentQuery(
            final Context context,
            final Uri uri,
            final String[] projection,
            final String accessTime,
            final String useCount) {
        // "Recently" means seven days in the past
        final long last = System.currentTimeMillis() - 7*DateUtils.DAY_IN_MILLIS;
        Uri limit = uri.buildUpon().appendQueryParameter("limit", "20").build();
        return new CursorLoader(context,
                limit,
                projection,
                "(" +
                    accessTime + " IS NOT NULL AND " +
                    accessTime + " > " + last +
                ") OR (" + useCount + " > 0)",
                null,
                accessTime + " desc, " +
                useCount + " desc");
    }

    static final class RouteList {
        public interface Columns {
            public static final String[] PROJECTION = {
                ObaContract.Routes._ID,
                ObaContract.Routes.SHORTNAME,
                ObaContract.Routes.LONGNAME,
                ObaContract.Routes.URL
            };
            public static final int COL_ID = 0;
            public static final int COL_SHORTNAME = 1;
            // private static final int COL_LONGNAME = 2;
            public static final int COL_URL = 3;
        }

        public static SimpleCursorAdapter newAdapter(Context context) {
            final String[] from = {
                    ObaContract.Routes.SHORTNAME,
                    ObaContract.Routes.LONGNAME
            };
            final int[] to = {
                    R.id.short_name,
                    R.id.long_name
            };
            SimpleCursorAdapter simpleAdapter =
                    new SimpleCursorAdapter(context, R.layout.route_list_item,
                            null, from, to, 0);
            return simpleAdapter;
        }

        static protected String getId(ListView l, int position) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_ID);
        }

        static protected String getUrl(ListView l, int position) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_URL);
        }
    }

    static final class StopList {
        public interface Columns {
            public static final String[] PROJECTION = {
                ObaContract.Stops._ID,
                ObaContract.Stops.UI_NAME,
                ObaContract.Stops.DIRECTION,
                ObaContract.Stops.LATITUDE,
                ObaContract.Stops.LONGITUDE,
                ObaContract.Stops.UI_NAME,
                ObaContract.Stops.FAVORITE
            };
            public static final int COL_ID = 0;
            public static final int COL_NAME = 1;
            public static final int COL_DIRECTION = 2;
            public static final int COL_LATITUDE = 3;
            public static final int COL_LONGITUDE = 4;
            public static final int COL_UI_NAME = 5;
            public static final int COL_FAVORITE = 6;
        }

        public static SimpleCursorAdapter newAdapter(Context context) {
            String[] from = new String[] {
                    ObaContract.Stops.UI_NAME,
                    ObaContract.Stops.DIRECTION,
                    ObaContract.Stops.FAVORITE
            };
            int[] to = new int[] {
                    R.id.stop_name,
                    R.id.direction,
                    R.id.stop_name
            };
            SimpleCursorAdapter simpleAdapter =
                new SimpleCursorAdapter(context, R.layout.stop_list_item, null, from, to, 0);

            // We need to convert the direction text (N/NW/E/etc)
            // to user level text (North/Northwest/etc..)
            simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    if (columnIndex == Columns.COL_FAVORITE) {
                        TextView favorite = (TextView)view.findViewById(R.id.stop_name);
                        int icon = (cursor.getInt(columnIndex) == 1) ? R.drawable.star_on : 0;
                        favorite.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
                        return true;
                    }
                    else if (columnIndex == Columns.COL_DIRECTION) {
                        UIHelp.setStopDirection(view.findViewById(R.id.direction),
                                cursor.getString(columnIndex),
                                true);
                        return true;
                    }
                    return false;
                }
            });
            return simpleAdapter;
        }

        static protected String getId(ListView l, int position) {
            // Get the cursor and fetch the stop ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter)l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_ID);
        }
    }

}
