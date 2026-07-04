/*
 * Copyright (C) 2015-2026 University of South Florida (sjbarbeau@gmail.com),
 * Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.arrivals.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalsViewModel
import org.onebusaway.android.ui.compose.components.RadioOptionList

// Selections need to match strings.xml "route_favorite_options"
private const val SELECTION_THIS_STOP = 0

/**
 * Renders the route-favorite dialog when the [viewModel] has a pending request. Hosted by both the
 * standalone arrivals screen and the map panel so the per-arrival "favorite route" action works in
 * either. The favoriting write + route-details backfill live in [ArrivalsRepository]; this is pure UI.
 */
@Composable
internal fun RouteFavoriteHost(viewModel: ArrivalsViewModel) {
    val request by viewModel.favoriteRequest.collectAsStateWithLifecycle()
    request?.let { actions ->
        RouteFavoriteDialog(
            actions = actions,
            onDismiss = viewModel::dismissRouteFavorite,
            onConfirm = { allStops -> viewModel.favoriteRoute(actions, allStops) }
        )
    }
}

/**
 * Asks whether to save (or remove) a route/headsign favorite for all stops, or just this stop.
 * [onConfirm] receives true when "all stops" was chosen.
 */
@Composable
private fun RouteFavoriteDialog(
    actions: ArrivalActions,
    onDismiss: () -> Unit,
    onConfirm: (allStops: Boolean) -> Unit
) {
    val starring = !actions.isRouteFavorite
    val routeTitle = buildRouteTitle(actions.routeShortName, actions.headsign)
    val title = stringResource(
        if (starring) R.string.route_favorite_options_title_star
        else R.string.route_favorite_options_title_unstar,
        routeTitle
    )
    val options = stringArrayResource(R.array.route_favorite_options)
    var selected by remember { mutableStateOf(SELECTION_THIS_STOP) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            RadioOptionList(options = options, selectedIndex = selected, onSelect = { selected = it })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected != SELECTION_THIS_STOP) }) {
                Text(stringResource(R.string.stop_info_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.stop_info_cancel)) }
        }
    )
}

/** The route short name and headsign, each truncated with an ellipsis, for the dialog title. */
private fun buildRouteTitle(routeShortName: String?, headsign: String?): String = buildString {
    if (!routeShortName.isNullOrEmpty()) {
        if (routeShortName.length > 3) {
            append(routeShortName.substring(0, 3)).append("...")
        } else {
            append(routeShortName)
        }
        append(" - ")
    }
    if (!headsign.isNullOrEmpty()) {
        if (headsign.length > 8) {
            append(headsign.substring(0, 8)).append("...")
        } else {
            append(headsign)
        }
    }
}
