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

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import org.onebusaway.android.util.GeoPoint

/**
 * The **time-varying** half of the demo transit system (#2164): a tiny timetable simulator that turns
 * [DemoTransitFixture]'s static geometry into the moving parts a live OBA deployment would publish —
 * where each bus is right now, what it will do at every stop it hasn't reached, and how far off
 * schedule it is running.
 *
 * The whole system is generated from one rule, so it is coherent rather than a bag of canned answers:
 * each route runs buses at a fixed [DemoRouteService.headwaySeconds] at a fixed
 * [DemoRouteService.speedMetersPerSecond] along its shape, and every observable fact is derived from
 * that. A bus's position, its arrival time at any stop, and the countdown the ETA pill shows are three
 * views of the same number, so they can never disagree — which is exactly what makes a live deployment
 * hard to demo against and what the scripted tutorial needs guaranteed.
 *
 * **Everything is a pure function of `now`.** Nothing is stored and no clock is read in here (the
 * caller passes the time in, per `CLAUDE.md`'s "no `currentTimeMillis` in helpers"), so the simulation
 * is JVM-unit-testable and the tour's ETAs count down and its vehicles move for as long as the user
 * leaves it running — however long after the release that is.
 *
 * ### Run identity
 * A *run* is one bus making one trip along a route. Runs are indexed by [DemoRun.index], anchored on
 * the epoch rather than on when the tutorial started: index `k` is the run that begins its shape at
 * `k * headway + phase`. That makes a bus's identity — its trip id and its coach number — stable as
 * time passes, so the same vehicle keeps the same label while the user watches it move, and a trip id
 * the UI held onto a minute ago still resolves.
 */
object DemoScenario {

    /** Every demo route's service pattern, keyed by route id. */
    private val SERVICE: Map<String, DemoRouteService> = mapOf(
        // Route 10 — Capitol Hill via 15th Ave E.
        "1_100002" to DemoRouteService(headwaySeconds = 720, phaseSeconds = 0, speedMetersPerSecond = 4.8),
        // Route 12 — Interlaken Park via 19th Ave.
        "1_100018" to DemoRouteService(headwaySeconds = 900, phaseSeconds = 240, speedMetersPerSecond = 5.0),
        // Route 49 — U-District Station via Broadway.
        "1_100447" to DemoRouteService(headwaySeconds = 600, phaseSeconds = 420, speedMetersPerSecond = 5.2)
    )

    /**
     * The schedule deviations demo buses run at, in seconds (+late / −early), cycled by run index.
     *
     * Authored rather than random so the tour can *teach* the deviation colors: the cycle covers each
     * side of [org.onebusaway.android.util.ScheduleDeviation]'s 90-second on-time band — comfortably
     * on time, clearly late, clearly early, and slightly-off-but-still-on-time — so however the
     * sliding window of upcoming buses happens to fall when the user starts the tour, the arrivals
     * list shows the colors the legend step is about. (The fourth display state, "scheduled", isn't a
     * deviation at all: it's what a run with no prediction yet reports — see [DemoRun.hasPrediction].)
     */
    private val DEVIATION_CYCLE_SECONDS = listOf(0L, 330L, -240L, 45L)

    /** The route the demo service alert is published against — the tour's corner-glyph example. */
    const val ALERT_ROUTE_ID: String = "1_100018"

    /** The id of that alert, stable so the app's own de-duplication treats it as one alert. */
    const val ALERT_ID: String = "1_demo_alert_12"

    /**
     * How far ahead of a stop a run becomes visible in that stop's arrivals list. Runs that haven't
     * started their shape yet still appear here (as scheduled-only arrivals) — that's how a real
     * arrivals board behaves, and it's what puts a "scheduled" row in the demo list.
     */
    private const val ARRIVALS_HORIZON_SECONDS = 45 * 60L

    /**
     * How far either side of its schedule a run can be displaced, in seconds.
     *
     * Both queries below narrow the runs they build to a window of run indices before testing them, and
     * the window has to be widened by this on **both** ends — a late run is still short of somewhere its
     * schedule says it has left, and an early one has already reached somewhere its schedule says it
     * hasn't. Getting this wrong drops exactly one bus at each edge, which is invisible in the middle of
     * a headway and then isn't. The tests are the guard: every active run must be on its shape, and no
     * pending arrival may be missing from a stop's list.
     */
    private val DEVIATION_SLACK_SECONDS: Long = DEVIATION_CYCLE_SECONDS.maxOf { abs(it) }

