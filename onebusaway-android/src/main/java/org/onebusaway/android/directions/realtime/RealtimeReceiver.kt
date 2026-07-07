/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.onebusaway.android.directions.util.OTPConstants

/**
 * Entry point for the trip-plan-monitoring broadcasts (from [RealtimeChecker.start] and the
 * [android.app.AlarmManager] check/reschedule alarms). Replaces the retired
 * `WakefulBroadcastReceiver` + `IntentService` pair:
 *
 *  - `START_CHECKS` only reads the bundle and (re)schedules alarms — no network — so it runs
 *    synchronously here (the receiver's `onReceive` already holds a wakelock until it returns).
 *  - `CHECK_TRIP_TIME` does the blocking network re-plan, so it's handed to [RealtimeCheckWorker];
 *    WorkManager provides the background thread and the wakelock the old wakeful service did.
 *    The check bundle is simplified to primitives ([RealtimeChecker.getSimplifiedBundle]), so it
 *    round-trips cleanly through WorkManager [androidx.work.Data].
 */
class RealtimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.extras ?: Bundle()
        // Use the application context: the work outlives this receiver call.
        val appContext = context.applicationContext
        when (intent.action) {
            OTPConstants.INTENT_START_CHECKS -> RealtimeChecker(appContext).handleStartChecks(bundle)

            OTPConstants.INTENT_CHECK_TRIP_TIME -> {
                val request = OneTimeWorkRequestBuilder<RealtimeCheckWorker>()
                    .setInputData(bundle.toWorkData())
                    .build()
                // KEEP: if a check is already queued/running, skip this tick rather than pile up
                // (checks are idempotent re-plans; the next alarm fires ~60 s later).
                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(RealtimeCheckWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
