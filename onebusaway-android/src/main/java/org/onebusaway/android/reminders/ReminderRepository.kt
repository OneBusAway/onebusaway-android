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
package org.onebusaway.android.reminders

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.onebusaway.android.api.bridge.ReminderClient
import org.onebusaway.android.app.di.AppScope
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.TripDao
import org.onebusaway.android.util.ReminderUtils

/**
 * The saved trip reminders (the legacy `trips` table), replacing the storage half of `ReminderUtils`.
 * Reads/writes go through [TripDao] after the one-time import gate; the server-side alarm deletion goes
 * through [ReminderClient]. Fire-and-forget variants run on the application scope so an Activity/Service
 * caller (or a Compose callback that navigates immediately after) doesn't need its own coroutine scope.
 */
@Singleton
class ReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val tripDao: TripDao,
    private val importGate: ImportGate,
) {

    /**
     * Cancels the reminder for this trip/stop: server-deletes the alarm (fire-and-forget via
     * [ReminderClient]) and removes the trip row (the legacy `requestDeleteAlarm`).
     */
    suspend fun deleteReminder(tripId: String, stopId: String) {
        importGate.awaitReady()
        val alarmDeletePath = tripDao.getTrip(tripId, stopId)?.alarmDeletePath
        ReminderClient.deleteAlarm(context, alarmDeletePath)
        tripDao.delete(tripId, stopId)
    }

    /** [deleteReminder] on the application scope, for Activity/Service/Compose callers. */
    fun deleteReminderInBackground(tripId: String, stopId: String) {
        appScope.launch { deleteReminder(tripId, stopId) }
    }

    /**
     * Deletes the reminder referenced by an FCM `arrival_and_departure` payload (the legacy
     * `handleArrivalPayload` -> `deleteSavedReminder`: removes the row only, no server call). Runs on
     * the application scope.
     */
    fun deleteReminderFromPayload(arrivalJson: String?) {
        val tripId = ReminderUtils.getTripIdFromPayload(arrivalJson) ?: return
        val stopId = ReminderUtils.getStopIdFromPayload(arrivalJson) ?: return
        appScope.launch {
            importGate.awaitReady()
            tripDao.delete(tripId, stopId)
        }
    }
}
