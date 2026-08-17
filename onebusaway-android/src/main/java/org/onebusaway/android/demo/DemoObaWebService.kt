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
package org.onebusaway.android.demo

import java.net.HttpURLConnection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import org.onebusaway.android.api.contract.AgencyCoverage
import org.onebusaway.android.api.contract.AgencyReference
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.ArrivalsForLocation
import org.onebusaway.android.api.contract.ArrivalsForLocationData
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.CurrentTime
import org.onebusaway.android.api.contract.DirectionSchedule
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ListWithReferences
import org.onebusaway.android.api.contract.NearbyStop
import org.onebusaway.android.api.contract.NoData
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.api.contract.ObaWebService
import org.onebusaway.android.api.contract.Position
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.RouteSchedule
import org.onebusaway.android.api.contract.ScheduleStopTime
import org.onebusaway.android.api.contract.ShapeEntry
import org.onebusaway.android.api.contract.SituationAffects
import org.onebusaway.android.api.contract.SituationReference
import org.onebusaway.android.api.contract.SituationText
import org.onebusaway.android.api.contract.SituationWindow
import org.onebusaway.android.api.contract.StopGroup
import org.onebusaway.android.api.contract.StopGroupName
import org.onebusaway.android.api.contract.StopGrouping
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.api.contract.StopSchedule
import org.onebusaway.android.api.contract.StopTime
import org.onebusaway.android.api.contract.StopsForRoute
import org.onebusaway.android.api.contract.TripDetailsEntry
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.api.contract.TripSchedule
import org.onebusaway.android.api.contract.TripStatus

/**
 * A complete, offline OneBusAway deployment for the scripted tutorial (#2164) — the "fake transit
 * agency in a particular state" the issue asks for, standing in for the region's real server while
 * demo mode is on.
 *
 * It implements [ObaWebService] itself rather than intercepting HTTP, so every one of the app's
 * dozen-odd OBA data sources, and all the repositories, view models and UI above them, run completely
 * unmodified — they cannot tell they aren't talking to a real region. That is the point: the tutorial
 * has to demonstrate the *actual* app, not a mock of it. [org.onebusaway.android.api.net.ObaApiProvider]
 * routes to this instead of Retrofit while [DemoModeController] is active, which also means the tour
 * needs no region, no API key and no network at all.
 *
 * Static facts come from [DemoTransitFixture]; everything that moves is generated per call by
 * [DemoScenario] against the current clock, so arrivals count down and buses drive along their shapes
 * for as long as the user leaves the tour open.
 *
 * Ids the demo system doesn't know are answered the way a real deployment answers them — a 404-coded
 * envelope, or an empty list for the `*-for-location` queries — rather than by throwing, so a stray
 * lookup (the trip planner's itineraries name real routes this fixture doesn't carry) degrades to
 * "nothing to show" instead of an error dialog over the tutorial.
 */
class DemoObaWebService(private val fixture: DemoTransitFixture) : ObaWebService {

    // ---------------------------------------------------------------- reference data

    override suspend fun route(routeId: String): ObaEnvelope<EntryWithReferences<RouteReference>> {
        val route = fixture.routeById[routeId] ?: return notFound()
        return ok(EntryWithReferences(route, References(agencies = listOf(fixture.agency), routes = listOf(route))))
    }

    override suspend fun stop(stopId: String): ObaEnvelope<EntryWithReferences<StopReference>> {
        val stop = fixture.stopById[stopId] ?: return notFound()
        return ok(EntryWithReferences(stop, references(stops = listOf(stop), routes = routesServing(stop))))
    }

    override suspend fun agenciesWithCoverage(): ObaEnvelope<ListWithReferences<AgencyCoverage>> = ok(
        ListWithReferences(
            list = listOf(AgencyCoverage(fixture.agency.id)),
            references = References(agencies = listOf(fixture.agency))
        )
    )

    override suspend fun agency(agencyId: String): ObaEnvelope<EntryWithReferences<AgencyReference>> = if (agencyId == fixture.agency.id) ok(EntryWithReferences(fixture.agency)) else notFound()

