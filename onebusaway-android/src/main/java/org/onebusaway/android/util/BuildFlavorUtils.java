/*
 * Copyright (C) 2015 Sean J. Barbeau (sjbarbeau@gmail.com)
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
package org.onebusaway.android.util;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import android.content.SharedPreferences;
import android.content.res.Resources;

/**
 * Constants and utilities used in the build.gradle build flavors to define certain features per
 * build flavor.
 *
 * @author barbeau
 */
public class BuildFlavorUtils {

    public static final String OBA_FLAVOR_BRAND = "oba";

    public static final String AMAZON_FLAVOR_PLATFORM = "amazon";

    public static final String AGENCYY_FLAVOR_BRAND = "agencyY";

    public static final int ARRIVAL_INFO_STYLE_A = 0; // Original OBA style

    public static final int ARRIVAL_INFO_STYLE_B = 1; // Style used by York Region Transit

    /**
     * Returns the preference option string for the provided build flavor style integer constant
     * for arrival info styles.  This translation is used to help decide if we should use the
     * arrival info style defined in the BuildConfig (via build.gradle), or the one selected by
     * the user.  User provided options always override BuildConfig defaults, but to do this we
     * need to store BuildConfig defaults initially as a preference.
     *
     * @param buildFlavorStyle arrival info style as defined in build.gradle - must be one of the
     *                         ARRIVAL_INFO_STYLE_* contants defined in this class
     * @return preference options string for the provided arrival info style build flavor integer
     */
    public static String getPreferenceOptionForArrivalInfoBuildFlavorStyle(int buildFlavorStyle) {
        switch (buildFlavorStyle) {
            case BuildFlavorUtils.ARRIVAL_INFO_STYLE_A:
                // OBA Classic
                return Application.get().getResources()
                        .getString(R.string.preferences_arrival_info_style_options_a);
            case BuildFlavorUtils.ARRIVAL_INFO_STYLE_B:
                // Use a card-styled footer
                return Application.get().getResources()
                        .getString(R.string.preferences_arrival_info_style_options_b);
            default:
                return Application.get().getResources()
                        .getString(R.string.preferences_arrival_info_style_options_b);
        }
    }

    /**
     * Returns the current Arrival Info Style saved in the preferences, represented using the
     * ARRIVAL_INFO_STYLE_* constants in this class
     * @return the current Arrival Info Style saved in the preferences, which will be one of the
     * ARRIVAL_INFO_STYLE_* constants in this class
     */
    public static int getArrivalInfoStyleFromPreferences() {
        Resources r = Application.get().getResources();

        SharedPreferences settings = Application.getPrefs();
        String arrivalInfoStylePref = settings.getString(r.getString(
                R.string.preference_key_arrival_info_style), null);

        String arrivalInfoStyleOptionA = r.getString(
                R.string.preferences_arrival_info_style_options_a);
        String arrivalInfoStyleOptionB = r.getString(
                R.string.preferences_arrival_info_style_options_b);

        if (arrivalInfoStylePref.equalsIgnoreCase(arrivalInfoStyleOptionA)) {
            return ARRIVAL_INFO_STYLE_A;
        }
        if (arrivalInfoStylePref.equalsIgnoreCase(arrivalInfoStyleOptionB)) {
            return ARRIVAL_INFO_STYLE_B;
        }
        // Return style A by default
        return ARRIVAL_INFO_STYLE_A;
    }
}
