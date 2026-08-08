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
package org.onebusaway.android.util

import android.content.Context
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.rentalLayersFromPreferences

/** Utility methods related to optional map layers. */
object LayerUtils {

    /**
     * Which rental layers to draw, or empty when the region has no rental server at all (#2168). The
     * preference reading itself lives in [rentalLayersFromPreferences], shared with the controller and
     * the map chrome so the three cannot disagree about what is on.
     */
    fun visibleRentalLayers(context: Context): Set<RentalLayer> = if (BikeshareAvailability.isStationLayerEnabled(context)) {
        rentalLayersFromPreferences(PreferencesEntryPoint.get(context))
    } else {
        emptySet()
    }
}
