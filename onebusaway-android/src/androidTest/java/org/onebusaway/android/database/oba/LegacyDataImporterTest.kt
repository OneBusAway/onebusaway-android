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
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.database.AppDatabase
import org.onebusaway.android.database.survey.entity.Study
import org.onebusaway.android.database.survey.entity.Survey
import org.onebusaway.android.database.widealerts.entity.AlertEntity
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Verifies the one-time legacy-to-Room data import preserves user data and is idempotent. The seeded
 * legacy `regions` table deliberately omits the late (v31–v33) columns to also exercise the importer's
 * defensive missing-column reads (an old legacy file must import without a "no such column" failure).
 */
@RunWith(AndroidJUnit4::class)
class LegacyDataImporterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var room: AppDatabase
    private lateinit var legacyFile: File
    private lateinit var importer: LegacyDataImporter

    @Before
    fun setUp() {
        room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        legacyFile = File.createTempFile("legacy-oba", ".db", context.cacheDir)
        importer = LegacyDataImporter(context, room, NoopPreferences)
        seedLegacy(legacyFile)
    }

    @After
    fun tearDown() {
        room.close()
        legacyFile.delete()
    }

    @Test
    fun importsAllTablesAndPreservesUserData() = runBlocking {
        importer.importFrom(legacyFile)

        assertEquals(1, count("stops"))
        assertEquals(1, count("routes"))
        assertEquals(1, count("trips"))
        assertEquals(1, count("stop_routes_filter"))
        assertEquals(1, count("trip_alerts"))
        assertEquals(1, count("service_alerts"))
        assertEquals(1, count("regions"))
        assertEquals(1, count("region_bounds"))
        assertEquals(1, count("open311_servers"))
        assertEquals(1, count("route_headsign_favorites"))
        assertEquals(1, count("nav_stops"))

        // Load-bearing user state survives.
        assertEquals(1, scalarInt("SELECT favorite FROM stops WHERE _id='1_100'"))
        assertEquals("My Stop", scalarStr("SELECT user_name FROM stops WHERE _id='1_100'"))
        assertEquals(5, scalarInt("SELECT reminder FROM trips WHERE _id='trip1'"))
        assertEquals(1, scalarInt("SELECT hidden FROM service_alerts WHERE _id='sit1'"))
        // regions._id = 0 is preserved (not reassigned) and region_bounds.region_id still points at it.
        assertEquals(0, scalarInt("SELECT _id FROM regions LIMIT 1"))
        assertEquals(0, scalarInt("SELECT region_id FROM region_bounds LIMIT 1"))
        // A late column missing from the seeded legacy file imported as NULL, not a crash.
        assertEquals(null, scalarStr("SELECT umami_analytics_url FROM regions LIMIT 1"))
    }

    @Test
    fun importFailure_rollsBackLeavingRoomEmpty_soRetryIsClean() = runBlocking {
        // Corrupt the seeded file so a region_bounds row references a region that isn't present: the
        // import will hit a FOREIGN KEY failure part-way through.
        SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE region_bounds SET region_id = 999")
        }

        var threw = false
        try {
            importer.importFrom(legacyFile)
        } catch (e: Exception) {
            threw = true
        }

        // The whole replaceAll is one transaction, so a mid-import failure rolls everything back — no
        // partial/corrupt state. That is what lets ImportGate swallow the failure and leave the legacy
        // file in place for a clean retry next launch instead of crash-looping. See ImportGate.
        assertEquals(true, threw)
        assertEquals(0, count("regions"))
        assertEquals(0, count("stops"))
        assertEquals(0, count("region_bounds"))
    }

    @Test
    fun importsSurveyDb() = runBlocking {
        val surveyFile = File.createTempFile("study-survey", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(surveyFile, null).use { db ->
                db.execSQL(
                    "CREATE TABLE studies (study_id INTEGER PRIMARY KEY, name TEXT, description TEXT, " +
                        "is_subscribed INTEGER)"
                )
                db.execSQL("INSERT INTO studies VALUES (7,'Study','Desc',1)")
                db.execSQL(
                    "CREATE TABLE surveys (survey_id INTEGER PRIMARY KEY, study_id INTEGER, name TEXT, " +
                        "state INTEGER)"
                )
                db.execSQL("INSERT INTO surveys VALUES (3,7,'Survey',1)")
            }
            importer.importSurveyFrom(surveyFile)

            assertEquals(1, count("studies"))
            assertEquals(1, count("surveys"))
            assertEquals(7, scalarInt("SELECT study_id FROM surveys WHERE survey_id=3"))

            // Idempotent: a second import (e.g. an ImportGate retry) must not duplicate rows — the
            // primary keys dedupe to one row (studies via IGNORE, surveys via REPLACE).
            importer.importSurveyFrom(surveyFile)
            assertEquals(1, count("studies"))
            assertEquals(1, count("surveys"))
        } finally {
            surveyFile.delete()
        }
    }

    @Test
    fun importSurveyFrom_skipsSurveysWithMissingStudy() = runBlocking {
        val surveyFile = File.createTempFile("study-survey-orphan", ".db", context.cacheDir)
        try {
            SQLiteDatabase.openOrCreateDatabase(surveyFile, null).use { db ->
                db.execSQL(
                    "CREATE TABLE studies (study_id INTEGER PRIMARY KEY, name TEXT, description TEXT, " +
                        "is_subscribed INTEGER)"
                )
                db.execSQL("INSERT INTO studies VALUES (7,'Study','Desc',1)")
                db.execSQL(
                    "CREATE TABLE surveys (survey_id INTEGER PRIMARY KEY, study_id INTEGER, name TEXT, " +
                        "state INTEGER)"
                )
                db.execSQL("INSERT INTO surveys VALUES (3,7,'Valid',1)")   // references the imported study
                db.execSQL("INSERT INTO surveys VALUES (4,99,'Orphan',1)") // references a missing study
            }
            importer.importSurveyFrom(surveyFile)

            // The orphan (study_id=99) is dropped rather than FK-aborting the whole import; the valid
            // survey survives.
            assertEquals(1, count("surveys"))
            assertEquals(0, scalarInt("SELECT count(*) FROM surveys WHERE survey_id=4"))
        } finally {
            surveyFile.delete()
        }
    }

    @Test
    fun importRoomBackup_replacesEveryTableIncludingSurveyAndAlerts() = runBlocking {
        // Live DB starts with rows that a full Room-format restore must replace/clear: a stale study +
        // survey and a stale wide-alert marker that are absent from the backup.
        room.studiesDao().insertStudy(Study(1, "Stale", "Old", false))
        room.surveysDao().insertSurvey(Survey(1, 1, "Stale Survey", 0))
        room.alertsDao().insertAlert(AlertEntity("stale-alert"))

        // The seeded file already holds the 11 legacy tables; add the survey + alert tables so it looks
        // like a full Room-format backup (byte copy of the unified AppDatabase file).
        SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "CREATE TABLE studies (study_id INTEGER PRIMARY KEY, name TEXT, description TEXT, " +
                    "is_subscribed INTEGER)"
            )
            db.execSQL("INSERT INTO studies VALUES (7,'Study','Desc',1)")
            db.execSQL(
                "CREATE TABLE surveys (survey_id INTEGER PRIMARY KEY, study_id INTEGER, name TEXT, " +
                    "state INTEGER)"
            )
            db.execSQL("INSERT INTO surveys VALUES (3,7,'Survey',1)")
            db.execSQL("CREATE TABLE alerts (id TEXT PRIMARY KEY)")
            db.execSQL("INSERT INTO alerts VALUES ('a1')")
        }

        importer.importRoomBackupFrom(legacyFile)

        // Legacy tables imported.
        assertEquals(1, count("stops"))
        assertEquals(1, count("regions"))
        // Survey + alert tables imported and the stale live rows are gone (whole-DB replace semantics).
        assertEquals(1, count("studies"))
        assertEquals(7, scalarInt("SELECT study_id FROM studies LIMIT 1"))
        assertEquals(1, count("surveys"))
        assertEquals(3, scalarInt("SELECT survey_id FROM surveys LIMIT 1"))
        assertEquals(1, count("alerts"))
        assertEquals("a1", scalarStr("SELECT id FROM alerts LIMIT 1"))
    }

    @Test
    fun importRoomBackup_rollsBackOnIncompatibleBackup_leavingLiveDataUntouched() = runBlocking {
        // A pre-existing live row that must survive a failed restore.
        room.alertsDao().insertAlert(AlertEntity("keep-me"))

        // Corrupt the seeded file so a region_bounds row references a missing region: the merge hits a
        // FOREIGN KEY failure part-way through and the single transaction rolls everything back.
        SQLiteDatabase.openDatabase(legacyFile.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("CREATE TABLE alerts (id TEXT PRIMARY KEY)")
            it.execSQL("INSERT INTO alerts VALUES ('a1')")
            it.execSQL("UPDATE region_bounds SET region_id = 999")
        }

        var threw = false
        try {
            importer.importRoomBackupFrom(legacyFile)
        } catch (e: Exception) {
            threw = true
        }

        assertEquals(true, threw)
        // Nothing from the backup landed, and the pre-existing live data is intact.
        assertEquals(0, count("stops"))
        assertEquals(0, count("regions"))
        assertEquals(1, count("alerts"))
        assertEquals("keep-me", scalarStr("SELECT id FROM alerts LIMIT 1"))
    }

    @Test
    fun reimportIsIdempotent() = runBlocking {
        importer.importFrom(legacyFile)
        importer.importFrom(legacyFile)

        assertEquals(1, count("stops"))
        assertEquals(1, count("stop_routes_filter"))
        assertEquals(1, count("route_headsign_favorites"))
    }

    private fun count(table: String): Int = scalarInt("SELECT count(*) FROM $table")

    private fun scalarInt(sql: String): Int =
        room.query(SimpleSQLiteQuery(sql)).use { it.moveToFirst(); it.getInt(0) }

    private fun scalarStr(sql: String): String? =
        room.query(SimpleSQLiteQuery(sql)).use {
            it.moveToFirst()
            if (it.isNull(0)) null else it.getString(0)
        }

    /** Builds a legacy ObaProvider-shaped SQLite file with one meaningful row per table. */
    private fun seedLegacy(file: File) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE stops (_id VARCHAR PRIMARY KEY, code VARCHAR, name VARCHAR, " +
                    "direction CHAR[2], use_count INTEGER, latitude DOUBLE, longitude DOUBLE, " +
                    "user_name VARCHAR, access_time INTEGER, favorite INTEGER, region_id INTEGER)"
            )
            db.execSQL(
                "INSERT INTO stops VALUES ('1_100','100','Main St & 1st','N',5,47.6,-122.3," +
                    "'My Stop',1000,1,1)"
            )
            db.execSQL(
                "CREATE TABLE routes (_id VARCHAR PRIMARY KEY, short_name VARCHAR, long_name VARCHAR, " +
                    "use_count INTEGER, user_name VARCHAR, access_time INTEGER, favorite INTEGER, " +
                    "url VARCHAR, region_id INTEGER)"
            )
            db.execSQL(
                "INSERT INTO routes VALUES ('1_10','10','Downtown',3,NULL,2000,1,'http://r',1)"
            )
            db.execSQL(
                "CREATE TABLE trips (_id VARCHAR, stop_id VARCHAR, route_id VARCHAR, departure INTEGER, " +
                    "headsign VARCHAR, name VARCHAR, reminder INTEGER, alarm_delete_path VARCHAR, " +
                    "service_date INTEGER, stop_sequence INTEGER, trip_id VARCHAR, vehicle_id VARCHAR)"
            )
            db.execSQL(
                "INSERT INTO trips VALUES ('trip1','1_100','1_10',480,'Downtown','My Trip',5," +
                    "'/delete',123456,2,'trip1','veh1')"
            )
            db.execSQL(
                "CREATE TABLE stop_routes_filter (stop_id VARCHAR, route_id VARCHAR)"
            )
            db.execSQL("INSERT INTO stop_routes_filter VALUES ('1_100','1_10')")
            db.execSQL(
                "CREATE TABLE trip_alerts (_id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id VARCHAR, " +
                    "stop_id VARCHAR, start_time INTEGER, state INTEGER)"
            )
            db.execSQL("INSERT INTO trip_alerts (trip_id, stop_id, start_time, state) " +
                "VALUES ('trip1','1_100',999,1)")
            db.execSQL(
                "CREATE TABLE service_alerts (_id VARCHAR PRIMARY KEY, marked_read_time INTEGER, " +
                    "hidden INTEGER)"
            )
            db.execSQL("INSERT INTO service_alerts VALUES ('sit1',5000,1)")
            // Regions: v17-era columns only (no sidecar/plausible/umami) to exercise defensive reads.
            db.execSQL(
                "CREATE TABLE regions (_id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR, " +
                    "oba_base_url VARCHAR, siri_base_url VARCHAR, lang VARCHAR, contact_email VARCHAR, " +
                    "supports_api_discovery INTEGER, supports_api_realtime INTEGER, " +
                    "supports_siri_realtime INTEGER)"
            )
            db.execSQL(
                // _id = 0 on purpose: Tampa Bay is region 0 in production, and a 0-valued autoGenerate
                // PK would be reassigned by SQLite, orphaning the region_bounds FK. Regression for that.
                "INSERT INTO regions (_id, name, oba_base_url, siri_base_url, lang, contact_email, " +
                    "supports_api_discovery, supports_api_realtime, supports_siri_realtime) " +
                    "VALUES (0,'Puget Sound','http://oba','http://siri','en','e@x.com',1,1,0)"
            )
            db.execSQL(
                "CREATE TABLE region_bounds (_id INTEGER PRIMARY KEY AUTOINCREMENT, region_id INTEGER, " +
                    "lat REAL, lon REAL, lat_span REAL, lon_span REAL)"
            )
            db.execSQL("INSERT INTO region_bounds (region_id, lat, lon, lat_span, lon_span) " +
                "VALUES (0,47.6,-122.3,0.5,0.5)")
            db.execSQL(
                "CREATE TABLE open311_servers (_id INTEGER PRIMARY KEY AUTOINCREMENT, region_id INTEGER, " +
                    "jurisdiction VARCHAR, api_key VARCHAR, open311_base_url VARCHAR)"
            )
            db.execSQL("INSERT INTO open311_servers (region_id, jurisdiction, api_key, open311_base_url) " +
                "VALUES (1,'jur','key','http://311')")
            db.execSQL(
                "CREATE TABLE route_headsign_favorites (route_id VARCHAR, headsign VARCHAR, " +
                    "stop_id VARCHAR, exclude INTEGER)"
            )
            db.execSQL("INSERT INTO route_headsign_favorites VALUES ('1_10','Downtown','1_100',0)")
            db.execSQL(
                "CREATE TABLE nav_stops (_id INTEGER PRIMARY KEY AUTOINCREMENT, nav_id VARCHAR, " +
                    "start_time INTEGER, trip_id VARCHAR, destination_id VARCHAR, before_id VARCHAR, " +
                    "seq_num INTEGER, is_active INTEGER)"
            )
            db.execSQL("INSERT INTO nav_stops (nav_id, start_time, trip_id, destination_id, before_id, " +
                "seq_num, is_active) VALUES ('nav',111,'trip1','1_100','1_99',0,1)")
        }
    }
}

