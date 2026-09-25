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
package org.onebusaway.android.ui.arrivals

import org.onebusaway.android.preferences.PreferencesRepository

/** Persist stable values, independently of translated labels and the retired A/B preference. */
enum class ArrivalDisplayMode(val value: String) {
    TIME("time"),
    ROUTE("route");

    companion object {
        const val PREFERENCE_KEY = "arrival_display_default"
        fun fromPreference(value: String?): ArrivalDisplayMode = entries.firstOrNull { it.value == value } ?: ROUTE
    }
}

fun PreferencesRepository.arrivalDisplayDefault(): ArrivalDisplayMode = ArrivalDisplayMode.fromPreference(getString(ArrivalDisplayMode.PREFERENCE_KEY, null))

/** Stop is part of identity here because the nearby drawer contains several stops. */
fun ArrivalInfo.arrivalRowKey(): String = "$routeId\u0000$tripId\u0000$serviceDate\u0000$stopId\u0000$stopSequence"

/** Exact expected instants break ties between arrivals displayed in the same minute. */
fun chronologicalArrivals(arrivals: List<ArrivalInfo>): List<ArrivalInfo> = arrivals.sortedBy { it.displayTime }
