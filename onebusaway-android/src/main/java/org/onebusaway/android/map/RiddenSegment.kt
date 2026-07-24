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

/**
 * One leg of a stay-aboard interline ride, beyond the first (#2000): the passenger stays on one vehicle
 * as it runs consecutive trips in a block, so the ride crosses [routeId]/direction seams the rider never
 * acts on. Each extra segment names the route it continues onto and the stop it boards there ([anchorStopId],
 * the seam) — enough for the route focus to load that route (or reuse the leader's when [routeId] matches),
 * resolve the ridden direction from the anchor, and draw that segment's shape + stops + shared vehicle.
 *
 * A **self-interline** (12→12) yields extra segments whose [routeId] equals the leader's — a second
 * *direction* of one route; a **cross-route interline** (45→75) yields a different [routeId] — a second
 * route to load. Both are handled uniformly by the leader-plus-extra-segments model in [RouteMapController].
 */
data class RiddenSegment(
    val routeId: String,
    val anchorStopId: String?
)
