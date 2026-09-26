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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.util.GeoPoint

class OnDemandCoverageTest {

    private val now = instant("2026-03-10T14:00:00-04:00")

    private fun match(id: String, reason: OnDemandMatchReason, distance: Double?, tier: Int = TIER_OPEN_NOW): OnDemandMatch {
        val areas = if (distance == null) emptyList() else listOf(area(distance = distance, nearest = GeoPoint(45.0, -85.1)))
        val match = matchFor(service(id = id, name = "Service $id", areas = areas, matchReason = reason), now)
        return match.copy(availability = match.availability.copy(usabilityTier = tier))
    }

    @Test
    fun `inside names the soonest usable service and counts the others`() {
        val line = requireNotNull(coverageLineFor(listOf(match("advance", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0, TIER_ADVANCE), match("open", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0))))
        assertTrue(line.isInside)
        assertEquals("Service open", line.serviceName)
        assertEquals(1, line.othersInside)
    }

    @Test
    fun `outside names the nearest service within five kilometres`() {
        val line = requireNotNull(coverageLineFor(listOf(match("far", OnDemandMatchReason.AREA_NEARBY, 4_000.0), match("near", OnDemandMatchReason.AREA_NEARBY, 300.0))))
        assertFalse(line.isInside)
        assertEquals("Service near", line.serviceName)
        assertEquals(0, line.othersInside)
        assertNull(coverageLineFor(listOf(match("toofar", OnDemandMatchReason.AREA_NEARBY, 6_000.0))))
    }

    @Test
    fun `a stop-group match never names the address line`() {
        assertNull(coverageLineFor(listOf(match("stops", OnDemandMatchReason.STOP_WITHIN_RADIUS, null))))
        assertNull(coverageLineFor(emptyList()))
    }
}
