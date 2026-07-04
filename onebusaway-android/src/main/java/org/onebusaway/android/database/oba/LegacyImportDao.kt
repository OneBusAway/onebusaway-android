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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/** The 11 legacy tables' rows, read from the legacy ContentProvider DB, ready to insert into Room. */
data class LegacyData(
    val stops: List<StopRecord>,
    val routes: List<RouteRecord>,
    val trips: List<TripRecord>,
    val stopRouteFilters: List<StopRouteFilterRecord>,
    val tripAlerts: List<TripAlertRecord>,
    val serviceAlerts: List<ServiceAlertRecord>,
    val regions: List<RegionRecord>,
    val regionBounds: List<RegionBoundRecord>,
    val open311Servers: List<Open311ServerRecord>,
    val routeHeadsignFavorites: List<RouteHeadsignFavoriteRecord>,
    val navStops: List<NavStopRecord>,
)

/**
 * Every table of a full Room-format backup: the 11 [legacy] tables plus the survey and wide-alert
 * tables that a Room backup carries but a legacy ContentProvider backup doesn't. Restoring one replaces
 * the whole database, so all three groups are cleared and re-inserted together (see [replaceAll]).
 */
data class RoomBackupData(
    val legacy: LegacyData,
    val studies: List<Study>,
    val surveys: List<Survey>,
    val alerts: List<AlertEntity>,
)

/**
 * Inserts the legacy ContentProvider data into the unified Room database (one-time migration). Used
 * only by the importer; consumer DAOs are added per-table where they're consumed. [replaceAll] clears
 * the destination tables and re-inserts in a single transaction so a crashed/retried import is
 * idempotent (the synthetic-rowid tables would otherwise duplicate rows on re-run).
 */
@Dao
interface LegacyImportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(rows: List<StopRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(rows: List<RouteRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(rows: List<TripRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopRouteFilters(rows: List<StopRouteFilterRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripAlerts(rows: List<TripAlertRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceAlerts(rows: List<ServiceAlertRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegions(rows: List<RegionRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegionBounds(rows: List<RegionBoundRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpen311Servers(rows: List<Open311ServerRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteHeadsignFavorites(rows: List<RouteHeadsignFavoriteRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavStops(rows: List<NavStopRecord>)

    // Survey + wide-alert tables — only present in a full Room-format backup (see [replaceAll]).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudies(rows: List<Study>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurveys(rows: List<Survey>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(rows: List<AlertEntity>)

    @Query("DELETE FROM stops") suspend fun clearStops()
    @Query("DELETE FROM routes") suspend fun clearRoutes()
    @Query("DELETE FROM trips") suspend fun clearTrips()
    @Query("DELETE FROM stop_routes_filter") suspend fun clearStopRouteFilters()
    @Query("DELETE FROM trip_alerts") suspend fun clearTripAlerts()
    @Query("DELETE FROM service_alerts") suspend fun clearServiceAlerts()
    @Query("DELETE FROM region_bounds") suspend fun clearRegionBounds()
    @Query("DELETE FROM open311_servers") suspend fun clearOpen311Servers()
    @Query("DELETE FROM regions") suspend fun clearRegions()
    @Query("DELETE FROM route_headsign_favorites") suspend fun clearRouteHeadsignFavorites()
    @Query("DELETE FROM nav_stops") suspend fun clearNavStops()
    @Query("DELETE FROM surveys") suspend fun clearSurveys()
    @Query("DELETE FROM studies") suspend fun clearStudies()
    @Query("DELETE FROM alerts") suspend fun clearAlerts()

    /**
     * Replaces *every* table from a full Room-format backup atomically — the 11 legacy tables plus the
     * survey and wide-alert tables. Restoring a Room backup means "make the database match the backup",
     * so tables absent from an older backup end up empty. Runs in one transaction: any incompatibility
     * (unreadable file surfaced by the caller, or an FK violation here) rolls the whole thing back,
     * leaving the live database untouched. Surveys are cleared before studies (the surveys -> studies FK)
     * and inserted after, mirroring [replaceAll]'s parent/child ordering for the legacy tables.
     */
    @Transaction
    suspend fun replaceAll(data: RoomBackupData) {
        clearSurveys()
        clearStudies()
        clearAlerts()
        replaceAll(data.legacy)
        insertStudies(data.studies)
        insertSurveys(data.surveys)
        insertAlerts(data.alerts)
    }

    /**
     * Replaces all legacy-table data atomically. Children are cleared before parents and parents
     * inserted before children to satisfy the region_bounds -> regions foreign key.
     */
    @Transaction
    suspend fun replaceAll(data: LegacyData) {
        clearRegionBounds()
        clearOpen311Servers()
        clearRegions()
        clearStops()
        clearRoutes()
        clearTrips()
        clearStopRouteFilters()
        clearTripAlerts()
        clearServiceAlerts()
        clearRouteHeadsignFavorites()
        clearNavStops()

        insertRegions(data.regions)
        insertRegionBounds(data.regionBounds)
        insertOpen311Servers(data.open311Servers)
        insertStops(data.stops)
        insertRoutes(data.routes)
        insertTrips(data.trips)
        insertStopRouteFilters(data.stopRouteFilters)
        insertTripAlerts(data.tripAlerts)
        insertServiceAlerts(data.serviceAlerts)
        insertRouteHeadsignFavorites(data.routeHeadsignFavorites)
        insertNavStops(data.navStops)
    }
}
