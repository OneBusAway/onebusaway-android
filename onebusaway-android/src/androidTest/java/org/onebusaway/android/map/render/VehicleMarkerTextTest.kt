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

import android.content.Context
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.ObaTripDetails
import org.onebusaway.android.models.ObaTripStatus
import org.onebusaway.android.models.Occupancy
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.Status
import org.onebusaway.android.util.GeoPoint

/**
 * A vehicle marker's accessible name (#2194). Since the marker draws crowding as silhouettes and shows
 * no info window, this string is the *only* way that crowding reaches a rider using a screen reader —
 * so what it says when the references pool can't fully resolve the vehicle is a real behaviour, not an
 * edge case: an interlining vehicle reports an activeTripId this route's poll never fetched (#2020) and
 * still gets a drawn marker.
 *
 * Instrumented rather than a JVM test because the title is assembled from string resources, and the
 * point of the fallbacks is that the *localized* text comes out, not that a code path was taken.
 */
@RunWith(AndroidJUnit4::class)
class VehicleMarkerTextTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Everything resolves: route, headsign, and crowding, in that order. */
    @Test
    fun namesRouteHeadsignAndOccupancy() {
        val response = routeTrips(trip = FakeTrip(headsign = "Ballard"), route = FakeRoute(shortName = "44"))
        assertEquals(
            "44 - Ballard - " + context.getString(R.string.realtime_standing_room),
            vehicleTitle(context, vehicle(Occupancy.STANDING_ROOM_ONLY), response)
        )
    }

    /**
     * The #2020 fallback: the trip isn't in the references pool, so there is no route and no headsign —
     * but the marker is drawn with its pips, so the title has to still say how full it is. This returned
     * `""` before, which left the marker unnamed and its crowding sight-only.
     */
    @Test
    fun unresolvedTripStillNamesItsOccupancy() {
        val response = routeTrips(trip = null, route = FakeRoute(shortName = "44"))
        assertEquals(
            context.getString(R.string.vehicle_marker_unidentified) + " - " + context.getString(R.string.realtime_full),
            vehicleTitle(context, vehicle(Occupancy.FULL), response)
        )
    }

    /** The trip resolved but its route didn't — the headsign it does have is worth more than nothing. */
    @Test
    fun unresolvedRouteKeepsTheHeadsign() {
        val response = routeTrips(trip = FakeTrip(headsign = "Ballard"), route = null)
        assertEquals(
            "Ballard - " + context.getString(R.string.realtime_full),
            vehicleTitle(context, vehicle(Occupancy.FULL), response)
        )
    }

    /**
     * A route whose short *and* long name are empty resolves to an empty display name, which must drop
     * out of the title rather than leave it hanging off a separator ("44 - " with nothing after it).
     */
    @Test
    fun anEmptyRouteNameAndHeadsignDoNotProduceStraySeparators() {
        val response = routeTrips(trip = FakeTrip(headsign = null), route = FakeRoute(shortName = null))
        assertEquals(
            context.getString(R.string.vehicle_marker_unidentified) + " - " + context.getString(R.string.realtime_full),
            vehicleTitle(context, vehicle(Occupancy.FULL), response)
        )
    }

    /** Nothing resolved and nothing to report: the marker still needs *a* name to be announced by. */
    @Test
    fun anUnidentifiedVehicleWithoutOccupancyStillHasAName() {
        val response = routeTrips(trip = null, route = null)
        assertEquals(
            context.getString(R.string.vehicle_marker_unidentified),
            vehicleTitle(context, vehicle(occupancy = null), response)
        )
    }

    /** A scheduled vehicle reports no crowding (#959), so the title is identity alone. */
    @Test
    fun aScheduledVehicleSaysNothingAboutCrowding() {
        val response = routeTrips(trip = FakeTrip(headsign = "Ballard"), route = FakeRoute(shortName = "44"))
        assertEquals(
            "44 - Ballard",
            vehicleTitle(context, vehicle(Occupancy.FULL, isRealtime = false), response)
        )
    }

    /**
     * The marker draws three hollow pips for a vehicle that reports itself EMPTY, and a screen-reader
     * user has to hear that as "Empty" — not as silence, which is what a vehicle reporting *nothing*
     * gets. This is the audible half of the null-vs-EMPTY split the tab exists to draw.
     */
    @Test
    fun anEmptyVehicleSaysSoAndAnUnreportedOneStaysSilent() {
        val response = routeTrips(trip = FakeTrip(headsign = "Ballard"), route = FakeRoute(shortName = "44"))
        assertEquals(
            "44 - Ballard - " + context.getString(R.string.realtime_empty),
            vehicleTitle(context, vehicle(Occupancy.EMPTY), response)
        )
        assertEquals(
            "44 - Ballard",
            vehicleTitle(context, vehicle(occupancy = null), response)
        )
    }

    private fun vehicle(occupancy: Occupancy?, isRealtime: Boolean = true): VehicleMarker = VehicleMarker(
        activeTripId = "trip1",
        point = GeoPoint(47.6, -122.3),
        isRealtime = isRealtime,
        status = FakeStatus(occupancy)
    )

    /** A [RouteTrips] whose references pool holds at most the given trip/route. */
    private fun routeTrips(trip: ObaTrip?, route: ObaRoute?): RouteTrips = object : RouteTrips {
        override val trips: List<ObaTripDetails> = emptyList()
        override fun trip(tripId: String?): ObaTrip? = trip.takeIf { !tripId.isNullOrEmpty() }
        override fun route(routeId: String): ObaRoute? = route
        override val currentTimeMs: Long = 0L
    }

    private class FakeTrip(override val headsign: String?) : ObaTrip {
        override val id: String = "trip1"
        override val routeId: String = "routeA"
        override val shortName: String? = null
        override val shapeId: String? = null
        override val directionId: Int = 0
        override val serviceId: String? = null
        override val timezone: String? = null
        override val blockId: String? = null
    }

    private class FakeRoute(override val shortName: String?) : ObaRoute {
        override val id: String = "routeA"
        override val type: Int = ObaRoute.TYPE_BUS
        override val longName: String? = null
        override val description: String? = null
        override val url: String? = null
        override val color: Int? = null
        override val textColor: Int? = null
        override val agencyId: String = "agency"
    }

    private class FakeStatus(override val occupancyStatus: Occupancy?) : ObaTripStatus {
        override val serviceDate: Long = 0L
        override val isPredicted: Boolean = true
        override val scheduleDeviation: Duration = Duration.ZERO
        override val vehicleId: String? = null
        override val closestStop: String? = null
        override val closestStopTimeOffset: Long = 0L
        override val position: Location? = null
        override val activeTripId: String = "trip1"
        override val distanceAlongTrip: Double? = null
        override val scheduledDistanceAlongTrip: Double? = null
        override val totalDistanceAlongTrip: Double? = null
        override val orientation: Double? = null
        override val nextStop: String? = null
        override val nextStopTimeOffset: Long? = null
        override val phase: String? = null
        override val status: Status? = null
        override val lastUpdateTime: Long = 0L
        override val lastKnownLocation: Location? = null
        override val lastLocationUpdateTime: Long = 0L
        override val lastKnownDistanceAlongTrip: Double? = null
        override val lastKnownOrientation: Double? = null
        override val blockTripSequence: Int = 0
    }
}