/** Minimal stub — [LegacyDataImporter.importFrom] under test does not touch preferences. */
private object NoopPreferences : PreferencesRepository {
    override fun observeBoolean(keyRes: Int, default: Boolean) = throw NotImplementedError()
    override fun observeString(keyRes: Int, default: String?) = throw NotImplementedError()
    override fun observeChanges() = throw NotImplementedError()
    override fun getBoolean(keyRes: Int, default: Boolean) = default
    override fun getBoolean(key: String, default: Boolean) = default
    override fun getString(keyRes: Int, default: String?) = default
    override fun getString(key: String, default: String?) = default
    override fun getInt(keyRes: Int, default: Int) = default
    override fun getInt(key: String, default: Int) = default
    override fun getLong(keyRes: Int, default: Long) = default
    override fun getLong(key: String, default: Long) = default
    override fun getFloat(keyRes: Int, default: Float) = default
    override fun getFloat(key: String, default: Float) = default
    override fun getAppLaunchCount() = 0
    override fun setBoolean(keyRes: Int, value: Boolean) = Unit
    override fun setBoolean(key: String, value: Boolean) = Unit
    override fun setString(keyRes: Int, value: String?) = Unit
    override fun setString(key: String, value: String?) = Unit
    override fun setInt(keyRes: Int, value: Int) = Unit
    override fun setInt(key: String, value: Int) = Unit
    override fun setLong(keyRes: Int, value: Long) = Unit
    override fun setLong(key: String, value: Long) = Unit
    override fun setFloat(keyRes: Int, value: Float) = Unit
    override fun setFloat(key: String, value: Float) = Unit
}
