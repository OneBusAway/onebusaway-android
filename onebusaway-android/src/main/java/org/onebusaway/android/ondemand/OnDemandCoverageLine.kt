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
package org.onebusaway.android.ondemand

/** What the dropped pin's extra line says (spec §3.7): the named service, inside or outside, and how many more contain the pin. */
data class OnDemandCoverageLine(val serviceName: String, val isInside: Boolean, val match: OnDemandMatch, val othersInside: Int)

/**
 * Inside: the first inside match by §2.6, with the count of the others. Outside: the nearest match
 * with a finite distance ≤ 5,000 m. Null when nothing is within reach — a pure stop group never counts.
 */
fun coverageLineFor(matches: List<OnDemandMatch>): OnDemandCoverageLine? {
    val inside = matches.filter { it.isInside }.sortedSoonestUsable()
    inside.firstOrNull()?.let { return OnDemandCoverageLine(it.service.name, isInside = true, match = it, othersInside = inside.size - 1) }
    val nearest = matches.filter { it.isNearby }.minByOrNull { it.distanceToAreaMeters ?: Double.MAX_VALUE } ?: return null
    return OnDemandCoverageLine(nearest.service.name, isInside = false, match = nearest, othersInside = 0)
}
