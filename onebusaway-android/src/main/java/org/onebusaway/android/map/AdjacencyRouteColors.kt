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
package org.onebusaway.android.map

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct

/**
 * Assigns the focused-stop routes evenly spaced HCT hues at a common chroma and tone. Input order is
 * identity order; duplicate route ids retain their first position. A session computes this once so
 * colors remain stable while its independently loaded shapes and route metadata arrive.
 */
@SuppressLint("RestrictedApi") // Material Components' vendored color-science utility.
internal fun adjacencyRouteColors(routeIds: Iterable<String>): Map<String, Int> {
    val ids = routeIds.distinct()
    if (ids.isEmpty()) return emptyMap()
    val hueStep = HUE_CIRCLE_DEGREES / ids.size
    return ids.mapIndexed { index, routeId ->
        routeId to Hct.from(index * hueStep, ADJACENCY_ROUTE_CHROMA, ADJACENCY_ROUTE_TONE).toInt()
    }.toMap(LinkedHashMap())
}

private const val HUE_CIRCLE_DEGREES = 360.0
private const val ADJACENCY_ROUTE_CHROMA = 75.0
private const val ADJACENCY_ROUTE_TONE = 55.0
