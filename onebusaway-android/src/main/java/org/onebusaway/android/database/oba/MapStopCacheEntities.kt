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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bus stop cached from a nearby-stops load, so a viewport can render its stops instantly (before —
 * or without — a network response) and reconcile once the network returns (#1754). This is a spatial
 * cache keyed by stop id and scoped per [regionId]; it is deliberately SEPARATE from the user-state
 * `stops` table ([StopRecord]), which only holds favorites/recents/renames and must not be polluted
 * with transient viewport stops.
 *
 * [lastSeen] (device wall-clock millis, stamped on every network upsert) is both the LRU key and the
 * TTL staleness clock — reads never bump it, so it means "last network-confirmed". [direction] is
 * nullable and stored verbatim (it is often the literal string `"null"`; see [CachedStopRecord.toObaStop]).
 * [routeIds] is a delimited string (see [joinRouteIds]/[splitRouteIds]); `""` for a stop with no routes.
 *
 * The `(region_id, latitude, longitude)` index serves the bounding-box viewport query; the
 * `(region_id, last_seen)` index serves the TTL + size-cap eviction.
 */
@Entity(
    tableName = "cached_stops",
    indices = [
        Index(value = ["region_id", "latitude", "longitude"]),
        Index(value = ["region_id", "last_seen"])
    ]
)
data class CachedStopRecord(
    @PrimaryKey @ColumnInfo(name = "_id") val id: String,
    @ColumnInfo(name = "code") val code: String?,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "direction") val direction: String?,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "location_type") val locationType: Int,
    @ColumnInfo(name = "route_ids") val routeIds: String,
    @ColumnInfo(name = "region_id") val regionId: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long
)

/**
 * The GTFS route type ([org.onebusaway.android.models.ObaRoute.TYPE_BUS], etc.) for a route id, cached
 * alongside [CachedStopRecord] so a cache-rendered stop marker gets its correct icon colour without a
 * network response. Region-scoped; [lastSeen] drives the TTL eviction (no size cap — the routes-per-
 * region count is small and bounded).
 */
@Entity(
    tableName = "cached_route_types",
    indices = [Index(value = ["region_id", "last_seen"])]
)
data class CachedRouteTypeRecord(
    @PrimaryKey @ColumnInfo(name = "_id") val id: String,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "region_id") val regionId: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long
)
