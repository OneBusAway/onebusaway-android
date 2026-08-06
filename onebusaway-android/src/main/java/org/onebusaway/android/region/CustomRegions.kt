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
package org.onebusaway.android.region

/**
 * The user-supplied half of a custom region — exactly what an `add-region` deep link can say (#2027),
 * with no ids, defaults or capability flags decided yet. [ExternalDeepLinks] produces one of these,
 * the confirmation dialog renders it, and [customRegion] turns it into a [Region].
 *
 * [name] and [obaBaseUrl] are required (matching OneBusAway for iOS); everything else is optional.
 */
data class CustomRegionRequest(
    val name: String,
    val obaBaseUrl: String,
    val otpBaseUrl: String? = null,
    val sidecarBaseUrl: String? = null,
    val umamiAnalyticsUrl: String? = null,
    val umamiAnalyticsId: String? = null,
    /**
     * The id the sidecar knows this deployment by (`region-id`, #2165) — optional on both ends, so a
     * link without it behaves exactly as before. Becomes [Region.sidecarRegionId], *not* [Region.id]:
     * the local id stays negative (see the id-space comment below).
     */
    val regionId: Long? = null
)

/*
 * The region id space, in one place, because custom regions are the reason it has structure at all:
 *
 *   >= 0   a region from the OBA regions directory (Tampa is 0)
 *   -1     [NO_REGION_ID] — the region-id preference's "no region set" sentinel
 *   <= -2  a user-added custom region (#2027)
 *
 * A custom region stays in the negative half even when the `add-region` link names the server's own id
 * (`region-id`, #2165): that id goes to [Region.sidecarRegionId] instead. Adopting it as [Region.id]
 * would put a custom row back into the directory's space, where the next directory refresh carrying the
 * same id collides with it on the primary key — `RegionDao.replaceAll` deletes only `custom = 0` rows,
 * so the custom row would survive the delete and the insert would land on top of it. The two ids mean
 * different things (our primary key vs. the sidecar's name for the same deployment), and conflating
 * them is what created the bug this fixes.
 *
 * Anything reading or writing the region-id preference must go through [NO_REGION_ID] rather than
 * testing the sign: `id < 0` would swallow every custom region, which is exactly the bug that shipped
 * in the first draft of this feature (a custom region survived in the database but was never restored
 * at cold start, so the app silently fell back to resolution on every launch).
 */

/** The region-id preference value meaning "no region is set". */
internal const val NO_REGION_ID = -1L

/**
 * The first id handed to a custom region — one below [NO_REGION_ID], so the two can never be confused.
 * See [nextCustomRegionId].
 */
internal const val FIRST_CUSTOM_REGION_ID = NO_REGION_ID - 1

/**
 * The region id stored in the preference, or null when it means "no region" ([NO_REGION_ID]).
 *
 * Pure so the sentinel rule is unit-tested rather than living inline in the `Context`-coupled
 * repository — see `RegionRepository.loadPersistedRegion`, its only caller.
 */
internal fun persistedRegionId(stored: Long): Long? = stored.takeIf { it != NO_REGION_ID }

/**
 * The next custom-region id given the lowest id already in the cache ([minId], null when empty) — one
 * below everything present, so no two *live* regions can share an id.
 *
 * Note this reads the live minimum rather than a persisted counter, so removing the lowest custom region
 * does free its id for the next one. That is safe because the only durable reference to a region id is
 * the region-id preference, and removing the current region rewrites it (see
 * `RegionRepository.deleteCustomRegion`) — there is no dangling id left to be re-pointed at a different
 * server. A monotonic counter would need its own persisted state to buy nothing.
 */
internal fun nextCustomRegionId(minId: Long?): Long = minOf(minId ?: NO_REGION_ID, NO_REGION_ID) - 1

/**
 * Builds the [Region] for [request] under [id].
 *
 * The capability flags are **declarations, not observations** — nothing has probed this server. They are
 * set true because that is what makes a region usable at all (`RegionUtils.isRegionUsable` requires
 * active + discovery + realtime, and rejects experimental regions unless the rider opted in), and a
 * region the app refuses to use would make the link pointless. The failure mode is honest and local: if
 * the server doesn't actually serve those APIs, its requests fail and the rider sees the same errors as
 * any unreachable server — nothing silently reads as empty data.
 *
 * [Region.bounds] is deliberately empty: a deep link carries no coverage area, and `getClosestRegion`
 * skips a region it can't measure a distance to, so a custom region is never auto-selected. It is
 * reachable by the link itself and by the region picker.
 *
 * [Region.contactEmail] is left blank rather than filled with a placeholder (iOS hardcodes
 * `example@example.com`): the "email a problem report" option keys off it, and mailing a made-up
 * address is worse than not offering the option.
 */
internal fun customRegion(id: Long, request: CustomRegionRequest): Region = Region(
    id = id,
    name = request.name,
    active = true,
    custom = true,
    obaBaseUrl = request.obaBaseUrl,
    otpBaseUrl = request.otpBaseUrl,
    sidecarBaseUrl = request.sidecarBaseUrl,
    umamiAnalytics = request.umamiAnalyticsUrl?.let {
        Region.UmamiAnalyticsConfig(url = it, id = request.umamiAnalyticsId)
    },
    // The server's own id for this deployment, when the link named one — kept apart from [id] so the
    // sidecar is addressed by a name it recognizes without a custom region entering the directory's id
    // space. Null leaves Region.sidecarId falling back to [id], i.e. the pre-#2165 behaviour.
    sidecarRegionId = request.regionId,
    // See the KDoc: declared so the region is usable, not observed from the server.
    supportsObaDiscoveryApis = true,
    supportsObaRealtimeApis = true,
    experimental = false,
    bounds = emptyArray(),
    contactEmail = ""
)
