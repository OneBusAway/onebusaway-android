/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
package com.joulespersecond.seattlebusbot.map.googlemapsv2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.seattlebusbot.R;

/**
 * Utilities to help process data for Android Maps API v1
 */
public class MapHelpV2 {

    /**
     * Converts a latitude/longitude to a LatLng.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A LatLng representing this latitude/longitude.
     */
    public static final LatLng makeLatLng(double lat, double lon) {
        return new LatLng(lat, lon);
    }

    /**
     * Converts a Location to a LatLng.
     *
     * @param l Location to convert
     * @return A LatLng representing this LatLng.
     */
    public static final LatLng makeLatLng(Location l) {
        return makeLatLng(l.getLatitude(), l.getLongitude());
    }

    /**
     * Converts a LatLng to a Location.
     *
     * @param latLng LatLng to convert
     * @return A Location representing this LatLng.
     */
    public static final Location makeLocation(LatLng latLng) {
        Location l = new Location("FromLatLng");
        l.setLatitude(latLng.latitude);
        l.setLongitude(latLng.longitude);
        return l;
    }

    /**
     * Returns the bounds for the entire region.
     *
     * @return LatLngBounds for the region
     */
    public static LatLngBounds getRegionBounds(ObaRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Region is null");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        // This is fairly simplistic
        for (ObaRegion.Bounds bound : region.getBounds()) {
            // Get the top bound
            double lat = bound.getLat();
            double latSpanHalf = bound.getLatSpan() / 2.0;
            double lat1 = lat - latSpanHalf;
            double lat2 = lat + latSpanHalf;
            if (lat1 < latMin) {
                latMin = lat1;
            }
            if (lat2 > latMax) {
                latMax = lat2;
            }

            double lon = bound.getLon();
            double lonSpanHalf = bound.getLonSpan() / 2.0;
            double lon1 = lon - lonSpanHalf;
            double lon2 = lon + lonSpanHalf;
            if (lon1 < lonMin) {
                lonMin = lon1;
            }
            if (lon2 > lonMax) {
                lonMax = lon2;
            }
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(MapHelpV2.makeLatLng(latMin, lonMin));
        builder.include(MapHelpV2.makeLatLng(latMax, lonMax));

        return builder.build();
    }


    /**
     * Returns true if Android Maps V2 is installed, false if it is not
     */
    public static boolean isGoogleMapsInstalled(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo("com.google.android.apps.maps", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Prompts the user to install Android Maps V2 from Google Play
     *
     * @param context
     */
    public static void promptUserInstallGoogleMaps(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(context.getString(R.string.please_install_google_maps_dialog_title));
        builder.setCancelable(false);
        builder.setPositiveButton(context.getString(R.string.install_google_maps_positive_button),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.android_maps_v2_market_url)));
                        context.startActivity(intent);
                    }
                }
        );
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
