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
package org.onebusaway.android.api.adapters

import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.models.AgencyDetails
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.RouteDetails
import org.onebusaway.android.util.parseObaHexColor

/** Presents a [RouteReference] as an [ObaRoute]. */
internal class DtoRoute(private val ref: RouteReference) : ObaRoute {
    override val id: String get() = ref.id
    override val shortName: String? get() = ref.shortName
    override val longName: String? get() = ref.longName
    override val description: String? get() = ref.description
    override val type: Int get() = ref.type
    override val url: String? get() = ref.url
    override val color: Int? get() = ref.colorArgb()
    override val textColor: Int? get() = ref.textColorArgb()
    override val agencyId: String get() = ref.agencyId
}

/**
 * Reads [RouteReference.color] / [RouteReference.textColor] as Android ARGB ints (or null when
 * absent/malformed) via the shared [parseObaHexColor] parser, so color consumers don't re-implement
 * `Color.parseColor`.
 */
fun RouteReference.colorArgb(): Int? = parseObaHexColor(color)

/** Parses [RouteReference.textColor] to an Android ARGB int, or null when absent/invalid. */
fun RouteReference.textColorArgb(): Int? = parseObaHexColor(textColor)

/**
 * Maps the route-details payload to the [RouteDetails] model, resolving the agency reference by id.
 * Pure (no Android/IO dependencies) so it is exercised directly in JVM unit tests.
 */
fun EntryWithReferences<RouteReference>.toRouteDetails(): RouteDetails {
    val route = entry
    val agency = references.agency(route.agencyId)
    return RouteDetails(
        id = route.id,
        shortName = route.shortName,
        longName = route.longName,
        description = route.description,
        url = route.url,
        agency = agency?.let { AgencyDetails(it.id, it.name, it.url) },
    )
}