    override suspend fun routeIdsForAgency(agencyId: String): ObaEnvelope<ListWithReferences<String>> = if (agencyId == fixture.agency.id) ok(ListWithReferences(fixture.routes.map { it.id })) else notFound()

    override suspend fun stopIdsForAgency(agencyId: String): ObaEnvelope<ListWithReferences<String>> = if (agencyId == fixture.agency.id) ok(ListWithReferences(fixture.stops.map { it.id })) else notFound()

    override suspend fun trip(tripId: String): ObaEnvelope<EntryWithReferences<TripReference>> {
        val run = DemoScenario.runById(fixture, tripId) ?: return notFound()
        val route = fixture.routeById[run.routeId] ?: return notFound()
        return ok(EntryWithReferences(tripReference(run), references(routes = listOf(route))))
    }

    override suspend fun shape(shapeId: String): ObaEnvelope<EntryWithReferences<ShapeEntry>> {
        val geometry = fixture.routeStops.entries.firstOrNull { shapeIdFor(it.key) == shapeId }?.value
            ?: return notFound()
        return ok(EntryWithReferences(geometry.polyline))
    }

    override suspend fun currentTime(): ObaEnvelope<EntryWithReferences<CurrentTime>> {
        val now = nowMs()
        return ok(EntryWithReferences(CurrentTime(now, isoTime(now))), now)
    }

    // ---------------------------------------------------------------- spatial queries

    override suspend fun stopsForLocation(
        lat: Double,
        lon: Double,
        query: String?,
        radius: Int?,
        latSpan: Double?,
        lonSpan: Double?,
        maxCount: Int?
    ): ObaEnvelope<ListWithReferences<StopReference>> {
        val stops = fixture.stops
            .filter { within(it.lat, it.lon, lat, lon, radius, latSpan, lonSpan) }
            .filter { matches(it, query) }
            .let { if (maxCount != null && maxCount > 0) it.take(maxCount) else it }
        return ok(ListWithReferences(stops, references(stops = stops, routes = fixture.routes)))
    }

    override suspend fun routesForLocation(
        lat: Double,
        lon: Double,
        query: String?,
        radius: Int?
    ): ObaEnvelope<ListWithReferences<RouteReference>> {
        // A demo route is "near" the query point when any stop it serves is.
        val routes = fixture.routes.filter { route ->
            val serves = fixture.routeStops[route.id]?.stopIds.orEmpty()
            val near = serves.any { id ->
                fixture.stopById[id]?.let { within(it.lat, it.lon, lat, lon, radius, null, null) } == true
            }
            near && (query.isNullOrBlank() || route.shortName?.contains(query, ignoreCase = true) == true)
        }
        return ok(ListWithReferences(routes, references(routes = routes)))
    }

    override suspend fun tripsForLocation(
        lat: Double,
        lon: Double,
        radius: Int?
    ): ObaEnvelope<ListWithReferences<TripDetailsEntry>> {
        val now = nowMs()
        val runs = fixture.routeStops.keys
            .flatMap { DemoScenario.activeRuns(fixture, it, now) }
            .filter { run ->
                run.positionAt(now)?.let { within(it.latitude, it.longitude, lat, lon, radius, null, null) } == true
            }
        return ok(ListWithReferences(runs.map { detailsEntry(it, now, includeSchedule = false) }, referencesFor(runs)), now)
    }

    // ---------------------------------------------------------------- routes and trips

    override suspend fun stopsForRoute(
        routeId: String,
        includePolylines: Boolean
    ): ObaEnvelope<EntryWithReferences<StopsForRoute>> {
        val geometry = fixture.routeStops[routeId] ?: return notFound()
        val route = fixture.routeById[routeId] ?: return notFound()
        val stops = geometry.stopIds.mapNotNull(fixture.stopById::get)
        val polylines = if (includePolylines) listOf(geometry.polyline) else emptyList()
        val entry = StopsForRoute(
            stopGroupings = listOf(
                StopGrouping(
                    stopGroups = listOf(
                        StopGroup(
                            id = geometry.directionId,
                            name = StopGroupName(names = listOf(geometry.name)),
                            stopIds = geometry.stopIds,
                            polylines = polylines
                        )
                    )
                )
            ),
            polylines = polylines
        )
        return ok(EntryWithReferences(entry, references(stops = stops, routes = listOf(route))))
    }

