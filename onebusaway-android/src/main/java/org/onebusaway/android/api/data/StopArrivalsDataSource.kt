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
package org.onebusaway.android.api.data

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.adapters.DtoTrip
import org.onebusaway.android.api.adapters.asArrivalData
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaWebService
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.SituationReference
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.FocusedTrip
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.ObaTrip
import org.onebusaway.android.models.boardingPoint
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.liveServerTime

/**
 * A resolved snapshot of a stop's arrivals-and-departures: the [arrivals] plus the references the
 * UI projection needs, all exposed as the `models` interfaces ([ObaStop]/[ObaRoute]/[ObaTrip]/
 * [ObaSituation]) so the arrivals feature never touches a wire DTO. The wire envelope stays private
 * to this class.
 */
class StopArrivals internal constructor(
    private val data: EntryWithReferences<ArrivalsForStop>,
    /** The server `currentTime` of this response (epoch millis). */
    val currentTime: Long,
    /** The effective minutes-after window this response was fetched with. */
    val minutesAfter: Int,
    /** Monotonic receipt of the primary response, before fetching siblings. */
    val receivedAt: ElapsedTime
) {
    /** Project from the original receipt, including sibling loading and downstream processing time. */
    fun serverNow(elapsed: ElapsedTime): ServerTime = liveServerTime(ServerTime(currentTime), receivedAt, elapsed)

    private val refs get() = data.references
    private val entry get() = data.entry

    val stopId: String get() = entry.stopId

    /** The focused stop, or null when the references don't include it. */
    val stop: ObaStop? get() = refs.stop(stopId)?.let(::DtoStop)

    /**
     * The arrivals/departures, adapted to the [ArrivalData] model (display-ready), with unrecoverable
     * corrupt timestamps dropped and duplicate trip instances collapsed (see
     * [collapseDuplicateTripInstances] / #1710 / #2012). Downstream may rely on
     * `(tripId, serviceDate, stopSequence)` being unique across this list — the ETA strip keys its
     * pills by exactly that triple.
     */
    val arrivals: List<ArrivalData>
        get() = entry.arrivalsAndDepartures.mapNotNull {
            it.asArrivalData(refs.trip(it.tripId)?.directionId?.toIntOrNull())
        }
            .collapseDuplicateTripInstances { tripId -> refs.trip(tripId)?.blockId }

    /** Only nearby IDs explicitly describing this exact boarding point are eligible for merging. */
    internal val colocatedStopIds: List<String>
        get() {
            val point = stop?.boardingPoint() ?: return emptyList()
            return entry.nearbyStopIds.distinct().filter { id ->
                id != stopId && refs.stop(id)?.let(::DtoStop)?.boardingPoint() == point
            }
        }

    internal fun withColocatedArrivals(others: List<StopArrivals>): StopArrivals {
        if (others.isEmpty()) return this
        val snapshots = listOf(this) + others
        val references = snapshots.map { it.refs }
        return StopArrivals(
            data.copy(
                entry = entry.copy(
                    arrivalsAndDepartures = snapshots.flatMap { it.entry.arrivalsAndDepartures },
                    situationIds = snapshots.flatMap { it.entry.situationIds }.distinct()
                ),
                references = References(
                    agencies = references.flatMap { it.agencies }.distinctBy { it.id },
                    stops = references.flatMap { it.stops }.distinctBy { it.id },
                    routes = references.flatMap { it.routes }.distinctBy { it.id },
                    trips = references.flatMap { it.trips }.distinctBy { it.id },
                    situations = references.flatMap { it.situations }.distinctBy { it.id }
                )
            ),
            currentTime,
            minutesAfter,
            receivedAt
        )
    }

    /** Every referenced route (for the map overlay). */
    val routes: List<ObaRoute> get() = refs.routes.map(::DtoRoute)

    /** True only when at least one wire row survives adaptation into a displayable arrival. */
    val hasArrivals: Boolean get() = arrivals.isNotEmpty()

    /** Resolves a route from the references pool by id, or null when absent. */
    fun route(id: String): ObaRoute? = refs.route(id)?.let(::DtoRoute)

    /** Resolves a route's agency name from the references pool, or null. */
    fun agencyName(id: String): String? = refs.agency(id)?.name

    /** Resolves a trip from the references pool by id (for its block id), or null. */
    fun trip(id: String): ObaTrip? = refs.trip(id)?.let(::DtoTrip)

    /** Resolves the displayed arrivals to exact trips without expanding through route membership. */
    fun focusedTrips(trips: Iterable<Pair<String, String>>): Set<FocusedTrip> = trips.mapNotNullTo(LinkedHashSet()) { (rawTripId, routeId) ->
        val tripId = rawTripId.takeIf(String::isNotBlank) ?: return@mapNotNullTo null
        val trip = trip(tripId)
        FocusedTrip(
            tripId = tripId,
            routeId = routeId,
            shapeId = trip?.shapeId?.takeIf(String::isNotBlank),
            routeColor = route(routeId)?.color,
            directionId = refs.trip(tripId)?.directionId?.toIntOrNull()
        )
    }

    /** Resolves a situation (service alert) from the references pool by id, or null. */
    fun situation(id: String): ObaSituation? = refs.situation(id)?.let(::DtoSituation)

    /**
     * All situations (service alerts) for the stop: the stop/agency-level alerts the entry references
     * directly, plus every route-specific alert referenced by an arrival. De-duplicated by id, order
     * preserved. (Ports the former `SituationUtils.getAllSituations`; see #700.)
     */
    fun situations(): List<ObaSituation> {
        val arrivalSituationIds = entry.arrivalsAndDepartures.flatMap { it.situationIds }
        // Stop-level alerts first, then the per-arrival alerts, de-duplicated by id.
        return (entry.situationIds + arrivalSituationIds)
            .distinct()
            .mapNotNull { refs.situation(it) }
            .map(::DtoSituation)
    }
}

