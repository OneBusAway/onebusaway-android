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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.directions.OtpObaIdResolver
import org.onebusaway.android.directions.model.InterchangeableRoute
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.interchangeableRoutes
import org.onebusaway.android.directions.util.DirectionsGenerator
import org.onebusaway.android.map.RiddenSegment
import org.onebusaway.android.util.geoPointOrNull
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Projects [TripItinerary] objects onto the Compose results model. The turn-by-turn directions reuse
 * the legacy [DirectionsGenerator] (which needs a [Context] for resources), and the option cards carry
 * structured data (route badges / walk / duration / time range / walk distance) formatted by the UI. All
 * on the IO thread so [TripResultsViewModel] stays JVM-testable.
 */
interface TripResultsRepository {

    /** Summarizes each itinerary into an option card ([ItineraryOption]). */
    suspend fun summarize(itineraries: List<TripItinerary>): Result<List<ItineraryOption>>

    /** Builds the trip-log timeline entries for a single itinerary. */
    suspend fun directionsFor(itinerary: TripItinerary): Result<List<TripLogEntry>>
}

class DefaultTripResultsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val otpObaIdResolver: OtpObaIdResolver
) : TripResultsRepository {

    override suspend fun summarize(
        itineraries: List<TripItinerary>
    ): Result<List<ItineraryOption>> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            itineraries.map { itinerary -> summarize(itinerary) }
        }
    }

    /** Projects one [TripItinerary] into the structured [ItineraryOption] the card renders. */
    private fun summarize(itinerary: TripItinerary): ItineraryOption {
        // A badge per distinct vehicle-and-route (#2000), each naming every route that ride can be
        // taken on, so an interchangeable pair reads as one joined "1 Line/2 Line" roundel rather than
        // picking a winner (#2010).
        val badges = Interlines.routeBadges(itinerary.legs, itinerary.substitutableRoutes())
        // A transit trip shows route badges; a walk-only trip shows the walk glyph; anything else
        // (bike/car) keeps the legacy mode-label title.
        val mode = when {
            badges.isNotEmpty() -> ModeSummary.Routes(badges)
            itinerary.legs.all { it.mode == TripMode.WALK } -> ModeSummary.Walk
            else -> ModeSummary.Label(DirectionsGenerator(itinerary.legs, context).itineraryTitle)
        }
        return ItineraryOption(
            mode = mode,
            durationMinutes = itinerary.duration.inWholeMinutes,
            startTime = itinerary.startTime,
            endTime = itinerary.startTime + itinerary.duration,
            // Total walking (meters) across the trip's WALK legs; the card formats it to the user's units.
            walkDistanceMeters = itinerary.legs
                .filter { it.mode == TripMode.WALK }
                .sumOf { it.distance }
        )
    }

    override suspend fun directionsFor(
        itinerary: TripItinerary
    ): Result<List<TripLogEntry>> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            // The legacy generator supplies the localized step / intermediate-stop text (needs a Context
            // for resources); the pure builder re-shapes its flat output — plus the legs' structured
            // times/distances/colours — into the trip-log timeline (JVM-testable). Each transit leg's OTP
            // route/stop ids are resolved to OBA ids here (a suspend, network-backed step) so the drawer
            // can highlight the route and show each stop's live ETAs.
            val flat = DirectionsGenerator(itinerary.legs, context).directions
            // One RouteLegRef per transit chain (a stay-aboard interline folds its continuation legs into
            // the chain leader, #2000); the builder folds the same continuation legs into the leader's
            // Transit entry so the two agree.
            val routeLegRefs = resolveRouteLegRefs(itinerary.legs, itinerary.substitutableRoutes())
            TripLogBuilder.build(itinerary.legs, flat, routeLegRefs)
        }
    }

    /**
     * Resolves one [RouteLegRef] per transit chain ([Interlines.chains]), aligned to [legs] (non-transit
     * legs and interlined continuations are null). A stay-aboard interline (#2000) collapses into the
     * chain leader's ref: it boards at the leader's origin, alights at the *last* leg's destination, and
     * lists each cross-route change ([RouteLegRef.interlineTransitions]) so the rider is told to stay
     * aboard rather than get off and reboard. A self-interline (same route) leaves no transition, hiding
     * the seam entirely.
     *
     * [substitutable] is index-aligned to [legs] and supplies the chain leader's interchangeable routes
     * (#2010) — already empty for an interlined chain, see [TripItinerary.substitutableRoutes].
     */
    private suspend fun resolveRouteLegRefs(
        legs: List<TripLeg>,
        substitutable: List<List<InterchangeableRoute>>
    ): List<RouteLegRef?> {
        val refs = MutableList<RouteLegRef?>(legs.size) { null }
        for (chain in Interlines.chains(legs)) {
            val leader = legs[chain.leaderIndex]
            val transitions = chain.transitionLegIndices.associateWith { j ->
                InterlineTransition(
                    routeShortName = Interlines.badgeShortName(legs[j]),
                    headsign = legs[j].headsign,
                    stop = legs[j].from.resolveStop(legs[j])
                )
            }
            // The ride's legs beyond the leader — each continued onto on the same vehicle, boarding at
            // its own seam stop. The map focus loads/draws each (reusing the leader's route when the id
            // matches — a self-interline) and shows the shared vehicle across them (#2000). A leg whose
            // route can't be resolved to an OBA id is dropped (it can't be loaded), same as the leader.
            val extraSegments = ((chain.leaderIndex + 1)..chain.alightIndex).mapNotNull { j ->
                val routeId = otpObaIdResolver.obaRouteId(legs[j].routeId, legs[j].agencyId, legs[j].agencyName)
                    ?: return@mapNotNull null
                RiddenSegment(
                    routeId = routeId,
                    anchorStopId = otpObaIdResolver.obaStopId(legs[j].from.stopId, legs[j].agencyId, legs[j].agencyName)
                )
            }
            val alternatives = substitutable[chain.leaderIndex]
            refs[chain.leaderIndex] = RouteLegRef(
                routeId = otpObaIdResolver.obaRouteId(leader.routeId, leader.agencyId, leader.agencyName),
                headsign = leader.headsign,
                board = leader.from.resolveStop(leader),
                alight = legs[chain.alightIndex].to.resolveStop(legs[chain.alightIndex]),
                interlineTransitions = transitions,
                extraSegments = extraSegments,
                alternatives = alternatives.map { it.resolve() },
                // Built here, alongside the option cards' badges, so the drawer renders one rather than
                // deriving it per row (#2010).
                badge = legBadge(leader, alternatives)
            )
        }
        return refs
    }

    /** The same OTP-route-id → OBA-route-id resolution the planned route gets, for an alternative. */
    private suspend fun InterchangeableRoute.resolve(): AlternativeRouteRef {
        val badge = badge()
        return AlternativeRouteRef(
            routeId = otpObaIdResolver.obaRouteId(routeId, agencyId, agencyName),
            headsign = headsign,
            shortName = badge.shortName,
            routeColor = badge.routeColor
        )
    }

    private suspend fun TripPlace.resolveStop(leg: TripLeg) = RouteStopRef(
        stopId = otpObaIdResolver.obaStopId(stopId, leg.agencyId, leg.agencyName),
        stopCode = stopCode,
        name = name,
        point = geoPointOrNull(lat, lon)
    )
}
