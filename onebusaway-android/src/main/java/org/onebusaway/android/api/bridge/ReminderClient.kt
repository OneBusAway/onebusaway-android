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
package org.onebusaway.android.api.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.onebusaway.android.app.di.NetworkEntryPoint

/**
 * Java-callable bridge over [ReminderWebService] for the alarm DELETE, which is invoked from the
 * static `ReminderUtils` (not injectable). Replaces the hand-rolled `ObaReminderDeleteRequest`: a
 * fire-and-forget background DELETE whose only outcome is a log line, matching the legacy behavior
 * (the caller deletes the local Trips row regardless).
 */
object ReminderClient {

    private const val TAG = "ReminderClient"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Fires a best-effort DELETE of the alarm at [alarmDeletePath]; no-op if the path is blank. */
    @JvmStatic
    fun deleteAlarm(context: Context, alarmDeletePath: String?) {
        if (alarmDeletePath.isNullOrEmpty()) return
        val service = NetworkEntryPoint.getReminder(context)
        scope.launch {
            runCatching { service.deleteAlarm(alarmDeletePath) }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Delete request successful")
                    } else {
                        Log.d(TAG, "Delete request failed: HTTP ${response.code()}")
                    }
                }
                .onFailure { Log.d(TAG, "Delete request failed: $it") }
        }
    }
}
