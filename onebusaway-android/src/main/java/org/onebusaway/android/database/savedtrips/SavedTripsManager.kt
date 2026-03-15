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
package org.onebusaway.android.database.savedtrips

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.onebusaway.android.database.DatabaseProvider
import org.onebusaway.android.database.savedtrips.entity.SavedTripEntity

/**
 * Manages saved trip data by interacting with the database.
 * Handles saving, retrieving, updating, and deleting saved trips.
 */
object SavedTripsManager {

    /**
     * Saves a trip to the database on a background thread.
     */
    @JvmStatic
    fun saveTrip(context: Context, trip: SavedTripEntity, callback: ((Long) -> Unit)? = null) {
        val db = DatabaseProvider.getDatabase(context)
        val dao = db.savedTripDao()

        CoroutineScope(Dispatchers.IO).launch {
            val id = dao.insert(trip)
            callback?.invoke(id)
        }
    }

    /**
     * Retrieves all saved trips, ordered by most recent first.
     */
    @JvmStatic
    fun getAllTrips(context: Context): List<SavedTripEntity> {
        return runBlocking {
            val db = DatabaseProvider.getDatabase(context)
            val dao = db.savedTripDao()
            withContext(Dispatchers.IO) {
                dao.getAll()
            }
        }
    }

    /**
     * Retrieves only favorite saved trips.
     */
    @JvmStatic
    fun getFavoriteTrips(context: Context): List<SavedTripEntity> {
        return runBlocking {
            val db = DatabaseProvider.getDatabase(context)
            val dao = db.savedTripDao()
            withContext(Dispatchers.IO) {
                dao.getFavorites()
            }
        }
    }

    /**
     * Retrieves a single saved trip by ID.
     */
    @JvmStatic
    fun getTripById(context: Context, id: Long): SavedTripEntity? {
        return runBlocking {
            val db = DatabaseProvider.getDatabase(context)
            val dao = db.savedTripDao()
            withContext(Dispatchers.IO) {
                dao.getById(id)
            }
        }
    }

    /**
     * Toggles the favorite status of a saved trip.
     */
    @JvmStatic
    fun toggleFavorite(context: Context, trip: SavedTripEntity) {
        val db = DatabaseProvider.getDatabase(context)
        val dao = db.savedTripDao()

        CoroutineScope(Dispatchers.IO).launch {
            dao.update(trip.copy(favorite = !trip.favorite))
        }
    }

    /**
     * Deletes a saved trip from the database.
     */
    @JvmStatic
    fun deleteTrip(context: Context, trip: SavedTripEntity) {
        val db = DatabaseProvider.getDatabase(context)
        val dao = db.savedTripDao()

        CoroutineScope(Dispatchers.IO).launch {
            dao.delete(trip)
        }
    }
}
