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
package org.onebusaway.android.map

/** An additional route/direction drawn with a directions leg's primary route. [relationship] says why
 * it belongs: the same vehicle continues onto it, or the rider may board it interchangeably. */
data class RouteFocusSegment(
    val routeId: String,
    val anchorStopId: String?,
    val relationship: RouteFocusRelationship = RouteFocusRelationship.STAY_ABOARD,
    /** OTP's trip headsign, used when [anchorStopId] belongs to several direction groups. */
    val directionHeadsign: String? = null
)

enum class RouteFocusRelationship {
    /** One vehicle continues across this route/direction seam (#2000). */
    STAY_ABOARD,

    /** This is another route the rider may board for the same leg (#2042). */
    INTERCHANGEABLE
}
