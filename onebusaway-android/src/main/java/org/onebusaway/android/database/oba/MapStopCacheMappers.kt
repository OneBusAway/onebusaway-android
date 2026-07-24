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
package org.onebusaway.android.database.oba

import android.location.Location
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.WheelchairBoarding
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.locationOf

/**
 * Pure mapping + decision helpers for the map stop cache ([CachedStopRecord] ⇄ [ObaStop], the viewport
 * bounding box, and the TTL cutoff). Kept free of Room/coroutines/clock reads so they are JVM-unit-
 * testable; the clock ("now") is always passed in, never read here
 * (see feedback_no_currenttimemillis_in_helpers).
 */

/** How long a cached stop stays servable before it is treated as stale and evicted. */
val STOP_CACHE_TTL: Duration = 7.days

/**
 * The delimiter joining a stop's route ids into the [CachedStopRecord.routeIds] column. ASCII Unit
 * Separator: OBA route ids contain `_`/`:`/`-`/digits but never a control character, so this can never
 * collide with an id, unlike a comma/space.
 */
private const val ROUTE_ID_DELIM = '\u001F'

/** Joins route ids for storage; an empty list becomes `""` (round-trips back to an empty array). */
fun joinRouteIds(ids: Array<String>): String = ids.joinToString(ROUTE_ID_DELIM.toString())

/** Splits the stored [CachedStopRecord.routeIds]; `""` becomes an empty array (not `arrayOf("")`). */
fun splitRouteIds(joined: String): Array<String> = if (joined.isEmpty()) emptyArray() else joined.split(ROUTE_ID_DELIM).toTypedArray()

/** A viewport's latitude/longitude bounds for the cache bounding-box query. */
data class LatLonBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

/**
 * The bounds of the viewport centred on ([centerLat], [centerLon]) spanning [latSpan]/[lonSpan] (full
 * spans, matching how [org.onebusaway.android.api.data.MapDataSource.nearbyStops] interprets them):
 * centre ± span/2.
 */
fun boundsFor(centerLat: Double, centerLon: Double, latSpan: Double, lonSpan: Double): LatLonBounds = LatLonBounds(
    minLat = centerLat - latSpan / 2,
    maxLat = centerLat + latSpan / 2,
    minLon = centerLon - lonSpan / 2,
    maxLon = centerLon + lonSpan / 2
)

/** Instants before this cutoff are stale — cached older than [STOP_CACHE_TTL] relative to [now]. */
fun ttlCutoff(now: WallTime): WallTime = now - STOP_CACHE_TTL

/**
 * A cached stop presented as the [ObaStop] model interface. A dedicated impl (not
 * [org.onebusaway.android.api.adapters.ObaStopElement], which coerces [direction] to a non-null `""`)
 * so the nullable [direction] — often the literal `"null"` sentinel — and nullable [stopCode]/[name]
 * round-trip verbatim; [org.onebusaway.android.map.StopsMapController.toStopMarker] relies on the exact
 * `direction ?: "null"` behaviour.
 */
private class CachedObaStop(private val record: CachedStopRecord) : ObaStop {
    override val id: String get() = record.id
    override val stopCode: String? get() = record.code
    override val name: String? get() = record.name
    override val location: Location get() = locationOf(record.latitude, record.longitude)
    override val latitude: Double get() = record.latitude
    override val longitude: Double get() = record.longitude
    override val direction: String? get() = record.direction
    override val locationType: Int get() = record.locationType
    override val routeIds: Array<String> get() = splitRouteIds(record.routeIds)
    override val wheelchairBoarding: WheelchairBoarding
        get() = WheelchairBoarding.fromString(record.wheelchairBoarding)
}

/** Presents this cached row as an [ObaStop] for the map overlay. */
fun CachedStopRecord.toObaStop(): ObaStop = CachedObaStop(this)

/** Maps a freshly-loaded [ObaStop] into a cache row, stamping [regionId]/[now]. */
fun ObaStop.toCachedRecord(regionId: Long, now: Long): CachedStopRecord = CachedStopRecord(
    id = id,
    code = stopCode,
    name = name,
    direction = direction,
    latitude = latitude,
    longitude = longitude,
    locationType = locationType,
    routeIds = joinRouteIds(routeIds),
    wheelchairBoarding = wheelchairBoarding.name,
    regionId = regionId,
    lastSeen = now
)
