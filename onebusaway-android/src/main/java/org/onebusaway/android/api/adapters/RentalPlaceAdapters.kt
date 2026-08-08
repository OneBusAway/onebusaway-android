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
import org.onebusaway.android.api.graphql.RentalsByBboxQuery
import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalPlace

/**
 * Maps both rental wire shapes onto the app-owned [RentalPlace] domain model (#2168): OTP2's
 * `vehicleRentalsByBbox` union (the rich path) and OTP1's REST `/bike_rental` stations (the fallback
 * for regions with no OTP2 endpoint). Map consumers read only [RentalPlace].
 *
 * Every field OTP1 cannot state stays null on that path, rather than being defaulted to something
 * plausible — see [RentalPlace] for why the difference is load-bearing.
 */

/** The OTP2 union, mapped element-wise. Entities of neither member type are dropped, not guessed at. */
fun RentalsByBboxQuery.Data.toRentalPlaces(): List<RentalPlace> = vehicleRentalsByBbox.mapNotNull { entity ->
    entity.onRentalVehicle?.toRentalPlace() ?: entity.onVehicleRentalStation?.toRentalPlace()
}

/**
 * A free-floating vehicle. It has one form factor, its own charge, and no occupancy counts at all —
 * a parked scooter is not a dock with one scooter in it, and the detail window must not offer
 * "spaces available" for one.
 */
private fun RentalsByBboxQuery.OnRentalVehicle.toRentalPlace(): RentalPlace? = RentalPlace(
    id = vehicleId?.ifBlank { null } ?: id,
    // No `name`: OTP fills a vehicle's from its GBFS type, which every vehicle in the one reachable
    // deployment publishes as the literal "Default vehicle type" (see Rentals.graphql). The UI words
    // the vehicle from its form factor instead.
    name = "",
    latitude = lat ?: return null,
    longitude = lon ?: return null,
    kind = RentalKind.VEHICLE,
    networkId = rentalNetwork.rentalNetworkFields.networkId,
    networkUrl = rentalNetwork.rentalNetworkFields.url.absoluteUriOrNull(),
    androidUri = rentalUris?.rentalUriFields?.android.absoluteUriOrNull(),
    webUri = rentalUris?.rentalUriFields?.web.absoluteUriOrNull(),
    formFactors = setOfNotNull(vehicleType?.formFactor?.rawValue.toEnum<RentalFormFactor>()),
    propulsion = vehicleType?.propulsionType?.rawValue.toEnum<RentalPropulsion>(),
    rangeMeters = fuel?.range,
    fuelPercent = fuel?.percent,
    allowPickupNow = allowPickupNow,
    operative = operative
)

/**
 * A docking station. Its [RentalPlace.formFactors] come from the types its available vehicles are
 * counted under, since a dock publishes no `vehicleType` of its own — that is what decides whether a
 * dock of scooters appears on the scooters layer.
 */
private fun RentalsByBboxQuery.OnVehicleRentalStation.toRentalPlace(): RentalPlace? = RentalPlace(
    id = stationId?.ifBlank { null } ?: id,
    name = name,
    latitude = lat ?: return null,
    longitude = lon ?: return null,
    kind = RentalKind.STATION,
    networkId = rentalNetwork.rentalNetworkFields.networkId,
    networkUrl = rentalNetwork.rentalNetworkFields.url.absoluteUriOrNull(),
    androidUri = rentalUris?.rentalUriFields?.android.absoluteUriOrNull(),
    webUri = rentalUris?.rentalUriFields?.web.absoluteUriOrNull(),
    formFactors = availableVehicles?.byType.orEmpty()
        // A type with a zero count is a type this dock is *out* of right now, and a rider filtering
        // the map to scooters is looking for one to ride — so an empty scooter bay doesn't put the
        // dock on the scooters layer.
        .filter { it.count > 0 }
        .mapNotNullTo(mutableSetOf()) { it.vehicleType.formFactor?.rawValue.toEnum<RentalFormFactor>() },
    vehiclesAvailableCount = availableVehicles?.total,
    docksAvailableCount = availableSpaces?.total,
    allowPickupNow = allowPickupNow,
    allowDropoffNow = allowDropoffNow,
    operative = operative
)

/**
 * The OTP1 REST fallback. The protocol has no form factor, no operator network, no rental URIs, no
 * charge and no current-pickup/dropoff facts, so all of those stay null — and
 * [org.onebusaway.android.map.rental.rentalLayersOf] draws a place stating no form factor on the
 * bikes layer, which is the only layer this path has ever had.
 *
 * `isFloatingBike` finally has a consumer: it is how OTP1 signals a free-floating vehicle disguised
 * as a station, and it becomes the [RentalKind] the renderer and detail window branch on. Before
 * #2168 it was decoded, carried through this adapter and read by nobody, so a parked scooter drew a
 * dock's marker and a dock's "bikes available / spaces available" info window.
 */
fun BikeRentalStationsDto.toRentalPlaces(): List<RentalPlace> = stations.map { it.toRentalPlace() }

fun BikeStationDto.toRentalPlace(): RentalPlace = RentalPlace(
    id = id,
    name = name,
    latitude = y,
    longitude = x,
    kind = if (isFloatingBike) RentalKind.VEHICLE else RentalKind.STATION,
    // A free-floating vehicle carries the whole fleet's counts on this endpoint (OTP1 fabricates a
    // one-bike "station" for it), so reporting them as a dock's occupancy would be inventing a dock.
    vehiclesAvailableCount = if (isFloatingBike) null else bikesAvailable,
    docksAvailableCount = if (isFloatingBike) null else spacesAvailable,
    // OTP1's `allowDropoff` is the station's standing policy, not a live "right now" reading — the
    // distinction OTP2 draws with `allowDropoff` vs. `allowDropoffNow`. Mapping it onto the live
    // field would let the detail window say "not accepting returns" on a full-but-open dock.
    allowDropoffNow = null
)
