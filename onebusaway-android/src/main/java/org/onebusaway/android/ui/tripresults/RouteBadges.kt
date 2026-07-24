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

import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.routeDisplayShortName
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.util.ROUTE_NAME_ORDER
import org.onebusaway.android.util.parseObaHexColor

/**
 * Builds the [LegBadge] a transit leg draws — the planned route plus whatever else the rider may ride
 * for that leg (#2010). Both places a leg's routes appear (the itinerary option cards and the
 * directions drawer) are fed from here by [TripResultsRepository], so the two can't name or order a
 * corridor's routes differently.
 *
 * Pure (no `Context`), so `RouteBadgesTest` covers the naming, ordering and color parsing directly.
 */

/** The badge for one transit leg: its planned route joined by the routes ruled interchangeable with
 *  it ([org.onebusaway.android.directions.model.interchangeableRoutes]). */
internal fun legBadge(leg: TripLeg, alternatives: List<InterchangeableRoute>): LegBadge = legBadge(leg.plannedBadge(), alternatives.map { it.badge() })

/**
 * The leg's badge: [planned] joined by [alternatives], in natural name order. The plan's own choice
 * isn't given pride of place, deliberately — the routes are interchangeable, so "1 Line/2 Line" should
 * read the same whichever one the planner picked; which one it did pick is still on the card's header
 * line and its ETA strip.
 */
internal fun legBadge(planned: RouteBadge, alternatives: List<RouteBadge>): LegBadge = LegBadge(
    (listOf(planned) + alternatives)
        .distinctBy { it.shortName }
        .sortedWith(compareBy(ROUTE_NAME_ORDER) { it.shortName })
)

/** The transit leg's own roundel: its display short name and parsed GTFS color. */
internal fun TripLeg.plannedBadge(): RouteBadge = RouteBadge(routeDisplayShortName().orEmpty(), badgeColor(routeColor))

/** An interchangeable route's roundel, alongside [plannedBadge] in the same leg's badge. */
internal fun InterchangeableRoute.badge(): RouteBadge = RouteBadge(displayName, badgeColor(routeColor))

/** A wire route color as a badge color: OTP hands over a bare hex, but tolerate a leading '#'. */
private fun badgeColor(wireHex: String?): Int? = parseObaHexColor(wireHex?.removePrefix("#"))
