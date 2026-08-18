/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.map.rental

import androidx.annotation.StringRes
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Which rental layers the rider has switched on (#2168), from the three preferences that decide it.
 *
 * The map's rental control is a **master button with two mode toggles under it**, so the answer is a
 * conjunction: the master says whether rentals show at all, and each mode says whether its own kind
 * does. Written once here because three readers need the same answer and must not drift — the map
 * chrome (which tints the buttons) and `RentalLayerController` (which recomputes after every tap and
 * on resume).
 *
 * Keeping the master separate from the two modes is what lets the button hide everything without
 * forgetting which modes were on: flipping it back restores the rider's choice rather than turning
 * both kinds on.
 *
 * The master is the legacy `layer_bike_selected` key, so a device upgrading from the bikeshare-only
 * layer carries its on/off choice straight across onto the new master — [RENTALS_VISIBLE_BY_DEFAULT]
 * only applies where nothing was ever set.
 */
fun rentalLayersFromPreferences(prefs: PreferencesRepository): Set<RentalLayer> {
    if (!prefs.getBoolean(R.string.preference_key_layer_bikeshare_visible, RENTALS_VISIBLE_BY_DEFAULT)) {
        return emptySet()
    }
    return RentalLayer.entries.filterTo(mutableSetOf()) { prefs.getBoolean(it.preferenceKey, it.defaultVisible) }
}

/**
 * Whether rentals show at all before the rider has said. **Off** — the layer is an optional overlay
 * over the transit map the app is actually for, the same reasoning that starts scooters off within it
 * (see [defaultVisible]), and one tap brings it up.
 *
 * On was tried first and exposed a real defect rather than a preference problem: the controller was
 * seeded from a region-gated helper that answered empty before region discovery landed, so a fresh
 * install drew a lit button over a layer that never loaded. That is fixed in
 * `RentalLayerController.syncFromPreferences` — this default is now a product choice, and flipping it
 * back on would work.
 */
internal const val RENTALS_VISIBLE_BY_DEFAULT = false

/** Where each mode's own toggle is persisted. */
@get:StringRes
val RentalLayer.preferenceKey: Int
    get() = when (this) {
        RentalLayer.BIKES -> R.string.preference_key_layer_bikes_visible
        RentalLayer.SCOOTERS -> R.string.preference_key_layer_scooters_visible
    }

/**
 * Whether a mode starts on. Bikes do and scooters don't, copying the sibling iOS app's defaults *and*
 * its reasoning: scooters are the large majority of a dockless fleet — 74% of the supply in iOS's
 * launch region — so defaulting them on buries the transit map the app is actually for. The rider who
 * wants them is one tap away.
 */
val RentalLayer.defaultVisible: Boolean
    get() = this == RentalLayer.BIKES
