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

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs one `CHECK_TRIP_TIME` re-plan off the main thread with a wakelock, replacing the wakelock the
 * retired `WakefulBroadcastReceiver`/`IntentService` used to hold. [Worker.doWork] is already invoked
 * on a WorkManager background thread, so the blocking network plan in [RealtimeChecker.handleCheck]
 * runs directly here. Enqueued by [RealtimeReceiver] with the simplified check bundle as input data.
 */
class RealtimeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        RealtimeChecker(applicationContext).handleCheck(inputData.toBundle())
        return Result.success()
    }

    companion object {
        /** Unique-work name so overlapping check ticks coalesce (see [RealtimeReceiver]). */
        const val WORK_NAME = "realtime_check"
    }
}
