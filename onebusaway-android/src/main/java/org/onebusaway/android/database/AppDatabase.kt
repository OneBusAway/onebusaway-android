package org.onebusaway.android.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.onebusaway.android.database.recentStops.dao.RegionDao
import org.onebusaway.android.database.recentStops.dao.StopDao
import org.onebusaway.android.database.recentStops.entity.RegionEntity
import org.onebusaway.android.database.recentStops.entity.StopEntity
import org.onebusaway.android.database.widealerts.dao.AlertDao
import org.onebusaway.android.database.widealerts.entity.AlertEntity
import org.onebusaway.android.ui.survey.dao.StudiesDao
import org.onebusaway.android.ui.survey.dao.SurveysDao
import org.onebusaway.android.ui.survey.entity.Study
import org.onebusaway.android.ui.survey.entity.Survey

/**
 * Main database class for the app, containing `Study` and `Survey` entities.
 * Provides abstract methods for accessing `StudiesDao` and `SurveysDao`.
 * The `@Database` annotation sets up Room with version 1 of the schema.
 */

@Database(
    entities = [Study::class, Survey::class, RegionEntity::class, StopEntity::class, AlertEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    // Studies
    abstract fun studiesDao(): StudiesDao
    abstract fun surveysDao(): SurveysDao

    // Recent stops for region
    abstract fun regionDao(): RegionDao
    abstract fun stopDao(): StopDao

    // Region wide alerts
    abstract fun alertsDao(): AlertDao
}
