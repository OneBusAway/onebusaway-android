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
import org.onebusaway.android.directions.model.Interlines
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.directions.model.interchangeableRoutes
import org.onebusaway.android.directions.model.routeDisplayName
import org.onebusaway.android.directions.model.substitutableRoutes
import org.onebusaway.android.directions.util.DirectionsGenerator
import org.onebusaway.android.map.RiddenSpan
import org.onebusaway.android.map.RouteFocusSegment
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.geoPointOrNull
import org.onebusaway.android.util.parseObaHexColor
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Projects [TripItinerary] objects onto the Compose results model. The turn-by-turn directions reuse
 * the legacy [DirectionsGenerator] (which needs a [Context] for resources), and the option cards carry
 * structured data (mode symbols / duration / time range / street distances) formatted by the UI. All
 * on the IO thread so [TripResultsViewModel] stays JVM-testable.
 */
interface TripResultsRepository {

    /** Summarizes each itinerary into an option card ([ItineraryOption]). */
    suspend fun summarize(itineraries: List<TripItinerary>): Result<List<ItineraryOption>>

    /**
     * Builds the trip-log timeline entries for a single itinerary. [plannedStart] is when the plan puts
     * the rider at its starting point ([TripPlanParams.plannedStart]
     * [org.onebusaway.android.ui.tripplan.TripPlanParams.plannedStart]), or null when it doesn't say.
     */
    suspend fun directionsFor(itinerary: TripItinerary, plannedStart: ServerTime?): Result<List<TripLogEntry>>
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
    private fun summarize(itinerary: TripItinerary): ItineraryOption = ItineraryOption(
        // The trip as one sequence of mode symbols — the walks between the rides included (#2047),
        // each ride badged with every route it can be taken on (#2010) and a stay-aboard interline
        // folded to the routes actually ridden (#2000).
        symbols = ModeSymbols.forLegs(itinerary.legs, itinerary.substitutableRoutes()),
        durationMinutes = itinerary.duration.inWholeMinutes,
        startTime = itinerary.startTime,
        endTime = itinerary.startTime + itinerary.duration,
        // How far the trip goes on each street mode it uses (meters), one metric line each on the card
        // (#2122); the card formats them to the user's units.
        streetDistanceMeters = itinerary.legs.streetDistancesMeters()
    )

    override suspend fun directionsFor(
        itinerary: TripItinerary,
        plannedStart: ServerTime?
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
            TripLogBuilder.build(itinerary.legs, flat, routeLegRefs, plannedStart)
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
        // Every OBA id this itinerary needs, resolved in one network round (#2170) and index-aligned to
        // `legs`, so each leg's stops can only be named on that leg's own route.
        val ids = otpObaIdResolver.resolveLegs(legs)
        for (chain in Interlines.chains(legs)) {
            val leader = legs[chain.leaderIndex]
            val transitions = chain.transitionLegIndices.associateWith { j ->
                InterlineTransition(
                    badge = legs[j].shortNameBadge(),
                    routeDisplayName = legs[j].routeDisplayName(),
                    headsign = legs[j].headsign,
                    stop = legs[j].from.toStopRef(ids[j].fromStopId)
                )
            }
            // The ride's legs beyond the leader — each continued onto on the same vehicle, boarding at
            // its own seam stop. The map focus loads/draws each (reusing the leader's route when the id
            // matches — a self-interline) and shows the shared vehicle across them (#2000). A leg whose
            // route can't be resolved to an OBA id is dropped (it can't be loaded), same as the leader.
            val extraSegments = ((chain.leaderIndex + 1)..chain.alightIndex).mapNotNull { j ->
                RouteFocusSegment(
                    routeId = ids[j].routeId ?: return@mapNotNull null,
                    anchorStopId = ids[j].fromStopId,
                    directionHeadsign = legs[j].headsign
                )
            }
            // The ride's own geometry, one span per leg it is ridden as, in travel order — the map draws the
            // drilled-into ride from these (#2127). Built here rather than by splitting the joined focus
            // geometry later because this is where both halves are known at once: the leg's shape, and the
            // OBA route id that colours it. `startsCutover` reads the same chain transitions the drawer's
            // "stay on board" rows do, so the two mark one ride's route changes identically. A leg with no
            // geometry contributes an empty span rather than being dropped — the spans stay aligned to the
            // ride's legs, and the map skips one it can't draw.
            //
            // The leg's own published colour rides along so the map can draw the span before its route loads
            // (see [riddenSpanColorSource], #2186). Deliberately the raw parse rather than
            // [ridePresentationColor]'s colourless-ride substitution: this stands in for the route's
            // *published* colour until that route can state it, and the map's own route lines have never had
            // that substitution either (#2041's remaining work), so a colourless ride resolves the same way
            // before and after the load instead of changing colour on landing.
            val riddenSpans = (chain.leaderIndex..chain.alightIndex).map { j ->
                RiddenSpan(
                    points = legs[j].legGeometry?.decodedPoints().orEmpty(),
                    routeId = ids[j].routeId,
                    plannedColor = parseObaHexColor(legs[j].routeColor),
                    startsCutover = j in chain.transitionLegIndices
                )
            }
            refs[chain.leaderIndex] = RouteLegRef(
                routeId = ids[chain.leaderIndex].routeId,
                headsign = leader.headsign,
                board = leader.from.toStopRef(ids[chain.leaderIndex].fromStopId),
                alight = legs[chain.alightIndex].to.toStopRef(ids[chain.alightIndex].toStopId),
                interlineTransitions = transitions,
                extraSegments = extraSegments,
                riddenSpans = riddenSpans,
                alternatives = substitutable[chain.leaderIndex].map { it.resolve() },
                // Alternatives cannot identify which same-named route was planned; the ETA strip needs
                // this route's own color (#2099).
                plannedBadge = leader.plannedBadge()
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

    /**
     * The stop as the drawer/map refer to it. The name/code/point stand on their own, so a stop whose
     * OBA id couldn't be resolved is still labelled and framed; it just gets no arrivals board.
     */
    private fun TripPlace.toStopRef(obaStopId: String?) = RouteStopRef(
        stopId = obaStopId,
        stopCode = stopCode,
        name = name,
        point = geoPointOrNull(lat, lon)
    )
}
