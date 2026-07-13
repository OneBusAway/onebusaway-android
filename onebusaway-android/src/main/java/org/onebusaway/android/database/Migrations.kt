package org.onebusaway.android.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `alerts` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

/**
 * Unifies the legacy `ObaProvider` ContentProvider's 11 tables into this Room database
 * (storage-modernization). The dead recentStops module owned `stops`/`regions` tables that collide by
 * name with the legacy tables, so they are dropped first. The CREATE statements are copied verbatim
 * from the exported `3.json` schema so Room's identity validation passes. The tables are created empty;
 * the one-time data import from the legacy database file lands in a later slice.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Drop the dead recentStops tables (zero consumers) that collide by name.
        db.execSQL("DROP TABLE IF EXISTS `stops`")
        db.execSQL("DROP TABLE IF EXISTS `regions`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stops` (`_id` TEXT NOT NULL, `code` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `direction` TEXT NOT NULL, `use_count` INTEGER NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `user_name` TEXT, " +
                "`access_time` INTEGER, `favorite` INTEGER, `region_id` INTEGER, PRIMARY KEY(`_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `routes` (`_id` TEXT NOT NULL, `short_name` TEXT NOT NULL, " +
                "`long_name` TEXT, `use_count` INTEGER NOT NULL, `user_name` TEXT, " +
                "`access_time` INTEGER, `favorite` INTEGER, `url` TEXT, `region_id` INTEGER, " +
                "PRIMARY KEY(`_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (`_id` TEXT NOT NULL, `stop_id` TEXT NOT NULL, " +
                "`route_id` TEXT, `departure` INTEGER NOT NULL, `headsign` TEXT, `name` TEXT NOT NULL, " +
                "`reminder` INTEGER NOT NULL, `alarm_delete_path` TEXT NOT NULL, " +
                "`service_date` INTEGER NOT NULL, `stop_sequence` INTEGER NOT NULL, " +
                "`trip_id` TEXT NOT NULL, `vehicle_id` TEXT, PRIMARY KEY(`_id`, `stop_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stop_routes_filter` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `stop_id` TEXT NOT NULL, `route_id` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_alerts` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `trip_id` TEXT NOT NULL, `stop_id` TEXT NOT NULL, " +
                "`start_time` INTEGER NOT NULL, `state` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `service_alerts` (`_id` TEXT NOT NULL, " +
                "`marked_read_time` INTEGER, `hidden` INTEGER, PRIMARY KEY(`_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `regions` (`_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `oba_base_url` TEXT NOT NULL, `siri_base_url` TEXT NOT NULL, " +
                "`lang` TEXT NOT NULL, `contact_email` TEXT NOT NULL, " +
                "`supports_api_discovery` INTEGER NOT NULL, `supports_api_realtime` INTEGER NOT NULL, " +
                "`supports_siri_realtime` INTEGER NOT NULL, `twitter_url` TEXT, `experimental` INTEGER, " +
                "`stop_info_url` TEXT, `otp_base_url` TEXT, `otp_contact_email` TEXT, " +
                "`supports_otp_bikeshare` INTEGER, `supports_embedded_social` INTEGER, " +
                "`payment_android_app_id` TEXT, `payment_warning_title` TEXT, " +
                "`payment_warning_body` TEXT, " +
                "`sidecar_base_url` TEXT DEFAULT 'https://onebusaway.co', " +
                "`plausible_analytics_server_url` TEXT, `umami_analytics_url` TEXT, " +
                "`umami_analytics_id` TEXT, PRIMARY KEY(`_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `region_bounds` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `region_id` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, " +
                "`lat_span` REAL NOT NULL, `lon_span` REAL NOT NULL, " +
                "FOREIGN KEY(`region_id`) REFERENCES `regions`(`_id`) ON UPDATE NO ACTION " +
                "ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_region_bounds_region_id` ON `region_bounds` (`region_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `open311_servers` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `region_id` INTEGER NOT NULL, `jurisdiction` TEXT, `api_key` TEXT NOT NULL, " +
                "`open311_base_url` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `route_headsign_favorites` (`_id` INTEGER PRIMARY KEY " +
                "AUTOINCREMENT NOT NULL, `route_id` TEXT NOT NULL, `headsign` TEXT NOT NULL, " +
                "`stop_id` TEXT NOT NULL, `exclude` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nav_stops` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`nav_id` TEXT NOT NULL, `start_time` INTEGER NOT NULL, `trip_id` TEXT NOT NULL, " +
                "`destination_id` TEXT NOT NULL, `before_id` TEXT NOT NULL, `seq_num` INTEGER NOT NULL, " +
                "`is_active` INTEGER NOT NULL)"
        )
    }
}

/**
 * Two schema changes that both landed at v4:
 *
 * 1. #1751 — retire the two-tier route-favorite model: `routes.favorite` becomes the single source of
 *    truth (matching `stops.favorite`) and the authoritative `route_headsign_favorites` table is
 *    dropped. First reconcile the mirror from the authoritative table — set `favorite = 1` for any
 *    route with an active (non-excluded) headsign favorite — so a drifted mirror can't silently drop a
 *    star, then drop the table.
 *
 * 2. #1739 — add the missing foreign-key child index on `surveys.study_id` so a parent-table write
 *    doesn't full-scan `surveys`.
 *
 * `stops`/`routes`/`surveys` columns are otherwise untouched, so the v4 schema differs from v3 only by
 * the dropped table and the new index.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE `routes` SET `favorite` = 1 WHERE `_id` IN " +
                "(SELECT DISTINCT `route_id` FROM `route_headsign_favorites` WHERE `exclude` = 0)"
        )
        db.execSQL("DROP TABLE IF EXISTS `route_headsign_favorites`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_surveys_study_id` ON `surveys` (`study_id`)")
    }
}

/**
 * Adds the map stop cache (#1754): `cached_stops` (a region-scoped spatial cache of nearby-stops loads,
 * separate from the user-state `stops` table) + `cached_route_types` (the icon-colour lookup). Purely
 * additive — no v4 table is touched (v4 is a frozen, released schema). The CREATE statements must match
 * the exported `5.json` verbatim or Room's identity validation fails (see AppDatabaseMigrationTest).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_stops` (`_id` TEXT NOT NULL, `code` TEXT, " +
                "`name` TEXT, `direction` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                "`location_type` INTEGER NOT NULL, `route_ids` TEXT NOT NULL, " +
                "`region_id` INTEGER NOT NULL, `last_seen` INTEGER NOT NULL, PRIMARY KEY(`_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_stops_region_id_latitude_longitude` " +
                "ON `cached_stops` (`region_id`, `latitude`, `longitude`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_stops_region_id_last_seen` " +
                "ON `cached_stops` (`region_id`, `last_seen`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_route_types` (`_id` TEXT NOT NULL, " +
                "`type` INTEGER NOT NULL, `region_id` INTEGER NOT NULL, `last_seen` INTEGER NOT NULL, " +
                "PRIMARY KEY(`_id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_route_types_region_id_last_seen` " +
                "ON `cached_route_types` (`region_id`, `last_seen`)"
        )
    }
}

/**
 * Adds `regions.otp_base_graphql_url` (#1780): the per-region OTP 2.x GraphQL endpoint. A non-null
 * value routes that region's trip planning through the OTP2 `planConnection` path; NULL (every
 * existing row) stays on OTP1 REST. Purely additive, no other v5 table/column changes.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `regions` ADD COLUMN `otp_base_graphql_url` TEXT"
        )
    }
}

/**
 * Retires the per-stop route filter (#1807-era arrivals cleanup): drops the `stop_routes_filter` table
 * now that arrivals are grouped by route and the "show only this route" filter is gone. Purely
 * subtractive — no other v6 table/column changes. Verified by AppDatabaseMigrationTest.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `stop_routes_filter`")
    }
}
