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
package org.onebusaway.android.map.render

/** Which icon (if any) a rental marker shows at a given zoom — the legacy <=12 / 12-15 / >15 bands. */
enum class RentalBand { HIDDEN, SMALL, BIG }

/**
 * The zoom band a rental marker falls in. Mirrors the legacy BikeStationOverlay rules: hidden at or
 * below zoom 12, the small dot up to zoom 15, the big pin above 15.
 */
fun rentalZoomBand(zoom: Float): RentalBand = when {
    zoom <= 12f -> RentalBand.HIDDEN
    zoom <= 15f -> RentalBand.SMALL
    else -> RentalBand.BIG
}

/**
 * The zoom at which a vehicle's range label appears beneath its pin (#2168).
 *
 * Above the [RentalBand.BIG] threshold rather than at it: the pin itself is what the rider is
 * scanning for, and hanging a text chip off every one of them the moment the big pins appear turns a
 * street of parked scooters into a wall of numbers. One further zoom step in, the markers are far
 * enough apart for the labels to be readable rather than merely present.
 */
private const val RENTAL_LABEL_MIN_ZOOM = 16.5f

/** Whether a vehicle's range label is shown at [zoom]. */
fun showsRentalRangeLabel(zoom: Float): Boolean = zoom > RENTAL_LABEL_MIN_ZOOM
