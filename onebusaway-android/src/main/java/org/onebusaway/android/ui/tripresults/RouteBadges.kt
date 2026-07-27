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
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.routeDisplayLabel
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
internal fun legBadge(leg: TripLeg, alternatives: List<InterchangeableRoute>): LegBadge = legBadge(leg.plannedBadge(), alternatives.map { it.badge() }, leg.mode.transitMode())

/**
 * The leg's badge: [planned] joined by [alternatives], in natural name order. The plan's own choice
 * isn't given pride of place, deliberately — the routes are interchangeable, so "1 Line/2 Line" should
 * read the same whichever one the planner picked; which one it did pick is still on the card's header
 * line and its ETA strip.
 *
 * [planned] is null only for a route that names itself in no way at all; an alternative can't be, since
 * an unnameable one is never built. A leg left with no routes renders as its [mode] instead.
 */
internal fun legBadge(planned: RouteBadge?, alternatives: List<RouteBadge>, mode: TransitMode): LegBadge = LegBadge(
    (listOfNotNull(planned) + alternatives)
        .distinctBy { it.shortName }
        .sortedWith(compareBy(ROUTE_NAME_ORDER) { it.shortName }),
    mode
)

/**
 * The transit leg's own roundel: its badge name and parsed GTFS color; null when it names itself in no
 * way at all.
 *
 * Uses the label rather than the badge name, so a route publishing no short name badges its long name
 * ("Seattle - Bremerton") — the option cards cap the roundel's width and ellipsize, since a long name is
 * still a better answer to "what do I ride?" than a glyph or, as it once was, a raw GTFS id. The
 * directions pane still draws no roundel for such a route: it has room to print the long name in full as
 * the row's title, and doesn't reach for this badge to do it.
 */
internal fun TripLeg.plannedBadge(): RouteBadge? = routeDisplayLabel()?.let { RouteBadge(it, badgeColor(routeColor)) }

/** An interchangeable route's roundel, alongside [plannedBadge] in the same leg's badge. */
internal fun InterchangeableRoute.badge(): RouteBadge = RouteBadge(displayName, badgeColor(routeColor))

/** A wire route color as a badge color: OTP hands over a bare hex, but tolerate a leading '#'. */
private fun badgeColor(wireHex: String?): Int? = parseObaHexColor(wireHex?.removePrefix("#"))
