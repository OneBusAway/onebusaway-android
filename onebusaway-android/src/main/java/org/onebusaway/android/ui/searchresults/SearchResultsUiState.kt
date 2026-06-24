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

/**
 * One row of the combined search results list — a matching route or stop. The screen renders
 * these into a single heterogeneous list (routes first, then stops), matching the legacy screen.
 */
sealed interface SearchResultItem {

    /**
     * @param longName secondary name (long name or description), or null when there is none
     * @param url the route's schedule page, used when registering the route in recents
     */
    data class Route(
        val id: String,
        val shortName: String,
        val longName: String?,
        val url: String?
    ) : SearchResultItem

    /** @param direction raw compass direction code ("N", "SW", ...); empty when unknown. */
    data class Stop(
        val id: String,
        val name: String,
        val direction: String,
        val isFavorite: Boolean,
        val latitude: Double,
        val longitude: Double
    ) : SearchResultItem
}
