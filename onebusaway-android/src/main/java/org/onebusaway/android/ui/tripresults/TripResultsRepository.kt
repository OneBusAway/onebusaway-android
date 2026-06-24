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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.directions.model.Direction
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.directions.util.DirectionsGenerator
import org.onebusaway.android.directions.util.OTPConstants
import org.opentripplanner.api.model.Itinerary

/**
 * Projects OpenTripPlanner [Itinerary] objects onto the Compose results model. Reuses the legacy
 * [DirectionsGenerator]/[ConversionUtils] (which need a [Context] for resources/formatting) and the
 * card interval formatting ported from the old TripResultsFragment, all on the IO thread so
 * [TripResultsViewModel] stays JVM-testable.
 */
interface TripResultsRepository {

    /** Summarizes each itinerary into an option card (title, duration, time interval). */
    suspend fun summarize(itineraries: List<Itinerary>): Result<List<ItineraryOption>>

    /** Builds the turn-by-turn directions for a single itinerary. */
    suspend fun directionsFor(itinerary: Itinerary): Result<List<DirectionItem>>
}

class DefaultTripResultsRepository @Inject constructor(@ApplicationContext private val context: Context) : TripResultsRepository {

    override suspend fun summarize(
        itineraries: List<Itinerary>
    ): Result<List<ItineraryOption>> = withContext(Dispatchers.IO) {
        runCatching {
            itineraries.map { itinerary ->
                val durationSec = itinerary.duration.toLong()
                ItineraryOption(
                    title = DirectionsGenerator(itinerary.legs, context).itineraryTitle,
                    durationText = ConversionUtils
                        .getFormattedDurationTextNoSeconds(durationSec, false, context),
                    intervalText = formatTimeString(itinerary.startTime.toString(), durationSec * 1000)
                )
            }
        }
    }

    override suspend fun directionsFor(
        itinerary: Itinerary
    ): Result<List<DirectionItem>> = withContext(Dispatchers.IO) {
        runCatching {
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

    // Ported verbatim from the legacy TripResultsFragment (e.g. "3:45p - 4:30p").
    private fun formatTimeString(startMs: String, durationMs: Long): String {
        val start = startMs.toLong()
        return "${toDateFmt(start)} - ${toDateFmt(start + durationMs)}"
    }

    private fun toDateFmt(ms: Long): String {
        val s = SimpleDateFormat(OTPConstants.TRIP_RESULTS_TIME_STRING_FORMAT_SUMMARY, Locale.getDefault())
            .format(Date(ms))
        return s.substring(0, 6).lowercase()
    }
}
