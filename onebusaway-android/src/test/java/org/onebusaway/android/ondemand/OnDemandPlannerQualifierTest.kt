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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.EligibilityRequirement
import org.onebusaway.android.models.OnDemandEligibility
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.util.GeoPoint

class OnDemandPlannerQualifierTest {

    private val now = instant("2026-03-10T14:00:00-04:00")

    /** [insideArea] has distance 0 (contains the probe), every other area is 3 km away. */
    private fun at(service: OnDemandService, insideArea: String?): OnDemandMatch {
        val areas = service.areas.map { it.copy(distanceToAreaMeters = if (it.id == insideArea) 0.0 else 3_000.0, nearestPointOnBoundary = GeoPoint(45.0, -85.0)) }
        val reason = if (insideArea == null) OnDemandMatchReason.AREA_NEARBY else OnDemandMatchReason.AREA_CONTAINS_POINT
        return matchFor(service.copy(areas = areas, matchReason = reason), now)
    }

    private val singleZone = service(id = "zone", rules = listOf(rule(fromIds = listOf("CC_area"), toIds = listOf("CC_area"))))
    private val aToB = service(id = "ab", areas = listOf(area(id = "A", name = "A"), area(id = "B", name = "B")), rules = listOf(rule(fromIds = listOf("A"), toIds = listOf("B"))))
    private val bToA = aToB.copy(id = "ba", rules = listOf(rule(fromIds = listOf("B"), toIds = listOf("A"))))

    @Test
    fun `a single zone containing both ends qualifies`() {
        val result = qualifyingServices(listOf(at(singleZone, "CC_area")), listOf(at(singleZone, "CC_area")))
        assertEquals(listOf("zone"), result.qualifying.map { it.service.id })
        assertEquals(0, result.hiddenCount)
    }

    @Test
    fun `zone to zone qualifies only in the rule's direction`() {
        assertEquals(listOf("ab"), qualifyingServices(listOf(at(aToB, "A")), listOf(at(aToB, "B"))).qualifying.map { it.service.id })
        val wrongWay = qualifyingServices(listOf(at(bToA, "A")), listOf(at(bToA, "B")))
        assertTrue(wrongWay.qualifying.isEmpty())
        assertEquals(1, wrongWay.hiddenCount)
    }

    @Test
    fun `eligibility required and one end outside are hidden`() {
        val restricted = singleZone.copy(eligibility = OnDemandEligibility(EligibilityRequirement.CERTIFICATION_REQUIRED, null))
        val hidden = qualifyingServices(listOf(at(restricted, "CC_area")), listOf(at(restricted, "CC_area")))
        assertTrue(hidden.qualifying.isEmpty())
        assertEquals(1, hidden.hiddenCount)
        val oneEnd = qualifyingServices(listOf(at(singleZone, "CC_area")), listOf(at(singleZone, null)))
        assertTrue(oneEnd.qualifying.isEmpty())
        assertEquals(1, oneEnd.hiddenCount)
    }
}
