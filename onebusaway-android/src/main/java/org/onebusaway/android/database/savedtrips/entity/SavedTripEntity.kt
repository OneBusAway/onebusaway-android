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
package org.onebusaway.android.database.savedtrips.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing a saved trip plan in the `saved_trips` table.
 * Stores origin/destination, serialized itinerary data, and user-assigned name.
 */
@Entity(tableName = "saved_trips")
data class SavedTripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fromAddress: String,
    val toAddress: String,
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val itineraryJson: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun withToggledFavorite(): SavedTripEntity = copy(favorite = !favorite)
}
