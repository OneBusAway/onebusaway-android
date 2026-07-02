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
import android.graphics.Bitmap
import android.util.Log
import androidx.test.InstrumentationRegistry.getTargetContext
import androidx.test.runner.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.data.asRouteTrips
import org.onebusaway.android.extrapolation.extrapolatedVehicles
import org.onebusaway.android.map.googlemapsv2.BitmapDescriptorCache
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.mock.Resources
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.RouteTrips
import java.util.concurrent.TimeUnit

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

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // A real, busy trips-for-route snapshot (38 vehicles across many HART routes, with full route refs so
    // icon type/color resolve) — the "busy route" the issue calls out. Decoded through the io/client DTO
    // path the production fetch now uses, then adapted to the [RouteTrips] model.
    private fun response(): RouteTrips {
        val envelope: ObaEnvelope<ListWithReferences<TripDetailsEntry>> =
            Resources.read(context, Resources.getTestUri("trips_for_route_hart_5"))
                .use { json.decodeFromString(it.readText()) }
        return envelope.asRouteTrips()
    }

    /** The live vehicles for every route the snapshot serves, mapped to render markers as RouteMapController does. */
    private fun vehicles(response: RouteTrips): List<VehicleMarker> {
        val routeIds = response.trips
            .mapNotNull { it.status?.activeTripId }
            .mapNotNull { response.trip(it)?.routeId }
            .toSet()
        return extrapolatedVehicles(response, routeIds, nowMs = 1_000_000L) { null }
            .map { v ->
                VehicleMarker(
                    activeTripId = v.status.activeTripId.orEmpty(),
                    point = v.point,
                    isRealtime = v.status.isLocationRealtime,
                    status = v.status,
                    fixTimeMs = v.fixTimeMs,
                    bearing = v.bearing,
                )
            }
    }

    /** Tallies for one replay: total icon requests, descriptors minted, bitmaps decoded, and distinct keys. */
    private class Counts {
        var requests = 0
        var allocations = 0
        var bitmapDecodes = 0
        val keys = HashSet<String>()
    }

    /**
     * Replay [frames] of the renderer's icon hot path: each frame every vehicle's bearing rotates, and —
     * exactly like `GoogleMapRenderer` — an icon is (re)requested only when its heading octant flips. Each
     * request goes through a fresh [BitmapDescriptorCache] keyed by the real [VehicleBitmaps.iconKey]; the
     * descriptor `wrap` and the bitmap supplier each bump a counter so the test can see how many actually ran.
     */
    private fun replay(
        vehicles: List<VehicleMarker>,
        response: RouteTrips,
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
                    val key = VehicleBitmaps.iconKey(moved, response)
                    counts.keys.add(key)
                    cache.get(key) {
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
        // The sharing property, asserted exactly: the cache mints one descriptor per *distinct* icon key,
        // not one per request and not one per vehicle. A regression that keyed per-activeTripId (or dropped
        // a shared dimension) would break this equality — the loose `<= 8 * vehicles.size` bound wouldn't.
        assertEquals(
            "the cache must mint exactly one descriptor per distinct icon key",
            longRun.keys.size,
            longRun.allocations,
        )
        assertTrue(
            "descriptors must actually be shared — far fewer allocations than requests",
            longRun.allocations < longRun.requests,
        )
    }

    /**
     * The cache's correctness contract: equal [VehicleBitmaps.iconKey] ⟺ equal bitmap (they share
     * `createBitmapCacheKey`). A key *collision* — two vehicles that should show different icons resolving
     * to one key — would only *lower* the allocation count, so the bound tests above would still pass while
     * the wrong icon was served. Sampling every vehicle across all 8 octants and grouping by `iconKey`, each
     * group must therefore resolve to a single bitmap; a dropped key dimension (e.g. forgetting color) would
     * merge differing bitmaps into one group and fail here.
     */
    @Test
    fun equalIconKeysResolveToEqualBitmaps() {
        val response = response()
        val vehicles = vehicles(response)
        assertTrue("fixture must yield at least one vehicle", vehicles.isNotEmpty())

        // bearing = octant * 45° lands at each octant's center, so the sample spans all 8 directions.
        val samples = vehicles.flatMap { vehicle ->
            (0 until 8).map { octant ->
                val moved = vehicle.copy(bearing = octant * 45f)
                VehicleBitmaps.iconKey(moved, response) to
                    VehicleBitmaps.vehicleBitmap(context, moved, response)
            }
        }
        val byKey = samples.groupBy({ it.first }, { it.second })

        // Non-vacuous: the sample must actually exercise sharing (several vehicles/octants per key).
        assertTrue("expected multiple distinct icon keys", byKey.size > 1)
        assertTrue("expected at least one key shared by multiple samples", samples.size > byKey.size)

        for ((key, bitmaps) in byKey) {
            val reference = bitmaps.first()
            for (bitmap in bitmaps) {
                assertTrue("all bitmaps for iconKey '$key' must be identical", bitmap.sameAs(reference))
            }
        }
    }

    /**
     * The replay holds `nowMs` fixed and only rotates bearing, so schedule-deviation color never varies —
     * leaving the key's color dimension unexercised. Hold a vehicle's octant fixed and change only its
     * deviation (early vs. late, distinct colors): the key must change and the cache must mint a second
     * descriptor, proving color participates in the key.
     */
    @Test
    fun changedScheduleDeviationMintsANewDescriptor() {
        val response = response()
        val vehicle = vehicles(response).firstOrNull()
        assertTrue("fixture must yield at least one vehicle", vehicle != null)

        val early = withRealtimeDeviation(vehicle!!, TimeUnit.MINUTES.toSeconds(-10))
        val late = withRealtimeDeviation(vehicle, TimeUnit.MINUTES.toSeconds(10))

        val earlyKey = VehicleBitmaps.iconKey(early, response)
        val lateKey = VehicleBitmaps.iconKey(late, response)
        assertNotEquals("deviation color must be part of the icon key", earlyKey, lateKey)

        val counts = Counts()
        val cache = BitmapDescriptorCache(CACHE_SIZE) { counts.allocations++ }
        cache.get(earlyKey) { VehicleBitmaps.vehicleBitmap(context, early, response) }
        cache.get(lateKey) { VehicleBitmaps.vehicleBitmap(context, late, response) }
        assertEquals("a changed deviation color must mint a second descriptor", 2, counts.allocations)
    }

    /**
     * The cache's eviction + [BitmapDescriptorCache.clear] contract, which the production [CACHE_SIZE] is
     * deliberately large enough to never hit. Drive past `maxSize` distinct keys and confirm an evicted key
     * re-invokes its supplier, and that a `get` after `clear()` is a miss.
     */
    @Test
    fun evictedKeyAndPostClearGetReinvokeTheSupplier() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var supplied = 0
        val cache = BitmapDescriptorCache<Int>(2) { 0 }
        fun get(key: String) = cache.get(key) { supplied++; bitmap }

        get("a")
        get("b")            // cache now holds [a, b]
        get("c")            // exceeds maxSize: evicts a (LRU)
        assertEquals("three distinct keys are three misses", 3, supplied)

        get("b")            // still cached -> hit
        assertEquals("a cached key must not re-supply", 3, supplied)

        get("a")            // evicted -> miss, supplier re-invoked
        assertEquals("an evicted key must re-invoke the supplier", 4, supplied)

        cache.clear()
        get("b")            // after clear -> miss
        assertEquals("get after clear must re-invoke the supplier", 5, supplied)
    }

    /**
     * Force the realtime color path and stamp [deviationSeconds] onto a copy of [vehicle], pinning the
     * bearing so the heading octant is fixed — only the deviation color varies between the variants.
     */
    private fun withRealtimeDeviation(vehicle: VehicleMarker, deviationSeconds: Long): VehicleMarker =
        vehicle.copy(
            isRealtime = true,
            bearing = 0f,
            status = object : ObaTripStatus by vehicle.status {
                override val scheduleDeviation: Long = deviationSeconds
            },
        )

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
