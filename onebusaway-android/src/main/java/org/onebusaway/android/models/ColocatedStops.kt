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
package org.onebusaway.android.models

/**
 * Two feed IDs can describe the same boarding point (#2286). Use exact published coordinates,
 * name and direction, never a distance threshold: adjacent bays and opposite directions must remain
 * distinct. Unnamed/undirected stops and stations lack enough information to establish equivalence.
 */
internal data class BoardingPoint(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val direction: String
)

internal fun ObaStop.boardingPoint(): BoardingPoint? {
    val name = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val direction = direction?.takeIf { it in setOf("N", "NE", "E", "SE", "S", "SW", "W", "NW") }
        ?: return null
    if (locationType != ObaStop.LOCATION_STOP) return null
    return BoardingPoint(latitude, longitude, name, direction)
}
