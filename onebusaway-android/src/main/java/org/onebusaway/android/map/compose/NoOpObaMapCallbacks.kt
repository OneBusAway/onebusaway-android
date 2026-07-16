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

import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.map.bike.BikeStation
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.map.render.StopMarker

/**
 * A do-nothing [ObaMapCallbacks] for map screens that don't react to taps (the trip-plan location
 * picker, the trip-results itinerary map). The Google adapter requires non-null callbacks, so these
 * screens pass this instead of building an empty object each.
 */
object NoOpObaMapCallbacks : ObaMapCallbacks {
    override fun onStopClick(marker: StopMarker) {}

    override fun onMapClick(point: GeoPoint?) {}

    override fun onBikeClick(station: BikeStation) {}

    override fun onVehicleInfoWindowClick(status: ObaTripStatus) {}

    override fun onBikeInfoWindowClick(station: BikeStation) {}
}
