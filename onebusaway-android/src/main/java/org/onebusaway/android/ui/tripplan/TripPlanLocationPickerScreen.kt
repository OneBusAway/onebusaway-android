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
package org.onebusaway.android.ui.tripplan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.onebusaway.android.R
import org.onebusaway.android.map.StopsMapViewModel
import org.onebusaway.android.map.compose.NoOpObaMapCallbacks
import org.onebusaway.android.map.compose.ObaMap
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * Lets the user pick a point for a trip-plan endpoint by panning the map under a fixed center
 * crosshair and confirming (former TripPlanLocationPickerActivity). Hosts the declarative
 * [ObaMap] driven by an entry-scoped [MapViewModel] in stop mode (so nearby stops show as you pan),
 * seeded with the initial center passed as nav-args. On confirm it writes the chosen lat/lon (Doubles)
 * to the previous back-stack entry's SavedStateHandle and pops back to the trip-plan destination.
 */
@Composable
fun TripPlanLocationPickerDestination(
    navController: NavHostController,
    lat: Double?,
    lon: Double?
) {
    // Entry-scoped — distinct from HomeActivity's map. A StopsMapViewModel: just the nearby-stops map
    // surface (no route/vehicle/directions/trip-focus machinery), starting in stop mode at construction.
    val mapViewModel = hiltViewModel<StopsMapViewModel>()

    Scaffold(
        topBar = {
            ObaTopAppBar(
                title = stringResource(R.string.trip_plan_pick_on_map),
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ObaMap(
                host = mapViewModel.host,
                callbacks = NoOpObaMapCallbacks,
                modifier = Modifier.fillMaxSize(),
                initialLatitude = lat ?: 0.0,
                initialLongitude = lon ?: 0.0,
                initialZoom = 16f,
            )
            // Fixed, non-interactive center crosshair (replicates the layout's ImageView): the map pans
            // under it and its center marks the chosen point.
            Icon(
                painter = painterResource(R.drawable.ic_my_location),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
            )
            Button(
                onClick = {
                    // The current viewport center, from the last camera idle the adapter published.
                    val center = mapViewModel.camera.value?.center
                    val chosenLat = center?.latitude ?: lat
                    val chosenLon = center?.longitude ?: lon
                    if (chosenLat != null && chosenLon != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.let { handle ->
                            handle[NavRoutes.RESULT_PICK_LAT] = chosenLat
                            handle[NavRoutes.RESULT_PICK_LON] = chosenLon
                        }
                    }
                    navController.popBackStack()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Text(stringResource(R.string.trip_plan_use_this_location))
            }
        }
    }
}
