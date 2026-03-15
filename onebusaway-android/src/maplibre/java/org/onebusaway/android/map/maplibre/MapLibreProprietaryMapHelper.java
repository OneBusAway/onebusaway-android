/*
 * Copyright (C) 2024 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.map.maplibre;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.fragment.app.Fragment;

import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.map.ProprietaryMapHelper;

/**
 * Stub implementation of {@link ProprietaryMapHelper} for the MapLibre flavor.
 * The MapLibre flavor does not use Google Places for autocomplete; it uses the
 * Pelias geocoding path already built into TripPlanFragment.
 */
public class MapLibreProprietaryMapHelper implements ProprietaryMapHelper {

    @Override
    public CustomAddress getCustomAddressFromPlacesIntent(Context context, Intent intent) {
        throw new UnsupportedOperationException(
                "Google Places is not available in the MapLibre flavor. "
                        + "Use Pelias geocoding instead.");
    }

    @Override
    public View.OnClickListener createPlacesAutocompleteOnClick(int requestCode,
            Fragment fragment, ObaRegion region) {
        // Return null — TripPlanFragment checks for null and falls back to Pelias autocomplete
        return null;
    }
}
