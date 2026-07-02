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
 * The result of a trips-for-route (or single trip-details) fetch, narrowed to what the
 * speed-estimation/vehicle-render code consumes: the per-vehicle [trips] (each carrying an
 * [ObaTripStatus] via [ObaTripDetails.status]), the [trip]/[route] lookups those statuses point
 * into, and the server clock ([currentTimeMs]). io.client produces it (`asRouteTrips`) from the wire.
 */
interface RouteTrips {

    /** The active/served trips in this poll; each exposes its vehicle via [ObaTripDetails.status]. */
    val trips: List<ObaTripDetails>

    /** Resolves a trip from the references pool by id, or null when the id is null or absent. */
    fun trip(tripId: String?): ObaTrip?

    /** Resolves a route from the references pool by id, or null when absent. */
    fun route(routeId: String): ObaRoute?

    /** The server's response time, epoch millis (the observation's server clock). */
    val currentTimeMs: Long
}
