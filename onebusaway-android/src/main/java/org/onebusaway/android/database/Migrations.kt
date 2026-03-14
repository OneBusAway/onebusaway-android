package org.onebusaway.android.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `alerts` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_trips` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`fromAddress` TEXT NOT NULL, " +
                    "`toAddress` TEXT NOT NULL, " +
                    "`fromLat` REAL NOT NULL, " +
                    "`fromLon` REAL NOT NULL, " +
                    "`toLat` REAL NOT NULL, " +
                    "`toLon` REAL NOT NULL, " +
                    "`itineraryJson` TEXT NOT NULL, " +
                    "`favorite` INTEGER NOT NULL DEFAULT 0, " +
                    "`createdAt` INTEGER NOT NULL)"
        )
    }
}
