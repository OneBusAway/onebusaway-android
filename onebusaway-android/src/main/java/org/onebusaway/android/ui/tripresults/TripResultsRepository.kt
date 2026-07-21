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
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.util.DirectionsGenerator
import org.onebusaway.android.util.geoPointOrNull
import org.onebusaway.android.util.parseObaHexColor
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

    /** Builds the turn-by-turn directions for a single itinerary. */
    suspend fun directionsFor(itinerary: TripItinerary): Result<List<DirectionItem>>
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
        val transitLegs = itinerary.legs.filter { it.mode?.isTransit == true }
        // A transit trip shows route badges; a walk-only trip shows the walk glyph; anything else
        // (bike/car) keeps the legacy mode-label title.
        val mode = when {
            transitLegs.isNotEmpty() -> ModeSummary.Routes(
                transitLegs.map { leg ->
                    RouteBadge(
                        shortName = leg.badgeShortName(),
                        // routeColor is a bare wire hex; tolerate a leading '#' just in case.
                        routeColor = parseObaHexColor(leg.routeColor?.removePrefix("#"))
                    )
                }
            )
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

    /** The route's display short name (short name, else the route, else the id). */
    private fun TripLeg.badgeShortName(): String = listOf(routeShortName, route, routeId).firstOrNull { !it.isNullOrEmpty() }.orEmpty()

    override suspend fun directionsFor(
        itinerary: TripItinerary
    ): Result<List<DirectionItem>> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            // The legacy generator supplies the localized step text (needs a Context for resources);
            // the pure grouping re-shapes its flat output into one card per leg (JVM-testable). Each
            // transit leg's OTP route/stop ids are resolved to OBA ids here (a suspend, network-backed
            // step) so the drawer can highlight the route and show each stop's live ETAs.
            val flat = DirectionsGenerator(itinerary.legs, context).directions
            val routeLegRefs = itinerary.legs.map { leg ->
                if (leg.mode?.isOnStreetNonTransit == true) null else resolveRouteLeg(leg)
            }
            DirectionCardGrouping.groupByLeg(itinerary.legs, flat, routeLegRefs)
        }
    }

    /** Resolve a transit leg's OTP route/stop ids onto the OBA ids the drawer's map + ETA strips need. */
    private suspend fun resolveRouteLeg(leg: TripLeg): RouteLegRef = RouteLegRef(
        routeId = otpObaIdResolver.obaRouteId(leg.routeId, leg.agencyId, leg.agencyName),
        headsign = leg.headsign,
        board = leg.from.resolveStop(leg),
        alight = leg.to.resolveStop(leg)
    )

    private suspend fun TripPlace.resolveStop(leg: TripLeg) = RouteStopRef(
        stopId = otpObaIdResolver.obaStopId(stopId, leg.agencyId, leg.agencyName),
        stopCode = stopCode,
        name = name,
        point = geoPointOrNull(lat, lon)
    )
}
