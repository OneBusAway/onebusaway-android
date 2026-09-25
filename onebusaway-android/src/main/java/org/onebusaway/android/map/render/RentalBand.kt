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
