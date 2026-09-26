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
package org.onebusaway.android.ui.home.ondemand

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.models.OnDemandMatchReason
import org.onebusaway.android.ondemand.LocalityResolver
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.TIER_ADVANCE
import org.onebusaway.android.ondemand.area
import org.onebusaway.android.ondemand.instant
import org.onebusaway.android.ondemand.matchFor
import org.onebusaway.android.ondemand.service
import org.onebusaway.android.testing.MainDispatcherRule
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.TimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class OnDemandSheetsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private class GatedResolver : LocalityResolver {
        val answer = CompletableDeferred<String?>()
        override suspend fun locality(point: GeoPoint): String? = answer.await()
    }

    private val now = instant("2026-03-10T14:00:00-04:00")
    private val probe = ProbePoint(GeoPoint(45.05, -85.1), ProbeSource.Rider)
    private val open = matchFor(service(id = "open", areas = listOf(area(distance = 0.0)), matchReason = OnDemandMatchReason.AREA_CONTAINS_POINT), now)
    private val advance = open.copy(service = open.service.copy(id = "advance"), availability = open.availability.copy(usabilityTier = TIER_ADVANCE))

    @Test
    fun `the picker opens sorted at once and gains its locality when the geocoder answers`() = runTest {
        val resolver = GatedResolver()
        val vm = OnDemandSheetsViewModel(resolver, TimeProvider { now.toEpochMilli() })
        vm.openPicker(listOf(advance, open), probe, nearby = true)
        val request = requireNotNull(vm.picker.value)
        assertEquals(listOf("open", "advance"), request.matches.map { it.service.id })
        assertNull(request.locality)
        assertEquals(true, request.nearby)

        resolver.answer.complete("Boyne City")
        assertEquals("Boyne City", vm.picker.value?.locality)
        assertEquals(now, vm.now())
    }

    @Test
    fun `a late locality for a closed picker is dropped`() = runTest {
        val resolver = GatedResolver()
        val vm = OnDemandSheetsViewModel(resolver, TimeProvider { now.toEpochMilli() })
        vm.openPicker(listOf(open), probe, nearby = false)
        vm.closePicker()
        resolver.answer.complete("Boyne City")
        assertNull(vm.picker.value)
    }

    @Test
    fun `the planner probes both ends and qualifies the services that cover both`() = runTest {
        val vm = OnDemandSheetsViewModel(GatedResolver(), TimeProvider { now.toEpochMilli() })
        val origin = GeoPoint(45.05, -85.1)
        val destination = GeoPoint(45.06, -85.1)
        vm.openPlanner(origin, destination) { point -> if (point == origin || point == destination) listOf(open) else emptyList() }
        val ready = vm.planner.value as PlannerFallbackState.Ready
        assertEquals(listOf("open"), ready.qualification.qualifying.map { it.service.id })
        assertEquals(ProbeSource.Point(null), ready.origin.source)
        vm.closePlanner()
        assertNull(vm.planner.value)
    }

    @Test
    fun `an unanswerable probe leaves the planner empty rather than failing`() = runTest {
        val vm = OnDemandSheetsViewModel(GatedResolver(), TimeProvider { now.toEpochMilli() })
        vm.openPlanner(GeoPoint(45.05, -85.1), GeoPoint(45.06, -85.1)) { null }
        val ready = vm.planner.value as PlannerFallbackState.Ready
        assertEquals(0, ready.qualification.qualifying.size)
        assertEquals(0, ready.qualification.hiddenCount)
    }
}
