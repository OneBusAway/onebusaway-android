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
package org.onebusaway.android.ui.arrivals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.app.di.ArrivalsViewModelFactoryEntryPoint
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.region.RegionState
import org.onebusaway.android.ui.arrivals.components.rememberArrivalDisplayMode
import org.onebusaway.android.ui.common.Shortcuts
import org.onebusaway.android.ui.compose.components.SearchableTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.home.FocusedStop
import org.onebusaway.android.ui.home.homeStartDestination
import org.onebusaway.android.ui.home.map.FocusBannerViewModel
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.StopReveal
import org.onebusaway.android.ui.nav.arrivalsMapEnterTransition
import org.onebusaway.android.ui.nav.arrivalsMapExitTransition
import org.onebusaway.android.ui.nav.navigateUpFromArrivals
import org.onebusaway.android.ui.nav.showRouteMapFromArrivals
import org.onebusaway.android.ui.nav.showStopMapFromArrivals
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher
import org.onebusaway.android.util.GeoPoint

/** A mapless stop destination. Its VM and scroll position live with the back-stack entry. */
fun NavGraphBuilder.arrivalsGraph(navController: NavHostController) {
    composable(
        NavRoutes.ARRIVALS,
        enterTransition = { arrivalsMapEnterTransition() },
        exitTransition = { arrivalsMapExitTransition() },
        arguments = listOf(
            navArgument(NavRoutes.ARG_STOP_ID) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_STOP_NAME) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { entry ->
        val stopId = requireNotNull(entry.arguments?.getString(NavRoutes.ARG_STOP_ID)) { "Arrivals requires a stop id" }
        val initialName = entry.arguments?.getString(NavRoutes.ARG_STOP_NAME)
        ObaTheme { ArrivalsBoard(stopId, initialName, navController) }
    }
}

@Composable
private fun ArrivalsBoard(stopId: String, initialName: String?, navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: ArrivalsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ArrivalsViewModelFactoryEntryPoint.get(context).create(stopId) }
        }
    )
    val activity = context.findActivity()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val handler = remember(viewModel, navController) {
        createArrivalActionHandler(
            activity = activity,
            viewModel = viewModel,
            currentContent = { viewModel.state.value as? ArrivalsUiState.Content },
            revealRoute = { _, request -> navController.showRouteMapFromArrivals(request) },
            showUndoSnackbar = { message, action, undo ->
                scope.launch {
                    if (snackbar.showSnackbar(activity.getString(message), action?.let(activity::getString)) ==
                        SnackbarResult.ActionPerformed
                    ) {
                        undo?.invoke()
                    }
                }
            },
            onShowTrip = { trip, stop ->
                navController.navigate(NavRoutes.tripDetails(trip, stop, TripDetailsLauncher.SCROLL_MODE_STOP))
            },
            onEditReminder = { navController.navigate(NavRoutes.tripInfo(it)) }
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state as? ArrivalsUiState.Content
    val title = initialName ?: content?.header?.name ?: stringResource(R.string.arrivals_board_title)
    val prefs = remember { PreferencesEntryPoint.get(context) }
    var displayMode by rememberArrivalDisplayMode(stopId) { prefs.arrivalDisplayDefault() }
    val listState = rememberLazyListState()
    val callbacks = rememberArrivalRowCallbacks(handler, viewModel, mapless = true)
    // Share the existing optimistic, import-gated stop favorite implementation with the map banner.
    val favorites: FocusBannerViewModel = hiltViewModel()
    val favoriteIds by favorites.favoriteStopIds.collectAsStateWithLifecycle()
    val favoritesReady by favorites.stopFavoritesReady.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    val point = content?.let { GeoPoint(it.stopLat, it.stopLon) }
    // Wait for endpoint resolution. Active(null) is a configured custom API URL and can poll
    // without a region; Resolving/NeedsManualChoice/Failed must still wait.
    val regions = remember { RegionEntryPoint.get(context) }
    val regionState by regions.state.collectAsStateWithLifecycle()
    if (regionState is RegionState.Active) ArrivalsPolling(viewModel)

    Scaffold(
        modifier = Modifier.testTag("arrivals_board"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SearchableTopAppBar(
                title = title,
                onSearch = { navController.navigate(NavRoutes.search(it)) },
                onBack = { navController.navigateUpFromArrivals(prefs.homeStartDestination()) }
            ) {
                IconButton(
                    enabled = favoritesReady && point != null,
                    onClick = {
                        favorites.toggleStopFavorite(FocusedStop(stopId, title, content?.stopCode, point))
                    }
                ) {
                    Icon(
                        painterResource(if (stopId in favoriteIds) R.drawable.star else R.drawable.star_outline),
                        stringResource(if (stopId in favoriteIds) R.string.stop_remove_star else R.string.stop_add_star)
                    )
                }
                IconButton(onClick = { navController.showStopMapFromArrivals(StopReveal(stopId, initialName ?: content?.header?.name, point)) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_location_map),
                        contentDescription = stringResource(R.string.home_map)
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.more_vert), stringResource(R.string.stop_info_item_options_title))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.stop_info_option_refresh)) }, onClick = {
                            menuOpen = false
                            viewModel.manualRefresh()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.my_context_create_shortcut)) }, onClick = {
                            menuOpen = false
                            Shortcuts.createStopShortcut(context, title, StopLauncher.Builder(context, stopId).setStopName(title))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.stop_info_option_report_problem)) }, enabled = content != null, onClick = {
                            menuOpen = false
                            handler.onReportStopProblem()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.stop_info_option_night_light)) }, onClick = {
                            menuOpen = false
                            navController.navigate(NavRoutes.NIGHT_LIGHT)
                        })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                ArrivalsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ArrivalsUiState.Error -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(current.message)
                    Button(onClick = viewModel::manualRefresh) { Text(stringResource(R.string.retry)) }
                }
                is ArrivalsUiState.Content -> ArrivalsList(
                    content = current,
                    rowCallbacks = callbacks,
                    onShowAlert = handler::onShowAlert,
                    onHideAlert = handler::onHideAlert,
                    onShowHiddenAlerts = viewModel::showHiddenAlerts,
                    onLoadMore = viewModel::loadMore,
                    loadingMore = viewModel.loadingMore,
                    listState = listState,
                    displayMode = displayMode,
                    onDisplayModeChange = { displayMode = it },
                    modeSwitchModifier = Modifier.padding(top = 3.dp, end = 3.dp)
                )
            }
        }
    }
}
