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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

/**
 * The zone layer's own change boundary: [ZonePolygonReconciler] keeps equal zones' native polygons,
 * removes gone ones and creates new ones, and [onDemandZoneRenderFlow] ignores unrelated snapshot
 * changes. A fake native polygon records whether it is still on the map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZonePolygonReconcilerTest {

    private class FakePolygon(val zone: ZonePolygon) {
        var removed = false
    }

    private class Harness {
        val created = mutableListOf<FakePolygon>()
        val reconciler = ZonePolygonReconciler(
            createPolygon = { zone -> if (zone.rings.isEmpty()) null else FakePolygon(zone).also(created::add) },
            removePolygons = { polygons -> polygons.forEach { it.removed = true } }
        )

        fun live(): List<FakePolygon> = created.filterNot { it.removed }
    }

    private fun zone(serviceId: String, offset: Double) = ZonePolygon(
        serviceId = serviceId,
        serviceName = "Service $serviceId",
        rings = listOf(listOf(GeoPoint(offset, offset), GeoPoint(offset + 1, offset), GeoPoint(offset, offset + 1))),
        color = null
    )

    @Test
    fun `new zones are created, gone zones removed and unchanged zones kept`() {
        val h = Harness()
        val kept = zone("a", 0.0)
        val gone = zone("b", 1.0)
        assertTrue(h.reconciler.reconcile(listOf(kept, gone)))
        val keptPolygon = h.live().first { it.zone == kept }

        val added = zone("c", 2.0)
        assertTrue(h.reconciler.reconcile(listOf(kept, added)))

        assertEquals(listOf(kept, added), h.live().map { it.zone })
        assertSame(keptPolygon, h.live().first { it.zone == kept })
        assertTrue(h.created.single { it.zone == gone }.removed)
    }

    @Test
    fun `members of one multipolygon service are reconciled separately`() {
        val h = Harness()
        val west = zone("a", 0.0)
        val east = zone("a", 5.0)
        h.reconciler.reconcile(listOf(west, east))

        assertFalse(h.reconciler.reconcile(listOf(east)))

        assertEquals(listOf(east), h.live().map { it.zone })
        assertEquals(2, h.created.size)
    }

    @Test
    fun `an equal list creates nothing and reports no addition`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(zone("a", 0.0)))

        assertFalse(h.reconciler.reconcile(listOf(zone("a", 0.0))))

        assertEquals(1, h.created.size)
    }

    @Test
    fun `a zone with nothing to draw reports no addition`() {
        val h = Harness()

        assertFalse(h.reconciler.reconcile(listOf(ZonePolygon("a", "A", emptyList(), null))))
        assertTrue(h.created.isEmpty())
    }

    @Test
    fun `zoneFor maps a live polygon back to its zone until it is removed`() {
        val h = Harness()
        val a = zone("a", 0.0)
        h.reconciler.reconcile(listOf(a))
        val polygon = h.live().single()

        assertEquals(a, h.reconciler.zoneFor(polygon))
        h.reconciler.reconcile(emptyList())
        assertNull(h.reconciler.zoneFor(polygon))
    }

    @Test
    fun `clear removes every polygon and a later reconcile redraws`() {
        val h = Harness()
        val a = zone("a", 0.0)
        h.reconciler.reconcile(listOf(a))

        h.reconciler.clear()
        assertTrue(h.created.all { it.removed })

        assertTrue(h.reconciler.reconcile(listOf(a)))
        assertEquals(listOf(a), h.live().map { it.zone })
    }

    @Test
    fun `the zone flow skips snapshot changes that leave the zones alone`() = runTest {
        val zones = listOf(zone("a", 0.0))
        val snapshot = MutableStateFlow(MapRenderSnapshot(onDemandZones = zones))
        val emitted = mutableListOf<List<ZonePolygon>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { onDemandZoneRenderFlow(snapshot).collect(emitted::add) }

        snapshot.value = snapshot.value.copy(rentalsVisible = true)
        snapshot.value = snapshot.value.copy(onDemandZones = emptyList())

        assertEquals(listOf(zones, emptyList()), emitted)
    }
}
