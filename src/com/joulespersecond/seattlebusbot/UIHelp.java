package com.joulespersecond.seattlebusbot;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.provider.ObaContract;

final class UIHelp {
    //private static final String TAG = "UIHelp";

    public static final String PREFS_NAME = "com.joulespersecond.seattlebusbot.prefs";

    public static void setChildClickable(Activity parent, int id, ClickableSpan span) {
        TextView v = (TextView)parent.findViewById(id);
        Spannable text = (Spannable)v.getText();
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
    // Shows or hides the view, depending on whether or not the direction is available.
    public static final void setStopDirection(View v, String direction, boolean show) {
        final TextView text = (TextView)v;
        final int directionText = UIHelp.getStopDirectionText(direction);
        if ((directionText != R.string.direction_none) || show) {
            text.setText(directionText);
            text.setVisibility(View.VISIBLE);
        }
        else {
            text.setVisibility(View.GONE);
        }
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
            Cursor c = cr.query(ObaContract.Stops.CONTENT_URI,
                        STOP_USER_PROJECTION,
                        "(" + ObaContract.Stops.USER_NAME + " IS NOT NULL)" +
                        "OR (" + ObaContract.Stops.FAVORITE + "=1)",
                        null, null);
            mMap = new ContentQueryMap(c, ObaContract.Stops._ID, true, null);
        }

        public void setView(View stopRoot, String stopId, String stopName) {
            TextView nameView = (TextView)stopRoot.findViewById(R.id.stop_name);
            View favoriteView = stopRoot.findViewById(R.id.stop_favorite);

            ContentValues values = mMap.getValues(stopId);
            if (values != null) {
                final boolean favorite = (values.getAsInteger(ObaContract.Stops.FAVORITE) == 1);
                final String userName = values.getAsString(ObaContract.Stops.USER_NAME);

                nameView.setText(TextUtils.isEmpty(userName) ? stopName : userName);
                favoriteView.setVisibility(favorite ? View.VISIBLE : View.GONE);
            }
            else {
                nameView.setText(stopName);
                favoriteView.setVisibility(View.GONE);
            }
        }
    }

    public static final int getRouteErrorString(int code) {
        switch (code) {
        case ObaApi.OBA_INTERNAL_ERROR:
            return R.string.internal_error;
        case ObaApi.OBA_NOT_FOUND:
            return R.string.route_not_found_error;
        default:
            return R.string.generic_comm_error;
        }
    }
    public static final int getStopErrorString(int code) {
        switch (code) {
        case ObaApi.OBA_INTERNAL_ERROR:
            return R.string.internal_error;
        case ObaApi.OBA_NOT_FOUND:
            return R.string.stop_not_found_error;
        default:
            return R.string.generic_comm_error;
        }
    }

    public static final GeoPoint DEFAULT_SEARCH_CENTER =
        ObaApi.makeGeoPoint(47.612181, -122.22908);
    public static final int DEFAULT_SEARCH_RADIUS = 15000;

    // We need to provide the API for a location used to disambiguate
    // stop IDs in case of collision, or to provide multiple results
    // in the case multiple agencies. But we really don't need it to be very accurate.
    public static GeoPoint getLocation(Context cxt) {
        LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            Location loc = mgr.getLastKnownLocation(i.next());
            // If this provider has a last location, and either:
            // 1. We don't have a last location,
            // 2. Our last location is older than this location.
            if (loc != null &&
                (last == null || loc.getTime() > last.getTime())) {
                last = loc;
            }
        }
        if (last != null) {
            return ObaApi.makeGeoPoint(last.getLatitude(), last.getLongitude());
        }
        else {
            // Make up a fake "Seattle" location.
            // ll=47.620975,-122.347355
            return ObaApi.makeGeoPoint(47.620975, -122.347355);
        }
    }

    /**
     * Returns the first string for the query URI.
     * @param context
     * @param uri
     * @param column
     * @return
     */
    public static String stringForQuery(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(uri, new String[] { column }, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            }
            finally {
                c.close();
            }
        }
        return "";
    }

    public static Integer intForQuery(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(uri, new String[] { column }, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getInt(0);
                }
            }
            finally {
                c.close();
            }
        }
        return null;
    }
}
