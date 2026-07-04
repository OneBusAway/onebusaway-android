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
package org.onebusaway.android.models

/**
 * A route's details, decoupled from the wire DTOs. Built in api (`toRouteDetails`) from the
 * route-details response; [agency] is resolved from the response references.
 */
data class RouteDetails(
    val id: String,
    val shortName: String?,
    val longName: String?,
    val description: String?,
    val url: String?,
    val agency: AgencyDetails?,
)

/** The agency operating a route. */
data class AgencyDetails(
    val id: String,
    val name: String,
    val url: String?,
)
