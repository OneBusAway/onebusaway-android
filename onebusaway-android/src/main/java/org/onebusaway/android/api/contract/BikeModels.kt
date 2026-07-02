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
import org.opentripplanner.routing.bike_rental.BikeRentalStation

/**
 * Bike-rental response models for [BikeWebService]. These come from an OpenTripPlanner server
 * (`routers/default/bike_rental`), not the OBA `where` API, and are plain JSON.
 *
 * The map/overlay consumers (BikeLayerController, MapRenderState, BikeInfoWindow, …) all read the
 * OTP library's [BikeRentalStation] POJO, so rather than ripple a new type through them, the DTO
 * decodes the wire and [toBikeRentalStations] maps onto that POJO (its fields are public and it has
 * a no-arg constructor). Property names mirror the OTP field names so no `@SerialName` is needed.
 */
@Serializable
data class BikeRentalStationsDto(
    val stations: List<BikeStationDto> = emptyList(),
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
    val realTimeData: Boolean = false,
)

/** Maps the decoded stations onto the OTP [BikeRentalStation] POJO the map consumers expect. */
fun BikeRentalStationsDto.toBikeRentalStations(): List<BikeRentalStation> =
    stations.map { it.toBikeRentalStation() }

private fun BikeStationDto.toBikeRentalStation(): BikeRentalStation = BikeRentalStation().also {
    it.id = id
    it.name = name
    it.x = x
    it.y = y
    it.bikesAvailable = bikesAvailable
    it.spacesAvailable = spacesAvailable
    it.allowDropoff = allowDropoff
    it.isFloatingBike = isFloatingBike
    it.realTimeData = realTimeData
}
