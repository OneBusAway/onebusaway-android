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
package org.onebusaway.android.ui.searchresults

import org.onebusaway.android.preferences.PreferencesRepository

/** Search navigation is independent of the ordering of departures on the arrivals board. */
enum class SearchResultMode(val value: String) {
    MAP("map"),
    LISTS("lists");

    companion object {
        const val PREFERENCE_KEY = "search_result_mode"
        fun fromPreference(value: String?): SearchResultMode = entries.firstOrNull { it.value == value } ?: MAP
    }
}

fun PreferencesRepository.searchResultMode(): SearchResultMode = SearchResultMode.fromPreference(getString(SearchResultMode.PREFERENCE_KEY, null))
