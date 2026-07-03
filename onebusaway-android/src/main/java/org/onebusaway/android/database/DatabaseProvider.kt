package org.onebusaway.android.database

import android.content.Context
import androidx.room.Room

/**
 * Provides a singleton instance of the Room database (`AppDatabase`).
 * Ensures that only one instance of the database is created and used throughout the application.
 */
object DatabaseProvider {
    /** The Room database filename (also the backup target). */
    const val DATABASE_NAME = "app_database"

    @Volatile
    private var INSTANCE: AppDatabase? = null

    /**
     * Retrieves the singleton instance of the Room database.
     * If the instance does not exist, it creates and initializes it.
     *
     * @return The singleton `AppDatabase` instance.
     */
    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            // Double-checked locking: two callers can both observe a null INSTANCE and serialize here, so
            // re-read inside the lock and reuse whatever the first one built rather than creating a second.
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
        }
    }

    /**
     * Closes and forgets the singleton so the on-disk file can be replaced (a backup restore). The next
     * [getDatabase] reopens it. The legacy `ObaProvider.closeDB()` analogue.
     */
    fun closeDatabase() {
        synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
