/*
 * Copyright (C) 2015 University of South Florida, Sean J. Barbeau (sjbarbeau@gmail.com)
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
package org.onebusaway.android.map.googlemapsv2;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;

import org.onebusaway.android.R;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.view.View;

/**
 * Helper methods specific to Google Maps API v2
 */
public class ProprietaryMapHelpV2 {

    private static final String TAG = "ProprietaryMapHelpV2";

    private static final String PLACES_ADDRESS_SEPARATOR = ",";

    /**
     * Returns true if Android Maps V2 is installed, false if it is not
     */
    public static boolean isMapsInstalled(Context context) {
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
     */
    public static void promptUserInstallMaps(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(context.getString(R.string.please_install_google_maps_dialog_title));
        builder.setCancelable(false);
        builder.setPositiveButton(context.getString(R.string.install_google_maps_positive_button),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(context.getString(R.string.android_maps_v2_market_url)));
                        ResolveInfo info = context.getPackageManager()
                                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                        if (info != null) {
                            context.startActivity(intent);
                        } else {
                            // User doesn't have Play Store installed
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setMessage(context.getString(
                                    R.string.no_play_store));
                            builder.setCancelable(true);
                            builder.setPositiveButton(context.getString(R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.dismiss();
                                        }
                                    });
                            AlertDialog d = builder.create();
                            d.show();
                        }
                    }
                }
        );
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Decode the result of an Intent to Google Places Autocomplete into a CustomAddress.
     * Here because of LatLng, Place which are specific to Google Places API.
     */
    public static CustomAddress getCustomAddressFromPlacesIntent(Context context, Intent intent) {
        Place place = PlaceAutocomplete.getPlace(context, intent);

        CustomAddress address = new CustomAddress();

        String placeName = place.getName().toString();
        address.setAddressLine(0, placeName);

        String addressString = place.getAddress().toString();
        String[] tokens = addressString.split(PLACES_ADDRESS_SEPARATOR);

        // Posible that first line of address is place name.
        int start = placeName.equals(tokens[0]) ? 1 : 0;

        int j = 1; // address line index
        for (int i = start; i < tokens.length; i++) {
            address.setAddressLine(j++, tokens[i]);
        }

        LatLng loc = place.getLatLng();

        address.setLatitude(loc.latitude);
        address.setLongitude(loc.longitude);

        return address;
    }

    /**
     * Helper class to start Google Places Autocomplete when a field is clicked.
     * Does not check that Google API is available.
     * Here because of the use of LatLngBounds. Decode result with
     * getCustomAddressFromPlacesIntent.
     */
    public static class StartPlacesAutocompleteOnClick implements View.OnClickListener {

        int mRequestCode;
        Fragment mFragment;
        ObaRegion mRegion;

        public StartPlacesAutocompleteOnClick(int requestCode, Fragment fragment, ObaRegion region) {
            mRequestCode = requestCode;
            mFragment = fragment;
            mRegion = region;
        }

        @Override
        public void onClick(View v) {
            Intent intent = null;
            PlaceAutocomplete.IntentBuilder builder =
                    new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY);

            if (mRegion != null) {
                // Bias search results to region bounds
                LatLngBounds bounds = MapHelpV2.getRegionBounds(mRegion);
                builder.setBoundsBias(bounds);
            }

            try {
                intent = builder.build(mFragment.getActivity());
            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }

            mFragment.startActivityForResult(intent, mRequestCode);
        }
    }

    /**
     * Sets the zIndex for the given marker to the given zIndex.  This is in ProprietaryMapHelpV2
     * because there is no such corresponding Marker.setZIndex() method on Amazon Maps v2.
     * @param m marker to set the zIndex for
     * @param zIndex zIndex to set on the given marker (default is 0)
     */
    public static void setZIndex(Marker m, float zIndex) {
        m.setZIndex(zIndex);
    }
}
