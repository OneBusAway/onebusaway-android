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
package org.onebusaway.android.map.bike

/**
 * The app-owned replacement for the vendored, unpublished-snapshot
 * `org.opentripplanner.routing.bike_rental.BikeRentalStation` POJO (#1779) — bikeshare station data
 * read directly by shared, cross-flavor map UI: [org.onebusaway.android.map.compose.ObaMapCallbacks]
 * (`onBikeClick`/`onBikeInfoWindowClick`), [org.onebusaway.android.map.compose.BikeInfoWindow], the
 * [org.onebusaway.android.map.render.BikeMarker] render-state snapshot, and
 * [BikeStationsRepository].
 *
 * Minted exactly once, in `api/adapters/BikeStationAdapters.kt`, from the OTP bike-rental wire DTO
 * (`api/contract/BikeModels.kt`) — consumers never touch the wire shape. [latitude]/[longitude] replace
 * the vendored POJO's `y`/`x` naming (OTP's GeoJSON-style axis order), the one field rename from the
 * wire DTO; every other field mirrors [org.onebusaway.android.api.contract.BikeStationDto] exactly.
 *
 * All fields default so tests can build a fixture tersely (e.g. `BikeStation(id = "a")`); production
 * code always goes through the adapter, which reads every field off the decoded wire response.
 */
data class BikeStation(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bikesAvailable: Int = 0,
    val spacesAvailable: Int = 0,
    val allowDropoff: Boolean = false,
    val isFloatingBike: Boolean = false,
    val realTimeData: Boolean = false,
)
