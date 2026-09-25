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
package org.onebusaway.android.ui.tripplan.pinned

import org.onebusaway.android.ui.tripplan.TripPlanParams

/**
 * Whether this pin names the same trip the form is currently showing — so the pin control reads
 * "Pinned" rather than "Pin", and so a fresh plan for it can refresh the pin's snapshot in place.
 *
 * Every field of the request is compared exactly, with **one stated exception**: when both sides were
 * asked on the moving "now" anchor, `dateTimeMillis` is not compared. That field is the moment the
 * request was *submitted*, and it is restamped on every re-plan — so comparing it would make a refresh
 * of a pinned "leave now" trip read as a different trip, un-pinning it in the rider's eyes the moment
 * they asked for it again.
 *
 * This is a rule keyed on an explicit stored flag, not an inference from the value: nothing here looks
 * at how large or how old a timestamp is and guesses what it meant.
 */
internal fun PinnedTrip.describesSameTripAs(
    current: TripPlanParams,
    currentDepartNow: Boolean
): Boolean {
    if (departNow != currentDepartNow) return false
    val comparable = if (departNow) current.copy(dateTimeMillis = params.dateTimeMillis) else current
    return params == comparable
}
