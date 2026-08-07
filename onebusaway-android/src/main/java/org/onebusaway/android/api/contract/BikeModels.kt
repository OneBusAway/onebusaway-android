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
package org.onebusaway.android.api.contract

import kotlinx.serialization.Serializable

/**
 * Bike-rental response models for [BikeWebService]. These come from an **OTP 1.x** server
 * (`routers/default/bike_rental`), not the OBA `where` API, and are plain JSON. Since #2168 this is
 * the *fallback* wire shape for the rental map layer, used where a region publishes no
 * bikeshare-capable OTP2 GraphQL endpoint — see `Rentals.graphql` for the richer path.
 *
 * [org.onebusaway.android.api.adapters.toRentalPlaces] (in `api/adapters/RentalPlaceAdapters.kt`) maps
 * these onto the app-owned [org.onebusaway.android.map.rental.RentalPlace] domain model the map/overlay
 * consumers (RentalLayerController, MapRenderState, RentalInfoWindow, …) actually read. Property names
 * mirror the OTP field names so no `@SerialName` is needed.
 */
@Serializable
data class BikeRentalStationsDto(
    val stations: List<BikeStationDto> = emptyList()
)

@Serializable
data class BikeStationDto(
    val id: String = "",
    val name: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val bikesAvailable: Int = 0,
    val spacesAvailable: Int = 0,
    val allowDropoff: Boolean = false,
    val isFloatingBike: Boolean = false,
    val realTimeData: Boolean = false
)