    override suspend fun tripsForRoute(
        routeId: String,
        includeStatus: Boolean,
        includeSchedule: Boolean
    ): ObaEnvelope<ListWithReferences<TripDetailsEntry>> {
        if (routeId !in fixture.routeStops) return notFound()
        val now = nowMs()
        val runs = DemoScenario.activeRuns(fixture, routeId, now)
        val entries = runs.map { detailsEntry(it, now, includeSchedule, includeStatus) }
        return ok(ListWithReferences(entries, referencesFor(runs)), now)
    }

    override suspend fun tripDetails(tripId: String): ObaEnvelope<EntryWithReferences<TripDetailsEntry>> {
        val run = DemoScenario.runById(fixture, tripId) ?: return notFound()
        val now = nowMs()
        return ok(EntryWithReferences(detailsEntry(run, now, includeSchedule = true), referencesFor(listOf(run))), now)
    }

    override suspend fun tripForVehicle(
        vehicleId: String,
        includeTrip: Boolean
    ): ObaEnvelope<EntryWithReferences<TripDetailsEntry>> {
        val now = nowMs()
        // A vehicle that isn't running a trip is a 404 on the real API, which is how callers tell that
        // case apart from a lookup that failed — so an unknown demo coach answers the same way.
        val run = DemoScenario.runByVehicle(fixture, vehicleId, now) ?: return notFound()
        return ok(EntryWithReferences(detailsEntry(run, now, includeSchedule = true), referencesFor(listOf(run))), now)
    }

    // ---------------------------------------------------------------- arrivals

    override suspend fun arrivalsAndDeparturesForStop(
        stopId: String,
        minutesAfter: Int?
    ): ObaEnvelope<EntryWithReferences<ArrivalsForStop>> {
        val stop = fixture.stopById[stopId] ?: return notFound()
        val now = nowMs()
        val calls = DemoScenario.arrivalsAt(fixture, stopId, now).withinWindow(now, minutesAfter)
        val entry = ArrivalsForStop(
            stopId = stopId,
            arrivalsAndDepartures = calls.map { arrival(it, now) },
            nearbyStopIds = nearbyStopIds(stop),
            situationIds = emptyList()
        )
        return ok(EntryWithReferences(entry, referencesFor(calls.map { it.run }, stops = listOf(stop))), now)
    }

    override suspend fun arrivalsAndDeparturesForLocation(
        lat: Double,
        lon: Double,
        latSpan: Double,
        lonSpan: Double,
        minutesAfter: Int?
    ): ObaEnvelope<ArrivalsForLocationData> {
        val now = nowMs()
        val stops = fixture.stops.filter { within(it.lat, it.lon, lat, lon, null, latSpan, lonSpan) }
        val calls = stops
            .flatMap { DemoScenario.arrivalsAt(fixture, it.id, now).withinWindow(now, minutesAfter) }
            .sortedBy { it.arrivalTimeMs }
        // A box with no stops in it is answered with an absent entry, exactly as the real endpoint does
        // for an empty result — see ArrivalsForLocationData's own note.
        val entry = if (stops.isEmpty()) {
            null
        } else {
            ArrivalsForLocation(
                arrivalsAndDepartures = calls.map { arrival(it, now) },
                nearbyStopIds = stops.map { NearbyStop(it.id, distanceMeters(it.lat, it.lon, lat, lon)) },
                stopIds = calls.map { it.stopId }
            )
        }
        return ok(ArrivalsForLocationData(entry, referencesFor(calls.map { it.run }, stops = stops)), now)
    }

