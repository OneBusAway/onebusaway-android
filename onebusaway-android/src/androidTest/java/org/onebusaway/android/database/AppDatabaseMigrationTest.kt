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
package org.onebusaway.android.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.SmokeTest

/**
 * Validates that [MIGRATION_2_3] produces a schema matching the exported `3.json` and that the dead
 * recentStops `stops`/`regions` tables are dropped and replaced with the legacy-schema tables (created
 * empty — the data import from the legacy ContentProvider DB is a separate slice); and that
 * [MIGRATION_3_4] reconciles `routes.favorite` from the authoritative `route_headsign_favorites` table
 * before dropping it (#1751) and adds the `surveys.study_id` foreign-key child index (#1739); and that
 * [MIGRATION_5_6] adds `regions.otp_base_graphql_url` defaulting existing rows to OTP1 (#1780); and
 * that [MIGRATION_6_7] drops the retired `stop_routes_filter` table.
 */
@SmokeTest // API-23 floor smoke subset (#1818): exercises Room migrations + java.time desugaring
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2To3_dropsRecentStopsTables_andCreatesLegacyTables() {
        // v2: seed the recentStops regions/stops tables (their schema, not the legacy one).
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO regions (regionId) VALUES (1)")
            db.execSQL(
                "INSERT INTO stops (stop_id, name, regionId, timestamp) VALUES ('1_1', 'A', 1, 123)"
            )
        }

        // runMigrationsAndValidate asserts the resulting schema matches the entities/3.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // The recentStops rows are gone (tables dropped + recreated with the legacy schema), and the
        // new legacy tables exist and are empty.
        for (table in listOf("stops", "routes", "trips", "regions", "service_alerts", "nav_stops")) {
            db.query("SELECT count(*) FROM $table").use { c ->
                c.moveToFirst()
                assertEquals("expected empty $table", 0, c.getInt(0))
            }
        }
        // The new stops table has the legacy column the recentStops one lacked.
        db.query("SELECT favorite, user_name, region_id FROM stops").use { /* no-op: column resolves */ }
        db.close()
    }

    @Test
    fun migrate3To4_reconcilesRouteFavoriteFromHeadsignTable_thenDropsIt() {
        // v3: seed routes whose favorite mirror has drifted from the authoritative
        // route_headsign_favorites table.
        helper.createDatabase(TEST_DB, 3).use { db ->
            // A starred route whose mirror drifted to 0 — must be reconciled back to 1.
            db.execSQL(
                "INSERT INTO routes (_id, short_name, use_count, favorite) VALUES ('1_10', '10', 0, 0)"
            )
            // A route with only an excluded favorite — must stay unstarred.
            db.execSQL(
                "INSERT INTO routes (_id, short_name, use_count, favorite) VALUES ('1_20', '20', 0, 0)"
            )
            // A plain unstarred route with no headsign rows at all — stays 0.
            db.execSQL(
                "INSERT INTO routes (_id, short_name, use_count, favorite) VALUES ('1_30', '30', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO route_headsign_favorites (route_id, headsign, stop_id, exclude) " +
                    "VALUES ('1_10', 'Downtown', '1_100', 0)"
            )
            db.execSQL(
                "INSERT INTO route_headsign_favorites (route_id, headsign, stop_id, exclude) " +
                    "VALUES ('1_20', 'Uptown', '1_200', 1)"
            )
        }

        // runMigrationsAndValidate asserts the resulting schema matches the entities/4.json (which no
        // longer has route_headsign_favorites).
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        assertEquals(1, favoriteOf(db, "1_10")) // reconciled up from the non-excluded headsign favorite
        assertEquals(0, favoriteOf(db, "1_20")) // only excluded -> stays unstarred
        assertEquals(0, favoriteOf(db, "1_30")) // never favorited -> stays unstarred

        // The legacy table is gone.
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='route_headsign_favorites'"
        ).use { c -> assertEquals("route_headsign_favorites should be dropped", 0, c.count) }
        // #1739: the surveys foreign-key child index now exists (also covered by the schema validation
        // above, which checks the migrated schema against 4.json).
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_surveys_study_id'"
        ).use { c -> assertEquals("index_surveys_study_id should exist", 1, c.count) }
        db.close()
    }

    @Test
    fun migrate4To5_createsCacheTables() {
        helper.createDatabase(TEST_DB, 4).close()

        // runMigrationsAndValidate asserts the resulting schema matches the exported 5.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        // Both cache tables exist and are empty.
        for (table in listOf("cached_stops", "cached_route_types")) {
            db.query("SELECT count(*) FROM $table").use { c ->
                c.moveToFirst()
                assertEquals("expected empty $table", 0, c.getInt(0))
            }
        }
        // And accept a row each (columns resolve).
        db.execSQL(
            "INSERT INTO cached_stops " +
                "(_id, code, name, direction, latitude, longitude, location_type, route_ids, region_id, last_seen) " +
                "VALUES ('1_1', '1', 'A', 'N', 47.6, -122.3, 0, '', 1, 123)"
        )
        db.execSQL(
            "INSERT INTO cached_route_types (_id, type, region_id, last_seen) VALUES ('r1', 3, 1, 123)"
        )
        db.query("SELECT count(*) FROM cached_stops").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate5To6_addsOtpBaseGraphqlUrlColumn_defaultingExistingRowsToNull() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO regions (_id, name, oba_base_url, siri_base_url, lang, contact_email, " +
                    "supports_api_discovery, supports_api_realtime, supports_siri_realtime) " +
                    "VALUES (1, 'Puget Sound', '', '', '', '', 0, 0, 0)"
            )
        }

        // runMigrationsAndValidate asserts the resulting schema matches the exported 6.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query("SELECT otp_base_graphql_url FROM regions WHERE _id = 1").use { c ->
            c.moveToFirst()
            assertTrue("pre-existing region should default to OTP1 (NULL GraphQL URL)", c.isNull(0))
        }
        db.close()
    }

    @Test
    fun migrate6To7_dropsStopRoutesFilterTable() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            // A pre-existing filter row; the migration drops the whole table along with it.
            db.execSQL("INSERT INTO stop_routes_filter (stop_id, route_id) VALUES ('1_100', '1_10')")
        }

        // validateDroppedTables = true asserts stop_routes_filter is gone; schema validated vs. 7.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='stop_routes_filter'"
        ).use { c -> assertEquals("stop_routes_filter should be dropped", 0, c.count) }
        db.close()
    }

    private fun favoriteOf(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        routeId: String
    ): Int = db.query("SELECT favorite FROM routes WHERE _id='$routeId'").use {
        it.moveToFirst(); it.getInt(0)
    }

    private companion object {
        const val TEST_DB = "migration-test-db"
    }
}
