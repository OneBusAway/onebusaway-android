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
package org.onebusaway.android.ui.tripresults

import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.routeDisplayShortName
import org.onebusaway.android.util.parseObaHexColor

/**
 * Pure interline analysis over a trip's legs (#2000) — no `Context` / OBA-id resolution, so it's
 * JVM-unit-testable. OTP2 splits one continuous vehicle ride into consecutive legs, flagging every leg
 * after the first [TripLeg.interlineWithPreviousLeg] (same vehicle, passenger stays aboard). Two shapes
 * matter:
 *  - a **self-interline** — the same route reversing onto itself (identical `routeId` across the seam),
 *    e.g. an inbound 12 that becomes an outbound 12; the rider never acts, so the seam is hidden;
 *  - a **cross-route interline** — the vehicle changes to a *different* route while the rider stays
 *    seated; surfaced as a "stay on board" transition rather than a get-off/get-on pair.
 *
 * Comparing `routeId` across a seam is an exact match on the canonical GTFS route id (not a heuristic):
 * the [TripLeg.interlineWithPreviousLeg] flag is the sanctioned "same vehicle, stay aboard" signal, and
 * equal ids mean it is literally the same route.
 */
internal object Interlines {

    /**
     * One continuous vehicle ride rendered as a single directions card: board at [leaderIndex]'s
     * origin, alight at [alightIndex]'s destination. [transitionLegIndices] are the continuation legs
     * whose route differs from the leg they continue from — the cross-route changes the rider is told
     * to stay aboard through; a self-interline contributes none.
     */
    data class Chain(
        val leaderIndex: Int,
        val alightIndex: Int,
        val transitionLegIndices: List<Int>
    )

    /**
     * The transit chains in leg order. A chain leader is a transit leg that is not itself an interlined
     * continuation; it absorbs the following [TripLeg.interlineWithPreviousLeg] transit legs. Non-transit
     * legs (walk/bike/car) are not chains and don't appear here.
     */
    fun chains(legs: List<TripLeg>): List<Chain> {
        val chains = ArrayList<Chain>()
        var i = 0
        while (i < legs.size) {
            val leg = legs[i]
            if (leg.mode?.isTransit != true || leg.interlineWithPreviousLeg) {
                // Non-transit legs have no chain; an interlined continuation is absorbed by its leader
                // (or, if it has none — malformed / leading — is skipped so it never leads a chain).
                i++
                continue
            }
            var end = i
            val transitions = ArrayList<Int>()
            while (end + 1 < legs.size && legs[end + 1].interlineWithPreviousLeg && legs[end + 1].mode?.isTransit == true) {
                end++
                if (legs[end].routeId != legs[end - 1].routeId) transitions += end
            }
            chains += Chain(leaderIndex = i, alightIndex = end, transitionLegIndices = transitions)
            i = end + 1
        }
        return chains
    }

    /**
     * One [RouteBadge] per distinct vehicle-and-route the transit legs use, in order: a self-interline
     * folds to a single badge, a cross-route interline keeps both routes' badges.
     */
    fun routeBadges(legs: List<TripLeg>): List<RouteBadge> {
        val badges = ArrayList<RouteBadge>()
        legs.forEachIndexed { i, leg ->
            if (leg.mode?.isTransit != true) return@forEachIndexed
            val selfInterline = leg.interlineWithPreviousLeg && i > 0 && leg.routeId == legs[i - 1].routeId
            if (selfInterline) return@forEachIndexed
            badges += RouteBadge(
                shortName = badgeShortName(leg),
                // routeColor is a bare wire hex; tolerate a leading '#' just in case.
                routeColor = parseObaHexColor(leg.routeColor?.removePrefix("#"))
            )
        }
        return badges
    }

    /** The route's display short name, or empty — see [routeDisplayShortName]. */
    fun badgeShortName(leg: TripLeg): String = leg.routeDisplayShortName().orEmpty()
}
