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
package org.onebusaway.android.api.data

import org.onebusaway.android.models.ArrivalData

/**
 * Collapse block-id "phantom" duplicate arrivals.
 *
 * When a trip's real-time prediction reaches the OBA server with no assigned vehicle, the server
 * stamps the GTFS **block id** onto it as a stand-in `vehicleId` (the block-id fallback in the
 * server's `GtfsRealtimeTripLibrary`). If the real vehicle later matches the same block, one trip
 * instance briefly yields two `arrivalsAndDepartures` entries — the real coach and the schedule-only
 * phantom — so the arrivals list shows two identical-looking rows for one trip (same route, same
 * minute-floored ETA) until the phantom ages out of the server cache. See onebusaway-android#1710
 * and the server root cause onebusaway-application-modules#469.
 *
 * The phantom is identified exactly, not by guesswork: it is the entry whose `vehicleId` is the
 * trip's block id ([blockIdOf]). A real coach reports its own vehicle id, which is never the block
 * id, so this can't misfire on a genuine second vehicle that merely hasn't reported a GPS position
 * yet. When the block id can't be resolved (trip absent from the references), the trip is left
 * untouched rather than guessed at.
 *
 * For each trip instance — keyed by (tripId, serviceDate, stopSequence) so a loop route's two
 * genuine visits to one stop stay distinct — a phantom is dropped only when a non-phantom sibling
 * survives it. Order is preserved.
 */
internal fun List<ArrivalData>.collapseBlockIdPhantoms(
    blockIdOf: (tripId: String) -> String?,
): List<ArrivalData> {
    if (size < 2) return this
    fun ArrivalData.isBlockIdPhantom(): Boolean = vehicleId != null && vehicleId == blockIdOf(tripId)
    val tripInstance = { a: ArrivalData -> Triple(a.tripId, a.serviceDate, a.stopSequence) }
    val collapsible = groupBy(tripInstance)
        .filterValues { group -> group.any { it.isBlockIdPhantom() } && group.any { !it.isBlockIdPhantom() } }
        .keys
    if (collapsible.isEmpty()) return this
    return filterNot { tripInstance(it) in collapsible && it.isBlockIdPhantom() }
}
