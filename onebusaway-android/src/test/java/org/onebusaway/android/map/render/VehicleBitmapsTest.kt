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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripDetails
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.RouteTrips

/**
 * Pure-logic guards for [VehicleBitmaps]'s route-type resolution — the cablecar→tram promise and the
 * unresolvable-trip/route fallback that replaced the crashing `!!` chain (#2020) — plus the occupancy
 * bucketing the marker's pips read (#2194).
 */
class VehicleBitmapsTest {

    /**
     * `iconKey` and `vehicleBitmap` both route through [VehicleBitmaps.normalizeVehicleType], so a cablecar
     * route must collapse onto tram — otherwise the two paths could disagree (or mint a second icon for a
     * vehicle that should share tram's).
     */
    @Test
    fun cablecarNormalizesToTram() {
        assertEquals(
            ObaRoute.TYPE_TRAM,
            VehicleBitmaps.normalizeVehicleType(ObaRoute.TYPE_CABLECAR)
        )
        assertEquals(
            "a cablecar route resolves to the same type as a tram route",
            VehicleBitmaps.normalizeVehicleType(ObaRoute.TYPE_TRAM),
            VehicleBitmaps.normalizeVehicleType(ObaRoute.TYPE_CABLECAR)
        )
    }

    /** Every other route type passes through untouched. */
    @Test
    fun otherTypesPassThrough() {
        for (type in intArrayOf(
            ObaRoute.TYPE_TRAM,
            ObaRoute.TYPE_SUBWAY,
            ObaRoute.TYPE_RAIL,
            ObaRoute.TYPE_BUS,
            ObaRoute.TYPE_FERRY
        )) {
            assertEquals(type.toLong(), VehicleBitmaps.normalizeVehicleType(type).toLong())
        }
    }

    @Test
    fun resolvesTheRouteTypeWhenReferencesCarryTripAndRoute() {
        val response = routeTrips(trip = FakeTrip("trip1", "routeA"), route = FakeRoute(ObaRoute.TYPE_FERRY))
        assertEquals(ObaRoute.TYPE_FERRY, VehicleBitmaps.routeTypeFor(response, "trip1"))
    }

    /**
     * #2020: a vehicle mid-block-interline reports an activeTripId this route's poll never fetched, so
     * the references pool can't resolve it. That used to be a `!!` and killed the process on the
     * reactive poll path; it must now degrade to the default glyph.
     */
    @Test
    fun missingTripFallsBackToTheDefaultGlyphInsteadOfThrowing() {
        val response = routeTrips(trip = null, route = FakeRoute(ObaRoute.TYPE_RAIL))
        assertEquals(ObaRoute.TYPE_BUS, VehicleBitmaps.routeTypeFor(response, "absent-trip"))
    }

    /** The trip resolved but its route didn't — the second half of the old `!!` chain. */
    @Test
    fun missingRouteFallsBackToTheDefaultGlyphInsteadOfThrowing() {
        val response = routeTrips(trip = FakeTrip("trip1", "routeA"), route = null)
        assertEquals(ObaRoute.TYPE_BUS, VehicleBitmaps.routeTypeFor(response, "trip1"))
    }

    /** A blank/absent id (cf. #2003, where OBA sends "" rather than null for block-edge trip ids). */
    @Test
    fun blankOrNullTripIdFallsBackToTheDefaultGlyph() {
        val response = routeTrips(trip = null, route = null)
        assertEquals(ObaRoute.TYPE_BUS, VehicleBitmaps.routeTypeFor(response, ""))
        assertEquals(ObaRoute.TYPE_BUS, VehicleBitmaps.routeTypeFor(response, null))
    }

    /** Cablecar still collapses onto tram when it *is* resolvable, fallback path notwithstanding. */
    @Test
    fun resolvedCablecarStillNormalizesToTram() {
        val response = routeTrips(trip = FakeTrip("trip1", "routeA"), route = FakeRoute(ObaRoute.TYPE_CABLECAR))
        assertEquals(ObaRoute.TYPE_TRAM, VehicleBitmaps.routeTypeFor(response, "trip1"))
    }

    /**
     * The whole GTFS-realtime occupancy enum, bucketed onto the marker's 0..3 pips. Pinned exhaustively
     * because this is the only thing standing between a rider and a wrong crowding reading, and because
     * a new enum value must be assigned a bucket deliberately rather than falling into one.
     */
    @Test
    fun occupancyBucketsOntoPips() {
        val full = VehicleBitmaps.MAX_PIPS
        assertEquals("absent occupancy draws nothing", 0, VehicleBitmaps.occupancyPips(null))
        assertEquals(0, VehicleBitmaps.occupancyPips(Occupancy.EMPTY))
        assertEquals(1, VehicleBitmaps.occupancyPips(Occupancy.MANY_SEATS_AVAILABLE))
        assertEquals(2, VehicleBitmaps.occupancyPips(Occupancy.FEW_SEATS_AVAILABLE))
        assertEquals(2, VehicleBitmaps.occupancyPips(Occupancy.STANDING_ROOM_ONLY))
        assertEquals(full, VehicleBitmaps.occupancyPips(Occupancy.CRUSHED_STANDING_ROOM_ONLY))
        assertEquals(full, VehicleBitmaps.occupancyPips(Occupancy.FULL))
        assertEquals(full, VehicleBitmaps.occupancyPips(Occupancy.NOT_ACCEPTING_PASSENGERS))
    }

    /** A [RouteTrips] whose references pool holds at most the given trip/route. */
    private fun routeTrips(trip: ObaTrip?, route: ObaRoute?): RouteTrips = object : RouteTrips {
        override val trips: List<ObaTripDetails> = emptyList()
        override fun trip(tripId: String?): ObaTrip? = trip.takeIf { !tripId.isNullOrEmpty() }
        override fun route(routeId: String): ObaRoute? = route
        override val currentTimeMs: Long = 0L
    }

    private class FakeTrip(override val id: String, override val routeId: String) : ObaTrip {
        override val shortName: String? = null
        override val shapeId: String? = null
        override val directionId: Int = 0
        override val serviceId: String? = null
        override val headsign: String? = null
        override val timezone: String? = null
        override val blockId: String? = null
    }

    private class FakeRoute(override val type: Int) : ObaRoute {
        override val id: String = "routeA"
        override val shortName: String? = null
        override val longName: String? = null
        override val description: String? = null
        override val url: String? = null
        override val color: Int? = null
        override val textColor: Int? = null
        override val agencyId: String = "agency"
    }
}
