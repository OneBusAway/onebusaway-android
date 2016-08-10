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

import com.amazon.geo.mapsv2.model.Marker;
import com.amazon.geo.mapsv2.util.AmazonMapsRuntimeUtil;
import com.amazon.geo.mapsv2.util.ConnectionResult;

import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.view.View;
/**
 * Helper methods specific to Amazon Maps API v2
 */
public class ProprietaryMapHelpV2 {

    public static boolean isMapsInstalled(Context context) {
        int resultCode = AmazonMapsRuntimeUtil
                .isAmazonMapsRuntimeAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    public static void promptUserInstallMaps(final Context context) {
        if (context instanceof Activity) {
            Activity a = (Activity) context;
            int resultCode = AmazonMapsRuntimeUtil
                    .isAmazonMapsRuntimeAvailable(context);
            AmazonMapsRuntimeUtil.getErrorDialog(resultCode, a, 0).show();
        }
    }

    /* unused */
    public static CustomAddress getCustomAddressFromPlacesIntent(Context context, Intent intent) {
        return null;
    }

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
           /* unused */
        }
    }

    /**
     * This method is a no-op because there is no such corresponding Marker.setZIndex() method on
     * Amazon Maps v2 as of Aug 8th 2016.  However, this method must exist for the code in
     * VehicleOverlay to remain the same on both the Amazon and Google build variants.
     *
     * @param m      marker to set the zIndex for
     * @param zIndex zIndex to set on the given marker (default is 0)
     */
    public static void setZIndex(Marker m, float zIndex) {
        // Do nothing - no Marker.setZIndex() method on Amazon Maps v2
    }
}
