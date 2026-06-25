/*
 * Copyright (C) 2014-2015 University of South Florida (sjbarbeau@gmail.com),
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
package org.onebusaway.android.report.ui

import android.app.Activity
import org.onebusaway.android.io.elements.ObaArrivalInfo
import org.onebusaway.android.report.ReportContext
import org.onebusaway.android.report.toTripReportContext
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * Launcher facade for the infrastructure-issue (stop/trip problem) screen (former
 * Activity). The screen is now the [NavRoutes.INFRASTRUCTURE_ISSUE] NavHost destination
 * ([InfrastructureIssueDestination]); [startWithService] encodes the stop/location context plus the
 * live [ObaArrivalInfo] (flattened to a scalar [org.onebusaway.android.report.TripReportContext]) and
 * agency/block ids into a single [ReportContext] nav-arg on the route, so the destination reads its
 * own (process-death-safe) back-stack args. Reached from the arrivals "report problem" actions (this
 * facade → HomeActivity → translator).
 */
object InfrastructureIssueLauncher {

    @JvmStatic
    @JvmOverloads
    fun startWithService(
        activity: Activity,
        serviceKeyword: String,
        stopId: String?,
        stopName: String?,
        stopCode: String?,
        latitude: Double,
        longitude: Double,
        arrivalInfo: ObaArrivalInfo? = null,
        agencyName: String? = null,
        blockId: String? = null
    ) {
        val context = ReportContext(
            stopId = stopId,
            stopName = stopName,
            stopCode = stopCode,
            lat = latitude,
            lon = longitude,
            agencyName = agencyName,
            blockId = blockId,
            trip = arrivalInfo?.toTripReportContext(),
        )
        activity.startActivity(
            HomeActivity.navIntent(
                activity, NavRoutes.infrastructureIssue(serviceKeyword, context.encode())
            )
        )
    }
}
