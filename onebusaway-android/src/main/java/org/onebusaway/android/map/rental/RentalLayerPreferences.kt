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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Which rental layers the map draws (#2168), from the four preferences that decide it.
 *
 * The map's rental control is a **master button with two mode toggles under it**, so the answer is a
 * conjunction: the master says whether rentals show at all, and each mode says whether its own kind
 * does. Keeping the master separate from the two modes is what lets the button hide everything without
 * forgetting which modes were on: flipping it back restores the rider's choice rather than turning
 * both kinds on.
 *
 * **Hiding the button hides the layer too** (#2392). The button is the only control on the map that
 * can switch rentals off, so a layer drawn under a hidden button is one the rider can no longer clear —
 * and "hide this" is exactly what a rider reaches for when they don't want bikes and scooters at all.
 * The other three preferences are left as they were, so showing the button again brings back exactly
 * what the rider had.
 */
fun drawnRentalLayers(
    buttonShown: Boolean,
    rentalsVisible: Boolean,
    bikesVisible: Boolean,
    scootersVisible: Boolean
): Set<RentalLayer> = when {
    !buttonShown || !rentalsVisible -> emptySet()
    else -> buildSet {
        if (bikesVisible) add(RentalLayer.BIKES)
        if (scootersVisible) add(RentalLayer.SCOOTERS)
    }
}

/**
 * [drawnRentalLayers], kept current as the rider changes any of its four preferences — wherever they
 * are written from (the map's buttons, Settings, the guided tour), so no writer has to remember to tell
 * the map.
 *
 * The master is the legacy `layer_bike_selected` key, so a device upgrading from the bikeshare-only
 * layer carries its on/off choice straight across onto the new master — [RENTALS_VISIBLE_BY_DEFAULT]
 * only applies where nothing was ever set.
 */
fun PreferencesRepository.observeDrawnRentalLayers(): Flow<Set<RentalLayer>> = combine(
    observeBoolean(R.string.preference_key_show_rental_button, RENTAL_BUTTON_SHOWN_BY_DEFAULT),
    observeBoolean(R.string.preference_key_layer_bikeshare_visible, RENTALS_VISIBLE_BY_DEFAULT),
    observeBoolean(RentalLayer.BIKES.preferenceKey, RentalLayer.BIKES.defaultVisible),
    observeBoolean(RentalLayer.SCOOTERS.preferenceKey, RentalLayer.SCOOTERS.defaultVisible),
    ::drawnRentalLayers
).distinctUntilChanged()

/** Whether the map shows its rental button before the rider has said. Hiding it hides the layer too. */
internal const val RENTAL_BUTTON_SHOWN_BY_DEFAULT = true

/**
 * Whether rentals show at all before the rider has said. **Off** — the layer is an optional overlay
 * over the transit map the app is actually for, the same reasoning that starts scooters off within it
 * (see [defaultVisible]), and one tap brings it up.
 *
 * On was tried first and exposed a real defect rather than a preference problem: the controller was
 * seeded from a region-gated helper that answered empty before region discovery landed, so a fresh
 * install drew a lit button over a layer that never loaded. That is fixed in
 * `RentalLayerController`, which now observes the preferences rather than being seeded — this default is now a product choice, and flipping it
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
