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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Room access for the service-alert read/hidden state (the legacy `service_alerts` table). */
@Dao
interface ServiceAlertDao {

    /** Inserts the row only if absent (leaves an existing row's read/hidden state untouched). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: ServiceAlertRecord)

    /**
     * The rows with an explicit hide decision (HIDDEN non-null), re-emitting on every table change.
     * The reactive single-source-of-truth for hidden state the arrivals screen derives from. See #1593.
     */
    @Query("SELECT * FROM service_alerts WHERE hidden IS NOT NULL")
    fun hideDecisions(): Flow<List<ServiceAlertRecord>>

    @Query("UPDATE service_alerts SET marked_read_time = :time WHERE _id = :id")
    suspend fun updateMarkedReadTime(id: String, time: Long)

    @Query("UPDATE service_alerts SET hidden = :hidden WHERE _id = :id")
    suspend fun updateHidden(id: String, hidden: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM service_alerts WHERE _id = :id AND hidden = 1)")
    suspend fun isHidden(id: String): Boolean

    @Query("UPDATE service_alerts SET hidden = :hidden")
    suspend fun setAllHidden(hidden: Int)

    /**
     * Records/updates [id] and stamps it read, without touching its hide decision (the legacy
     * mark-as-read). [now] is supplied by the caller so this stays a stateless helper.
     */
    @Transaction
    suspend fun markRead(id: String, now: Long) {
        insertIfAbsent(ServiceAlertRecord(id = id, hidden = null))
        updateMarkedReadTime(id, now)
    }

    /** Records an explicit hide decision for [id] (true = hidden, false = shown). */
    @Transaction
    suspend fun setHidden(id: String, hidden: Boolean) {
        insertIfAbsent(ServiceAlertRecord(id = id, hidden = null))
        updateHidden(id, if (hidden) 1 else 0)
    }
}
