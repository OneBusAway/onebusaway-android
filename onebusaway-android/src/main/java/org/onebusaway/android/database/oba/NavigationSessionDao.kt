/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.database.oba

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(tableName = "navigation_sessions")
data class NavigationSessionRecord(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "plan_json") val planJson: String,
    @ColumnInfo(name = "state_json") val stateJson: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "log_file_path") val logFilePath: String?
)

@Dao
interface NavigationSessionDao {
    @Query("SELECT * FROM navigation_sessions ORDER BY updated_at_ms DESC LIMIT 1")
    suspend fun active(): NavigationSessionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: NavigationSessionRecord)

    @Query("DELETE FROM navigation_sessions")
    suspend fun clear()

    @Transaction
    suspend fun replace(record: NavigationSessionRecord) {
        clear()
        upsert(record)
    }

    @Query(
        "UPDATE navigation_sessions SET state_json = :stateJson, updated_at_ms = :updatedAtMs, " +
            "log_file_path = :logFilePath WHERE session_id = :sessionId"
    )
    suspend fun updateState(
        sessionId: String,
        stateJson: String,
        updatedAtMs: Long,
        logFilePath: String?
    )
}
