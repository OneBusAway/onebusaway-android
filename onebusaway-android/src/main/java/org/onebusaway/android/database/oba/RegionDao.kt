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
    val open311Servers: List<Open311ServerRecord>
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

    /**
     * Clears the *directory* regions, leaving user-added custom ones (#2027) in place — the regions
     * directory has no opinion about them, so a refresh must not take them with it.
     */
    @Query("DELETE FROM regions WHERE custom = 0")
    suspend fun clearDirectoryRegions()

    /**
     * The legacy open311_servers table has no foreign key (see [Open311ServerRecord]), so it must be
     * cleared explicitly; region_bounds cascades off the region delete. Scoped to the rows whose region
     * is going away, for the same reason as [clearDirectoryRegions]. (A custom region never has Open311
     * servers today — the deep link can't supply any — but scoping it keeps the two deletes consistent.)
     */
    @Query("DELETE FROM open311_servers WHERE region_id IN (SELECT _id FROM regions WHERE custom = 0)")
    suspend fun clearDirectoryOpen311Servers()

    /** The user-added custom regions (#2027), which [replaceAll] deliberately preserves. */
    @Transaction
    @Query("SELECT * FROM regions WHERE custom != 0")
    suspend fun getCustomRegions(): List<RegionWithChildren>

    /**
     * The lowest region id in the table, or null when it's empty. Custom regions are numbered downwards
     * from -2, so the next id is `min(this, -1) - 1` — see `RegionCache.saveCustom`. (-1 is the "no
     * region" sentinel in the region-id preference, and directory ids are non-negative.)
     */
    @Query("SELECT MIN(_id) FROM regions")
    suspend fun minRegionId(): Long?

    /**
     * The id of the custom region served by [obaBaseUrl], or null — the key re-adding an existing region
     * matches on. Reads only the id: the caller reuses it and nothing else, and selecting the whole
     * [RegionWithChildren] would cost a transaction plus a query per `@Relation`.
     */
    @Query("SELECT _id FROM regions WHERE custom != 0 AND oba_base_url = :obaBaseUrl LIMIT 1")
    suspend fun customRegionIdByObaUrl(obaBaseUrl: String): Long?

    /** Removes one region and its children (region_bounds cascades; open311 has no FK, so it's explicit). */
    @Transaction
    suspend fun deleteRegionById(id: Long) {
        deleteOpen311ServersForRegion(id)
        deleteRegionRow(id)
    }

    @Query("DELETE FROM open311_servers WHERE region_id = :id")
    suspend fun deleteOpen311ServersForRegion(id: Long)

    @Query("DELETE FROM regions WHERE _id = :id")
    suspend fun deleteRegionRow(id: Long)

    /**
     * Atomically replaces the *directory* region cache (the legacy `saveToProvider`): clear the
     * directory rows and their children, then insert each region with its children. Callers filter to
     * usable regions first. User-added custom regions are left untouched — see [clearDirectoryRegions].
     */
    @Transaction
    suspend fun replaceAll(regions: List<RegionWithChildren>) {
        clearDirectoryOpen311Servers()
        clearDirectoryRegions() // region_bounds cascades
        regions.forEach { insertEntry(it) }
    }

    /** Inserts or replaces one custom region and its children (there are no bounds to write today). */
    @Transaction
    suspend fun upsertCustomRegion(entry: RegionWithChildren) {
        deleteRegionById(entry.region.id)
        insertEntry(entry)
    }

    /**
     * Inserts one region and its children. Parent first: `region_bounds` has a foreign key onto
     * `regions`, so the order is load-bearing — which is the reason both writers share this rather than
     * each spelling out the three inserts.
     */
    @Transaction
    suspend fun insertEntry(entry: RegionWithChildren) {
        insertRegion(entry.region)
        insertBounds(entry.bounds)
        insertOpen311Servers(entry.open311Servers)
    }
}
