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
 * Collapse duplicate arrivals down to **one entry per trip instance**.
 *
 * A trip instance — `(tripId, serviceDate, stopSequence)` — is one GTFS `stop_time` on one service
 * day, i.e. exactly one arrival event. So more than one `arrivalsAndDepartures` entry for it is
 * always a server-side vehicle-matching artifact, never two things a rider could board. The server
 * emits one entry per vehicle it matched to the trip's block, and that matching can double up in at
 * least two observed shapes:
 *
 * - **The block-id phantom (#1710).** When a trip's real-time prediction reaches the OBA server with
 *   no assigned vehicle, the server stamps the GTFS **block id** onto it as a stand-in `vehicleId`
 *   (the block-id fallback in the server's `GtfsRealtimeTripLibrary`). If the real vehicle later
 *   matches the same block, one trip instance briefly yields two entries — the real coach and the
 *   schedule-only phantom — until the phantom ages out of the server cache. See
 *   onebusaway-android#1710 and the server root cause onebusaway-application-modules#469.
 * - **Two coach ids on one block (#2012).** Some feeds assign two ordinary vehicle ids to the same
 *   block, so *neither* entry's `vehicleId` is the block id and the block-id rule above can't see
 *   the duplicate. Live example (Everett Transit, 2026-07-24): stop `97_256`, trip `97_108567`,
 *   `stopSequence` 23, block `97_119`, vehicles `97_737` and `97_143` — identical predicted times,
 *   identical interpolated position, differing only in which coach had actually reported a fix.
 *
 * Collapsing by trip instance covers both without having to enumerate the shapes, and it is what
 * gives the ETA strip's `LazyRow` its key uniqueness *by construction*: the strip keys pills by this
 * exact triple, and a duplicate key is a fatal `IllegalArgumentException` at measure time (#2012).
 *
 * Which duplicate survives is decided by an explicit preference order, not by guesswork:
 *
 * 1. **Not the block-id phantom** ([blockIdOf]) — the #1710 rule, unchanged and exact: an entry whose
 *    `vehicleId` is the trip's own block id is the server's schedule-only stand-in. When the block id
 *    can't be resolved (trip absent from the references) no entry is treated as a phantom.
 * 2. **Backed by a real AVL fix** — an entry carrying a last-known location is the one whose coach
 *    genuinely reported, so its vehicle id and position are the ones worth attaching to a problem
 *    report. Both duplicates typically carry the same *interpolated* position, so the last-known
 *    location (not the position) is what distinguishes them. Assumption, stated plainly: when two
 *    entries describe one arrival event, the located one is the better representative. If that's ever
 *    wrong the failure is cosmetic and bounded — the pill's ETA, color, and status come from the
 *    shared scheduled/predicted times, which are identical between duplicates; only the reported
 *    vehicle id and position differ.
 * 3. **Feed order** — first entry wins, so the result is deterministic whatever order the server used.
 *
 * Order is preserved, and a list with no duplicate trip instance is returned untouched.
 */
internal fun List<ArrivalData>.collapseDuplicateTripInstances(
    blockIdOf: (tripId: String) -> String?
): List<ArrivalData> {
    if (size < 2) return this
    val tripInstance = { a: ArrivalData -> Triple(a.tripId, a.serviceDate, a.stopSequence) }
    val byInstance = withIndex().groupBy { (_, arrival) -> tripInstance(arrival) }
    // Every entry is already its own instance — the overwhelmingly common case, nothing to collapse.
    if (byInstance.size == size) return this

    fun ArrivalData.isBlockIdPhantom(): Boolean = vehicleId != null && vehicleId == blockIdOf(tripId)
    fun ArrivalData.hasAvlFix(): Boolean = lastKnownLat != null && lastKnownLon != null
    // Lowest wins, so each key is phrased "false sorts first": not-a-phantom before phantom, located
    // before unlocated, then the earliest feed position.
    val preference = compareBy<IndexedValue<ArrivalData>>(
        { it.value.isBlockIdPhantom() },
        { !it.value.hasAvlFix() },
        { it.index }
    )
    val kept = byInstance.values.mapTo(HashSet()) { group -> group.minWith(preference).index }
    return filterIndexed { index, _ -> index in kept }
}
