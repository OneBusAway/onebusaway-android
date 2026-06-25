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
package org.onebusaway.android.ui.routeinfo

/** A stop within a route direction, as displayed on the route info screen. */
data class RouteStopItem(
    val id: String,
    val name: String,
    /** Raw compass direction code ("N", "SW", ...); empty when unknown. */
    val direction: String,
    val latitude: Double,
    val longitude: Double
)

/** A direction (group) of a route, with its ordered stops. */
data class RouteDirection(
    val name: String,
    val stops: List<RouteStopItem>
)

/**
 * Route metadata plus its stops grouped by direction, decoupled from the io/elements
 * response types.
 *
 * @param longName secondary name (long name, falling back to description), or null if none
 * @param agencyName operating agency, or null if unknown
 * @param url the route's schedule page, or null if none (never blank)
 */
data class RouteInfo(
    val id: String,
    val shortName: String,
    val longName: String?,
    val agencyName: String?,
    val url: String?,
    val directions: List<RouteDirection>
)

/** UI state for the route info screen. */
sealed interface RouteInfoUiState {

    data object Loading : RouteInfoUiState

    data class Success(val route: RouteInfo) : RouteInfoUiState

    /** [message] is the route-specific error from ObaRequestErrors.getRouteErrorString. */
    data class Error(val message: String) : RouteInfoUiState
}
