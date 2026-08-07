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
     * Which rental layers to draw — every one, or none (#2168). Bikes and scooters come off a single
     * fetch and are shown or hidden together by one map button, so this is a boolean wearing a set's
     * clothing: the set is what the renderer needs (it picks a marker's colour and glyph from the
     * layer a place belongs to), not a second axis of user choice.
     *
     * Empty when the region has no rental server at all. Defaults to on, and keeps the legacy
     * `layer_bike_selected` key so an upgrading device carries its existing choice across.
     */
    fun visibleRentalLayers(context: Context): Set<RentalLayer> {
        if (!BikeshareAvailability.isStationLayerEnabled(context)) return emptySet()
        val on = PreferencesEntryPoint.get(context)
            .getBoolean(R.string.preference_key_layer_bikeshare_visible, true)
        return if (on) RentalLayer.entries.toSet() else emptySet()
    }
}
