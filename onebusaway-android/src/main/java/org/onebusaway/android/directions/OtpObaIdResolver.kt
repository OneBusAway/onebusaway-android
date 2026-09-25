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
package org.onebusaway.android.directions

import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.onebusaway.android.api.data.AgenciesDataSource
import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.models.AgencyContact

/** The OBA ids one planned transit leg resolves to; null anywhere OBA can't name the entity. */
data class ResolvedLegIds(
    val routeId: String?,
    val fromStopId: String?,
    val toStopId: String?
)

/**
 * Resolves an OTP transit leg's GTFS ids onto the OBA ids the where-API expects, so a planned trip's
 * route/stops can drive OBA route focus and its arrivals board.
 *
 * OTP2 ids are `{feedId}:{entityId}` and agency ids `{feedId}:{obaAgencyId}`; OBA ids are
 * `{obaAgencyId}_{entityId}`. The **entity id** (route/stop number) is identical on both sides, so only
 * the agency prefix is remapped. For a **route** the OBA agency id is **derived** from the OTP agency
 * gtfsId's suffix and then **verified** against the region's agencies-with-coverage; where the derived
 * value isn't a covered agency — feeds whose GTFS `agency_id` diverges from OBA's (verified for Puget
 * Sound: Intercity is `19:0` in OTP but agency `19` in OBA) — it falls back to matching the OTP agency
 * **name** against the covered agencies. Some OTP agencies are in neither: Puget Sound's graph plans on
 * Skagit and Whatcom, which OBA does not cover at all, and those correctly resolve to null.
 *
 * Membership is all the derived suffix is checked for, not identity — a feed whose GTFS `agency_id`
 * collided with a *different* OBA agency's id would resolve silently to the wrong agency. That does not
 * happen in Puget Sound today (every derived hit's OBA name equals the OTP agency name), but nothing
 * here enforces it; the regions-directory mapping below is the real fix.
 *
 * A **stop** does not get that prefix, because a route's agency does not own the stops it calls at
 * (#2170). Verified against the live Puget Sound deployments: ST route 522 is `kcm:100232` under agency
 * `kcm:40` in OTP and `40_100232` in OBA, yet every stop it serves is a King County Metro stop —
 * `kcm:23561` in OTP is `1_23561` in OBA, and `40_23561` does not exist. A single route can even span
 * two OBA agencies' stops (KCM route `1_102558` calls at both `1_*` and `29_*` stops). So a stop is
 * resolved against the route's **actual** OBA stop list rather than by guessing its prefix — see
 * [resolveLegs].
 *
 * That makes this path network-bound where it used to be pure string work: resolving an itinerary costs
 * one stops-for-route fetch per distinct transit route, and it is the *cold* one — the drawer's route
 * focus and the route map read the same shared cache, but only after the rider taps a leg, which cannot
 * happen until this has returned. [resolveLegs] therefore fetches an itinerary's routes in one
 * concurrent round rather than one serial round trip per leg.
 *
 * This is the client-side stand-in for an authoritative OTP-agency → OBA-agency map in the regions
 * directory: when that field lands it becomes the resolution/override source in place of the
 * coverage+name matching here. Callers treat a null result as "can't reach this route in OBA" and
 * degrade (e.g. to plain leg framing) rather than issuing a request that would 404 / return null.
 */
class OtpObaIdResolver @Inject constructor(
    private val agenciesDataSource: AgenciesDataSource,
    private val stopsForRoute: StopsForRouteRepository
) {
    private val mutex = Mutex()
    private var cachedAgencies: List<AgencyContact>? = null

    /** The OBA route id for an OTP transit leg's route, or null when the agency can't be resolved. */
    suspend fun obaRouteId(routeGtfsId: String?, agencyGtfsId: String?, agencyName: String?): String? {
        val entity = gtfsEntitySuffix(routeGtfsId) ?: return null
        val agency = resolveAgency(agencyGtfsId, agencyName) ?: return null
        return "${agency}_$entity"
    }

    /**
     * The OBA ids for each of [legs], index-aligned to it — a non-transit leg, or one whose route or
     * stops can't be reached, yields nulls rather than being dropped.
     *
     * Resolving the whole itinerary at once rather than one entity at a time is what lets each route's
     * stops be fetched once, concurrently, and indexed once; it also means a leg's stop ids can only
     * ever be looked up on that leg's own route.
     */
    suspend fun resolveLegs(legs: List<TripLeg>): List<ResolvedLegIds> = coroutineScope {
        val routeIds = legs.map { obaRouteId(it.routeId, it.agencyId, it.agencyName) }
        val stopsByRoute = routeIds.filterNotNull().distinct()
            .map { routeId -> async { routeId to stopIndex(routeId) } }
            .awaitAll().toMap()
        legs.mapIndexed { i, leg ->
            val stops = routeIds[i]?.let { stopsByRoute[it] }.orEmpty()
            ResolvedLegIds(
                routeId = routeIds[i],
                fromStopId = gtfsEntitySuffix(leg.from.stopId)?.let(stops::get),
                toStopId = gtfsEntitySuffix(leg.to.stopId)?.let(stops::get)
            )
        }
    }

    /**
     * A route's stops keyed by GTFS entity id, empty when they can't be reached. The entity id is the
     * same on both sides; only the OBA agency prefix has to be found, and the one place it is *stated*
     * rather than guessed is this list. Keyed by splitting each OBA id at its **first** `_`, which is
     * OBA's own id contract (`AgencyAndId.convertFromString`, server-side — the rule is not implemented
     * in this repo), so an entity id containing underscores survives intact. A suffix that names more
     * than one of the route's stops (two agencies' stops sharing an entity id) is ambiguous and dropped
     * rather than picking one arbitrarily — the caller degrades exactly as it does for an unreachable
     * stop.
     */
    private suspend fun stopIndex(obaRouteId: String): Map<String, String> = stopsForRoute.routeStopIds(obaRouteId)
        .getOrNull()
        .orEmpty()
        .groupBy { it.substringAfter('_', missingDelimiterValue = "") }
        .mapNotNull { (suffix, ids) -> ids.singleOrNull()?.let { suffix to it } }
        .toMap()

    /**
     * The OBA agency id for an OTP agency: the gtfsId suffix when it names a covered agency, else the
     * covered agency whose name matches [agencyName]. When coverage is unavailable (offline / fetch
     * failed) it trusts the derived suffix, which is correct for the agencies whose GTFS `agency_id`
     * already equals OBA's (the common case).
     */
    private suspend fun resolveAgency(agencyGtfsId: String?, agencyName: String?): String? {
        val derived = gtfsEntitySuffix(agencyGtfsId)
        val agencies = agencies() ?: return derived
        if (derived != null && agencies.any { it.id == derived }) return derived
        return agencyName
            ?.let { name -> agencies.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            ?.id
    }

    private suspend fun agencies(): List<AgencyContact>? = mutex.withLock {
        cachedAgencies ?: agenciesDataSource.getAgencies().getOrNull()?.also { cachedAgencies = it }
    }
}
