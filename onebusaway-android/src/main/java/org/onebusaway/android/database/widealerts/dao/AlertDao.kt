package org.onebusaway.android.database.widealerts.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/** Data Access Object (DAO) for the `AlertEntity` class. */

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("SELECT * FROM alerts WHERE id = :alertId")
    suspend fun getAlertById(alertId: String): AlertEntity?

    @Query("SELECT * FROM alerts")
    suspend fun getAllAlerts(): List<AlertEntity>
}