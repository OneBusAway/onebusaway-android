package org.onebusaway.android.database

import android.content.Context
import androidx.room.Room

/**
 * Provides a singleton instance of the Room database (`AppDatabase`).
 * Ensures that only one instance of the database is created and used throughout the application.
 */
object DatabaseProvider {
    private var INSTANCE: AppDatabase? = null

    /**
     * Retrieves the singleton instance of the Room database.
     * If the instance does not exist, it creates and initializes it.
     *
     * @return The singleton `AppDatabase` instance.
     */
    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app_database"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}
