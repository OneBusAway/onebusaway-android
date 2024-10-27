package org.onebusaway.android.database.widealerts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.onebusaway.android.database.DatabaseProvider
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/** Provides methods to interact with the alerts database. */
object AlertsRepository {

    /**
     * Checks if an alert exists in the database.
     *
     * @param context The context to access the database.
     * @param alertId The ID of the alert to check.
     * @return True if the alert exists, false otherwise.
     */
    @JvmStatic
     fun isAlertExists(context: Context, alertId: String): Boolean {
        val db = DatabaseProvider.getDatabase(context)
        val alertDao = db.alertsDao()

        return runBlocking {
            withContext(Dispatchers.IO) {
                alertDao.getAlertById(alertId) != null
            }
        }
    }

    /**
     * Inserts a new alert into the database.
     *
     * @param context The context to access the database.
     * @param alert The `AlertEntity` object to insert.
     */
    @JvmStatic
    fun insertAlert(context: Context, alert: AlertEntity) {
        val db = DatabaseProvider.getDatabase(context)
        val alertDao = db.alertsDao()

        CoroutineScope(Dispatchers.IO).launch {
            alertDao.insertAlert(alert)
        }
    }
}