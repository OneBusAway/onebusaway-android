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
import org.onebusaway.android.directions.model.Interlines
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.directions.model.routeDisplayShortName
import org.onebusaway.android.map.riddenRouteHue
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeJoin
import org.onebusaway.android.util.inInterchangeableOrder
import org.onebusaway.android.util.parseObaHexColor

/**
 * Builds the [LegBadge] a ride draws — the planned route, plus whatever else the rider may ride for that
 * leg (#2010) or whatever the vehicle goes on to become under them (#2000/#2049). Both places a ride's
 * routes appear (the itinerary option cards and the directions drawer) are fed from here by
 * [TripResultsRepository], so the two can't name, order, or join a ride's routes differently.
 *
 * Pure (no `Context`), so `RouteBadgesTest` covers the naming, ordering and color parsing directly.
 */

/**
 * The badge for one **ride** — a whole [Interlines.Chain] rather than a leg, because a stay-aboard
 * interline is one ride the rider is never asked to act on. Which of the two joins it takes is decided
 * here, once, for both places a ride is badged:
 *  - a ride the vehicle changes route during badges those routes in travel order, joined by chevrons
 *    (`5 > 12`, #2049) — the seam is a fact about the ride, so a badge standing for the *whole* ride has
 *    to carry it, or the picker promises a 5 all the way and the drawer's own "stay on board" row
 *    contradicts it. (The drawer itself no longer draws this form: it has a row per segment, so it
 *    badges each segment where the rider reaches it — #2071. It still builds it, for the picker.)
 *  - any other ride badges its planned route joined by whatever is interchangeable with it (#2010).
 *
 * The two can't both apply, and this says so rather than trusting it: [substitutableRoutes] empties the
 * alternatives of every leg in a chain longer than one leg, since a substitute vehicle would put the
 * rider off at the seam. Stated as a `require` because the alternative is a silent drop — the chevron
 * branch has no way to render an alternative, so a caller passing raw [interchangeableRoutes] instead
 * would lose them with nothing downstream able to tell that from a ride that genuinely had none. Both
 * call sites are wrapped in `runCatchingCancellable`, so a violation surfaces as a failed load, loudly,
 * rather than as a badge quietly missing a route.
 */
internal fun rideBadge(
    legs: List<TripLeg>,
    chain: Interlines.Chain,
    alternatives: List<InterchangeableRoute>
): LegBadge = if (chain.transitionLegIndices.isEmpty()) {
    legBadge(legs[chain.leaderIndex], alternatives)
} else {
    require(alternatives.isEmpty()) {
        "a ride that changes route mid-vehicle admits no substitutes (leg ${chain.leaderIndex}, ${alternatives.size} offered)"
    }
    LegBadge(
        // In travel order and *not* deduplicated: consecutive entries are legs OTP gave different route
        // ids, so two that read alike are two routes that publish the same name — a real change the
        // drawer's transition row announces too, and hiding it here would leave the two disagreeing.
        // A leg whose route names itself in no way at all (a null badge) drops out rather than leaving a
        // blank segment; the ride still names every route that can be named.
        routes = chain.riddenLegIndices.mapNotNull { legs[it].plannedBadge() },
        mode = legs[chain.leaderIndex].mode.transitMode(),
        join = RouteBadgeJoin.THEN
    )
}

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
    (listOfNotNull(planned) + alternatives).inInterchangeableOrder { it.shortName },
    mode,
    RouteBadgeJoin.ANY_OF
)

/**
 * The transit leg's roundel as the **directions drawer** draws one: its short name and parsed GTFS
 * color, and *no* badge at all for a route publishing no short name.
 *
 * The narrower rule the drawer has always applied to a board row's roundel ([TripLogEntry.Transit
 * .routeShortName]), lifted out so a seam row can badge the route continued onto by the same rule
 * (#2071) — the two rows name a route the same way or the ride reads as two different kinds of thing.
 * The wider [plannedBadge] rule (long name in the roundel) belongs to the option cards, which have no
 * room to print a long name any other way.
 */
internal fun TripLeg.shortNameBadge(): RouteBadge? = routeDisplayShortName()?.let { RouteBadge(it, badgeColor(routeColor)) }

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

/**
 * A wire route color as a badge color: OTP hands over a bare hex, but tolerate a leading '#'.
 *
 * A route publishing nothing usable takes the colourless-ride hue rather than leaving the badge to fall
 * back on its own, because the map draws that ride's line in exactly that hue ([riddenRouteHue]) — and a
 * badge picking the neutral theme chip instead is how a WSF ferry came to sit as a grey roundel beside its
 * own coral line. The hue, not a rendered colour: the chip still re-tones it for the active theme, which is
 * the one thing this pure, `Context`-free layer can't do.
 */
private fun badgeColor(wireHex: String?): Int = riddenRouteHue(parseObaHexColor(wireHex?.removePrefix("#")))
