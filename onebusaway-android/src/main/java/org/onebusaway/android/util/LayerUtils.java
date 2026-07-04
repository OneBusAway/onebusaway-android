/*
 * Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
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

import android.content.Context;

import org.onebusaway.android.R;
import org.onebusaway.android.app.di.PreferencesEntryPoint;

/**
 * Utility methods related to creating layers. Currently only methods related to the bikeshare layer
 * are present as this is the only layer at the moment.
 *
 * Created by carvalhorr on 7/21/17.
 */

public class LayerUtils {

    /**
    * @return true if the bikeshare layer is active and visible
     */
    public static boolean isBikeshareLayerVisible(Context context) {
        return BikeshareAvailability.isEnabled(context) && PreferencesEntryPoint.get(context).getBoolean(
                R.string.preference_key_layer_bikeshare_visible, true);
    }
}
