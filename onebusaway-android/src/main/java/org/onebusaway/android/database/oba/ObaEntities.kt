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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities mirroring the 11 tables of the legacy `ObaProvider` ContentProvider, unified into the
 * app's single Room database (storage-modernization). Column names match the legacy SQLite schema (via
 * [ColumnInfo]) so the one-time importer can copy rows faithfully and the names stay stable as the
 * intent/backup vocabulary.
 *
 * Nullability deliberately mirrors the loose legacy schema: columns added by `ALTER TABLE ADD COLUMN`
 * (favorites, custom names, access times, URLs, later region fields) are nullable and frequently NULL
 * in the wild, with computed helpers preserving the legacy "NULL reads as false/absent" semantics.
 * Two legacy tables (stop_routes_filter, route_headsign_favorites) had no primary key — duplicate rows
 * are allowed by design — so they get a synthetic auto-increment row id that is behaviorally inert.
 */

@Entity(tableName = "stops")
data class StopRecord(
    @PrimaryKey @ColumnInfo(name = "_id") val id: String,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "direction") val direction: String,
    @ColumnInfo(name = "use_count") val useCount: Int,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "user_name") val userName: String? = null,
    @ColumnInfo(name = "access_time") val accessTime: Long? = null,
    @ColumnInfo(name = "favorite") val favorite: Int? = null,
    @ColumnInfo(name = "region_id") val regionId: Long? = null,
) {
    /** The legacy projected UI_NAME: the user's custom name when set, otherwise the stop name. */
    val uiName: String get() = if (userName != null) userName else name
    val isFavorite: Boolean get() = favorite == 1
}

@Entity(tableName = "routes")
data class RouteRecord(
    @PrimaryKey @ColumnInfo(name = "_id") val id: String,
    @ColumnInfo(name = "short_name") val shortName: String,
    @ColumnInfo(name = "long_name") val longName: String? = null,
    @ColumnInfo(name = "use_count") val useCount: Int,
    @ColumnInfo(name = "user_name") val userName: String? = null,
    @ColumnInfo(name = "access_time") val accessTime: Long? = null,
    @ColumnInfo(name = "favorite") val favorite: Int? = null,
    @ColumnInfo(name = "url") val url: String? = null,
    @ColumnInfo(name = "region_id") val regionId: Long? = null,
) {
    val isFavorite: Boolean get() = favorite == 1
}

/**
 * The legacy trips table had no declared primary key; uniqueness was enforced by app logic on
 * (_id, stop_id). That composite key is made explicit here. route_id/headsign/vehicle_id are nullable
 * because the save path supplies nullable values (the legacy NOT NULL was never actually exercised
 * with nulls).
 */
@Entity(tableName = "trips", primaryKeys = ["_id", "stop_id"])
data class TripRecord(
    @ColumnInfo(name = "_id") val id: String,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "route_id") val routeId: String? = null,
    @ColumnInfo(name = "departure") val departure: Int,
    @ColumnInfo(name = "headsign") val headsign: String? = null,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "reminder") val reminder: Int,
    @ColumnInfo(name = "alarm_delete_path") val alarmDeletePath: String,
    @ColumnInfo(name = "service_date") val serviceDate: Long,
    @ColumnInfo(name = "stop_sequence") val stopSequence: Int,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String? = null,
)

@Entity(tableName = "stop_routes_filter")
data class StopRouteFilterRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "route_id") val routeId: String,
)

@Entity(tableName = "trip_alerts")
data class TripAlertRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "state", defaultValue = "0") val state: Int,
)

@Entity(tableName = "service_alerts")
data class ServiceAlertRecord(
    @PrimaryKey @ColumnInfo(name = "_id") val id: String,
    @ColumnInfo(name = "marked_read_time") val markedReadTime: Long? = null,
    @ColumnInfo(name = "hidden") val hidden: Int? = null,
) {
    val isHidden: Boolean get() = hidden == 1
}

