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
package org.onebusaway.android.map;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.fragment.app.Fragment;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;

/**
 * Provider-agnostic interface for proprietary map helpers (e.g., Places autocomplete).
 * Implementations live in flavor-specific source sets.
 * The concrete class name is provided by {@link BuildConfig#MAP_HELPER_CLASS}.
 */
public interface ProprietaryMapHelper {

    /**
     * Decode the result of an autocomplete Intent into a CustomAddress.
     */
    CustomAddress getCustomAddressFromPlacesIntent(Context context, Intent intent);

    /**
     * Creates an OnClickListener that starts the places autocomplete flow.
     */
    View.OnClickListener createPlacesAutocompleteOnClick(int requestCode, Fragment fragment, ObaRegion region);

    /**
     * Returns a cached singleton instance for the current build flavor.
     */
    static ProprietaryMapHelper getInstance() {
        return Holder.INSTANCE;
    }

    // Lazy-initialized singleton holder
    final class Holder {
        static final ProprietaryMapHelper INSTANCE;

        static {
            try {
                INSTANCE = (ProprietaryMapHelper) Class.forName(BuildConfig.MAP_HELPER_CLASS)
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Proprietary map helper implementation not found: "
                        + BuildConfig.MAP_HELPER_CLASS, e);
            }
        }
    }
}
