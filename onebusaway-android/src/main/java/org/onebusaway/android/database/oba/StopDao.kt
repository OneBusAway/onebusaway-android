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
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.MyTextUtils

/** A stop row projected for the My-tab lists; [uiName] is the legacy UI_NAME (user name or name). */
data class StopListRow(
    val id: String,
    @ColumnInfo(name = "ui_name") val uiName: String?,
    val direction: String?,
    val latitude: Double,
    val longitude: Double,
    val favorite: Int?,
)

/** The legacy projected UI_NAME expression, reused across the stop list queries. */
private const val UI_NAME = "(CASE WHEN user_name IS NOT NULL THEN user_name ELSE name END)"

/** The legacy region scope: rows for the active region, or with no region, or (when none active) all. */
private const val REGION_SCOPE = "(:regionId IS NULL OR region_id = :regionId OR region_id IS NULL)"

/** Room access for stop user-state + the My-tab recent/starred lists (the legacy `stops` table). */
@Dao
interface StopDao {

    /** Sets the favorite flag. A no-op when the row doesn't exist (matches the legacy UPDATE). */
    @Query("UPDATE stops SET favorite = :favorite WHERE _id = :stopId")
    suspend fun setFavorite(stopId: String, favorite: Int)

    @Query("SELECT favorite, user_name FROM stops WHERE _id = :stopId LIMIT 1")
    suspend fun userInfo(stopId: String): StopUserInfoRow?

    /** Every stop the user has favorited or renamed (the legacy StopUserInfoMap query). */
    @Query(
        "SELECT _id AS stopId, favorite, user_name AS userName FROM stops " +
            "WHERE user_name IS NOT NULL OR favorite = 1"
    )
    suspend fun userInfoMap(): List<StopUserInfoMapRow>

    @Query("SELECT latitude, longitude FROM stops WHERE _id = :stopId LIMIT 1")
    suspend fun location(stopId: String): StopLocationRow?

    @Query("SELECT name FROM stops WHERE _id = :stopId LIMIT 1")
    suspend fun nameForStop(stopId: String): String?

    @Query("SELECT * FROM stops WHERE _id = :stopId LIMIT 1")
    suspend fun getStop(stopId: String): StopRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stop: StopRecord)

    /**
     * Records a stop's identity/coordinates and marks it used (increment use_count, stamp
     * access_time), the legacy [org.onebusaway.android.util.DBUtil] `addToDB` write. Merges onto the
     * existing row so the user's favorite flag and custom name are never clobbered. [now] is supplied
     * by the caller so this stays a stateless helper.
     */
    @Transaction
    suspend fun markStopUsed(
        id: String,
        code: String,
        name: String,
        direction: String,
        latitude: Double,
        longitude: Double,
        regionId: Long?,
        now: Long,
    ) {
        val existing = getStop(id)
        upsert(
            existing?.copy(
                code = code,
                name = name,
                direction = direction,
                latitude = latitude,
                longitude = longitude,
                regionId = regionId ?: existing.regionId,
                useCount = existing.useCount + 1,
                accessTime = now,
            ) ?: StopRecord(
                id = id,
                code = code,
                name = name,
                direction = direction,
                useCount = 1,
                latitude = latitude,
                longitude = longitude,
                accessTime = now,
                regionId = regionId,
            )
        )
    }

    // --- Recents/starred lists (reactive) ---

    @Query(
        "SELECT _id AS id, $UI_NAME AS ui_name, direction, latitude, longitude, favorite FROM stops " +
            "WHERE ((access_time IS NOT NULL AND access_time > :cutoff) OR use_count > 0) " +
            "AND $REGION_SCOPE ORDER BY access_time DESC, use_count DESC LIMIT 20"
    )
    fun recents(cutoff: Long, regionId: Long?): Flow<List<StopListRow>>

    @Query(
        "SELECT _id AS id, $UI_NAME AS ui_name, direction, latitude, longitude, favorite FROM stops " +
            "WHERE favorite = 1 AND $REGION_SCOPE ORDER BY $UI_NAME ASC"
    )
    fun starredByName(regionId: Long?): Flow<List<StopListRow>>

    @Query(
        "SELECT _id AS id, $UI_NAME AS ui_name, direction, latitude, longitude, favorite FROM stops " +
            "WHERE favorite = 1 AND $REGION_SCOPE ORDER BY use_count DESC"
    )
    fun starredByFrequency(regionId: Long?): Flow<List<StopListRow>>

    // --- Recents/starred mutations ---

    @Query("UPDATE stops SET use_count = 0, access_time = NULL WHERE _id = :stopId")
    suspend fun markUnused(stopId: String)

    @Query("UPDATE stops SET use_count = 0, access_time = NULL")
    suspend fun markAllUnused()

    @Query("UPDATE stops SET favorite = 0")
    suspend fun clearAllFavorites()
}

/**
 * Records a stop from its [ObaStop] model (the legacy DBUtil.addToDB shape): applies the display-name
 * formatting and marks it used. Shared by the arrivals load and the trip-details reminder persist so
 * the ObaStop -> row mapping lives in one place.
 */
suspend fun StopDao.markStopUsed(stop: ObaStop, regionId: Long?, now: Long) =
    markStopUsed(
        id = stop.id,
        code = stop.stopCode.orEmpty(),
        name = MyTextUtils.formatDisplayText(stop.name).orEmpty(),
        direction = stop.direction.orEmpty(),
        latitude = stop.latitude,
        longitude = stop.longitude,
        regionId = regionId,
        now = now,
    )

/** The favorite flag + custom name for a single stop (the legacy StopUserInfo read). */
data class StopUserInfoRow(
    val favorite: Int?,
    @ColumnInfo(name = "user_name") val userName: String?,
)

/** A stop id with its favorite flag + custom name (the legacy StopUserInfoMap rows). */
data class StopUserInfoMapRow(
    val stopId: String,
    val favorite: Int?,
    val userName: String?,
)

/** A stop's coordinates (the legacy Stops.getLocation read). */
data class StopLocationRow(
    val latitude: Double,
    val longitude: Double,
)
