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
package org.onebusaway.android.map.compose

import org.onebusaway.android.io.elements.ObaStop
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.map.render.GeoPoint
import org.opentripplanner.routing.bike_rental.BikeRentalStation

/**
 * Map interaction a flavor's [ObaComposeMapAdapter] reports back to its host. Flavor-neutral (no
 * map-SDK types — [onMapClick] takes a [GeoPoint], not a Google/maplibre `LatLng`), so it lives in
 * `src/main` and both flavor adapters/hosts share it. A stop tap focuses the stop, a map tap clears
 * focus, a bike tap reports bike focus, and the two info-window taps deep link via the host.
 *
 * Adapters that own their own marker-click dispatch (the maplibre classic API, where the host wires
 * listeners on the raw map) may ignore this and receive null instead.
 */
interface ObaMapCallbacks {
    fun onStopClick(stop: ObaStop)

    fun onMapClick(point: GeoPoint?)

    fun onBikeClick(station: BikeRentalStation)

    /** A vehicle marker tap — the host selects it (e.g. to show its most-recent-data marker). */
    fun onVehicleClick(status: ObaTripStatus) {}

    /** The vehicle info-window "more info" tap — the host navigates (e.g. to TripDetails). */
    fun onVehicleInfoWindowClick(status: ObaTripStatus)

    /** The bike info-window "more info" tap — the host navigates (e.g. the bikeshare deep link). */
    fun onBikeInfoWindowClick(station: BikeRentalStation)
}
