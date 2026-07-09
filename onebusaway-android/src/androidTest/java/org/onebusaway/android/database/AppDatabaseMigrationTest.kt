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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates that [MIGRATION_2_3] produces a schema matching the exported `3.json` and that the dead
 * recentStops `stops`/`regions` tables are dropped and replaced with the legacy-schema tables (created
 * empty — the data import from the legacy ContentProvider DB is a separate slice); and that
 * [MIGRATION_3_4] reconciles `routes.favorite` from the authoritative `route_headsign_favorites` table
 * before dropping it (#1751) and adds the `surveys.study_id` foreign-key child index (#1739).
 */
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
