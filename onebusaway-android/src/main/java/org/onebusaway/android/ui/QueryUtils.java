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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.UIUtils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.CursorLoader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * Utilities mainly to support queries for the Stops and Routes lists
 *
 * @author paulw
 */
public final class QueryUtils {

    static protected CursorLoader newRecentQuery(
            final Context context,
            final Uri uri,
            final String[] projection,
            final String accessTime,
            final String useCount) {
        // "Recently" means seven days in the past
        final long last = System.currentTimeMillis() - 7 * DateUtils.DAY_IN_MILLIS;
        Uri limit = uri.buildUpon().appendQueryParameter("limit", "20").build();

        String regionWhere = "";
        if (Application.get().getCurrentRegion() != null) {
            if (projection.equals(QueryUtils.StopList.Columns.PROJECTION)) {
                regionWhere = " AND " + StopList.getRegionWhere();
            } else if (projection.equals(QueryUtils.RouteList.Columns.PROJECTION)) {
                regionWhere = " AND " + RouteList.getRegionWhere();
            }
        }

        return new CursorLoader(context,
                limit,
                projection,
                "((" +
                        accessTime + " IS NOT NULL AND " +
                        accessTime + " > " + last +
                        ") OR (" + useCount + " > 0))" + regionWhere,
                null,
                accessTime + " desc, " +
                        useCount + " desc"
        );
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
            // Get the cursor and fetch the route ID from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_ID);
        }

        static protected String getShortName(ListView l, int position) {
            // Get the cursor and fetch the route short name from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_SHORTNAME);
        }

        static protected String getUrl(ListView l, int position) {
            // Get the cursor and fetch the route URL from that.
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_URL);
        }

        static protected String getRegionWhere() {
            return Application.get().getCurrentRegion() == null ? "" :
                    QueryUtils.getRegionWhere(ObaContract.Routes.REGION_ID,
                            Application.get().getCurrentRegion().getId());
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
            String[] from = new String[]{
                    ObaContract.Stops.UI_NAME,
                    ObaContract.Stops.DIRECTION,
                    ObaContract.Stops.FAVORITE
            };
            int[] to = new int[]{
                    R.id.stop_name,
                    R.id.direction,
                    R.id.stop_favorite
            };
            SimpleCursorAdapter simpleAdapter =
                    new SimpleCursorAdapter(context, R.layout.stop_list_item, null, from, to, 0);

            // We need to convert the direction text (N/NW/E/etc)
            // to user level text (North/Northwest/etc..)
            simpleAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    if (columnIndex == Columns.COL_FAVORITE) {
                        ImageView favorite = (ImageView) view.findViewById(R.id.stop_favorite);
                        if (cursor.getInt(columnIndex) == 1) {
                            favorite.setVisibility(View.VISIBLE);
                            // Make sure the star is visible against white background
                            favorite.setColorFilter(
                                    favorite.getResources().getColor(R.color.navdrawer_icon_tint));
                        } else {
                            favorite.setVisibility(View.GONE);
                        }
                        return true;
                    } else if (columnIndex == Columns.COL_DIRECTION) {
                        UIUtils.setStopDirection(view.findViewById(R.id.direction),
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
            SimpleCursorAdapter cursorAdapter = (SimpleCursorAdapter) l.getAdapter();
            Cursor c = cursorAdapter.getCursor();
            c.moveToPosition(position - l.getHeaderViewsCount());
            return c.getString(Columns.COL_ID);
        }

        static protected String getRegionWhere() {
            return Application.get().getCurrentRegion() == null ? "" :
                    QueryUtils.getRegionWhere(ObaContract.Stops.REGION_ID,
                            Application.get().getCurrentRegion().getId());
        }
    }

    public static String getRegionWhere(String regionFieldName, long regionId) {
        return "(" + regionFieldName + "=" + regionId +
                " OR " + regionFieldName + " IS NULL)";
    }

    /**
     * Sets the given route and headsign and stop as a favorite, including checking to make sure that the
     * route has already been added to the local provider.  If this route/headsign should be marked
     * as a favorite for all stops, stopId should be null.
     *
     * @param routeUri Uri for the route to be added
     * @param headsign the headsign to be marked as favorite, along with the routeUri
     * @param stopId the stopId to be marked as a favorite, along with with route and headsign.  If
     *               this route/headsign should be marked for all stops, then stopId should be null
     * @param routeValues   content routeValues to be set for the route details (see ObaContract.RouteColumns)
     *                 (may be null)
     * @param favorite true if this route/headsign should be marked as a favorite, false if it
     *                 should not
     */
    public static void setFavoriteRouteAndHeadsign(Context context, Uri routeUri,
            String headsign, String stopId, ContentValues routeValues, boolean favorite) {
        if (routeValues == null) {
            routeValues = new ContentValues();
        }
        if (Application.get().getCurrentRegion() != null) {
            routeValues.put(ObaContract.Routes.REGION_ID,
                    Application.get().getCurrentRegion().getId());
        }

        String routeId = routeUri.getLastPathSegment();

        // Make sure this route has been inserted into the routes table
        ObaContract.Routes.insertOrUpdate(context, routeId, routeValues, true);
        // Mark the combination of route and headsign as a favorite or not favorite
        ObaContract.RouteHeadsignFavorites
                .markAsFavorite(context, routeId, headsign, stopId, favorite);
    }
}