@Entity(tableName = "regions")
data class RegionRecord(
    // NOT autoGenerate: region ids are assigned by the OBA regions directory (Tampa is _id=0), never by
    // the DB. With autoGenerate, Room binds NULL for a 0-valued id and SQLite reassigns it, orphaning
    // the region_bounds/open311 foreign keys that reference _id=0.
    @PrimaryKey @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "oba_base_url") val obaBaseUrl: String,
    @ColumnInfo(name = "siri_base_url") val siriBaseUrl: String,
    @ColumnInfo(name = "lang") val language: String,
    @ColumnInfo(name = "contact_email") val contactEmail: String,
    @ColumnInfo(name = "supports_api_discovery") val supportsObaDiscovery: Int,
    @ColumnInfo(name = "supports_api_realtime") val supportsObaRealtime: Int,
    @ColumnInfo(name = "supports_siri_realtime") val supportsSiriRealtime: Int,
    @ColumnInfo(name = "twitter_url") val twitterUrl: String? = null,
    @ColumnInfo(name = "experimental") val experimental: Int? = null,
    @ColumnInfo(name = "stop_info_url") val stopInfoUrl: String? = null,
    @ColumnInfo(name = "otp_base_url") val otpBaseUrl: String? = null,
    @ColumnInfo(name = "otp_contact_email") val otpContactEmail: String? = null,
    @ColumnInfo(name = "supports_otp_bikeshare") val supportsOtpBikeshare: Int? = null,
    @ColumnInfo(name = "supports_embedded_social") val supportsEmbeddedSocial: Int? = null,
    @ColumnInfo(name = "payment_android_app_id") val paymentAndroidAppId: String? = null,
    @ColumnInfo(name = "payment_warning_title") val paymentWarningTitle: String? = null,
    @ColumnInfo(name = "payment_warning_body") val paymentWarningBody: String? = null,
    @ColumnInfo(name = "sidecar_base_url", defaultValue = "https://onebusaway.co")
    val sidecarBaseUrl: String? = null,
    @ColumnInfo(name = "plausible_analytics_server_url") val plausibleAnalyticsServerUrl: String? = null,
    @ColumnInfo(name = "umami_analytics_url") val umamiAnalyticsUrl: String? = null,
    @ColumnInfo(name = "umami_analytics_id") val umamiAnalyticsId: String? = null,
)

@Entity(
    tableName = "region_bounds",
    foreignKeys = [
        ForeignKey(
            entity = RegionRecord::class,
            parentColumns = ["_id"],
            childColumns = ["region_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("region_id")],
)
data class RegionBoundRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "region_id") val regionId: Long,
    @ColumnInfo(name = "lat") val latitude: Double,
    @ColumnInfo(name = "lon") val longitude: Double,
    @ColumnInfo(name = "lat_span") val latSpan: Double,
    @ColumnInfo(name = "lon_span") val lonSpan: Double,
)

/**
 * The legacy open311_servers table had no cleanup trigger (RegionUtils deletes all three region tables
 * manually), so — unlike region_bounds — no foreign key is declared here, preserving that behavior.
 */
@Entity(tableName = "open311_servers")
data class Open311ServerRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "region_id") val regionId: Long,
    @ColumnInfo(name = "jurisdiction") val jurisdiction: String? = null,
    @ColumnInfo(name = "api_key") val apiKey: String,
    @ColumnInfo(name = "open311_base_url") val baseUrl: String,
)

@Entity(tableName = "route_headsign_favorites")
data class RouteHeadsignFavoriteRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "headsign") val headsign: String,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "exclude") val exclude: Int,
)

@Entity(tableName = "nav_stops")
data class NavStopRecord(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "nav_id") val navId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "destination_id") val destinationId: String,
    @ColumnInfo(name = "before_id") val beforeId: String,
    @ColumnInfo(name = "seq_num") val sequence: Int,
    @ColumnInfo(name = "is_active") val active: Int,
)
