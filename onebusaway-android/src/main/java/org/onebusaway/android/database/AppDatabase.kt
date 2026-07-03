package org.onebusaway.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.onebusaway.android.database.oba.LegacyImportDao
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavStopRecord
import org.onebusaway.android.database.oba.RegionDao
import org.onebusaway.android.database.oba.RouteDao
import org.onebusaway.android.database.oba.RouteHeadsignFavoriteDao
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopRouteFilterDao
import org.onebusaway.android.database.oba.TripDao
import org.onebusaway.android.database.oba.Open311ServerRecord
import org.onebusaway.android.database.oba.RegionBoundRecord
import org.onebusaway.android.database.oba.RegionRecord
import org.onebusaway.android.database.oba.RouteHeadsignFavoriteRecord
import org.onebusaway.android.database.oba.RouteRecord
import org.onebusaway.android.database.oba.ServiceAlertRecord
import org.onebusaway.android.database.oba.StopRecord
import org.onebusaway.android.database.oba.StopRouteFilterRecord
import org.onebusaway.android.database.oba.TripAlertRecord
import org.onebusaway.android.database.oba.TripRecord
import org.onebusaway.android.database.survey.dao.StudiesDao
import org.onebusaway.android.database.survey.dao.SurveysDao
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.database.widealerts.dao.AlertDao
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/**
 * The app's single Room database. Holds the survey + wide-alert tables and — as of v3 — the 11 tables
 * migrated from the legacy `ObaProvider` ContentProvider (storage-modernization). The dead recentStops
 * module (its own `stops`/`regions` tables) was removed in the same change; v3's migration drops those
 * and creates the legacy-schema tables in their place. These tables are now the authoritative store:
 * the ContentProvider has been removed and any pre-existing data is imported once via
 * [org.onebusaway.android.database.oba.LegacyDataImporter].
 */
@Database(
    entities = [
        Study::class,
        Survey::class,
        AlertEntity::class,
        StopRecord::class,
        RouteRecord::class,
        TripRecord::class,
        StopRouteFilterRecord::class,
        TripAlertRecord::class,
        ServiceAlertRecord::class,
        RegionRecord::class,
        RegionBoundRecord::class,
        Open311ServerRecord::class,
        RouteHeadsignFavoriteRecord::class,
        NavStopRecord::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    // Studies
    abstract fun studiesDao(): StudiesDao
    abstract fun surveysDao(): SurveysDao

    // Region wide alerts
    abstract fun alertsDao(): AlertDao

    // One-time import of the legacy ObaProvider data (storage-modernization).
    abstract fun legacyImportDao(): LegacyImportDao

    // Migrated legacy-table DAOs (storage-modernization).
    abstract fun serviceAlertDao(): ServiceAlertDao
    abstract fun stopRouteFilterDao(): StopRouteFilterDao
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun tripDao(): TripDao
    abstract fun routeHeadsignFavoriteDao(): RouteHeadsignFavoriteDao
    abstract fun regionDao(): RegionDao
    abstract fun navStopDao(): NavStopDao
}