    override suspend fun scheduleForStop(
        stopId: String,
        date: String?
    ): ObaEnvelope<EntryWithReferences<StopSchedule>> {
        val stop = fixture.stopById[stopId] ?: return notFound()
        val now = nowMs()
        val byRoute = DemoScenario.arrivalsAt(fixture, stopId, now).groupBy { it.run.routeId }
        val entry = StopSchedule(
            stopId = stop.id,
            timeZone = fixture.agency.timezone.orEmpty(),
            date = DemoScenario.serviceDateMs(fixture, now),
            stopRouteSchedules = byRoute.map { (routeId, calls) ->
                RouteSchedule(
                    routeId = routeId,
                    stopRouteDirectionSchedules = listOf(
                        DirectionSchedule(
                            tripHeadsign = calls.first().run.headsign,
                            scheduleStopTimes = calls.map {
                                ScheduleStopTime(
                                    tripId = it.run.tripId,
                                    stopHeadsign = it.run.headsign,
                                    arrivalTime = it.scheduledArrivalTimeMs,
                                    departureTime = it.scheduledArrivalTimeMs
                                )
                            }
                        )
                    )
                )
            }
        )
        return ok(EntryWithReferences(entry, references(stops = listOf(stop), routes = fixture.routes)), now)
    }

    // ---------------------------------------------------------------- problem reports

    // The demo deployment has nowhere to file a report, and the tour never reaches the report flow;
    // accepting them keeps a stray submission from surfacing an error over the tutorial.
    override suspend fun reportProblemWithStop(
        stopId: String,
        code: String,
        data: String,
        userComment: String?,
        userLat: Double?,
        userLon: Double?,
        userLocationAccuracy: Int?
    ): ObaEnvelope<NoData> = ok(NoData())

    override suspend fun reportProblemWithTrip(
        tripId: String,
        code: String,
        data: String,
        stopId: String?,
        serviceDate: Long?,
        vehicleId: String?,
        userComment: String?,
        userLat: Double?,
        userLon: Double?,
        userLocationAccuracy: Int?,
        userOnVehicle: Boolean?,
        userVehicleNumber: String?
    ): ObaEnvelope<NoData> = ok(NoData())

    // ---------------------------------------------------------------- payload builders

    /** One arrival row: the run's real time at the stop, and the schedule it's being measured against. */
    private fun arrival(call: DemoStopCall, now: Long): ArrivalDeparture {
        val run = call.run
        val predicted = run.isPredicted(now)
        return ArrivalDeparture(
            routeId = run.routeId,
            tripId = run.tripId,
            stopId = call.stopId,
            tripHeadsign = run.headsign,
            routeShortName = fixture.routeById[run.routeId]?.shortName,
            routeLongName = fixture.routeById[run.routeId]?.longName,
            stopSequence = run.geometry.indexOf(call.stopId) ?: 0,
            serviceDate = DemoScenario.serviceDateMs(fixture, now),
            vehicleId = run.vehicleId.takeIf { predicted },
            predicted = predicted,
            scheduledArrivalTime = call.scheduledArrivalTimeMs,
            scheduledDepartureTime = call.scheduledArrivalTimeMs,
            // A run with no vehicle out yet has nothing to predict from, so it reports schedule only —
            // which is what puts a "scheduled" row (and its grey pill) in the demo arrivals list.
            predictedArrivalTime = if (predicted) call.arrivalTimeMs else 0L,
            predictedDepartureTime = if (predicted) call.arrivalTimeMs else 0L,
            tripStatus = if (predicted) status(run, now) else null,
            situationIds = situationIdsFor(run.routeId)
        )
    }

    /** A run's real-time status: where the bus is, how late it is, and what it reaches next. */
    private fun status(run: DemoRun, now: Long): TripStatus {
        val position = run.positionAt(now)?.let { Position(it.latitude, it.longitude) }
        val nextIndex = run.nextStopIndexAt(now)
        return TripStatus(
            activeTripId = run.tripId,
            predicted = true,
            scheduleDeviation = run.deviationSeconds,
            serviceDate = DemoScenario.serviceDateMs(fixture, now),
            status = "default",
            phase = "in_progress",
            vehicleId = run.vehicleId,
            closestStop = nextIndex?.let(run.geometry.stopIds::get),
            closestStopTimeOffset = nextIndex?.let { secondsUntilStop(run, it, now) } ?: 0L,
            nextStop = nextIndex?.let(run.geometry.stopIds::get),
            nextStopTimeOffset = nextIndex?.let { secondsUntilStop(run, it, now) },
            position = position,
            orientation = run.bearingAt(now).toDouble(),
            distanceAlongTrip = run.distanceAlongShapeAt(now),
            scheduledDistanceAlongTrip = run.distanceAlongShapeAt(now) +
                run.deviationSeconds *
                run.service.speedMetersPerSecond,
            totalDistanceAlongTrip = run.geometry.totalDistance,
            // The demo feed is always fresh: a live AVL system had just reported when we were asked.
            lastUpdateTime = now - DEMO_AVL_AGE_MS,
            lastLocationUpdateTime = now - DEMO_AVL_AGE_MS,
            lastKnownLocation = position,
            lastKnownDistanceAlongTrip = run.distanceAlongShapeAt(now),
            lastKnownOrientation = run.bearingAt(now).toDouble(),
            blockTripSequence = 0
        )
    }

