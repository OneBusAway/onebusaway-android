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

import android.content.Context
import android.util.Log
import androidx.test.InstrumentationRegistry.getTargetContext
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.extrapolation.extrapolatedVehicles
import org.onebusaway.android.io.request.ObaTripsForRouteResponse
import org.onebusaway.android.map.googlemapsv2.BitmapDescriptorCache
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.mock.Resources

/**
 * The before/after allocation guard for #1580. `GoogleMapRenderer` re-stamps a gliding vehicle's icon on
 * every heading-octant change (the ~20Hz reconcile path), so before [BitmapDescriptorCache] it minted a
 * fresh `BitmapDescriptor` + native texture per flip — hundreds/sec on a busy route.
 *
 * This drives the *real* icon path (the cached [VehicleBitmaps.vehicleBitmap] feeding the descriptor
 * cache) over a simulated route session and asserts the property the fix introduces: the number of wrapper
 * allocations is bounded by the distinct icons and is **independent of how many frames run** — whereas the
 * naive per-flip allocation count grows with the session. The descriptor factory is a counting fake (the
 * cache's only allocator), so this needs no real `GoogleMap`.
 */
@RunWith(AndroidJUnit4::class)
class VehicleIconAllocationTest {

    private val context: Context get() = getTargetContext()

    // A real, busy trips-for-route snapshot (38 vehicles across many HART routes, with full route refs so
    // icon type/color resolve) — the "busy route" the issue calls out.
    private fun response(): ObaTripsForRouteResponse =
        Resources.readAs(
            context,
            Resources.getTestUri("trips_for_route_hart_5"),
            ObaTripsForRouteResponse::class.java,
        )

    /** The live vehicles for every route the snapshot serves, mapped to render markers as RouteMapController does. */
    private fun vehicles(response: ObaTripsForRouteResponse): List<VehicleMarker> {
        val routeIds = response.trips
            .mapNotNull { it.status?.activeTripId }
            .mapNotNull { response.getTrip(it)?.routeId }
            .toSet()
        return extrapolatedVehicles(response, routeIds, nowMs = 1_000_000L) { null }
            .map { v ->
                VehicleMarker(
                    activeTripId = v.status.activeTripId,
                    point = v.point,
                    isRealtime = VehicleBitmaps.isLocationRealtime(v.status),
                    status = v.status,
                    fixTimeMs = v.fixTimeMs,
                    bearing = v.bearing,
                )
            }
    }

    /** Tallies for one replay: total icon requests, descriptors minted, and bitmaps decoded. */
    private class Counts {
        var requests = 0
        var allocations = 0
        var bitmapDecodes = 0
    }

    /**
     * Replay [frames] of the renderer's icon hot path: each frame every vehicle's bearing rotates, and —
     * exactly like `GoogleMapRenderer` — an icon is (re)requested only when its heading octant flips. Each
     * request goes through a fresh [BitmapDescriptorCache] keyed by the real [VehicleBitmaps.iconKey]; the
     * descriptor `wrap` and the bitmap supplier each bump a counter so the test can see how many actually ran.
     */
    private fun replay(
        vehicles: List<VehicleMarker>,
        response: ObaTripsForRouteResponse,
        frames: Int,
    ): Counts {
        val counts = Counts()
        val cache = BitmapDescriptorCache(CACHE_SIZE) { counts.allocations++ }
        val lastOctant = HashMap<String, Int>()
        for (frame in 0 until frames) {
            for (vehicle in vehicles) {
                val moved = vehicle.copy(bearing = (frame * BEARING_STEP_DEG) % 360f)
                val octant = VehicleBitmaps.directionIndex(moved)
                if (lastOctant.put(moved.activeTripId, octant) != octant) {
                    counts.requests++
                    cache.get(VehicleBitmaps.iconKey(moved, response)) {
                        counts.bitmapDecodes++
                        VehicleBitmaps.vehicleBitmap(context, moved, response)
                    }
                }
            }
        }
        return counts
    }

    @Test
    fun descriptorAllocationsAreBoundedAndIndependentOfFrameCount() {
        val response = response()
        val vehicles = vehicles(response)
        assertTrue("fixture must yield at least one vehicle", vehicles.isNotEmpty())

        val shortRun = replay(vehicles, response, SHORT_FRAMES)
        val longRun = replay(vehicles, response, LONG_FRAMES)

        // Headline before/after for #1580: requests = uncached fromBitmap allocs; allocations = cached.
        Log.i(
            "VehicleIconAlloc",
            "${vehicles.size} vehicles: ${longRun.requests} requests -> " +
                "${longRun.allocations} allocs, ${longRun.bitmapDecodes} bitmap decodes",
        )

        // Naive work — a fromBitmap per octant flip — grows with the session length...
        assertTrue("a longer session must issue more icon requests", longRun.requests > shortRun.requests)
        // ...but the cache mints the same descriptors regardless: a 4x-longer session allocates no more.
        assertEquals(
            "descriptor allocations must not grow with frame count",
            shortRun.allocations,
            longRun.allocations,
        )
        // The expensive bitmap decode/tint runs only on a miss, i.e. exactly once per minted descriptor —
        // never re-run on the hot path even as the bounded VehicleBitmaps LRU evicts under a busy route.
        assertEquals(
            "bitmap decode must run only on descriptor-cache misses",
            longRun.allocations,
            longRun.bitmapDecodes,
        )
        // Bounded by the distinct directional icons (at most 8 octants per vehicle) — far below the
        // requests it served, which is the whole point of the cache.
        assertTrue(
            "allocations (${longRun.allocations}) must be bounded by the distinct icons",
            longRun.allocations <= 8 * vehicles.size,
        )
    }

    companion object {
        // Sweep the bearing through all 8 octants within a few frames, then revisit them (no new icons) —
        // which is the property under test (revisited octants reuse the cached descriptor).
        private const val BEARING_STEP_DEG = 25f
        private const val SHORT_FRAMES = 60
        private const val LONG_FRAMES = 240
        // Far larger than the snapshot's distinct icons (8 octants x a handful of type/deviation-color
        // combos), so eviction never confounds the "allocations independent of frame count" invariant. The
        // production cap is deliberately smaller — it only needs to cover the live working set, not history.
        private const val CACHE_SIZE = 1024
    }
}
