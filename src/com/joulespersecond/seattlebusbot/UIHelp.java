package com.joulespersecond.seattlebusbot;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaApi;

final class UIHelp {
    private static final String TAG = "UIHelp";
    
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
            Log.v(TAG, "Unknown direction: " + direction);
            return R.string.direction_n;
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
}
