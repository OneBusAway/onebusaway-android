/*
 * Copyright (C) 2011-2026 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Open Transit Software Foundation
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
package org.onebusaway.android.ui.tripinfo

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.onebusaway.android.R
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.ui.nav.ReminderEditorArgs
import org.onebusaway.android.ui.startHomeActivity

/**
 * Launches the trip-reminder editor (create or edit a trip arrival reminder).
 *
 * The editor is a NavHost destination hosted by HomeActivity; this is no longer an Activity but a launcher
 * facade. The edit path carries only the trip/stop ids; the arrivals "set reminder" path also passes the
 * full trip context so a brand-new reminder needs no DB round-trip. This [start] is the standalone-host
 * fallback (it re-enters HomeActivity with the route via [startHomeActivity], which the translator
 * turns into the [NavRoutes.TRIP_INFO] route); in-app callers that already hold a NavController navigate the
 * route directly instead.
 */
object TripInfoLauncher {

    /** Re-enters HomeActivity at the [NavRoutes.TRIP_INFO] reminder editor for [args]. */
    fun start(context: Context, args: ReminderEditorArgs) {
        context.startHomeActivity(NavRoutes.tripInfo(args))
    }
}

/**
 * The "this reminder will be deleted" confirmation dialog, shared by the reminder editor's delete
 * action and the My Reminders list's long-press delete.
 */
internal fun confirmDeleteReminder(context: Context, onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_App_DeleteDialog)
        .setTitle(R.string.trip_info_delete)
        .setMessage(R.string.trip_info_delete_trip)
        .setIcon(R.drawable.baseline_delete_forever_48)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