    /**
     * Every run of [routeId] that is somewhere on its shape at [nowMs] — the buses the map draws when
     * the route is focused. Ordered by how far along they are, farthest first, so the list is stable
     * frame to frame rather than reshuffling as runs enter and leave.
     */
    fun activeRuns(fixture: DemoTransitFixture, routeId: String, nowMs: Long): List<DemoRun> {
        val service = SERVICE[routeId] ?: return emptyList()
        val geometry = fixture.routeStops[routeId] ?: return emptyList()
        // A run is on the shape from when it departs until it has covered the whole length. Bound the
        // candidates in run-index terms so only the handful that can possibly qualify are ever built,
        // then let the filter — which asks the actual question — decide.
        val travelSeconds = geometry.totalDistance / service.speedMetersPerSecond
        val newest = service.indexAt(nowMs + DEVIATION_SLACK_SECONDS * 1000L + PULLOUT_MS)
        val oldest = service.indexAt(nowMs - (travelSeconds.toLong() + DEVIATION_SLACK_SECONDS) * 1000L)
        return (oldest..newest)
            .map { run(routeId, service, geometry, it) }
            .filter { it.isOnRoad(nowMs) }
            .sortedByDescending { it.progressAlongShapeAt(nowMs) }
    }

    /**
     * Every run of every demo route that will call at [stopId] within the arrivals horizon of [nowMs],
     * paired with the stop, in ascending order of arrival. Runs that already passed the stop are gone
     * from the list — an arrivals board shows what you can still catch.
     */
    fun arrivalsAt(fixture: DemoTransitFixture, stopId: String, nowMs: Long): List<DemoStopCall> = fixture.routeStops.keys
        .flatMap { routeId -> callsAt(fixture, routeId, stopId, nowMs) }
        .sortedBy { it.arrivalTimeMs }

    /** As [arrivalsAt], but for one route — the per-route half the whole-stop query is built from. */
    private fun callsAt(
        fixture: DemoTransitFixture,
        routeId: String,
        stopId: String,
        nowMs: Long
    ): List<DemoStopCall> {
        val service = SERVICE[routeId] ?: return emptyList()
        val geometry = fixture.routeStops[routeId] ?: return emptyList()
        val stopDistance = geometry.distanceTo(stopId) ?: return emptyList()
        // A run reaches this stop `reachSeconds` after departing, so the runs that can still be pending
        // are those scheduled to depart between (now − reach) and (now + horizon − reach) — widened by
        // the deviation slack at both ends, since a late run scheduled before that window may not have
        // got here yet and an early one scheduled after it may already be due.
        val reachSeconds = (stopDistance / service.speedMetersPerSecond).toLong()
        val newest = service.indexAt(
            nowMs + (ARRIVALS_HORIZON_SECONDS - reachSeconds + DEVIATION_SLACK_SECONDS) * 1000L
        )
        val oldest = service.indexAt(nowMs - (reachSeconds + DEVIATION_SLACK_SECONDS) * 1000L)
        return (oldest..newest)
            .map { run(routeId, service, geometry, it) }
            .map { DemoStopCall(it, stopId, it.timeAtDistance(stopDistance), stopDistance) }
            .filter { it.arrivalTimeMs >= nowMs && it.arrivalTimeMs <= nowMs + ARRIVALS_HORIZON_SECONDS * 1000L }
    }

    /** The run with [tripId], or null when no demo run has that id. */
    fun runById(fixture: DemoTransitFixture, tripId: String): DemoRun? {
        val routeId = fixture.routeStops.keys.firstOrNull { tripId.startsWith(tripPrefix(it)) } ?: return null
        val index = tripId.removePrefix(tripPrefix(routeId)).toLongOrNull() ?: return null
        val service = SERVICE[routeId] ?: return null
        val geometry = fixture.routeStops[routeId] ?: return null
        return run(routeId, service, geometry, index)
    }

