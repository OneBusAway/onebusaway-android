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
 * Assigns the focused-stop routes HCT hues at a common chroma and tone. Input order is identity order;
 * duplicate route ids retain their first position. Colors in [retained] survive a presentation
 * handoff by route id; only newly introduced routes receive open slots from the new evenly-spaced
 * palette. This keeps a continuing route visually stable when the next stop introduces more routes.
 */
@SuppressLint("RestrictedApi") // Material Components' vendored color-science utility.
internal fun adjacencyRouteColors(
    routeIds: Iterable<String>,
    retained: Map<String, Int> = emptyMap(),
): Map<String, Int> {
    val ids = routeIds.distinct()
    if (ids.isEmpty()) return emptyMap()
    val hueStep = HUE_CIRCLE_DEGREES / ids.size
    val candidates = ids.indices.map { index ->
        Hct.from(index * hueStep, ADJACENCY_ROUTE_CHROMA, ADJACENCY_ROUTE_TONE).toInt()
    }
    val assigned = ids.mapNotNull { id -> retained[id]?.let { id to it } }.toMap(LinkedHashMap())
    val unused = candidates.filterNotTo(ArrayDeque()) { it in assigned.values }
    ids.filterNot(assigned::containsKey).forEach { id ->
        assigned[id] = unused.removeFirst()
    }
    return ids.associateWithTo(LinkedHashMap(), assigned::getValue)
}

private const val HUE_CIRCLE_DEGREES = 360.0
private const val ADJACENCY_ROUTE_CHROMA = 75.0
private const val ADJACENCY_ROUTE_TONE = 55.0
