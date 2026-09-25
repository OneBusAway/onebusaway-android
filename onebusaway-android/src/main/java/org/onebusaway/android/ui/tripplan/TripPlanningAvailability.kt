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
package org.onebusaway.android.ui.tripplan

import android.content.Context
import android.widget.Toast
import org.onebusaway.android.R
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.directions.util.OtpTarget

/**
 * What to tell a rider who asked for a trip plan on a device that has no trip planner to ask — and, by
 * returning null, that there is one and the gesture may go ahead (#2264).
 *
 * Not every OneBusAway region publishes an OpenTripPlanner server, and which ones don't is directory
 * data that changes without this repo touching anything (CLAUDE.md carries the current list). The nav
 * drawer has always hidden its "Plan a trip" row for those regions, but the map's long-press "navigate
 * here" offer and a place shared in from another app both entered the trip planner regardless, and the
 * plan then failed with "No region selected" — while the region sat plainly selected in the drawer. So
 * the gate and its wording live here, together; affordances reach them through
 * [refuseTripPlanIfUnavailable], which is this answer plus the one way the app delivers it.
 *
 * The two "no planner" cases are kept apart because they read as completely different things: with no
 * region resolved the app really doesn't know which transit system to ask, and "No region selected" is
 * the honest answer; with one resolved, naming it is what stops the message from contradicting the
 * region the rider can see the app using.
 */
fun tripPlanningUnavailableMessage(context: Context): String? = when (OtpTarget.resolve(context).unavailable) {
    null -> null
    OtpTarget.Unavailable.NO_REGION -> context.getString(R.string.tripplanner_no_server_selected_error)
    // Read here rather than carried on [OtpTarget]: the region is a cached StateFlow value, so a second
    // read costs nothing, and the target stays a statement about servers rather than about names.
    OtpTarget.Unavailable.REGION_HAS_NO_PLANNER -> context.getString(
        R.string.trip_plan_unavailable_in_region,
        RegionEntryPoint.get(context.applicationContext).currentRegion()?.name.orEmpty()
    )
}

/**
 * Refuses a trip-planning gesture that has nowhere to go, telling the rider why: shows
 * [tripPlanningUnavailableMessage] and returns true, or does nothing and returns false when there is a
 * planner and the gesture should proceed.
 *
 * The one home for *how* the app declines — a toast, because the gesture is the rider's whole
 * interaction here and there is no surface of ours yet on screen to seat a snackbar in. Each caller
 * then reads as the single line it is, and the third affordance that needs this gate inherits the
 * decision instead of copying it.
 */
fun Context.refuseTripPlanIfUnavailable(): Boolean {
    val message = tripPlanningUnavailableMessage(this) ?: return false
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    return true
}

/**
 * The error for a plan that never left the device because there is no OTP server to send it to — the
 * same fact as [tripPlanningUnavailableMessage], classified for the directions error snackbar (which
 * renders a bare string resource, so this side can't name the region).
 *
 * Reached when a plan is submitted from a directions form that was already open — a region switch
 * underneath it, a pinned trip resumed, a notification restored — rather than from a gesture
 * [refuseTripPlanIfUnavailable] can turn away. Takes the resolved [target] rather than a nullable
 * reason so there is no "no reason" case to write an unreachable branch for.
 */
internal fun noPlannerError(target: OtpTarget): TripPlanError = TripPlanError(
    TripPlanError.Category.REQUEST,
    if (target.regionSelected) {
        R.string.tripplanner_error_region_no_planner
    } else {
        R.string.tripplanner_no_server_selected_error
    }
)
