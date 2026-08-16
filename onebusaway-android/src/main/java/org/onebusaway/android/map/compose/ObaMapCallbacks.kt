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

import org.onebusaway.android.map.render.RouteBadge
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.map.rental.RentalPlace
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.util.GeoPoint

/**
 * Map interaction a flavor's [ObaComposeMapAdapter] reports back to its host. Flavor-neutral (no
 * map-SDK types — [onMapClick] takes a [GeoPoint], not a Google/maplibre `LatLng`), so it lives in
 * `src/main` and both flavor adapters/hosts share it. A stop tap focuses the stop, a map tap clears
 * focus, a rental tap reports rental focus, and the two info-window taps deep link via the host.
 *
 * Adapters that own their own marker-click dispatch (the maplibre classic API, where the host wires
 * listeners on the raw map) may ignore this and receive null instead.
 */
interface ObaMapCallbacks {
    fun onStopClick(marker: StopMarker)

    fun onMapClick(point: GeoPoint?)

    /** A long-press on the map at [point] — the host offers "directions from/to here". */
    fun onMapLongClick(point: GeoPoint) {}

    fun onRentalClick(place: RentalPlace)

    /**
     * A vehicle marker tap — the host selects it (e.g. to show its most-recent-data marker), and opens
     * its trip details when the same vehicle is tapped again (#2194).
     *
     * Every tap arrives here; whether one is a re-tap is the host's to know, since the host owns the
     * selection. The flavors report taps, they don't interpret them.
     */
    fun onVehicleClick(status: ObaTripStatus) {}

    /** The route-continuation badge tap (#1691) — the host navigates the map to [routeId]'s [directionId]. */
    fun onRouteContinuationClick(routeId: String, routeShortName: String, directionId: Int?) {}

    /**
     * A route badge tap — an adjacency label opening its route (#1827), or a directions label focusing
     * the ride it names (#2101).
     *
     * The whole [badge] is passed, not its destination flattened into fields, because what a tap does is
     * the label's own to say ([RouteBadge.tap]) and the host is where that is read. Flattening it would
     * put the `when` over that sealed type in each flavor's marker dispatch instead — mirrored code that
     * a new kind of label has to be added to twice, and that would silently ignore it in the flavor that
     * was missed. Same shape as [onStopClick], which likewise hands over the render model.
     */
    fun onRouteBadgeClick(badge: RouteBadge) {}

    /** The rental info-window tap — the host opens the operator (app, deep link, or site). */
    fun onRentalInfoWindowClick(place: RentalPlace)
}
