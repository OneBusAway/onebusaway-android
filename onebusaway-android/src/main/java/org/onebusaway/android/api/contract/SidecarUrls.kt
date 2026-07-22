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
package org.onebusaway.android.api.contract

/**
 * Assembles a region-scoped sidecar endpoint URL:
 * `{sidecarBaseUrl}{regionsPath}{regionId}/{resource}` — e.g.
 * `https://sidecar.onebusaway.org/api/v2/regions/1/alarms`. The single source of the sidecar URL
 * shape, shared by the alarms client (`TripInfoRepository`) and `PushRegistrationClient`; a change to
 * the path scheme is made here once.
 *
 * [regionsPath] is the resolved `R.string.arrivals_reminders_api_endpoint` (`/api/v2/regions/`). It
 * stays a parameter rather than a constant because it is a string *resource* — a brand override
 * point — and because Context-free callers inject it for JVM testability.
 */
fun sidecarRegionUrl(
    sidecarBaseUrl: String,
    regionsPath: String,
    regionId: Long,
    resource: String
): String = "$sidecarBaseUrl$regionsPath$regionId/$resource"
