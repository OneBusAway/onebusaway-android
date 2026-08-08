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
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
import org.onebusaway.android.map.render.DEFAULT_ROUTE_LINE_COLOR
import org.onebusaway.android.map.render.MarkerRendering
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.mock.Resources
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.ScheduleDeviation

/**
 * The before/after allocation guard for #1580. `GoogleMapRenderer` re-stamps every vehicle's icon on
 * every poll, so before [BitmapDescriptorCache] it minted a fresh `BitmapDescriptor` + native texture per
 * marker per poll — and did so far more often still when the marker carried a heading arrow that also
 * had to be re-stamped mid-glide on every octant flip.
 *
 * This drives the *real* icon path (the cached [VehicleBitmaps.vehicleBitmap] feeding the descriptor
 * cache) over a simulated route session and asserts the property the fix introduces: the number of wrapper
 * allocations is bounded by the distinct icons and is **independent of how many polls run** — whereas the
 * naive per-re-stamp allocation count grows with the session. The descriptor factory is a counting fake
 * (the cache's only allocator), so this needs no real `GoogleMap`.
 */
@RunWith(AndroidJUnit4::class)
class VehicleIconAllocationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // A real, busy trips-for-route snapshot (38 vehicles across many HART routes, with full route refs so
    // icon type/color resolve) — the "busy route" the issue calls out. Decoded through the api/ DTO
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
        return extrapolatedVehicles(response, routeIds, nowMs = WallTime(1_000_000L)) { null }
            .map { v ->
                VehicleMarker(
                    activeTripId = v.status.activeTripId.orEmpty(),
                    point = v.point,
                    isRealtime = v.status.isLocationRealtime,
                    status = v.status,
                    fixTimeMs = v.fixTimeMs,
                    bearing = v.bearing
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
     * Replay [polls] of the renderer's icon path: `reconcileVehicleMarkers` re-stamps **every** marker on
     * **every** poll unconditionally, so that's what this drives — one icon request per vehicle per poll,
     * with each vehicle's reported fullness advancing a step so the keys genuinely vary the way a
     * filling-up bus does. Each request goes through a fresh [BitmapDescriptorCache] keyed by the real
     * [VehicleBitmaps.iconKey]; the descriptor `wrap` and the bitmap supplier each bump a counter so the
     * test can see how many actually ran.
     *
     * This used to replay ~20 Hz *frames* and re-stamp on heading-octant flips. #2194 removed the
     * heading arrow, so the icon no longer varies with bearing and nothing re-stamps between polls at
     * all — but the property the cache exists for is unchanged, and the poll path is now where it bites.
     */
    private fun replay(
        vehicles: List<VehicleMarker>,
        response: RouteTrips,
        polls: Int
    ): Counts {
        val counts = Counts()
        val cache = BitmapDescriptorCache(CACHE_SIZE) { counts.allocations++ }
        for (poll in 0 until polls) {
            for ((index, vehicle) in vehicles.withIndex()) {
                val filling = withOccupancy(vehicle, OCCUPANCY_CYCLE[(poll + index) % OCCUPANCY_CYCLE.size])
                counts.requests++
                val key = VehicleBitmaps.iconKey(context, filling, response)
                counts.keys.add(key)
                cache.get(key) {
                    counts.bitmapDecodes++
                    VehicleBitmaps.vehicleBitmap(context, filling, response)
                }
            }
        }
        return counts
    }

    @Test
    fun descriptorAllocationsAreBoundedAndIndependentOfPollCount() {
        val response = response()
        val vehicles = vehicles(response)
        assertTrue("fixture must yield at least one vehicle", vehicles.isNotEmpty())

        val shortRun = replay(vehicles, response, SHORT_POLLS)
        val longRun = replay(vehicles, response, LONG_POLLS)

        // Headline before/after for #1580: requests = uncached fromBitmap allocs; allocations = cached.
        Log.i(
            "VehicleIconAlloc",
            "${vehicles.size} vehicles: ${longRun.requests} requests -> " +
                "${longRun.allocations} allocs, ${longRun.bitmapDecodes} bitmap decodes"
        )

        // Naive work — a fromBitmap per re-stamp — grows with the session length...
        assertTrue("a longer session must issue more icon requests", longRun.requests > shortRun.requests)
        // ...but the cache mints the same descriptors regardless: a 4x-longer session allocates no more.
        assertEquals(
            "descriptor allocations must not grow with poll count",
            shortRun.allocations,
            longRun.allocations
        )
        // The expensive bitmap decode/tint runs only on a miss, i.e. exactly once per minted descriptor —
        // never re-run on the hot path even as the bounded VehicleBitmaps LRU evicts under a busy route.
        assertEquals(
            "bitmap decode must run only on descriptor-cache misses",
            longRun.allocations,
            longRun.bitmapDecodes
        )
        // The sharing property, asserted exactly: the cache mints one descriptor per *distinct* icon key,
        // not one per request and not one per vehicle. A regression that keyed per-activeTripId (or dropped
        // a shared dimension) would break this equality — the loose `<= 8 * vehicles.size` bound wouldn't.
        assertEquals(
            "the cache must mint exactly one descriptor per distinct icon key",
            longRun.keys.size,
            longRun.allocations
        )
        assertTrue(
            "descriptors must actually be shared — far fewer allocations than requests",
            longRun.allocations < longRun.requests
        )
    }

    /**
     * The cache's correctness contract: equal [VehicleBitmaps.iconKey] ⟺ equal bitmap (they share
     * `createBitmapCacheKey`). A key *collision* — two vehicles that should show different icons resolving
     * to one key — would only *lower* the allocation count, so the bound tests above would still pass while
     * the wrong icon was served. Sampling every vehicle across every fullness state and grouping by
     * `iconKey`, each group must therefore resolve to a single bitmap; a dropped key dimension (e.g.
     * forgetting color) would merge differing bitmaps into one group and fail here.
     */
    @Test
    fun equalIconKeysResolveToEqualBitmaps() {
        val response = response()
        val vehicles = vehicles(response)
        assertTrue("fixture must yield at least one vehicle", vehicles.isNotEmpty())

        // Every fullness state, including the tabless "no data" one, across every vehicle in the snapshot.
        val samples = vehicles.flatMap { vehicle ->
            OCCUPANCY_CYCLE.map { occupancy ->
                val reporting = withOccupancy(vehicle, occupancy)
                VehicleBitmaps.iconKey(context, reporting, response) to
                    VehicleBitmaps.vehicleBitmap(context, reporting, response)
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
     * The replay only rotates bearing, leaving the key's color dimension unexercised. Hold a vehicle's
     * octant fixed and flip only its **liveness**: a live vehicle draws in its route color, one without
     * real-time draws gray (#2043), so the key must change and the cache must mint a second descriptor.
     *
     * This replaces an equivalent early-vs-late assertion. Schedule deviation no longer reaches the
     * marker at all — punctuality now lives in the info window, and the disc means route identity +
     * liveness — so the deviation variant would assert behavior the app deliberately dropped. The
     * companion [scheduleDeviationDoesNotAffectTheIcon] pins that new contract from the other side.
     */
    @Test
    fun changedLivenessMintsANewDescriptor() {
        val (response, vehicle) = fixture()
        val live = withLiveness(vehicle, isRealtime = true)
        val stale = withLiveness(vehicle, isRealtime = false)

        val liveKey = VehicleBitmaps.iconKey(context, live, response)
        val staleKey = VehicleBitmaps.iconKey(context, stale, response)
        assertNotEquals("liveness must be part of the icon key", liveKey, staleKey)

        val counts = Counts()
        val cache = BitmapDescriptorCache(CACHE_SIZE) { counts.allocations++ }
        cache.get(liveKey) { VehicleBitmaps.vehicleBitmap(context, live, response) }
        cache.get(staleKey) { VehicleBitmaps.vehicleBitmap(context, stale, response) }
        assertEquals("a changed disc color must mint a second descriptor", 2, counts.allocations)
    }

    /**
     * The other half of the #2043 contract: with liveness and heading held fixed, a wildly different
     * schedule deviation must produce the *same* icon. A marker that still varied by punctuality would
     * both re-introduce the meaning the issue removed and silently multiply the icon working set.
     */
    @Test
    fun scheduleDeviationDoesNotAffectTheIcon() {
        val (response, vehicle) = fixture()
        val early = withRealtimeDeviation(vehicle, TimeUnit.MINUTES.toSeconds(-10))
        val late = withRealtimeDeviation(vehicle, TimeUnit.MINUTES.toSeconds(10))

        assertEquals(
            "schedule deviation must not reach the marker icon",
            VehicleBitmaps.iconKey(context, early, response),
            VehicleBitmaps.iconKey(context, late, response)
        )
        assertTrue(
            "an early and a late vehicle must render the identical disc",
            VehicleBitmaps.vehicleBitmap(context, early, response)
                .sameAs(VehicleBitmaps.vehicleBitmap(context, late, response))
        )
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
        fun get(key: String) = cache.get(key) {
            supplied++
            bitmap
        }

        get("a")
        get("b") // cache now holds [a, b]
        get("c") // exceeds maxSize: evicts a (LRU)
        assertEquals("three distinct keys are three misses", 3, supplied)

        get("b") // still cached -> hit
        assertEquals("a cached key must not re-supply", 3, supplied)

        get("a") // evicted -> miss, supplier re-invoked
        assertEquals("an evicted key must re-invoke the supplier", 4, supplied)

        cache.clear()
        get("b") // after clear -> miss
        assertEquals("get after clear must re-invoke the supplier", 5, supplied)
    }

    // --- Occupancy pips on the disc (#2194) ------------------------------------------------------

    /**
     * Occupancy is drawn on the marker, so it has to be part of the key: a vehicle that fills up between
     * polls must get a new icon rather than the cached emptier one. Held at one liveness, so only the
     * fullness varies.
     */
    @Test
    fun changedOccupancyMintsANewDescriptor() {
        val (response, vehicle) = fixture()
        val empty = withOccupancy(vehicle, Occupancy.EMPTY)
        val full = withOccupancy(vehicle, Occupancy.FULL)

        val emptyKey = VehicleBitmaps.iconKey(context, empty, response)
        val fullKey = VehicleBitmaps.iconKey(context, full, response)
        assertNotEquals("occupancy must be part of the icon key", emptyKey, fullKey)

        val counts = Counts()
        val cache = BitmapDescriptorCache(CACHE_SIZE) { counts.allocations++ }
        cache.get(emptyKey) { VehicleBitmaps.vehicleBitmap(context, empty, response) }
        cache.get(fullKey) { VehicleBitmaps.vehicleBitmap(context, full, response) }
        assertEquals("a changed fill level must mint a second descriptor", 2, counts.allocations)
    }

    /**
     * The tab's own reason for existing, at the key level: "reports nothing" and "reports EMPTY" are
     * different markers — no tab versus a tab with three hollow pips — so they must not share an icon.
     * These two were the same bitmap before this refinement, which is exactly the confusion it removes.
     */
    @Test
    fun absentOccupancyAndEmptyAreDifferentIcons() {
        val (response, vehicle) = fixture()
        val unreported = withOccupancy(vehicle, null)
        val empty = withOccupancy(vehicle, Occupancy.EMPTY)

        assertNotEquals(
            "a vehicle reporting no occupancy must not share an icon with an empty one",
            VehicleBitmaps.iconKey(context, unreported, response),
            VehicleBitmaps.iconKey(context, empty, response)
        )
        assertTrue(
            "...and must not render the same bitmap either",
            !VehicleBitmaps.vehicleBitmap(context, unreported, response)
                .sameAs(VehicleBitmaps.vehicleBitmap(context, empty, response))
        )
    }

    /**
     * A vehicle without real-time has no observed occupancy, so its gray marker must never grow a tab
     * however the status is populated (#959 — the old bubble showed occupancy on scheduled vehicles).
     * The realtime gate is the half of the bucketing the JVM test can't reach, which is why it's here.
     */
    @Test
    fun withoutRealtimeOccupancyDoesNotReachTheIcon() {
        val (response, vehicle) = fixture()
        val plain = withOccupancy(vehicle, null, isRealtime = false)
        val crowded = withOccupancy(vehicle, Occupancy.FULL, isRealtime = false)

        assertEquals(
            "occupancy must not reach a scheduled vehicle's icon",
            VehicleBitmaps.iconKey(context, plain, response),
            VehicleBitmaps.iconKey(context, crowded, response)
        )
        assertTrue(
            "a scheduled vehicle renders the identical tabless disc whatever occupancy says",
            VehicleBitmaps.vehicleBitmap(context, plain, response)
                .sameAs(VehicleBitmaps.vehicleBitmap(context, crowded, response))
        )
    }

    /** The snapshot plus its first vehicle — the prelude every icon-contract test below opens with. */
    private fun fixture(): Pair<RouteTrips, VehicleMarker> {
        val response = response()
        val vehicle = vehicles(response).firstOrNull()
        assertTrue("fixture must yield at least one vehicle", vehicle != null)
        return response to vehicle!!
    }

    /**
     * A copy of [vehicle] with only what the caller overrides varying — optionally forcing [isRealtime]
     * and overriding its [status]. The bearing is pinned too; it no longer reaches the icon (#2194
     * removed the heading arrow), but holding it fixed keeps these fixtures honest if it ever does again.
     */
    private fun pinned(
        vehicle: VehicleMarker,
        isRealtime: Boolean = true,
        status: ObaTripStatus = vehicle.status
    ): VehicleMarker = vehicle.copy(isRealtime = isRealtime, bearing = 0f, status = status)

    /** A copy of [vehicle] reporting [occupancy], with everything else held fixed. */
    private fun withOccupancy(
        vehicle: VehicleMarker,
        occupancy: Occupancy?,
        isRealtime: Boolean = true
    ): VehicleMarker = pinned(
        vehicle,
        isRealtime,
        object : ObaTripStatus by vehicle.status {
            override val occupancyStatus: Occupancy? = occupancy
        }
    )

    /** A copy of [vehicle] running [deviationSeconds] off schedule, with everything else held fixed. */
    private fun withRealtimeDeviation(vehicle: VehicleMarker, deviationSeconds: Long): VehicleMarker = pinned(
        vehicle,
        status = object : ObaTripStatus by vehicle.status {
            override val scheduleDeviation: Duration = deviationSeconds.seconds
        }
    )

    /** A copy of [vehicle] with [isRealtime] forced, so only liveness varies. */
    private fun withLiveness(vehicle: VehicleMarker, isRealtime: Boolean): VehicleMarker = pinned(vehicle, isRealtime)

    // --- The route display color on the disc (#2043) ---------------------------------------------

    /**
     * The disc is the color the map is currently *drawing that route's line* with, carried on the
     * marker by the controller — not something re-derived here from the route's GTFS color. In
     * stop-focus view `adjacencyRouteColors` gives each shown route a distinct synthesized hue so the
     * lines can be told apart, and a vehicle wearing its agency's nominal color there would point at
     * the wrong line. Asserted as exact equality, so distinctness between routes follows.
     */
    @Test
    fun discUsesTheCarriedRouteDisplayColor() {
        val assigned = 0xFF1B6EF3.toInt()

        assertEquals(
            "the disc is the route's drawn color, unmodified",
            assigned,
            VehicleBitmaps.discColor(context, liveVehicle().copy(routeColor = assigned))
        )
    }

    /** A vehicle whose route has no color falls back exactly as an uncolored polyline does. */
    @Test
    fun absentRouteColorFallsBackLikeAnUncoloredLine() {
        assertEquals(
            DEFAULT_ROUTE_LINE_COLOR,
            VehicleBitmaps.discColor(context, liveVehicle().copy(routeColor = null))
        )
    }

    /**
     * A vehicle without real-time takes the shared "no prediction" gray, not its route's color — the
     * marker's one remaining status meaning.
     */
    @Test
    fun withoutRealtimeTheDiscIsTheScheduledGray() {
        val stale = staleVehicle().copy(routeColor = 0xFF1B6EF3.toInt())

        assertEquals(
            context.getColor(ScheduleDeviation.Status.SCHEDULED.displayColorRes),
            VehicleBitmaps.discColor(context, stale)
        )
    }

    /**
     * The glyph/arrow color flips by the disc's luminance, so a route drawn in a pale yellow doesn't
     * get a white bus glyph washed out on it. Shared with the route badges via [MarkerRendering].
     */
    @Test
    fun glyphFlipsToStayLegibleOnLightAndDarkDiscs() {
        assertEquals("white glyph on a dark disc", Color.WHITE, MarkerRendering.legibleOn(0xFF0B1F55.toInt()))
        assertEquals("black glyph on a light disc", Color.BLACK, MarkerRendering.legibleOn(0xFFFFEB3B.toInt()))
    }

    private fun liveVehicle(): VehicleMarker = withLiveness(vehicles(response()).first(), isRealtime = true)

    private fun staleVehicle(): VehicleMarker = withLiveness(vehicles(response()).first(), isRealtime = false)

    companion object {
        // Every fullness state a marker can be in, tabless "not reported" included. The replay walks a
        // vehicle around this cycle so each poll can change its icon, then revisits states (no new icons)
        // — which is the property under test: a revisited state reuses the cached descriptor.
        private val OCCUPANCY_CYCLE = listOf(
            null,
            Occupancy.EMPTY,
            Occupancy.MANY_SEATS_AVAILABLE,
            Occupancy.STANDING_ROOM_ONLY,
            Occupancy.FULL
        )

        private const val SHORT_POLLS = 60
        private const val LONG_POLLS = 240

        // Far larger than the snapshot's distinct icons (5 fullness states x a handful of type/route-color
        // combos), so eviction never confounds the "allocations independent of poll count" invariant. The
        // production cap is deliberately smaller — it only needs to cover the live working set, not history.
        private const val CACHE_SIZE = 1024
    }
}
