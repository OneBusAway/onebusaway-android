/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.database.oba

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import java.io.File
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.database.AppDatabase
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * One-time migration of the legacy `ObaProvider` ContentProvider data (the separate
 * `<applicationId>.db` SQLite file) into the unified Room database (storage-modernization).
 *
 * Reads the legacy file defensively — each column is looked up by name and missing columns default —
 * so a file left at any historical schema version (e.g. a user who skipped releases) imports without a
 * "no such column" failure, and no copy of the legacy 34-step upgrade chain is needed. The only
 * fidelity caveat is a pre-v30 trips table (which the app itself dropped-and-recreated on upgrade):
 * such rows import with their newer columns defaulted, which is harmless for the stale reminders they'd
 * represent.
 *
 * The write is one transaction ([LegacyImportDao.replaceAll]); the legacy file is deleted afterward and
 * a DataStore flag records completion. Both the file-existence guard and the clear-then-insert make a
 * crashed/retried import idempotent, so the flag is an optimization, not a correctness dependency.
 */
class LegacyDataImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val prefs: PreferencesRepository,
) {

    /** Imports the legacy data once, then deletes the legacy file. No-op if already done or absent. */
    suspend fun importIfNeeded() {
        if (prefs.getBoolean(IMPORT_DONE_KEY, false)) return
        val legacyFile = context.getDatabasePath(LEGACY_DB_NAME)
        if (legacyFile.exists()) {
            importFrom(legacyFile)
            SQLiteDatabase.deleteDatabase(legacyFile)
        }
        prefs.setBoolean(IMPORT_DONE_KEY, true)
    }

    /** Reads every legacy table and writes it into Room in one transaction. Visible for testing. */
    suspend fun importFrom(legacyFile: File) {
        SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { legacy ->
            database.legacyImportDao().replaceAll(readAll(legacy))
        }
    }

    /**
     * Imports the rogue `study-survey-db` file (the old separate survey Room database) into the unified
     * database once, then deletes it. Read raw so the old file's Room schema version is irrelevant (it
     * had no migrations, so opening it as the now-v3 [AppDatabase] would otherwise crash).
     */
    suspend fun importSurveyDbIfNeeded() {
        if (prefs.getBoolean(SURVEY_IMPORT_DONE_KEY, false)) return
        val surveyFile = context.getDatabasePath(SURVEY_DB_NAME)
        if (surveyFile.exists()) {
            importSurveyFrom(surveyFile)
            SQLiteDatabase.deleteDatabase(surveyFile)
        }
        prefs.setBoolean(SURVEY_IMPORT_DONE_KEY, true)
    }

    /** Reads studies/surveys from a legacy survey DB file and writes them into Room. Visible for testing. */
    suspend fun importSurveyFrom(surveyFile: File) {
        val (studies, surveys) =
            SQLiteDatabase.openDatabase(surveyFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val studies = db.read("studies") {
                    Study(
                        study_id = int("study_id") ?: return@read null,
                        name = str("name").orEmpty(),
                        description = str("description").orEmpty(),
                        is_subscribed = (int("is_subscribed") ?: 0) != 0,
                    )
                }
                val surveys = db.read("surveys") {
                    Survey(
                        survey_id = int("survey_id") ?: return@read null,
                        study_id = int("study_id") ?: return@read null,
                        name = str("name").orEmpty(),
                        state = int("state") ?: 0,
                    )
                }
                studies to surveys
            }
        // Studies before surveys (the surveys -> studies foreign key). Drop any survey whose study was
        // skipped by the defensive read (a malformed study row) so one orphan can't FK-abort the whole
        // survey import.
        val studyIds = studies.mapTo(HashSet()) { it.study_id }
        database.withTransaction {
            studies.forEach { database.studiesDao().insertStudy(it) }
            surveys.filter { it.study_id in studyIds }
                .forEach { database.surveysDao().insertSurvey(it) }
        }
    }

    private fun readAll(db: SQLiteDatabase) = LegacyData(
        stops = db.read("stops") {
        StopRecord(
            id = str("_id") ?: return@read null,
            code = str("code").orEmpty(),
            name = str("name").orEmpty(),
            direction = str("direction").orEmpty(),
            useCount = int("use_count") ?: 0,
            latitude = dbl("latitude") ?: 0.0,
            longitude = dbl("longitude") ?: 0.0,
            userName = str("user_name"),
            accessTime = long("access_time"),
            favorite = int("favorite"),
            regionId = long("region_id"),
        )
        },
        routes = db.read("routes") {
        RouteRecord(
            id = str("_id") ?: return@read null,
            shortName = str("short_name").orEmpty(),
            longName = str("long_name"),
            useCount = int("use_count") ?: 0,
            userName = str("user_name"),
            accessTime = long("access_time"),
            favorite = int("favorite"),
            url = str("url"),
            regionId = long("region_id"),
        )
        },
        trips = db.read("trips") {
        TripRecord(
            id = str("_id") ?: return@read null,
            stopId = str("stop_id") ?: return@read null,
            routeId = str("route_id"),
            departure = int("departure") ?: 0,
            headsign = str("headsign"),
            name = str("name").orEmpty(),
            reminder = int("reminder") ?: 0,
            alarmDeletePath = str("alarm_delete_path").orEmpty(),
            serviceDate = long("service_date") ?: 0,
            stopSequence = int("stop_sequence") ?: 0,
            tripId = str("trip_id").orEmpty(),
            vehicleId = str("vehicle_id"),
        )
        },
        stopRouteFilters = db.read("stop_routes_filter") {
        StopRouteFilterRecord(
            stopId = str("stop_id") ?: return@read null,
            routeId = str("route_id") ?: return@read null,
        )
        },
        tripAlerts = db.read("trip_alerts") {
        TripAlertRecord(
            id = long("_id") ?: 0,
            tripId = str("trip_id") ?: return@read null,
            stopId = str("stop_id") ?: return@read null,
            startTime = long("start_time") ?: 0,
            state = int("state") ?: 0,
        )
        },
        serviceAlerts = db.read("service_alerts") {
        ServiceAlertRecord(
            id = str("_id") ?: return@read null,
            markedReadTime = long("marked_read_time"),
            hidden = int("hidden"),
        )
        },
        regions = db.read("regions") {
        RegionRecord(
            id = long("_id") ?: return@read null,
            name = str("name").orEmpty(),
            obaBaseUrl = str("oba_base_url").orEmpty(),
            siriBaseUrl = str("siri_base_url").orEmpty(),
            language = str("lang").orEmpty(),
            contactEmail = str("contact_email").orEmpty(),
            supportsObaDiscovery = int("supports_api_discovery") ?: 0,
            supportsObaRealtime = int("supports_api_realtime") ?: 0,
            supportsSiriRealtime = int("supports_siri_realtime") ?: 0,
            twitterUrl = str("twitter_url"),
            experimental = int("experimental"),
            stopInfoUrl = str("stop_info_url"),
            otpBaseUrl = str("otp_base_url"),
            otpContactEmail = str("otp_contact_email"),
            supportsOtpBikeshare = int("supports_otp_bikeshare"),
            supportsEmbeddedSocial = int("supports_embedded_social"),
            paymentAndroidAppId = str("payment_android_app_id"),
            paymentWarningTitle = str("payment_warning_title"),
            paymentWarningBody = str("payment_warning_body"),
            sidecarBaseUrl = str("sidecar_base_url"),
            plausibleAnalyticsServerUrl = str("plausible_analytics_server_url"),
            umamiAnalyticsUrl = str("umami_analytics_url"),
            umamiAnalyticsId = str("umami_analytics_id"),
        )
        },
        regionBounds = db.read("region_bounds") {
        RegionBoundRecord(
            id = long("_id") ?: 0,
            regionId = long("region_id") ?: return@read null,
            latitude = dbl("lat") ?: 0.0,
            longitude = dbl("lon") ?: 0.0,
            latSpan = dbl("lat_span") ?: 0.0,
            lonSpan = dbl("lon_span") ?: 0.0,
        )
        },
        open311Servers = db.read("open311_servers") {
        Open311ServerRecord(
            id = long("_id") ?: 0,
            regionId = long("region_id") ?: return@read null,
            jurisdiction = str("jurisdiction"),
            apiKey = str("api_key").orEmpty(),
            baseUrl = str("open311_base_url").orEmpty(),
        )
        },
        routeHeadsignFavorites = db.read("route_headsign_favorites") {
        RouteHeadsignFavoriteRecord(
            routeId = str("route_id") ?: return@read null,
            headsign = str("headsign").orEmpty(),
            stopId = str("stop_id").orEmpty(),
            exclude = int("exclude") ?: 0,
        )
        },
        navStops = db.read("nav_stops") {
        NavStopRecord(
            id = long("_id") ?: 0,
            navId = str("nav_id") ?: return@read null,
            startTime = long("start_time") ?: 0,
            tripId = str("trip_id").orEmpty(),
            destinationId = str("destination_id").orEmpty(),
            beforeId = str("before_id").orEmpty(),
            sequence = int("seq_num") ?: 0,
            active = int("is_active") ?: 0,
        )
        },
    )

    private companion object {
        val LEGACY_DB_NAME = "${BuildConfig.APPLICATION_ID}.db"
        const val SURVEY_DB_NAME = "study-survey-db"
        const val IMPORT_DONE_KEY = "legacy_oba_import_done"
        const val SURVEY_IMPORT_DONE_KEY = "legacy_survey_import_done"
    }
}

