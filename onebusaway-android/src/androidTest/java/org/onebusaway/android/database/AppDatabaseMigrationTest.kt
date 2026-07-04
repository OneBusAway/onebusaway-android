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
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates that [MIGRATION_2_3] produces a schema matching the exported `3.json` and that the dead
 * recentStops `stops`/`regions` tables are dropped and replaced with the legacy-schema tables (created
 * empty — the data import from the legacy ContentProvider DB is a separate slice).
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

    private companion object {
        const val TEST_DB = "migration-test-db"
    }
}
