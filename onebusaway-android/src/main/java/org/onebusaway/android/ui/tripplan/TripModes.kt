/*
 * Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.tripplan

import org.onebusaway.android.R

/**
 * Utility class to convert between the selected trip mode in the spinner and the trip mode code.
 */
object TripModes {

    const val TRANSIT_AND_BIKE = 0
    const val BUS_ONLY = 1
    const val RAIL_ONLY = 2
    const val BIKESHARE = 3
    const val TRANSIT_ONLY = 4

    /**
     * Return the trip mode code based on the selected label string resource id from the spinner
     * that shows the trip mode options.
     *
     * @param selection string resource id of the selected trip mode in the UI
     * @return corresponding trip mode code
     */
    fun getTripModeCodeFromSelection(selection: Int): Int {
        return when (selection) {
            R.string.transit_mode_transit_and_bikeshare -> TRANSIT_AND_BIKE
            R.string.transit_mode_transit_only -> TRANSIT_ONLY
            R.string.transit_mode_bus -> BUS_ONLY
            R.string.transit_mode_rail -> RAIL_ONLY
            R.string.transit_mode_bikeshare -> BIKESHARE
            else -> -1
        }
    }
}