    /** The run a demo vehicle is on at [nowMs], or null when no demo vehicle carries that id. */
    fun runByVehicle(fixture: DemoTransitFixture, vehicleId: String, nowMs: Long): DemoRun? = fixture.routeStops.keys
        .asSequence()
        .flatMap { activeRuns(fixture, it, nowMs).asSequence() }
        .firstOrNull { it.vehicleId == vehicleId }

    /**
     * The service day [nowMs] falls in, as the epoch millis of local midnight in the agency's zone —
     * the `serviceDate` every OBA arrival and trip status carries.
     */
    fun serviceDateMs(fixture: DemoTransitFixture, nowMs: Long): Long {
        val zone = runCatching { ZoneId.of(fixture.agency.timezone.orEmpty()) }.getOrElse { ZoneId.systemDefault() }
        return LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun run(
        routeId: String,
        service: DemoRouteService,
        geometry: DemoRouteStops,
        index: Long
    ) = DemoRun(
        routeId = routeId,
        index = index,
        service = service,
        geometry = geometry,
        deviationSeconds = DEVIATION_CYCLE_SECONDS[Math.floorMod(index, DEVIATION_CYCLE_SECONDS.size)]
    )

    private fun tripPrefix(routeId: String) = "${routeId}_demo_"

    /** The trip id a run of [routeId] at [index] carries — the inverse of [runById]. */
    internal fun tripIdFor(routeId: String, index: Long) = "${tripPrefix(routeId)}$index"
}

/** How often a demo route runs, and how fast, along its single direction of travel. */
data class DemoRouteService(
    val headwaySeconds: Long,
    /** Shifts this route's departures off the others' so their arrivals interleave in a stop's list. */
    val phaseSeconds: Long,
    /**
     * Average speed along the shape *including* dwell at stops — an in-service rate, not a road speed,
     * so a run's position and its arrival times need no separate dwell model to stay consistent.
     */
    val speedMetersPerSecond: Double
) {
    /** The index of the run that departs at or most recently before [epochMs]. */
    fun indexAt(epochMs: Long): Long = floor((epochMs / 1000.0 - phaseSeconds) / headwaySeconds).toLong()

    /** When the run at [index] departs the start of the shape, on schedule. */
    fun departureMs(index: Long): Long = (index * headwaySeconds + phaseSeconds) * 1000L
}

/**
 * One bus making one trip along a demo route — the demo system's unit of "a vehicle you can watch".
 *
 * Everything about it derives from [index] and the clock: [deviationSeconds] shifts the whole run
 * later (or earlier) than its schedule, so the bus's position, the times it reaches each stop, and the
 * lateness the ETA pill colors itself by are all the same displacement expressed three ways.
 */
data class DemoRun(
    val routeId: String,
    val index: Long,
    val service: DemoRouteService,
    val geometry: DemoRouteStops,
    /** How far off schedule this run is, in seconds: positive late, negative early. */
    val deviationSeconds: Long
) {
    /** The synthetic trip id, stable for this run for as long as it exists. */
    val tripId: String get() = DemoScenario.tripIdFor(routeId, index)

    /** A plausible coach number, stable for this run — what the vehicle marker labels itself with. */
    val vehicleId: String get() = "1_7${Math.floorMod(index * 37, 900) + 100}"

    /** The direction name, which the demo system also uses as the trip headsign. */
    val headsign: String get() = geometry.name

    /** When this run departs the start of its shape *in reality* (its schedule plus its deviation). */
    val actualDepartureMs: Long get() = service.departureMs(index) + deviationSeconds * 1000L

    /** When this run is *scheduled* to reach [distance] metres along the shape. */
    fun scheduledTimeAtDistance(distance: Double): Long = service.departureMs(index) + (distance / service.speedMetersPerSecond * 1000L).toLong()

    /** When this run actually reaches (or reached) [distance] metres along the shape. */
    fun timeAtDistance(distance: Double): Long = scheduledTimeAtDistance(distance) + deviationSeconds * 1000L

    /** How far along the shape this run is at [nowMs] — negative before it starts, past the end after. */
    fun distanceAlongShapeAt(nowMs: Long): Double = (nowMs - actualDepartureMs) / 1000.0 * service.speedMetersPerSecond

    /**
     * Where along the shape this run's bus actually *is* at [nowMs] — the same thing, but held at the
     * start of the line while it waits at the terminus during its pull-out window. This is what the
     * marker, the sort order and the next-stop countdown read; the raw signed value above is for
     * deciding whether the run has departed at all.
     */
    fun progressAlongShapeAt(nowMs: Long): Double = distanceAlongShapeAt(nowMs).coerceIn(0.0, geometry.totalDistance)

    /**
     * True when this run has a bus reporting a position at [nowMs] — the one thing the map can draw a
     * marker for.
     *
     * The window opens [PULLOUT_MS] *before* the run departs, because that is when a real bus starts
     * being visible: it is sitting at the terminus with the engine running, already assigned to the
     * trip and already reporting AVL, before it pulls out. Without that, a stop early in its route only
     * ever had a drawable vehicle for arrivals a few minutes away — so the ETA pills at the demo stop
     * showed the broadcast glyph and never the map pin, and the tour had no live example of the
     * difference to point at.
     */
    fun isOnRoad(nowMs: Long): Boolean = nowMs >= actualDepartureMs - PULLOUT_MS && distanceAlongShapeAt(nowMs) <= geometry.totalDistance

    /**
     * True when this run has a real-time prediction at [nowMs], whether or not a vehicle can be drawn
     * for it yet.
     *
     * These are deliberately two different things, and the difference is what the tour's legend step
     * teaches. An arrival is *predicted* — real-time — as soon as the operator knows how the run is
     * doing; it only has a **plottable** vehicle once a bus is out on this trip reporting a position. A
     * run in the window between the two is real-time with nowhere to fly the camera, which is exactly
     * the case an ETA pill draws its broadcast glyph for rather than its map pin (see
     * `ArrivalData.hasPlottableVehicle`, #1992). Beyond that window there is no prediction at all and
     * the row falls back to schedule grey.
     *
     * So one timetable produces all three pill states as an emergent property, rather than by flagging
     * rows by hand.
     */
    fun hasPrediction(nowMs: Long): Boolean = isOnRoad(nowMs) || (actualDepartureMs - nowMs) in 0..PREDICTION_LEAD_MS

    /** Where this bus is at [nowMs], or null when it isn't on its shape. */
    fun positionAt(nowMs: Long): GeoPoint? = if (isOnRoad(nowMs)) geometry.line.interpolate(progressAlongShapeAt(nowMs)) else null

    /** Which way this bus is pointing at [nowMs], in compass degrees. */
    fun bearingAt(nowMs: Long): Float = geometry.line.bearingAt(progressAlongShapeAt(nowMs))

    /** The index into [DemoRouteStops.stopIds] of the next stop this run reaches after [nowMs]. */
    fun nextStopIndexAt(nowMs: Long): Int? {
        val distance = progressAlongShapeAt(nowMs)
        return geometry.stopDistances.indexOfFirst { it >= distance }.takeIf { it >= 0 }
    }
}

/**
 * How long before its departure a run's bus is already at the terminus reporting a position.
 *
 * Longer than the time any demo route takes to reach the tour's anchor stop, so the next arrival there
 * always has a drawable vehicle and its ETA pill can show the map pin — while arrivals further out
 * still fall into the prediction-only window below and show the broadcast glyph instead.
 */
private const val PULLOUT_MS = 8 * 60 * 1000L

/**
 * How long before a run departs its prediction becomes available — the window in which an arrival is
 * real-time but has no vehicle to draw yet.
 *
 * Comfortably longer than a demo headway, so that at any moment the anchor stop's list holds at least
 * one row in this state alongside rows that do have a vehicle. That is what lets the legend step point
 * at both pill glyphs at once.
 */
private const val PREDICTION_LEAD_MS = 18 * 60 * 1000L

/** One demo run's call at one stop: when it gets there, and how far along its shape that stop is. */
data class DemoStopCall(
    val run: DemoRun,
    val stopId: String,
    val arrivalTimeMs: Long,
    val stopDistance: Double
) {
    /** The scheduled counterpart of [arrivalTimeMs] — the two differ by the run's deviation. */
    val scheduledArrivalTimeMs: Long get() = run.scheduledTimeAtDistance(stopDistance)
}
