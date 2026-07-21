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
package org.onebusaway.android.directions

/**
 * The **entity part** of an OTP GTFS id — the single, sanctioned normalization for OTP entity ids
 * across the OTP1/OTP2 planner split. Both [OtpObaIdResolver] (mapping OTP ids onto OBA ids) and the
 * trip-plan-change monitor's itinerary identity ([org.onebusaway.android.directions.model.ItineraryDescription])
 * consume this one rule, so there is a single definition rather than two.
 *
 * The contract (per the pinned OTP2 GTFS schema, `graphql/otp2/schema.graphqls`): an OTP2 entity id is
 * `{feedId}:{entityId}` (e.g. `kcm:102574`, `1:trip_5`); an OTP1 id is the bare `{entityId}` (e.g.
 * `trip_5`). The **entity id is identical across both schemes** — only the feed/agency prefix differs —
 * so stripping the feed prefix yields the same underlying GTFS entity id whichever planner produced it.
 * That makes it the stable key for comparing a trip planned under one scheme against a re-plan under the
 * other. (Verified against live Puget Sound OTP2 deployments; see [OtpObaIdResolver] for the OBA-id
 * mapping that rests on the same premise.)
 *
 * **Colon-only, deliberately.** The delimiter is `:`. Underscore is *not* a delimiter here: OBA ids are
 * `{agency}_{entity}`, and a GTFS entity id legitimately contains underscores (`agency_trip_5`), so
 * splitting on `_` would corrupt real ids. An unprefixed id (no colon) passes through unchanged.
 *
 * @return the substring after the first `:`, the whole value when there is no `:`, or null for a
 *   null/blank input.
 */
fun gtfsEntitySuffix(id: String?): String? {
    val s = id ?: return null
    return s.substringAfter(':', missingDelimiterValue = s).takeIf { it.isNotEmpty() }
}
