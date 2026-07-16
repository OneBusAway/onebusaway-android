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
 * Assigns focused-stop route identities HCT hues at a common chroma and tone. Duplicate identities
 * retain their first position. Colors in [retained] survive a presentation handoff. Newly introduced
 * identities take the hue farthest from every color already in
 * use, successively filling the largest gaps around the circle. This keeps every continuing route
 * visually stable while spreading the replacement stop's other routes as evenly as possible.
 */
@SuppressLint("RestrictedApi") // Material Components' vendored color-science utility.
internal fun <K : Any> adjacencyRouteColors(
    identities: Iterable<K>,
    retained: Map<K, Int> = emptyMap(),
): Map<K, Int> {
    val keys = identities.distinct()
    if (keys.isEmpty()) return emptyMap()
    val assigned = retained.filterKeys(keys::contains).toMutableMap()

    if (assigned.isEmpty()) {
        val hueStep = HUE_CIRCLE_DEGREES / keys.size
        keys.forEachIndexed { index, key ->
            assigned[key] = routeColor(index * hueStep)
        }
    } else {
        val usedHues = assigned.values.mapTo(mutableListOf()) { Hct.fromInt(it).hue }
        keys.filterNot(assigned::containsKey).forEach { key ->
            val color = routeColor(widestHueGapMidpoint(usedHues))
            assigned[key] = color
            usedHues += Hct.fromInt(color).hue
        }
    }
    return keys.associateWith(assigned::getValue)
}

private fun routeColor(hue: Double): Int =
    Hct.from(hue, ADJACENCY_ROUTE_CHROMA, ADJACENCY_ROUTE_TONE).toInt()

private fun widestHueGapMidpoint(hues: List<Double>): Double {
    val sorted = hues.sorted()
    val wrapped = sorted + (sorted.first() + HUE_CIRCLE_DEGREES)
    val widestGap = wrapped.zipWithNext().maxBy { (start, end) -> end - start }
    return (widestGap.first + widestGap.second) / 2 % HUE_CIRCLE_DEGREES
}

private const val HUE_CIRCLE_DEGREES = 360.0
private const val ADJACENCY_ROUTE_CHROMA = 75.0
private const val ADJACENCY_ROUTE_TONE = 55.0
