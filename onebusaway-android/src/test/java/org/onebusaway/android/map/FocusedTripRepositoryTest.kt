/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.api.adapters.StopTimeData
import org.onebusaway.android.api.adapters.TripScheduleData
import org.onebusaway.android.api.data.RouteStopsDataSource
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaTripSchedule
import org.onebusaway.android.models.RouteStopGroup
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.models.TripRouteInfo
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.Polyline

@OptIn(ExperimentalCoroutinesApi::class)
class FocusedTripRepositoryTest {

    private class Observations : TripObservationRepository {
        val schedules = mutableMapOf<String, ObaTripSchedule?>()
        val shapes = mutableMapOf<String, Polyline?>()
        val scheduleFailures = mutableSetOf<String>()
        val shapeRequests = mutableListOf<Pair<String, String>>()

        override fun lookupTripState(tripId: String?): TripState? = null
        override fun tripDetailsStream(tripId: String, intervalMs: Long): Flow<Unit> = emptyFlow()
        override fun routeVehiclesStream(routeId: String, intervalMs: Long): Flow<RouteTrips> = emptyFlow()
        override suspend fun ensureSchedule(tripId: String): ObaTripSchedule? {
            if (tripId in scheduleFailures) error("schedule failed")
            return schedules[tripId]
        }
        override suspend fun ensureShape(tripId: String, shapeId: String): Polyline? {
            shapeRequests += tripId to shapeId
            return shapes[shapeId]
        }
        override suspend fun resolveNeighborTrip(tripId: String): TripRouteInfo? = null
    }

    private class RouteStops : RouteStopsDataSource {
        val catalogs = mutableMapOf<String, Result<List<RouteStopGroup>>>()
        override suspend fun stopsForRoute(routeId: String): Result<List<RouteStopGroup>> =
            catalogs[routeId] ?: Result.success(emptyList())
    }

    @Test
    fun `stops are the exact trip schedule subset rather than every route stop`() = runTest {
        val observations = Observations().apply {
            schedules["trip"] = TripScheduleData(
                arrayOf(StopTimeData("a"), StopTimeData("c"))
            )
        }
        val routes = RouteStops().apply {
            catalogs["route"] = Result.success(
                listOf(RouteStopGroup("outbound", listOf(stop("a"), stop("b"), stop("c"))))
            )
        }
        val repository = DefaultFocusedTripRepository(
            observations, routes, backgroundScope, now = { ElapsedTime(0L) }
        )

        val result = repository.getStops(setOf(FocusedTrip("trip", "route", "shape", null)))

        assertEquals(listOf("a", "c"), result.stopIdsByTripId["trip"])
        assertEquals(setOf("a", "c"), result.stopIds)
        assertEquals(setOf("a", "b", "c"), result.stopsById.keys)
    }

    @Test
    fun `geometry fetches a shared shape once but retains each route direction`() = runTest {
        val observations = Observations().apply {
            shapes["shared"] = Polyline(emptyList())
            shapes["missing"] = null
        }
        val repository = DefaultFocusedTripRepository(
            observations, RouteStops(), backgroundScope, now = { ElapsedTime(0L) }
        )

        val result = repository.getGeometry(
            setOf(
                FocusedTrip("first", "route", "shared", 7, directionId = 1),
                FocusedTrip("second", "route", "shared", 7, directionId = 0),
                FocusedTrip("failed", "route", "missing", 8),
                FocusedTrip("no-shape", "route", null, 9),
            )
        )

        assertEquals(listOf(1, 0), result.shapes.map { it.directionId })
        assertTrue(result.shapes.all { it.shapeId == "shared" })
        assertEquals(1, observations.shapeRequests.count { it.second == "shared" })
        assertEquals(1, observations.shapeRequests.count { it.second == "missing" })
        assertTrue(result.shapes.none { it.shapeId == "missing" })
    }

    @Test
    fun `one failed schedule does not discard another displayed trip's stops`() = runTest {
        val observations = Observations().apply {
            scheduleFailures += "failed"
            schedules["good"] = TripScheduleData(arrayOf(StopTimeData("served")))
        }
        val routes = RouteStops().apply {
            catalogs["route"] = Result.success(listOf(RouteStopGroup(null, listOf(stop("served")))))
        }
        val repository = DefaultFocusedTripRepository(
            observations, routes, backgroundScope, now = { ElapsedTime(0L) }
        )

        val result = repository.getStops(
            setOf(
                FocusedTrip("failed", "route", "failed-shape", null),
                FocusedTrip("good", "route", "good-shape", null),
            )
        )

        assertEquals(setOf("served"), result.stopIds)
        assertEquals(setOf("good"), result.stopIdsByTripId.keys)
    }

    @Test
    fun `shape cache is keyed by shape rather than trip`() = runTest {
        val observations = Observations().apply { shapes["shared"] = Polyline(emptyList()) }
        val repository = DefaultFocusedTripRepository(
            observations, RouteStops(), backgroundScope, now = { ElapsedTime(0L) }
        )

        repository.getGeometry(setOf(FocusedTrip("older-trip", "route", "shared", null)))
        repository.getGeometry(setOf(FocusedTrip("newer-trip", "route", "shared", null)))

        assertEquals(listOf("older-trip" to "shared"), observations.shapeRequests)
    }

    private fun stop(id: String) = ObaStopElement(id = id, lat = 47.0, lon = -122.0)

}
