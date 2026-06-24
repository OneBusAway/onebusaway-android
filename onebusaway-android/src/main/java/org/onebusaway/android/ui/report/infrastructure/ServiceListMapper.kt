/*
 * Copyright (C) 2014 University of South Florida,
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

import edu.usf.cutr.open311client.models.Service

/**
 * Builds the ordered spinner rows from a (already transit-marked) list of Open311 services,
 * replacing the legacy InfrastructureIssueActivity.prepareServiceList grouping/sectioning. The
 * library `Service` is a plain JAR, so this is unit-testable without Android (the transit marking
 * itself, which needs resources, stays in the repository).
 *
 * Ordering mirrors the legacy: a hint row, the Transit section and its categories first, then the
 * remaining groups in sorted order. Services with no group fall under [othersGroupLabel].
 */
object ServiceListMapper {

    fun toItems(
        services: List<Service>,
        hintLabel: String,
        othersGroupLabel: String,
        transitGroupLabel: String
    ): List<ServiceListItem> {
        // sortedMapOf keeps the non-transit sections alphabetical, matching the legacy TreeMap.
        val grouped = sortedMapOf<String, MutableList<Service>>()
        for (service in services) {
            val group = service.group ?: othersGroupLabel
            grouped.getOrPut(group) { mutableListOf() }.add(service)
        }

        val items = mutableListOf<ServiceListItem>(ServiceListItem.Hint(hintLabel))

        grouped[transitGroupLabel]?.let { transitServices ->
            items.add(ServiceListItem.Section(transitGroupLabel))
            transitServices.forEach { items.add(it.toCategory()) }
        }

        for ((group, groupServices) in grouped) {
            if (group == transitGroupLabel) continue
            items.add(ServiceListItem.Section(group))
            groupServices.forEach { items.add(it.toCategory()) }
        }
        return items
    }

    private fun Service.toCategory() = ServiceListItem.Category(
        code = service_code,
        name = service_name,
        group = group,
        type = type,
        raw = this
    )
}
