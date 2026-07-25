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
package org.onebusaway.android.ui.report.infrastructure

import android.app.Activity
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.report.ReportContext
import org.onebusaway.android.ui.report.TripReportContext

/**
 * Launcher facade for the infrastructure-issue (stop/trip problem) screen (former
 * Activity). The screen is now the [NavRoutes.INFRASTRUCTURE_ISSUE] NavHost destination
 * ([InfrastructureIssueDestination]); [startWithService] encodes the stop/location context plus the
 * scalar [TripReportContext] and agency/block ids into a single [ReportContext] nav-arg on the
 * route, so the destination reads its own (process-death-safe) back-stack args. Reached from the
 * arrivals "report problem" actions (this facade → HomeActivity → translator).
 */
object InfrastructureIssueLauncher {

    /**
     * @param issueType which problem the report was started for; rides the route as its enum name so
     *   the destination's ViewModel can resolve it from nav-args without touching resources.
     */
    // @JvmStatic to match the other launcher facades, which legacy Java callers still reach. No
    // @JvmOverloads: the trailing defaults are only used from Kotlin, and generating Java overloads
    // of a method taking a Kotlin enum earns nothing.
    @JvmStatic
    fun startWithService(
        activity: Activity,
        issueType: DefaultIssueType,
        stopId: String?,
        stopName: String?,
        stopCode: String?,
        latitude: Double,
        longitude: Double,
        trip: TripReportContext? = null,
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
            trip = trip
        )
        activity.startActivity(
            HomeActivity.navIntent(
                activity,
                NavRoutes.infrastructureIssue(issueType.name, context.encode())
            )
        )
    }
}
