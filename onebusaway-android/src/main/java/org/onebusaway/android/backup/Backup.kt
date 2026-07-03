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
import org.apache.commons.io.FileUtils
import org.onebusaway.android.R
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.database.DatabaseProvider

/**
 * Backs up and restores the app's Room database (`app_database`) to/from a user-picked file.
 *
 * Backup writes a byte copy after folding the WAL into the main file, so the copy captures committed
 * writes. Restore sniffs the file: a Room-format backup replaces the database file directly, while an
 * older legacy ContentProvider-format backup is routed through the same defensive [LegacyDataImporter]
 * that migrates the live legacy DB, merging its rows into the current Room database.
 */
object Backup {

    const val FILE_NAME = "OneBusAway.backup"

    private const val TAG = "Backup"

    private fun dbFile(context: Context): File =
        context.getDatabasePath(DatabaseProvider.DATABASE_NAME)

    @JvmStatic
    fun backup(context: Context, uri: Uri) {
        try {
            // Fold committed WAL pages into the main file so the byte copy captures the latest state.
            DatabaseProvider.getDatabase(context).openHelper.writableDatabase
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
     * Restores [uri] into the app database, returning true when the caller must restart the process to
     * finish.
     *
     * A Room-format backup is restored by swapping the database *file*, which forces a
     * [DatabaseProvider.closeDatabase]/reopen and so a brand-new [org.onebusaway.android.database.AppDatabase]
     * instance. The Hilt object graph still holds the old (now-closed) singleton — and every repository
     * that captured a DAO from it — so those would throw on their next query. Returning true tells the
     * caller to restart the process, rebuilding the whole graph against the restored file. A
     * legacy-format backup is merged into the live instance in place (no swap, no close) and returns
     * false.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun restore(context: Context, uri: Uri): Boolean {
        val backupFile = uriToTempFile(context, uri)
            ?: throw IOException("Could not read backup file")
        return try {
            if (isRoomBackup(backupFile)) {
                restoreRoomBackup(context, backupFile)
                true
            } else {
                // A legacy ContentProvider-format backup: merge it into the current Room DB via the
                // same importer used for the one-time migration (rare, user-initiated, so blocking).
                val importer = DatabaseEntryPoint.get(context).legacyDataImporter()
                runBlocking { importer.importFrom(backupFile) }
                false
            }
        } finally {
            backupFile.delete()
        }
    }

    private fun restoreRoomBackup(context: Context, backupFile: File) {
        DatabaseProvider.closeDatabase()
        val dest = dbFile(context)
        // Snapshot the live DB (WAL already folded in by closeDatabase) so we can roll back if the
        // backup turns out to be incompatible — otherwise a mismatched-schema Room file would overwrite
        // the live database and then crash on open, bricking the app with no recovery.
        val previous = File(dest.path + ".restorebak")
        if (dest.exists()) FileUtils.copyFile(dest, previous)
        // Drop any stale WAL/SHM so the restored file isn't shadowed by the old journal.
        File(dest.path + "-wal").delete()
        File(dest.path + "-shm").delete()
        FileUtils.copyFile(backupFile, dest)
        try {
            // Force Room to open and validate the restored file's schema identity now; a mismatched or
            // corrupt backup throws here, while the previous DB is still recoverable.
            DatabaseProvider.getDatabase(context).openHelper.readableDatabase
            previous.delete()
        } catch (e: Exception) {
            DatabaseProvider.closeDatabase()
            if (previous.exists()) FileUtils.copyFile(previous, dest) else dest.delete()
            previous.delete()
            DatabaseProvider.getDatabase(context) // reopen the restored-original (or fresh) DB
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
