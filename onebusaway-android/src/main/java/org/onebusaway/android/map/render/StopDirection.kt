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

/**
 * The compass direction a bus-stop marker can point, shared by both map flavors' stop-icon factories.
 *
 * The entries are declared in the order the pre-rendered icon caches are indexed, so [ordinal] is the
 * array index the old parallel `String[]` + `directionToIndexMap` encoded in each flavor. [key] is the
 * wire value from `ObaStop.getDirection()`. [xSign]/[ySign] place the Google marker anchor a step
 * off-center so the pin tip lands on the circle center (maplibre centers its markers, so it ignores
 * them); the anchor is `0.5 + sign * percentOffset`. [compassAngle] is clockwise degrees from north,
 * used to rotate the starred-stop's direction arrow ([StopBitmaps.favoriteMarker]); [NONE] is 0 and
 * unused (a directionless stop passes `hasArrow=false`).
 */
enum class StopDirection(
    val key: String,
    val xSign: Int,
    val ySign: Int,
    val compassAngle: Float
) {
    NORTH("N", 0, 1, 0f),
    NORTH_WEST("NW", 1, 1, 315f),
    WEST("W", 1, 0, 270f),
    SOUTH_WEST("SW", 1, -1, 225f),
    SOUTH("S", 0, -1, 180f),
    SOUTH_EAST("SE", -1, -1, 135f),
    EAST("E", -1, 0, 90f),
    NORTH_EAST("NE", -1, 1, 45f),
    NONE("null", 0, 0, 0f);

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** Unknown directions fall back to [NONE] (index 8), matching the legacy default. */
        @JvmStatic
        fun fromKey(key: String): StopDirection = byKey[key] ?: NONE
    }
}
