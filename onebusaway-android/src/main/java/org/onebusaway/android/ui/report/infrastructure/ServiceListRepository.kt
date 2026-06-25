/*
 * Copyright (C) 2014 University of South Florida,
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
package org.onebusaway.android.ui.report.infrastructure

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import edu.usf.cutr.open311client.Open311
import edu.usf.cutr.open311client.Open311Manager
import edu.usf.cutr.open311client.models.Service
import edu.usf.cutr.open311client.models.ServiceListRequest
import org.onebusaway.android.R
import org.onebusaway.android.report.constants.ReportConstants
import org.onebusaway.android.report.ui.util.ServiceUtils

/** Loads the Open311 issue categories available at a location. */
interface ServiceListRepository {

    suspend fun loadServices(latitude: Double, longitude: Double): Result<ServiceListResult>
}

/**
 * Default implementation quarantining the Open311 client library. Ports ServiceListTask (probe
 * each endpoint until one manages this area) plus the filtering/grouping half of the legacy
 * prepareServiceList; the transit-marking heuristic stays in [ServiceUtils.markTransitServices]
 * (it needs resources) and the pure sectioning is delegated to [ServiceListMapper].
 */
class DefaultServiceListRepository(private val context: Context) : ServiceListRepository {

    override suspend fun loadServices(
        latitude: Double,
        longitude: Double
    ): Result<ServiceListResult> = withContext(Dispatchers.IO) {
        val endpoints = Open311Manager.getAllOpen311()
        var chosen: Open311? = null
        var services: List<Service> = emptyList()

        // Probe endpoints in order; stop at the first that manages this area, else the last.
        for ((index, open311) in endpoints.withIndex()) {
            chosen = open311
            val request = ServiceListRequest(latitude, longitude)
            request.setJurisdictionId(open311.jurisdiction)
            val response = open311.getServiceList(request)
            val list = response?.serviceList
            val managed = response?.isSuccess == true && Open311Manager.isAreaManagedByOpen311(list)
            if (index == endpoints.lastIndex || managed) {
                services = if (managed) list.orEmpty() else emptyList()
                break
            }
        }

        // Keep only fully-specified Open311 services (the built-in stop/trip ones are added below).
        val serviceList = services
            .filter { it.service_name != null && it.service_code != null }
            .toMutableList()
        val areaManaged = Open311Manager.isAreaManagedByOpen311(serviceList)

        // Marks transit categories in place and may append built-in stop/trip categories.
        val allTransitHeuristicMatch = ServiceUtils.markTransitServices(context, serviceList)

        val items = ServiceListMapper.toItems(
            services = serviceList,
            hintLabel = context.getString(R.string.ri_service_default),
            othersGroupLabel = context.getString(R.string.ri_others),
            transitGroupLabel = ReportConstants.ISSUE_GROUP_TRANSIT
        )

        Result.success(ServiceListResult(items, chosen, areaManaged, allTransitHeuristicMatch))
    }
}
