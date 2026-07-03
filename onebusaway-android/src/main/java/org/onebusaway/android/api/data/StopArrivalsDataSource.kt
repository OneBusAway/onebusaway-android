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

import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoTrip
import org.onebusaway.android.api.adapters.asArrivalData
import org.onebusaway.android.api.requireData

import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.contract.SituationReference

import android.util.Log
import javax.inject.Inject
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.ObaTrip

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
) {
    private val refs get() = data.references
    private val entry get() = data.entry

    val stopId: String get() = entry.stopId

    /** The focused stop, or null when the references don't include it. */
    val stop: ObaStop? get() = refs.stop(stopId)?.let(::DtoStop)

    /** The arrivals/departures, adapted to the [ArrivalData] model (display-ready). */
    val arrivals: List<ArrivalData> get() = entry.arrivalsAndDepartures.map { it.asArrivalData() }

    /** Every referenced route (for the map overlay + the route-filter options). */
    val routes: List<ObaRoute> get() = refs.routes.map(::DtoRoute)

    val hasArrivals: Boolean get() = entry.arrivalsAndDepartures.isNotEmpty()

    /** Resolves a route from the references pool by id, or null when absent. */
    fun route(id: String): ObaRoute? = refs.route(id)?.let(::DtoRoute)

    /** Resolves a route's agency name from the references pool, or null. */
    fun agencyName(id: String): String? = refs.agency(id)?.name

    /** Resolves a trip from the references pool by id (for its block id), or null. */
    fun trip(id: String): ObaTrip? = refs.trip(id)?.let(::DtoTrip)

    /** Resolves a situation (service alert) from the references pool by id, or null. */
    fun situation(id: String): ObaSituation? = refs.situation(id)?.let(::DtoSituation)

    /**
     * All situations (service alerts) specific to the stop, agency, and (filtered) routes — the
     * stop/agency-level alerts the entry references directly, plus route-specific alerts for routes
     * in [filter] (empty/null filter = all routes). De-duplicated by id, order preserved. (Ports the
     * former `SituationUtils.getAllSituations`; see #700.)
     */
    fun situations(filter: List<String>?): List<ObaSituation> {
        val filterIds = filter.orEmpty().toHashSet()
        val arrivalSituationIds = entry.arrivalsAndDepartures
            .filter { filterIds.isEmpty() || it.routeId in filterIds }
            .flatMap { it.situationIds }
        // Stop-level alerts first, then the (filtered) per-arrival alerts, de-duplicated by id.
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
     */
    suspend fun arrivals(stopId: String, minutesAfter: Int): Result<StopArrivals>
}

class DefaultStopArrivalsDataSource @Inject constructor(
    private val api: ObaApiProvider,
) : StopArrivalsDataSource {

    override suspend fun arrivals(stopId: String, minutesAfter: Int): Result<StopArrivals> = api.call {
        val envelope = it.arrivalsAndDeparturesForStop(stopId, minutesAfter)
        StopArrivals(envelope.requireData(), serverNowOrDeviceClock(envelope.currentTime), minutesAfter)
    }.onFailure { Log.e(TAG, "arrivals($stopId) failed", it) }

    private companion object {
        const val TAG = "StopArrivalsDataSource"
    }
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
