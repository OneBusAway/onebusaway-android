/*
 * Copyright (C) 2026 Divesh
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
package org.onebusaway.android.database.savedtrips.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.onebusaway.android.database.savedtrips.entity.SavedTripEntity

/**
 * DAO interface for accessing and managing the `saved_trips` table in the database.
 */
@Dao
interface SavedTripDao {

    @Query("SELECT * FROM saved_trips ORDER BY createdAt DESC")
    suspend fun getAll(): List<SavedTripEntity>

    @Query("SELECT * FROM saved_trips WHERE favorite = 1 ORDER BY createdAt DESC")
    suspend fun getFavorites(): List<SavedTripEntity>

    @Query("SELECT * FROM saved_trips WHERE id = :id")
    suspend fun getById(id: Long): SavedTripEntity?

    @Insert
    suspend fun insert(trip: SavedTripEntity): Long

    @Update
    suspend fun update(trip: SavedTripEntity)

    @Delete
    suspend fun delete(trip: SavedTripEntity)
}
