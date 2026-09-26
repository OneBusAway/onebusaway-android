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
package org.onebusaway.android.ui.home.map

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.onebusaway.android.R
import org.onebusaway.android.map.Basemap
import org.onebusaway.android.map.rental.RENTALS_VISIBLE_BY_DEFAULT
import org.onebusaway.android.map.rental.RentalLayer
import org.onebusaway.android.map.rental.defaultVisible

enum class LayerTileId { ON_DEMAND_ZONES, BIKES, SCOOTERS }

enum class LayerGroup { TRANSIT, RENTALS }

/** One tile of the sheet's grid (spec §3.9): what it is, and whether it reads On. */
data class LayerTile(val id: LayerTileId, @StringRes val titleRes: Int, @DrawableRes val iconRes: Int, val enabled: Boolean, val group: LayerGroup)

data class MapLayersUiState(
    /** Null when this flavour offers no basemap choice. */
    val basemap: Basemap?,
    val transit: List<LayerTile>,
    val rentals: List<LayerTile>,
    val differsFromDefaults: Boolean,
    /** Enabled tiles among the visible ones: the FAB's badge. */
    val enabledCount: Int
) {
    /** Nothing to show, so neither the sheet nor its button appears. */
    val isEmpty: Boolean get() = basemap == null && transit.isEmpty() && rentals.isEmpty()

    companion object {
        val EMPTY = MapLayersUiState(basemap = null, transit = emptyList(), rentals = emptyList(), differsFromDefaults = false, enabledCount = 0)
    }
}

/** The five preferences the sheet reads and writes; the existing keys keep their names and defaults (spec §2.10). */
data class LayerPrefs(val zones: Boolean, val rentalsMaster: Boolean, val bikes: Boolean, val scooters: Boolean, val basemap: Basemap) {

    fun differsFrom(other: LayerPrefs): Boolean = this != other

    /**
     * Spec §3.9: a tile's displayed state is `master && layerPref`. Turning a tile on sets its layer
     * preference and the master; turning it off clears its preference and drops the master when no
     * layer preference remains true. `RentalLayerController.syncFromPreferences()` reads the result unchanged.
     */
    fun toggled(id: LayerTileId): LayerPrefs = when (id) {
        LayerTileId.ON_DEMAND_ZONES -> copy(zones = !zones)
        LayerTileId.BIKES -> {
            val on = !(rentalsMaster && bikes)
            copy(bikes = on, rentalsMaster = on || scooters)
        }
        LayerTileId.SCOOTERS -> {
            val on = !(rentalsMaster && scooters)
            copy(scooters = on, rentalsMaster = on || bikes)
        }
    }

    companion object {
        val DEFAULTS = LayerPrefs(
            zones = true,
            rentalsMaster = RENTALS_VISIBLE_BY_DEFAULT,
            bikes = RentalLayer.BIKES.defaultVisible,
            scooters = RentalLayer.SCOOTERS.defaultVisible,
            basemap = Basemap.STANDARD
        )
    }
}

fun layersUiState(prefs: LayerPrefs, onDemandTileVisible: Boolean, rentalsEnabled: Boolean, hasBasemapChoice: Boolean): MapLayersUiState {
    val transit = if (onDemandTileVisible) {
        listOf(LayerTile(LayerTileId.ON_DEMAND_ZONES, R.string.map_layers_on_demand_zones, R.drawable.ic_directions_car, prefs.zones, LayerGroup.TRANSIT))
    } else {
        emptyList()
    }
    val rentals = if (rentalsEnabled) {
        listOf(
            LayerTile(LayerTileId.BIKES, R.string.map_layers_bikes, R.drawable.ic_directions_bike, prefs.rentalsMaster && prefs.bikes, LayerGroup.RENTALS),
            LayerTile(LayerTileId.SCOOTERS, R.string.map_layers_scooters, R.drawable.ic_kick_scooter, prefs.rentalsMaster && prefs.scooters, LayerGroup.RENTALS)
        )
    } else {
        emptyList()
    }
    return MapLayersUiState(
        basemap = prefs.basemap.takeIf { hasBasemapChoice },
        transit = transit,
        rentals = rentals,
        differsFromDefaults = prefs.differsFrom(LayerPrefs.DEFAULTS),
        enabledCount = (transit + rentals).count { it.enabled }
    )
}
