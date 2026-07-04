/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.onebusaway.android.R
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.database.AppDatabase
import org.onebusaway.android.database.oba.LegacyDataImporter

/**
 * Backs up and restores the app's Room database (`app_database`) to/from a user-picked file.
 *
 * Backup writes a byte copy after folding the WAL into the main file, so the copy captures committed
 * writes. Restore sniffs the file and routes both formats through the same defensive [LegacyDataImporter],
 * which merges the backup's rows into the *live* Room database in a single transaction — a Room-format
 * backup replaces every table, while an older legacy ContentProvider-format backup replaces only the 11
 * legacy tables. Nothing is closed or file-swapped, so Hilt's [AppDatabase] singleton (and every DAO the
 * repositories captured from it) stays valid and no process restart is needed.
 */
object Backup {

    const val FILE_NAME = "OneBusAway.backup"

    private const val TAG = "Backup"

    private fun dbFile(context: Context): File =
        context.getDatabasePath(AppDatabase.DATABASE_NAME)

    @JvmStatic
    fun backup(context: Context, uri: Uri) {
        try {
            // Fold committed WAL pages into the main file so the byte copy captures the latest state.
            DatabaseEntryPoint.get(context).appDatabase().openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            // A null stream means the destination couldn't be opened — treat that as a failure, not a
            // silent success (otherwise the success Toast would fire without any bytes written).
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open output stream for backup: $uri")
            out.use { FileInputStream(dbFile(context)).use { input -> input.copyTo(it) } }
            Toast.makeText(
                context, context.getString(R.string.preferences_db_saved), Toast.LENGTH_LONG
            ).show()
            Log.d(TAG, "Database backup saved successfully to: $uri")
        } catch (e: IOException) {
            Toast.makeText(
                context,
                context.getString(R.string.preferences_db_save_error, e.message),
                Toast.LENGTH_LONG
            ).show()
            Log.e(TAG, "Error saving database backup", e)
        }
    }

    /**
     * Restores [uri] into the live app database in place. Both formats are merged via
     * [LegacyDataImporter] in a single transaction (rare, user-initiated, so blocking) — no file swap and
     * no close, so no consumer is left holding a stale/closed [AppDatabase] and the process is never
     * restarted.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun restore(context: Context, uri: Uri) {
        val backupFile = uriToTempFile(context, uri)
            ?: throw IOException("Could not read backup file")
        try {
            val importer = DatabaseEntryPoint.get(context).legacyDataImporter()
            if (isRoomBackup(backupFile)) {
                mergeBackup { importer.importRoomBackupFrom(backupFile) }
            } else {
                // A legacy ContentProvider-format backup: merge only the 11 legacy tables into the
                // current Room DB via the same importer used for the one-time migration.
                mergeBackup { importer.importFrom(backupFile) }
            }
        } finally {
            backupFile.delete()
        }
    }

    private fun mergeBackup(merge: suspend () -> Unit) {
        try {
            runBlocking { merge() }
        } catch (e: Exception) {
            // The merge is one transaction, so an incompatible backup — an unreadable file, or a schema/
            // FK mismatch surfacing mid-write — rolls back with the live database untouched. Nothing was
            // swapped and the AppDatabase was never closed, so there is nothing to roll back on disk.
            // Wrapping as IOException lets BackupUtils.doRestore() show the restore-error toast instead
            // of crashing on an unchecked exception.
            throw IOException("Restored backup is not a compatible database", e)
        }
    }

    /** True if [file] is a Room-format database (has Room's identity table), vs a legacy provider DB. */
    private fun isRoomBackup(file: File): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'",
                null
            ).use { it.count > 0 }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not sniff backup schema", e)
        false
    }
}
