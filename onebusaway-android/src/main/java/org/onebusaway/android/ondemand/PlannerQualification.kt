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

/** The planner fallback's answer (spec §3.8): the services that serve both ends, and how many were hidden. */
data class PlannerQualification(val qualifying: List<OnDemandMatch>, val hiddenCount: Int)

/**
 * A service qualifies when it contains both ends, is not tier 4, and some rule goes from an area
 * containing the origin (`distanceToArea == 0` there) to an area containing the destination. Areas
 * are read off the server's per-area distances; no client containment is used.
 */
fun qualifyingServices(origin: List<OnDemandMatch>, destination: List<OnDemandMatch>): PlannerQualification {
    val originInside = origin.filter { it.isInside }.associateBy { it.service.id }
    val destinationInside = destination.filter { it.isInside }.associateBy { it.service.id }
    val qualifying = originInside.values.filter { atOrigin ->
        val atDestination = destinationInside[atOrigin.service.id] ?: return@filter false
        if (atOrigin.availability.usabilityTier == TIER_ELIGIBILITY) return@filter false
        val originAreas = atOrigin.service.areas.filter { it.distanceToAreaMeters == 0.0 }.map { it.id }.toSet()
        val destinationAreas = atDestination.service.areas.filter { it.distanceToAreaMeters == 0.0 }.map { it.id }.toSet()
        atOrigin.service.rules.any { rule -> rule.fromIds.any { it in originAreas } && rule.toIds.any { it in destinationAreas } }
    }.sortedSoonestUsable()
    val considered = (originInside.keys + destinationInside.keys).size
    return PlannerQualification(qualifying, hiddenCount = considered - qualifying.size)
}
