package org.onebusaway.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.onebusaway.android.database.oba.CachedRouteTypeRecord
import org.onebusaway.android.database.oba.CachedStopRecord
import org.onebusaway.android.database.oba.LegacyImportDao
import org.onebusaway.android.database.oba.MapStopCacheDao
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavStopRecord
import org.onebusaway.android.database.oba.Open311ServerRecord
import org.onebusaway.android.database.oba.RegionBoundRecord
import org.onebusaway.android.database.oba.RegionDao
import org.onebusaway.android.database.oba.RegionRecord
import org.onebusaway.android.database.oba.RouteDao
import org.onebusaway.android.database.oba.RouteRecord
import org.onebusaway.android.database.oba.ServiceAlertDao
import org.onebusaway.android.database.oba.ServiceAlertRecord
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopRecord
import org.onebusaway.android.database.oba.TripAlertRecord
import org.onebusaway.android.database.oba.TripDao
import org.onebusaway.android.database.oba.TripRecord
import org.onebusaway.android.database.survey.dao.StudiesDao
import org.onebusaway.android.database.survey.dao.SurveysDao
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.database.widealerts.dao.AlertDao
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/**
 * The app's single Room database. Holds the survey + wide-alert tables and — as of v3 — the tables
 * migrated from the legacy `ObaProvider` ContentProvider (storage-modernization). The dead recentStops
 * module (its own `stops`/`regions` tables) was removed in the same change; v3's migration drops those
 * and creates the legacy-schema tables in their place. These tables are now the authoritative store:
 * the ContentProvider has been removed and any pre-existing data is imported once via
 * [org.onebusaway.android.database.oba.LegacyDataImporter].
 *
 * v4 retires the two-tier route-favorite model (#1751): the `route_headsign_favorites` table is dropped
 * and `routes.favorite` becomes the single source of truth, matching `stops.favorite`. It also adds the
 * missing foreign-key child index on `surveys.study_id` (#1739).
 *
 * v5 adds the map stop cache (#1754): `cached_stops` + `cached_route_types`, a region-scoped spatial
 * cache of nearby-stops loads (separate from the user-state `stops` table) so the map renders stops
 * instantly on a slow/cold-start load.
 *
 * v6 adds `regions.otp_base_graphql_url` (#1780): the per-region OTP 2.x GraphQL endpoint. A non-null
 * value routes that region through the OTP2 `planConnection` path; NULL (every existing cached row)
 * stays on OTP1 REST.
 *
 * v7 retires the per-stop route filter (#1807-era arrivals cleanup): the `stop_routes_filter` table is
 * dropped now that arrivals are grouped by route and the "show only this route" filter is gone.
 *
 * v8 adds `cached_stops.wheelchair_boarding` (#1029): the stop's GTFS wheelchair-boarding accessibility
 * (a WheelchairBoarding enum name), so the map focus banner can show an accessibility indicator for
 * cached stops too. NULL (every existing cached row) reads back as UNKNOWN.
 */
@Database(
    entities = [
        Study::class,
        Survey::class,
        AlertEntity::class,
        StopRecord::class,
        RouteRecord::class,
        TripRecord::class,
        TripAlertRecord::class,
        ServiceAlertRecord::class,
        RegionRecord::class,
        RegionBoundRecord::class,
        Open311ServerRecord::class,
        NavStopRecord::class,
        CachedStopRecord::class,
        CachedRouteTypeRecord::class
    ],
    version = 8,
    exportSchema = true
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
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun tripDao(): TripDao
    abstract fun regionDao(): RegionDao
    abstract fun navStopDao(): NavStopDao

    // Map stop cache (#1754).
    abstract fun mapStopCacheDao(): MapStopCacheDao

    companion object {
        /** The Room database filename (also the backup target). */
        const val DATABASE_NAME = "app_database"
    }
}
