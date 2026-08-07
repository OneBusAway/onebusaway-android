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
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.map.rental.RentalLayer

/** Utility methods related to optional map layers. */
object LayerUtils {

    /**
     * Which rental layers the rider has switched on — empty when the region has no rental server at
     * all (#2168).
     *
     * Bikes defaults **on** and scooters **off**, copying the sibling iOS app's defaults and its
     * reasoning: scooters are the large majority of a dockless fleet, so defaulting them on buries the
     * transit map. Bikes keeps the legacy `layer_bike_selected` key, so an upgrading device carries its
     * existing choice across.
     */
    fun visibleRentalLayers(context: Context): Set<RentalLayer> {
        if (!BikeshareAvailability.isStationLayerEnabled(context)) return emptySet()
        val prefs = PreferencesEntryPoint.get(context)
        return buildSet {
            if (prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, true)) add(RentalLayer.BIKES)
            if (prefs.getBoolean(R.string.preference_key_layer_scooters_visible, false)) add(RentalLayer.SCOOTERS)
        }
    }

    /**
     * The rider's minimum-range filter in metres, or null for "any". Stored as 0-means-no-filter so the
     * preference has one type; see `RentalLayerController.setMinimumRangeMeters`.
     */
    fun minimumRentalRangeMeters(context: Context): Int? = PreferencesEntryPoint.get(context)
        .getInt(R.string.preference_key_layer_rental_min_range_meters, 0)
        .takeIf { it > 0 }
}
