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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.onebusaway.android.api.data.AgenciesDataSource
import org.onebusaway.android.models.AgencyContact

/**
 * Resolves an OTP transit leg's GTFS ids onto the OBA ids the where-API expects, so a planned trip's
 * route/stops can drive OBA route focus and its arrivals board.
 *
 * OTP2 ids are `{feedId}:{entityId}` and agency ids `{feedId}:{obaAgencyId}`; OBA ids are
 * `{obaAgencyId}_{entityId}`. The **entity id** (route/stop number) is identical on both sides, so only
 * the agency prefix is remapped. The OBA agency id is **derived** from the OTP agency gtfsId's suffix
 * and then **verified** against the region's agencies-with-coverage; where the derived value isn't a
 * covered agency — feeds whose GTFS `agency_id` diverges from OBA's (verified for Puget Sound:
 * Intercity is `19:0` in OTP but agency `19` in OBA; Skagit uses a UUID agency id) — it falls back to
 * matching the OTP agency **name** against the covered agencies.
 *
 * This is the client-side stand-in for an authoritative OTP-agency → OBA-agency map in the regions
 * directory: when that field lands it becomes the resolution/override source in place of the
 * coverage+name matching here. Callers treat a null result as "can't reach this route in OBA" and
 * degrade (e.g. to plain leg framing) rather than issuing a request that would 404 / return null.
 */
class OtpObaIdResolver @Inject constructor(
    private val agenciesDataSource: AgenciesDataSource
) {
    private val mutex = Mutex()
    private var cachedAgencies: List<AgencyContact>? = null

    /** The OBA route id for an OTP transit leg's route, or null when the agency can't be resolved. */
    suspend fun obaRouteId(routeGtfsId: String?, agencyGtfsId: String?, agencyName: String?): String? = obaId(routeGtfsId, agencyGtfsId, agencyName)

    /**
     * The OBA stop id for a stop reached on an OTP transit leg. The stop is namespaced under the leg's
     * route agency (that's the agency serving it here), so it takes the same resolved agency prefix.
     */
    suspend fun obaStopId(stopGtfsId: String?, agencyGtfsId: String?, agencyName: String?): String? = obaId(stopGtfsId, agencyGtfsId, agencyName)

    private suspend fun obaId(entityGtfsId: String?, agencyGtfsId: String?, agencyName: String?): String? {
        val entity = gtfsEntitySuffix(entityGtfsId) ?: return null
        val agency = resolveAgency(agencyGtfsId, agencyName) ?: return null
        return "${agency}_$entity"
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
