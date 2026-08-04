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
import org.onebusaway.android.models.AgencyContact

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
 * [obaStopId].
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
     * The OBA stop id for a stop called at on an OTP transit leg travelling [obaRouteId], or null when
     * the route's stops can't be reached or none of them is this stop.
     *
     * The GTFS entity id is the same on both sides; only the OBA agency prefix has to be found, and the
     * one place it is *stated* rather than guessed is the route's own OBA stop list. So this looks the
     * entity up there, using OBA's id contract (`{agencyId}_{entityId}`, split at the first `_` — see
     * `AgencyAndId.convertFromString`) to compare. It goes through the shared, cached
     * [StopsForRouteRepository], which the drawer's route focus and the route map already fetch for the
     * very same routes, so a planned leg normally resolves off a cache hit.
     */
    suspend fun obaStopId(stopGtfsId: String?, obaRouteId: String?): String? {
        val entity = gtfsEntitySuffix(stopGtfsId) ?: return null
        val routeId = obaRouteId ?: return null
        val stopIds = stopsForRoute.routeStopIds(routeId).getOrNull() ?: return null
        return stopIds.firstOrNull { it.substringAfter('_', missingDelimiterValue = "") == entity }
    }

    /**
     * Warms the route stop lists [obaStopId] resolves against, so an itinerary's legs pay for one
     * round of concurrent fetches rather than one serial fetch each while the drawer shows Loading.
     */
    suspend fun prefetchRouteStops(obaRouteIds: Collection<String>): Unit = coroutineScope {
        obaRouteIds.distinct().map { async { stopsForRoute.routeStopIds(it) } }.awaitAll()
    }

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
