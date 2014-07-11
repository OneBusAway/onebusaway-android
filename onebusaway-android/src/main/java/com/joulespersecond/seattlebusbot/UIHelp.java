/*
 * Copyright (C) 2010-2013 Paul Watts (paulcwatts@gmail.com)
 * and individual contributors.
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

import com.google.android.maps.GeoPoint;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.oba.elements.ObaRoute;
import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.oba.region.RegionUtils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;

public final class UIHelp {
    // private static final String TAG = "UIHelp";

    public static void setupActionBar(SherlockFragmentActivity activity) {
        setupActionBar(activity.getSupportActionBar());
    }

    public static void setupActionBar(ActionBar bar) {
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setDisplayShowTitleEnabled(false);
    }

    public static void showProgress(SherlockFragment fragment, boolean visible) {
        SherlockFragmentActivity act = fragment.getSherlockActivity();
        if (act != null) {
            act.setSupportProgressBarIndeterminateVisibility(visible);
        }
    }

    public static void setChildClickable(Activity parent, int id, ClickableSpan span) {
        TextView v = (TextView) parent.findViewById(id);
        setClickable(v, span);
    }

    public static void setChildClickable(View parent, int id, ClickableSpan span) {
        TextView v = (TextView) parent.findViewById(id);
        setClickable(v, span);
    }

    public static void setClickable(TextView v, ClickableSpan span) {
        Spannable text = (Spannable) v.getText();
        text.setSpan(span, 0, text.length(), 0);
        v.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public static final int getStopDirectionText(String direction) {
        if (direction.equals("N")) {
            return R.string.direction_n;
        } else if (direction.equals("NW")) {
            return R.string.direction_nw;
        } else if (direction.equals("W")) {
            return R.string.direction_w;
        } else if (direction.equals("SW")) {
            return R.string.direction_sw;
        } else if (direction.equals("S")) {
            return R.string.direction_s;
        } else if (direction.equals("SE")) {
            return R.string.direction_se;
        } else if (direction.equals("E")) {
            return R.string.direction_e;
        } else if (direction.equals("NE")) {
            return R.string.direction_ne;
        } else {
            return R.string.direction_none;
        }
    }

    public static final String getRouteDisplayName(ObaRoute route) {
        String result = route.getShortName();
        if (!TextUtils.isEmpty(result)) {
            return result;
        }
        result = route.getLongName();
        if (!TextUtils.isEmpty(result)) {
            return result;
        }
        // Just so we never return null.
        return "";
    }

    public static final String getRouteDescription(ObaRoute route) {
        String shortName = route.getShortName();
        String longName = route.getLongName();

        if (TextUtils.isEmpty(shortName)) {
            shortName = longName;
        }
        if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
            longName = route.getDescription();
        }
        return MyTextUtils.toTitleCase(longName);
    }

    // Shows or hides the view, depending on whether or not the direction is
    // available.
    public static final void setStopDirection(View v, String direction, boolean show) {
        final TextView text = (TextView) v;
        final int directionText = UIHelp.getStopDirectionText(direction);
        if ((directionText != R.string.direction_none) || show) {
            text.setText(directionText);
            text.setVisibility(View.VISIBLE);
        } else {
            text.setVisibility(View.GONE);
        }
    }

    // Common code to set a route list item view
    public static final void setRouteView(View view, ObaRoute route) {
        TextView shortNameText = (TextView) view.findViewById(R.id.short_name);
        TextView longNameText = (TextView) view.findViewById(R.id.long_name);

        String shortName = route.getShortName();
        String longName = MyTextUtils.toTitleCase(route.getLongName());

        if (TextUtils.isEmpty(shortName)) {
            shortName = longName;
        }
        if (TextUtils.isEmpty(longName) || shortName.equals(longName)) {
            longName = MyTextUtils.toTitleCase(route.getDescription());
        }

        shortNameText.setText(shortName);
        longNameText.setText(longName);
    }

    private static final String[] STOP_USER_PROJECTION = {
            ObaContract.Stops._ID,
            ObaContract.Stops.FAVORITE,
            ObaContract.Stops.USER_NAME
    };

    public static class StopUserInfoMap {

        private final ContentQueryMap mMap;

        public StopUserInfoMap(Context context) {
            ContentResolver cr = context.getContentResolver();
            Cursor c = cr.query(ObaContract.Stops.CONTENT_URI, STOP_USER_PROJECTION, "("
                    + ObaContract.Stops.USER_NAME + " IS NOT NULL)" + "OR ("
                    + ObaContract.Stops.FAVORITE + "=1)", null, null);
            mMap = new ContentQueryMap(c, ObaContract.Stops._ID, true, null);
        }

        public void close() {
            mMap.close();
        }

        public void requery() {
            mMap.requery();
        }

        public void setView(View stopRoot, String stopId, String stopName) {
            TextView nameView = (TextView) stopRoot.findViewById(R.id.stop_name);
            setView2(nameView, stopId, stopName, true);
        }

        /**
         * This should be used with compound drawables
         */
        public void setView2(TextView nameView, String stopId, String stopName, boolean showIcon) {
            ContentValues values = mMap.getValues(stopId);
            int icon = 0;
            if (values != null) {
                Integer i = values.getAsInteger(ObaContract.Stops.FAVORITE);
                final boolean favorite = (i != null) && (i == 1);
                final String userName = values.getAsString(ObaContract.Stops.USER_NAME);

                nameView.setText(TextUtils.isEmpty(userName) ?
                        MyTextUtils.toTitleCase(stopName) : userName);
                icon = favorite && showIcon ? R.drawable.star_on : 0;
            } else {
                nameView.setText(MyTextUtils.toTitleCase(stopName));
            }
            nameView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        }
    }

    /**
     * Default implementation for creating a shortcut when in shortcut mode.
     *
     * @param name       The name of the shortcut.
     * @param destIntent The destination intent.
     */
    public static final Intent makeShortcut(Context context, String name, Intent destIntent) {
        // Set up the container intent
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, destIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        Parcelable iconResource = Intent.ShortcutIconResource
                .fromContext(context, R.drawable.ic_launcher);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        return intent;
    }

    public static void goToUrl(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, context.getString(R.string.browser_error), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public static final int getRouteErrorString(Context context, int code) {
        if (!isConnected(context)) {
            if (isAirplaneMode(context)) {
                Application.getAnalytics().reportEvent("error", "network", "Airplane mode");
                return R.string.airplane_mode_error;
            } else {
                Application.getAnalytics().reportEvent("error", "network", "No network");
                return R.string.no_network_error;
            }
        }
        switch (code) {
            case ObaApi.OBA_INTERNAL_ERROR:
                Application.getAnalytics().reportEvent("error", "general", "Internal error");
                return R.string.internal_error;
            case ObaApi.OBA_NOT_FOUND:
                Application.getAnalytics().reportEvent("error", "general", "Route not found");
                return R.string.route_not_found_error;
            case ObaApi.OBA_BAD_GATEWAY:
                Application.getAnalytics().reportEvent("error", "network", "Bad gateway");
                return R.string.bad_gateway_error;
            case ObaApi.OBA_OUT_OF_MEMORY:
                Application.getAnalytics().reportEvent("error", "general", "Out of memory");
                return R.string.out_of_memory_error;
            default:
                Application.getAnalytics().reportEvent("error", "network", "Communication error");
                return R.string.generic_comm_error;
        }
    }

    public static final int getStopErrorString(Context context, int code) {
        if (!isConnected(context)) {
            if (isAirplaneMode(context)) {
                Application.getAnalytics().reportEvent("error", "network", "Airplane mode");
                return R.string.airplane_mode_error;
            } else {
                Application.getAnalytics().reportEvent("error", "network", "No network");
                return R.string.no_network_error;
            }
        }
        switch (code) {
            case ObaApi.OBA_INTERNAL_ERROR:
                Application.getAnalytics().reportEvent("error", "general", "Internal error");
                return R.string.internal_error;
            case ObaApi.OBA_NOT_FOUND:
                Application.getAnalytics().reportEvent("error", "general", "Stop not found");
                return R.string.stop_not_found_error;
            case ObaApi.OBA_BAD_GATEWAY:
                Application.getAnalytics().reportEvent("error", "network", "Bad gateway");
                return R.string.bad_gateway_error;
            case ObaApi.OBA_OUT_OF_MEMORY:
                Application.getAnalytics().reportEvent("error", "general", "Out of memory");
                return R.string.out_of_memory_error;
            default:
                Application.getAnalytics().reportEvent("error", "network", "Communication error");
                return R.string.generic_comm_error;
        }
    }

    public static final int getMapErrorString(Context context, int code) {
        if (!isConnected(context)) {
            if (isAirplaneMode(context)) {
                Application.getAnalytics().reportEvent("error", "network", "Airplane mode");
                return R.string.airplane_mode_error;
            } else {
                Application.getAnalytics().reportEvent("error", "network", "No network");
                return R.string.no_network_error;
            }
        }
        switch (code) {
            case ObaApi.OBA_INTERNAL_ERROR:
                Application.getAnalytics().reportEvent("error", "general", "Internal error");
                return R.string.internal_error;
            case ObaApi.OBA_BAD_GATEWAY:
                Application.getAnalytics().reportEvent("error", "network", "Bad gateway");
                return R.string.bad_gateway_error;
            case ObaApi.OBA_OUT_OF_MEMORY:
                Application.getAnalytics().reportEvent("error", "general", "Out of memory");
                return R.string.out_of_memory_error;
            default:
                Application.getAnalytics().reportEvent("error", "general", "Map error");
                return R.string.map_generic_error;
        }
    }

    public static GeoPoint getDefaultSearchCenter() {
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null) {
            double results[] = new double[4];
            RegionUtils.getRegionSpan(region, results);
            return ObaApi.makeGeoPoint(results[2], results[3]);
        } else {
            return null;
        }
    }

    public static final int DEFAULT_SEARCH_RADIUS = 15000;

    // We need to provide the API for a location used to disambiguate
    // stop IDs in case of collision, or to provide multiple results
    // in the case multiple agencies. But we really don't need it to be very
    // accurate.
    public static GeoPoint getLocation(Context cxt) {
        Location last = getLocation2(cxt);
        if (last != null) {
            return ObaApi.makeGeoPoint(last.getLatitude(), last.getLongitude());
        } else {
            return getDefaultSearchCenter();
        }
    }

    public static Location getLocation2(Context cxt) {
        LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            Location loc = mgr.getLastKnownLocation(i.next());
            // If this provider has a last location, and either:
            // 1. We don't have a last location,
            // 2. Our last location is older than this location.
            if (loc != null && (last == null || loc.getTime() > last.getTime())) {
                last = loc;
            }
        }
        return last;
    }

    public static boolean isAirplaneMode(Context context) {
        ContentResolver cr = context.getContentResolver();
        return Settings.System.getInt(cr, Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return (activeNetwork != null) && activeNetwork.isConnectedOrConnecting();
    }

    /**
     * Returns the first string for the query URI.
     */
    public static String stringForQuery(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(uri, new String[]{column}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            } finally {
                c.close();
            }
        }
        return "";
    }

    public static Integer intForQuery(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(uri, new String[]{column}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getInt(0);
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    public static final int MINUTES_IN_HOUR = 60;

    /**
     * Takes the number of minutes, and returns a user-readable string
     * saying the number of minutes in which no arrivals are coming,
     * or the number of hours and minutes if minutes if minutes > 60
     *
     * @param minutes            number of minutes for which there are no upcoming arrivals
     * @param additionalArrivals true if the response should include the word additional, false if
     *                           it should not
     * @return a user-readable string saying the number of minutes in which no arrivals are coming,
     * or the number of hours and minutes if minutes > 60
     */
    public static String getNoArrivalsMessage(Context context, int minutes,
            boolean additionalArrivals) {
        if (minutes <= MINUTES_IN_HOUR) {
            // Return just minutes
            if (additionalArrivals) {
                return context.getString(R.string.stop_info_no_additional_data_minutes, minutes);
            } else {
                return context.getString(R.string.stop_info_nodata_minutes, minutes);
            }
        } else {
            // Return hours and minutes
            if (additionalArrivals) {
                return context.getResources()
                        .getQuantityString(R.plurals.stop_info_no_additional_data_hours_minutes,
                                minutes / 60, minutes % 60, minutes / 60);
            } else {
                return context.getResources()
                        .getQuantityString(R.plurals.stop_info_nodata_hours_minutes, minutes / 60,
                                minutes % 60, minutes / 60);
            }
        }
    }
}
