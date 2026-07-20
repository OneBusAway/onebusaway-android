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
package org.onebusaway.android.models

/**
 * A trip's route identity + shape, resolved from the standalone `trip/{id}.json` endpoint — used for
 * a trip the current route's trips-for-route poll never fetched, e.g. the interlining continuation's
 * neighbor trip (#1691), which is on a *different* route than anything trips-for-route returns.
 */
data class TripRouteInfo(
    val tripId: String,
    val routeId: String,
    val shapeId: String?,
    val routeShortName: String?,
    val routeColor: Int?,
    // The trip's GTFS direction, so a continuation can navigate straight to the right direction instead
    // of the route's default one. Null (rather than defaulting to 0, unlike ObaTrip.directionId's
    // missing-value convention) when the wire response carries no direction_id — direction_id is
    // optional per GTFS, and 0 is itself a valid direction, so collapsing "unknown" into it would send a
    // rider to a specific, wrong-but-plausible direction instead of the route's default.
    val directionId: Int? = null
)