/**
 * Reads every row of [table] (skipping the table entirely if it doesn't exist), mapping each row with
 * [map]; a null return from [map] drops that row. Returns an empty list for a missing table so a
 * legacy file at any historical schema version imports cleanly.
 */
private inline fun <T> SQLiteDatabase.read(table: String, map: Cursor.() -> T?): List<T> {
    if (!tableExists(table)) return emptyList()
    return query(table, null, null, null, null, null, null).use { c ->
        buildList { while (c.moveToNext()) c.map()?.let { add(it) } }
    }
}

private fun SQLiteDatabase.tableExists(table: String): Boolean =
    rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
        .use { it.moveToFirst() }

private fun Cursor.str(name: String): String? =
    columnIndex(name)?.takeIf { !isNull(it) }?.let { getString(it) }

private fun Cursor.int(name: String): Int? =
    columnIndex(name)?.takeIf { !isNull(it) }?.let { getInt(it) }

private fun Cursor.long(name: String): Long? =
    columnIndex(name)?.takeIf { !isNull(it) }?.let { getLong(it) }

private fun Cursor.dbl(name: String): Double? =
    columnIndex(name)?.takeIf { !isNull(it) }?.let { getDouble(it) }

/** The column's index, or null when the legacy file's schema version predates that column. */
private fun Cursor.columnIndex(name: String): Int? = getColumnIndex(name).takeIf { it >= 0 }
