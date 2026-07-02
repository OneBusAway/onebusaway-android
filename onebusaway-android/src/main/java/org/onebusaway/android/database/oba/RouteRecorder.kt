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
package org.onebusaway.android.database.oba

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.onebusaway.android.app.di.AppScope
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.MyTextUtils

/**
 * Records a route in the recents/search table so it shows up later (the legacy `DBUtil.addRouteToDB`).
 * Fire-and-forget on the application scope so the write survives the navigation that immediately
 * follows the call; a no-op when no region is set (legacy parity). Runs after the one-time import gate.
 */
@Singleton
class RouteRecorder @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val routeDao: RouteDao,
    private val regionRepository: RegionRepository,
    private val importGate: ImportGate,
) {

    /** Records a route from an arrival row (no URL); falls back to the headsign for an empty long name. */
    fun recordFromArrival(routeId: String, shortName: String?, routeLongName: String?, headsign: String?) {
        val longName = if (routeLongName.isNullOrEmpty()) {
            MyTextUtils.formatDisplayText(headsign)
        } else {
            routeLongName
        }
        appScope.launch {
            val regionId = regionRepository.region.value?.id ?: return@launch
            importGate.awaitReady()
            routeDao.markRouteUsed(routeId, shortName, longName, regionId, System.currentTimeMillis())
        }
    }

    /** Records a route's full details incl. URL (the search-result route rows). */
    fun recordDetails(routeId: String, shortName: String?, longName: String?, url: String?) {
        appScope.launch {
            val regionId = regionRepository.region.value?.id ?: return@launch
            importGate.awaitReady()
            routeDao.storeRouteDetails(routeId, shortName, longName, url, regionId, System.currentTimeMillis())
        }
    }
}
