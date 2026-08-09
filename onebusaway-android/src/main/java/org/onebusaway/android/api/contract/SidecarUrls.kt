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
 * Assembles a region-scoped **v2** sidecar endpoint URL:
 * `{sidecarBaseUrl}{regionsPath}{regionId}/{resource}` — e.g.
 * `https://sidecar.onebusaway.org/api/v2/regions/1/alarms`. The one home of that shape, shared by the
 * alarms client (`TripInfoRepository`) and `PushRegistrationClient`. (The v1 sidecar endpoints —
 * weather, surveys, alerts, vehicle search — are a separate pre-existing idiom, assembled by
 * [sidecarV1RegionUrl].)
 *
 * [regionsPath] is the resolved `R.string.arrivals_reminders_api_endpoint` (`/api/v2/regions/`). It
 * stays a parameter because the segment lives in that string resource — overridable per brand,
 * though no brand overrides it today — and Context-free callers inject the resolved value.
 */
fun sidecarRegionUrl(
    sidecarBaseUrl: String,
    regionsPath: String,
    regionId: Long,
    resource: String
): String = "$sidecarBaseUrl$regionsPath$regionId/$resource"

/**
 * Assembles a **v1** sidecar endpoint URL: `{sidecarBaseUrl}{endpoint}` with [endpoint]'s `regionID`
 * placeholder substituted — e.g. `/api/v1/regions/regionID/vehicles` + region 1 →
 * `https://sidecar.onebusaway.org/api/v1/regions/1/vehicles`.
 *
 * Unlike the v2 shape, each v1 endpoint carries its whole path (with the region id embedded mid-path)
 * in its own string resource, so what's shared is the substitution rather than the path layout. The one
 * home of it, for weather, surveys, wide alerts and the coach-number vehicle search; [endpoint] is the
 * caller's resolved `R.string.*_api_endpoint`, so Context stays with the caller.
 */
fun sidecarV1RegionUrl(
    sidecarBaseUrl: String,
    endpoint: String,
    regionId: String
): String = sidecarBaseUrl + endpoint.replace(REGION_ID_PLACEHOLDER, regionId)

/** The token a v1 endpoint string resource carries where the region id goes. */
private const val REGION_ID_PLACEHOLDER = "regionID"
