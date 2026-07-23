/*
 * Copyright The OneBusAway Authors.
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

package org.onebusaway.android.database.widealerts.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/**
 * DAO for the region wide-alert "already shown" markers (`alerts` table).
 *
 * These methods are intentionally blocking (not `suspend`): the only caller is the `GtfsAlerts` fetcher,
 * which runs them on its own background fetch thread. They must never be invoked on the main thread.
 */
@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts WHERE id = :alertId")
    fun getAlertById(alertId: String): AlertEntity?
}