    private fun secondsUntilStop(run: DemoRun, stopIndex: Int, now: Long): Long = (run.timeAtDistance(run.geometry.stopDistances[stopIndex]) - now) / 1000L

    private fun detailsEntry(
        run: DemoRun,
        now: Long,
        includeSchedule: Boolean,
        includeStatus: Boolean = true
    ) = TripDetailsEntry(
        tripId = run.tripId,
        status = if (includeStatus && run.isPredicted(now)) status(run, now) else null,
        schedule = if (includeSchedule) schedule(run, now) else null
    )

    /**
     * A run's stop times. The wire's `StopTime` counts **seconds from the start of the service day**
     * (unlike the epoch-millis `ScheduleStopTime` above), so each stop's scheduled moment is expressed
     * relative to the day the run belongs to.
     */
    private fun schedule(run: DemoRun, now: Long): TripSchedule {
        val serviceDate = DemoScenario.serviceDateMs(fixture, now)
        return TripSchedule(
            stopTimes = run.geometry.stopIds.mapIndexed { index, stopId ->
                val distance = run.geometry.stopDistances[index]
                val secondsIntoDay = (run.scheduledTimeAtDistance(distance) - serviceDate) / 1000L
                StopTime(
                    stopId = stopId,
                    stopHeadsign = run.headsign,
                    arrivalTime = secondsIntoDay,
                    departureTime = secondsIntoDay,
                    distanceAlongTrip = distance
                )
            },
            timeZone = fixture.agency.timezone,
            // The demo system's runs stand alone rather than chaining into a block, and OBA sends an
            // empty string (not null) at a block's ends — see the wire-boundary blank→null rule (#2003).
            previousTripId = "",
            nextTripId = ""
        )
    }

    private fun tripReference(run: DemoRun) = TripReference(
        id = run.tripId,
        routeId = run.routeId,
        tripHeadsign = run.headsign,
        directionId = run.geometry.directionId,
        shapeId = shapeIdFor(run.routeId),
        timeZone = fixture.agency.timezone
    )

    // ---------------------------------------------------------------- references

    private fun references(
        stops: List<StopReference> = emptyList(),
        routes: List<RouteReference> = emptyList(),
        trips: List<TripReference> = emptyList(),
        situations: List<SituationReference> = emptyList()
    ) = References(
        agencies = listOf(fixture.agency),
        stops = stops.distinctBy { it.id },
        routes = routes.distinctBy { it.id },
        trips = trips.distinctBy { it.id },
        situations = situations
    )

    /** The reference pool for a set of runs: their routes and trips, plus any stops the caller adds. */
    private fun referencesFor(runs: List<DemoRun>, stops: List<StopReference> = emptyList()): References {
        val routeIds = runs.mapTo(mutableSetOf()) { it.routeId }
        return references(
            stops = stops,
            routes = routeIds.mapNotNull(fixture.routeById::get),
            trips = runs.map(::tripReference),
            situations = if (DemoScenario.ALERT_ROUTE_ID in routeIds) listOf(serviceAlert()) else emptyList()
        )
    }

    /**
     * The demo system's one service alert. It exists so the tour's legend step has a real corner
     * warning glyph to point at — the arrivals row draws it from a situation the route actually
     * carries, so there is nothing to fake in the UI layer.
     */
    private fun serviceAlert() = SituationReference(
        id = DemoScenario.ALERT_ID,
        summary = SituationText("Reroute: E Pine St closed at Broadway"),
        description = SituationText(
            "Buses are rerouting around a street festival on E Pine St between Broadway and " +
                "Boylston Ave through the weekend. Use the stop on Bellevue Ave instead."
        ),
        severity = "warning",
        // Open-ended: `to == 0` means the alert has no stated end, so it is active whenever it's read.
        activeWindows = listOf(SituationWindow(from = 0, to = 0)),
        allAffects = listOf(SituationAffects(routeId = DemoScenario.ALERT_ROUTE_ID))
    )

