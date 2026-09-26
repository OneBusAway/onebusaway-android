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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.TIER_ADVANCE
import org.onebusaway.android.ondemand.TIER_OPEN_NOW
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.util.GeoPoint

class OnDemandDockDecisionsTest {

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.MapCenter)

    private fun match(id: String, reason: OnDemandMatchReason, distance: Double?, tier: Int = TIER_OPEN_NOW): OnDemandMatch {
        val areas = if (distance == null) emptyList() else listOf(area(distance = distance, nearest = GeoPoint(45.0, -85.1)))
        val match = matchFor(service(id = id, areas = areas, matchReason = reason), now)
        return match.copy(availability = match.availability.copy(usabilityTier = tier))
    }

    private val inside = match("in", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0, TIER_ADVANCE)
    private val insideOpen = match("in2", OnDemandMatchReason.AREA_CONTAINS_POINT, 0.0)
    private val near = match("near", OnDemandMatchReason.AREA_NEARBY, 800.0)
    private val far = match("far", OnDemandMatchReason.AREA_NEARBY, 6_000.0)
    private val stopGroup = match("stops", OnDemandMatchReason.STOP_WITHIN_RADIUS, null)

    private fun result(vararg matches: OnDemandMatch) = OnDemandProbeResult("https://maglev.example.org/", probe, matches.toList())

    @Test
    fun `region level shows the card only when something is inside, sorted`() {
        val state = dockStateFor(result(inside, insideOpen, near), OnDemandZoomLevel.REGION, visible = true)
        assertEquals(OnDemandDockState.Card(listOf(insideOpen, inside), probe), state)
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(near), OnDemandZoomLevel.REGION, visible = true))
    }

    @Test
    fun `street level stacks inside matches only when any is inside, else the nearby ones`() {
        assertEquals(OnDemandDockState.Bar(listOf(insideOpen, inside), probe), dockStateFor(result(near, inside, insideOpen), OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Bar(listOf(near), probe), dockStateFor(result(near, far), OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(far), OnDemandZoomLevel.STREET, visible = true))
    }

    @Test
    fun `a match with no area never enters the dock`() {
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(stopGroup), OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(stopGroup), OnDemandZoomLevel.REGION, visible = true))
        val withInside = dockStateFor(result(stopGroup, insideOpen), OnDemandZoomLevel.STREET, visible = true) as OnDemandDockState.Bar
        assertTrue(withInside.matches.none { it.service.id == "stops" })
    }

    @Test
    fun `hidden level, no result and suppression all hide`() {
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(insideOpen), OnDemandZoomLevel.HIDDEN, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(null, OnDemandZoomLevel.STREET, visible = true))
        assertEquals(OnDemandDockState.Hidden, dockStateFor(result(insideOpen), OnDemandZoomLevel.STREET, visible = false))
    }
}
