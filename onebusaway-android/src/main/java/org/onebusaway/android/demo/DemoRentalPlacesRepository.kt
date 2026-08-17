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
package org.onebusaway.android.demo

import android.location.Location
import javax.inject.Inject
import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.map.rental.DefaultRentalPlacesRepository
import org.onebusaway.android.map.rental.RentalKind
import org.onebusaway.android.map.rental.RentalPlace
import org.onebusaway.android.map.rental.RentalPlacesRepository

/**
 * Serves the scripted tutorial's micromobility layer (#2164) while demo mode is on, delegating to the
 * real [DefaultRentalPlacesRepository] otherwise.
 *
 * The set is authored rather than captured because it exists to *teach the layer*: it deliberately
 * covers each shape the renderer and the detail window draw differently — a docking station with
 * vehicles and spaces, free-floating e-bikes and scooters with a charge to show, a station that is full
 * (nothing to take), and one vehicle the operator has taken out of service — so the tour's bike-layer
 * step demonstrates the whole legend rather than whatever happened to be parked nearby.
 *
 * Positions are scattered around the demo anchor so they land in the same viewport the tour is already
 * looking at. The bounding box is honoured so panning behaves normally.
 */
class DemoRentalPlacesRepository @Inject constructor(
    private val demoMode: DemoModeController,
    private val real: DefaultRentalPlacesRepository
) : RentalPlacesRepository {

    override suspend fun getRentals(southWest: Location, northEast: Location): Result<List<RentalPlace>> {
        if (!demoMode.isActive) return real.getRentals(southWest, northEast)
        val inBox = DEMO_RENTALS.filter {
            it.latitude in southWest.latitude..northEast.latitude &&
                it.longitude in southWest.longitude..northEast.longitude
        }
        return Result.success(inBox)
    }

    private companion object {
        /** The demo anchor — E Pine St & Summit Ave, the stop the tour is standing at. Derived from
         *  [DemoModeController.CAMERA_TARGET] so moving the demo neighbourhood moves the rentals with it. */
        val ANCHOR_LAT = DemoModeController.CAMERA_TARGET.latitude
        val ANCHOR_LON = DemoModeController.CAMERA_TARGET.longitude

        val DEMO_RENTALS: List<RentalPlace> = listOf(
            RentalPlace(
                id = "demo_dock_pine_summit",
                name = "E Pine St & Summit Ave",
                latitude = ANCHOR_LAT + 0.0007,
                longitude = ANCHOR_LON + 0.0009,
                kind = RentalKind.STATION,
                networkId = "demo_bikeshare",
                formFactors = setOf(RentalFormFactor.BICYCLE),
                propulsion = RentalPropulsion.HUMAN,
                vehiclesAvailableCount = 6,
                docksAvailableCount = 9,
                allowPickupNow = true,
                allowDropoffNow = true,
                operative = true
            ),
            RentalPlace(
                id = "demo_ebike_bellevue",
                name = "E Pine St & Bellevue Ave",
                latitude = ANCHOR_LAT + 0.0004,
                longitude = ANCHOR_LON - 0.0021,
                kind = RentalKind.VEHICLE,
                networkId = "demo_bikeshare",
                formFactors = setOf(RentalFormFactor.BICYCLE),
                propulsion = RentalPropulsion.ELECTRIC_ASSIST,
                rangeMeters = 24_000,
                fuelPercent = 0.82,
                allowPickupNow = true,
                operative = true
            ),
            RentalPlace(
                id = "demo_scooter_broadway",
                name = "Broadway & E Pine St",
                latitude = ANCHOR_LAT + 0.0011,
                longitude = ANCHOR_LON - 0.0044,
                kind = RentalKind.VEHICLE,
                networkId = "demo_scooters",
                formFactors = setOf(RentalFormFactor.SCOOTER),
                propulsion = RentalPropulsion.ELECTRIC,
                rangeMeters = 11_500,
                fuelPercent = 0.41,
                allowPickupNow = true,
                operative = true
            ),
            RentalPlace(
                id = "demo_scooter_harvard",
                name = "E Pine St & Harvard Ave",
                latitude = ANCHOR_LAT - 0.0006,
                longitude = ANCHOR_LON - 0.0013,
                kind = RentalKind.VEHICLE,
                networkId = "demo_scooters",
                formFactors = setOf(RentalFormFactor.SCOOTER),
                propulsion = RentalPropulsion.ELECTRIC,
                rangeMeters = 3_200,
                fuelPercent = 0.12,
                allowPickupNow = true,
                operative = true
            ),
            // A full dock: somewhere to leave a bike, nothing to take. The renderer says so rather than
            // drawing it like any other station.
            RentalPlace(
                id = "demo_dock_denny",
                name = "Broadway & E Denny Way",
                latitude = ANCHOR_LAT + 0.0026,
                longitude = ANCHOR_LON - 0.0038,
                kind = RentalKind.STATION,
                networkId = "demo_bikeshare",
                formFactors = setOf(RentalFormFactor.BICYCLE),
                propulsion = RentalPropulsion.HUMAN,
                vehiclesAvailableCount = 0,
                docksAvailableCount = 14,
                allowPickupNow = false,
                allowDropoffNow = true,
                operative = true
            ),
            // Explicitly out of service — the one state that is a fact about the operator rather than
            // about availability.
            RentalPlace(
                id = "demo_ebike_boylston",
                name = "E Pine St & Boylston Ave",
                latitude = ANCHOR_LAT - 0.0013,
                longitude = ANCHOR_LON - 0.0029,
                kind = RentalKind.VEHICLE,
                networkId = "demo_bikeshare",
                formFactors = setOf(RentalFormFactor.BICYCLE),
                propulsion = RentalPropulsion.ELECTRIC_ASSIST,
                rangeMeters = 0,
                fuelPercent = 0.03,
                allowPickupNow = false,
                operative = false
            )
        )
    }
}
