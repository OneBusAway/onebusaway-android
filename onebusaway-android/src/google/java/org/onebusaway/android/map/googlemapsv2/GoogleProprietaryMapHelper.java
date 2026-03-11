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

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.fragment.app.Fragment;

import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.map.ProprietaryMapHelper;

/**
 * Google-specific implementation of {@link ProprietaryMapHelper}.
 * Delegates to the static methods in {@link ProprietaryMapHelpV2}.
 */
public class GoogleProprietaryMapHelper implements ProprietaryMapHelper {

    @Override
    public CustomAddress getCustomAddressFromPlacesIntent(Context context, Intent intent) {
        return ProprietaryMapHelpV2.getCustomAddressFromPlacesIntent(context, intent);
    }

    @Override
    public View.OnClickListener createPlacesAutocompleteOnClick(int requestCode, Fragment fragment, ObaRegion region) {
        return new ProprietaryMapHelpV2.StartPlacesAutocompleteOnClick(requestCode, fragment, region);
    }
}
