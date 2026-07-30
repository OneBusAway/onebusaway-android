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

/** A comparison an itinerary option can win in the route picker. */
enum class WinnerCategory {
    SHORTEST_TRAVEL_TIME,
    LEAST_WALKING,
    EARLIEST_ARRIVAL,
    LATEST_DEPARTURE
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

    // A non-finite distance is not comparable enough to declare a winner for the whole result set.
    val walks = options.map { it.walkDistanceMeters }
    if (walks.all(Double::isFinite)) {
        award(WinnerCategory.LEAST_WALKING, walks) { it.min() }
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
