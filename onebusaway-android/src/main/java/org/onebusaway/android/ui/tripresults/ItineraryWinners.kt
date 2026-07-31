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

import androidx.annotation.StringRes
import org.onebusaway.android.R

/**
 * A comparison an itinerary option can win in the route picker, and what that win is called aloud
 * ([labelRes] — the card announces its wins as a `stateDescription`).
 *
 * Every street mode a card can draw a distance for has its own "least" category, so the emphasis is on
 * whatever the trip actually costs the rider: a bikeshare plan's cards compete on how far they make you
 * ride, exactly as a walking plan's compete on how far they make you walk (#2122). [StreetMode.CAR] has
 * none, for the same reason it has no metric line at all — see [streetDistanceCategory].
 *
 * The declaration order is the order a card reads its wins in, so keep related categories together.
 */
enum class WinnerCategory(@StringRes val labelRes: Int) {
    SHORTEST_TRAVEL_TIME(R.string.trip_plan_winner_shortest_travel_time),
    LEAST_WALKING(R.string.trip_plan_winner_least_walking),
    LEAST_BIKING(R.string.trip_plan_winner_least_biking),
    LEAST_BIKESHARING(R.string.trip_plan_winner_least_bikesharing),
    EARLIEST_ARRIVAL(R.string.trip_plan_winner_earliest_arrival),
    LATEST_DEPARTURE(R.string.trip_plan_winner_latest_departure)
}

/**
 * Which category a street mode's distance is compared in — null for a mode the cards draw no distance
 * line for ([StreetMode.CAR]; see `streetMetricGlyph`), since a win with nothing to outline would be
 * announced and never seen.
 */
fun StreetMode.streetDistanceCategory(): WinnerCategory? = when (this) {
    StreetMode.WALK -> WinnerCategory.LEAST_WALKING
    StreetMode.BIKE -> WinnerCategory.LEAST_BIKING
    StreetMode.BIKESHARE -> WinnerCategory.LEAST_BIKESHARING
    StreetMode.CAR -> null
}

/** Which clock-time comparison is useful for the request that produced the options. */
enum class ScheduleWinnerMode {
    EARLIEST_ARRIVAL,
    LATEST_DEPARTURE,
    BOTH
}

/**
 * Finds the best option(s) in each useful category. Results are index-aligned with [options]. A
 * category that cannot distinguish the choices is deliberately omitted: calling every card a winner
 * communicates nothing, as does decorating the only card in a one-option result.
 *
 * Duration and clock times use the precision printed on the card. That prevents two equal-looking
 * values from receiving different emphasis because their hidden seconds differ.
 */
fun itineraryWinnerCategories(
    options: List<ItineraryOption>,
    scheduleMode: ScheduleWinnerMode
): List<Set<WinnerCategory>> {
    val winners = List(options.size) { mutableSetOf<WinnerCategory>() }
    if (options.size < 2) return winners

    fun <T : Comparable<T>> award(
        category: WinnerCategory,
        values: List<T>,
        best: (List<T>) -> T
    ) {
        if (values.distinct().size < 2) return
        val winningValue = best(values)
        values.forEachIndexed { index, value ->
            if (value == winningValue) winners[index].add(category)
        }
    }

    award(WinnerCategory.SHORTEST_TRAVEL_TIME, options.map { it.durationMinutes }) { it.min() }

    // One "least" per street mode the cards measure, rather than walking alone: whichever modes a plan
    // is made of, the rider can see which option asks least of them on each.
    //
    // A mode an option never uses counts as **zero** of it, which is the best value there is — "this one
    // needs no bikeshare at all" is a real answer to how much bikesharing it costs, and it is how a
    // trip that never walks has always won LEAST_WALKING. The card draws the winning line even at zero
    // (see `streetMetrics`), so the outline always has a value under it.
    for (mode in StreetMode.entries) {
        val category = mode.streetDistanceCategory() ?: continue
        val distances = options.map { it.streetDistanceMeters[mode] ?: 0.0 }
        // A non-finite distance is not comparable enough to declare a winner for the whole result set.
        if (distances.all(Double::isFinite)) {
            award(category, distances) { it.min() }
        }
    }

    if (scheduleMode == ScheduleWinnerMode.EARLIEST_ARRIVAL || scheduleMode == ScheduleWinnerMode.BOTH) {
        award(
            WinnerCategory.EARLIEST_ARRIVAL,
            options.map { Math.floorDiv(it.endTime.epochMs, MILLIS_PER_MINUTE) }
        ) { it.min() }
    }
    if (scheduleMode == ScheduleWinnerMode.LATEST_DEPARTURE || scheduleMode == ScheduleWinnerMode.BOTH) {
        award(
            WinnerCategory.LATEST_DEPARTURE,
            options.map { Math.floorDiv(it.startTime.epochMs, MILLIS_PER_MINUTE) }
        ) { it.max() }
    }

    return winners
}

private const val MILLIS_PER_MINUTE = 60_000L
