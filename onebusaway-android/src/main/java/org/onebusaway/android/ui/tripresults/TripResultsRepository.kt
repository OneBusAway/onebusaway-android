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
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.util.DirectionsGenerator
import org.onebusaway.android.util.parseObaHexColor
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Projects [TripItinerary] objects onto the Compose results model. The turn-by-turn directions reuse
 * the legacy [DirectionsGenerator] (which needs a [Context] for resources), and the option cards carry
 * structured data (route badges / walk / duration / time range) formatted by the UI. All on the IO
 * thread so [TripResultsViewModel] stays JVM-testable.
 */
interface TripResultsRepository {

    /** Summarizes each itinerary into an option card ([ItineraryOption]). */
    suspend fun summarize(itineraries: List<TripItinerary>): Result<List<ItineraryOption>>

    /** Builds the turn-by-turn directions for a single itinerary. */
    suspend fun directionsFor(itinerary: TripItinerary): Result<List<DirectionItem>>
}

class DefaultTripResultsRepository @Inject constructor(@param:ApplicationContext private val context: Context) : TripResultsRepository {

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
                        routeColor = parseObaHexColor(leg.routeColor?.removePrefix("#")),
                    )
                }
            )
            itinerary.legs.all { it.mode == TripMode.WALK } -> ModeSummary.Walk
            else -> ModeSummary.Label(DirectionsGenerator(itinerary.legs, context).itineraryTitle)
        }
        return ItineraryOption(
            mode = mode,
            durationMinutes = itinerary.duration.inWholeMinutes,
            startTimeMs = itinerary.startTime.epochMs,
            endTimeMs = (itinerary.startTime + itinerary.duration).epochMs,
        )
    }

    /** The route's display short name (short name, else the route, else the id). */
    private fun TripLeg.badgeShortName(): String =
        listOf(routeShortName, route, routeId).firstOrNull { !it.isNullOrEmpty() }.orEmpty()

    override suspend fun directionsFor(
        itinerary: TripItinerary
    ): Result<List<DirectionItem>> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            DirectionsGenerator(itinerary.legs, context).directions.map { it.toItem() }
        }
    }

    /** Mirrors DirectionExpandableListAdapter's group-view text composition. */
    private fun Direction.toItem(): DirectionItem {
        val index = directionIndex
        return if (!isTransit) {
            DirectionItem(
                iconRes = icon,
                text = "$index. ${directionText.orEmptyString()}",
                isTransit = false,
                subItems = subItemsOf(subDirections)
            )
        } else {
            val time = (if (isRealTimeInfo && newTime != null) newTime else oldTime).orEmptyString()
            DirectionItem(
                iconRes = icon,
                text = "$index. ${service.orEmptyString()} $time",
                placeAndHeadsign = placeAndHeadsign?.toString()?.takeIf { it.isNotEmpty() },
                agency = agency?.toString()?.takeIf { it.isNotEmpty() },
                extra = extra?.toString()?.takeIf { it.isNotEmpty() },
                isTransit = true,
                subItems = subItemsOf(subDirections)
            )
        }
    }

    private fun subItemsOf(subDirections: List<Direction>?): List<DirectionItem> =
        subDirections?.map {
            DirectionItem(iconRes = it.icon, text = it.directionText.orEmptyString())
        }.orEmpty()

    private fun CharSequence?.orEmptyString(): String = this?.toString().orEmpty()
}