/** Fetches a stop's arrivals-and-departures and resolves it to the [StopArrivals] model. */
interface StopArrivalsDataSource {

    /**
     * One arrivals fetch at [minutesAfter]. [Result.failure] (IO / HTTP / non-OK code via
     * [requireData]) rather than throwing; the caller owns the widen-on-empty + stale-fallback policy.
     * [colocatedStopIds] are the other feed IDs explicitly selected on the map. They are requested
     * even if nearby references are unavailable; API discovery may add further matching IDs.
     */
    suspend fun arrivals(stopId: String, minutesAfter: Int, colocatedStopIds: Set<String> = emptySet()): Result<StopArrivals>
}

class DefaultStopArrivalsDataSource @Inject constructor(
    private val api: ObaApiProvider,
    private val elapsedClock: ElapsedClock
) : StopArrivalsDataSource {

    override suspend fun arrivals(stopId: String, minutesAfter: Int, colocatedStopIds: Set<String>): Result<StopArrivals> = api.call {
        it.arrivalsAtBoardingPoint(stopId, minutesAfter, colocatedStopIds, elapsedClock)
    }.onFailure { Log.e(TAG, "arrivals($stopId) failed", it) }

    private companion object {
        const val TAG = "StopArrivalsDataSource"
    }
}

/**
 * OBA keeps separate arrivals per feed stop ID. Load every explicitly selected ID, augmented by
 * matching nearbyStopIds references for ID-only entry points such as favorites and deep links.
 * The server's StopWithArrivalsAndDeparturesBeanServiceImpl asks NearbyStopsBeanService for stops in
 * a 100m bounding box, excluding only the requested ID. Those IDs are neighbors, not equivalences;
 * [colocatedStopIds][StopArrivals.colocatedStopIds] applies the same exact match as the map.
 * Verified against the Puget Sound responses for 1_590 and 3_2479 (3rd & Pine), 2026-09-06.
 * Fetch siblings once, on this same service and window; do not recursively expand nearby stops.
 * Any failed member fails the refresh so the repository can retain a complete stale snapshot.
 */
internal suspend fun ObaWebService.arrivalsAtBoardingPoint(
    stopId: String,
    minutesAfter: Int,
    colocatedStopIds: Set<String> = emptySet(),
    elapsedClock: ElapsedClock
): StopArrivals = coroutineScope {
    suspend fun fetch(id: String): StopArrivals {
        val envelope = arrivalsAndDeparturesForStop(id, minutesAfter)
        return StopArrivals(envelope.requireData(), serverNowOrDeviceClock(envelope.currentTime), minutesAfter, elapsedClock.now())
    }
    val primary = fetch(stopId)
    // Explicit selection is authoritative even when nearby references are omitted. Discovery adds
    // siblings for ID-only entry points. Neither source can drop or duplicate a selected member.
    val siblingIds = (colocatedStopIds + primary.colocatedStopIds) - stopId
    val siblings = siblingIds.map { id -> async { fetch(id) } }.awaitAll()
    primary.withColocatedArrivals(siblings)
}

/** Presents a [SituationReference] DTO as the [ObaSituation] model interface. */
internal class DtoSituation(private val s: SituationReference) : ObaSituation {
    override val id: String get() = s.id
    override val summary: String? get() = s.summary.value
    override val description: String? get() = s.description.value
    override val url: String? get() = s.url.value
    override val severity: String? get() = s.severity
    override val advice: String? get() = null
    override val reason: String? get() = null
    override val creationTime: Long get() = 0
    override val allAffects: Array<ObaSituation.AllAffects>
        get() = s.allAffects.map { Affects(it.routeId) }.toTypedArray()
    override val consequences: Array<ObaSituation.Consequence> get() = emptyArray()
    override val activeWindows: Array<ObaSituation.ActiveWindow>
        // Normalize the polymorphic seconds-or-millis wire values to millis here so the domain model
        // is unambiguously millis downstream (see situationEpochToMillis).
        get() = s.activeWindows
            .map { Window(situationEpochToMillis(it.from), situationEpochToMillis(it.to)) }
            .toTypedArray()

    private class Affects(override val routeId: String?) : ObaSituation.AllAffects {
        override val directionId: String? get() = null
        override val stopId: String? get() = null
        override val tripId: String? get() = null
        override val applicationId: String? get() = null
        override val agencyId: String? get() = null
    }

    private class Window(override val from: Long, override val to: Long) : ObaSituation.ActiveWindow
}
