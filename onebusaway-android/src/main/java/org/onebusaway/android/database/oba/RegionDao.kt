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
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A region and its bounds + Open311 servers, the whole region-cache row for one deployment. */
data class RegionWithChildren(
    @Embedded val region: RegionRecord,
    @Relation(parentColumn = "_id", entityColumn = "region_id")
    val bounds: List<RegionBoundRecord>,
    @Relation(parentColumn = "_id", entityColumn = "region_id")
    val open311Servers: List<Open311ServerRecord>,
)

/**
 * Room access for the region cache (the legacy `regions` + `region_bounds` + `open311_servers`
 * tables). Replaces `RegionUtils.getRegionsFromProvider`/`saveToProvider`.
 */
@Dao
interface RegionDao {

    @Transaction
    @Query("SELECT * FROM regions WHERE _id = :id LIMIT 1")
    suspend fun getRegion(id: Long): RegionWithChildren?

    @Transaction
    @Query("SELECT * FROM regions")
    suspend fun getAllRegions(): List<RegionWithChildren>

    /** The region cache as a reactive stream, so cache refreshes propagate like the other domains. */
    @Transaction
    @Query("SELECT * FROM regions")
    fun regionsFlow(): Flow<List<RegionWithChildren>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: RegionRecord)

    @Insert
    suspend fun insertBounds(bounds: List<RegionBoundRecord>)

    @Insert
    suspend fun insertOpen311Servers(servers: List<Open311ServerRecord>)

    @Query("DELETE FROM regions")
    suspend fun clearRegions()

    /**
     * The legacy open311_servers table has no foreign key (see [Open311ServerRecord]), so it must be
     * cleared explicitly; region_bounds cascades off [clearRegions].
     */
    @Query("DELETE FROM open311_servers")
    suspend fun clearOpen311Servers()

    /**
     * Atomically replaces the whole region cache (the legacy `saveToProvider`): clear all three
     * tables, then insert each region with its children. Callers filter to usable regions first.
     */
    @Transaction
    suspend fun replaceAll(regions: List<RegionWithChildren>) {
        clearOpen311Servers()
        clearRegions() // region_bounds cascades
        for (entry in regions) {
            insertRegion(entry.region)
            insertBounds(entry.bounds)
            insertOpen311Servers(entry.open311Servers)
        }
    }
}