    private fun situationIdsFor(routeId: String): List<String> = if (routeId == DemoScenario.ALERT_ROUTE_ID) listOf(DemoScenario.ALERT_ID) else emptyList()

    private fun routesServing(stop: StopReference): List<RouteReference> = fixture.routeStops.filterValues { stop.id in it.stopIds }.keys.mapNotNull(fixture.routeById::get)

    /** The other demo stops within [NEARBY_STOP_METERS] — the "across the street" set. */
    private fun nearbyStopIds(stop: StopReference): List<String> = fixture.stops
        .filter { it.id != stop.id && distanceMeters(it.lat, it.lon, stop.lat, stop.lon) <= NEARBY_STOP_METERS }
        .map { it.id }

    // ---------------------------------------------------------------- helpers

    /** The demo deployment's clock — see [DemoClock] for why demo mode reads the device's. */
    private fun nowMs(): Long = DemoClock.nowMs()

    private fun <T> ok(data: T, now: Long = nowMs()) = ObaEnvelope(version = 2, code = HttpURLConnection.HTTP_OK, currentTime = now, text = "OK", data = data)

    /** The demo system's "no such thing" — the same coded envelope a real deployment answers with. */
    private fun <T> notFound() = ObaEnvelope<T>(
        version = 2,
        code = HttpURLConnection.HTTP_NOT_FOUND,
        currentTime = nowMs(),
        text = "not found",
        data = null
    )

    /** Trims a stop's calls to the caller's window; null means the server's own default horizon. */
    private fun List<DemoStopCall>.withinWindow(now: Long, minutesAfter: Int?): List<DemoStopCall> {
        val minutes = minutesAfter ?: DEFAULT_MINUTES_AFTER
        return filter { it.arrivalTimeMs <= now + minutes * 60_000L }
    }

    /**
     * Whether a point falls in the queried area. The OBA `*-for-location` endpoints accept either a
     * [radius] or a lat/lon *span* (a bounding box), so both are honoured here the way the server does,
     * with a default radius when the caller gives neither.
     */
    private fun within(
        pointLat: Double,
        pointLon: Double,
        lat: Double,
        lon: Double,
        radius: Int?,
        latSpan: Double?,
        lonSpan: Double?
    ): Boolean = if (latSpan != null && lonSpan != null) {
        abs(pointLat - lat) <= latSpan / 2 && abs(pointLon - lon) <= lonSpan / 2
    } else {
        distanceMeters(pointLat, pointLon, lat, lon) <= (radius?.toDouble() ?: DEFAULT_RADIUS_METERS)
    }

    private fun matches(stop: StopReference, query: String?): Boolean = query.isNullOrBlank() ||
        stop.name?.contains(query, ignoreCase = true) == true ||
        stop.code?.equals(query, ignoreCase = true) == true

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = org.onebusaway.android.util.haversineDistance(lat1, lon1, lat2, lon2)

    private fun isoTime(epochMs: Long): String = ISO_FORMAT.format(Instant.ofEpochMilli(epochMs))

    private fun shapeIdFor(routeId: String) = "${routeId}_demo_shape"

    private companion object {
        /** The window a stop's arrivals cover when the caller doesn't say — the API's own default. */
        const val DEFAULT_MINUTES_AFTER = 35

        /** The radius a `*-for-location` query covers when it names neither a radius nor a span. */
        const val DEFAULT_RADIUS_METERS = 1000.0

        /** How close another stop has to be to count as "nearby" (the across-the-street bay). */
        const val NEARBY_STOP_METERS = 150.0

        /** How stale the demo AVL feed reports itself as — recent enough to read as live. */
        const val DEMO_AVL_AGE_MS = 12_000L

        val ISO_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneId.systemDefault())
    }
}
