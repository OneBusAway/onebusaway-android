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
