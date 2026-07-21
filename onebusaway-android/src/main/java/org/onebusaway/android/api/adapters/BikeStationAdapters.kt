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
package org.onebusaway.android.api.adapters

import org.onebusaway.android.api.contract.BikeRentalStationsDto
import org.onebusaway.android.api.contract.BikeStationDto
import org.onebusaway.android.map.bike.BikeStation

/**
 * Maps the OTP bike-rental wire DTOs (`api/contract/BikeModels.kt`) onto the app-owned
 * [BikeStation] domain model (`map/bike/BikeStation.kt`), replacing the old `toBikeRentalStations()`
 * mapper that built `org.opentripplanner.routing.bike_rental.BikeRentalStation` POJOs directly (#1779).
 * Follows the same DTO-to-domain convention as [toTripItinerary].
 */
fun BikeRentalStationsDto.toBikeStations(): List<BikeStation> = stations.map { it.toBikeStation() }

fun BikeStationDto.toBikeStation(): BikeStation = BikeStation(
    id = id,
    name = name,
    latitude = y,
    longitude = x,
    bikesAvailable = bikesAvailable,
    spacesAvailable = spacesAvailable,
    allowDropoff = allowDropoff,
    isFloatingBike = isFloatingBike,
    realTimeData = realTimeData
)
