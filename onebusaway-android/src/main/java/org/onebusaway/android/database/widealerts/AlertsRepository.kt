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

package org.onebusaway.android.database.widealerts

import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.database.widealerts.dao.AlertDao
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/**
 * Reads/writes the region wide-alert "already shown" markers. Hilt-injected (storage-modernization
 * replaced the former `object` that reached the database singleton directly).
 * The GTFS alert fetcher reaches it via [org.onebusaway.android.app.di.DatabaseEntryPoint] from its
 * background fetch thread, so the [AlertDao] calls here are synchronous.
 */
@Singleton
class AlertsRepository @Inject constructor(
    private val alertDao: AlertDao
) {

    /** Whether an alert with [alertId] has already been recorded (shown). */
    fun isAlertExists(alertId: String): Boolean = alertDao.getAlertById(alertId) != null

    /** Records an alert as shown so it isn't surfaced again. */
    fun insertAlert(alert: AlertEntity) = alertDao.insertAlert(alert)
}
