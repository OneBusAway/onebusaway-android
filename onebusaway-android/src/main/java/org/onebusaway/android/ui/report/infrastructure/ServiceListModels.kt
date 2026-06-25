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
package org.onebusaway.android.ui.report.infrastructure

/**
 * JVM-pure models for the infrastructure-issue service spinner, replacing the legacy
 * SpinnerItem / ServiceSpinnerItem / SectionItem adapter types. The Open311 client library
 * (edu.usf.cutr.*) stays quarantined in the repository: a category's underlying library
 * `Service` rides along as an opaque [Category.raw] that the host casts back when launching the
 * Open311 form, so the ViewModel never imports the library.
 */
sealed interface ServiceListItem {

    /** The unselected "Choose a Problem" row. */
    data class Hint(val label: String) : ServiceListItem

    /** A non-selectable group header (e.g. "Transit"). */
    data class Section(val title: String) : ServiceListItem

    /**
     * A selectable issue category.
     *
     * @param code the Open311 service_code (null for the built-in stop/trip categories)
     * @param type "stop", "trip", "dynamic_stop", "dynamic_trip", or an Open311 type
     * @param raw the opaque library `Service`, passed back to the repository/host on selection
     */
    data class Category(
        val code: String?,
        val name: String,
        val group: String?,
        val type: String?,
        val raw: Any
    ) : ServiceListItem
}

/**
 * Result of loading the issue categories for a location.
 *
 * @param items the spinner rows (hint, sections, categories) in display order
 * @param open311 the chosen library `Open311` endpoint, opaque to the ViewModel
 * @param areaManagedByOpen311 whether the location is covered by an Open311 endpoint
 * @param allTransitHeuristicMatch true when the transit categories were matched only heuristically
 */
data class ServiceListResult(
    val items: List<ServiceListItem>,
    val open311: Any?,
    val areaManagedByOpen311: Boolean,
    val allTransitHeuristicMatch: Boolean
)
