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
package org.onebusaway.android.ui.tracking

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.onebusaway.android.R
import org.onebusaway.android.app.di.TripTrackingEntryPoint
import org.onebusaway.android.tracking.TrackedTrip
import org.onebusaway.android.tracking.TripTrackingController

/**
 * The Track action, shared by every surface that offers it (#2166) — the arrivals drawer's ETA-pill
 * menu and the starred-stop arrival badges in My Lists.
 *
 * One implementation on purpose: the two surfaces must agree about what a second tap means (stop, if
 * this exact instance is the one running; otherwise start, which repoints the existing session at
 * the bus just named — see `TrackedTripKey`), and about how each refusal is reported. Split across
 * two call sites, that agreement is a convention; here it is the code.
 */
fun AppCompatActivity.toggleTripTracking(trip: TrackedTrip) {
    val controller = TripTrackingEntryPoint.get(this)
    if (controller.isTracking(trip.instanceId)) {
        controller.untrack(trip.key)
        toast(getString(R.string.trip_tracking_stopped, trip.routeName))
        return
    }
    when (controller.track(trip)) {
        TripTrackingController.Refusal.NOTIFICATIONS_DISABLED -> {
            // On Android 13+ the rider may simply never have been asked; ask now, so a second tap
            // succeeds instead of hitting the same wall.
            requestNotificationPermission()
            toast(getString(R.string.trip_tracking_notifications_disabled), Toast.LENGTH_LONG)
        }

        TripTrackingController.Refusal.SERVICE_REFUSED ->
            toast(getString(R.string.trip_tracking_unavailable))

        null -> toast(getString(R.string.trip_tracking_started, trip.routeName))
    }
}

/**
 * Asks for POST_NOTIFICATIONS after a Track tap found notifications switched off. On Android 13+ the
 * rider may never have been asked — the app requests it at the reminder opt-ins, which a rider who
 * only ever uses arrivals never reaches — so the first Track tap is a legitimate moment to ask. A
 * no-op below API 33 (granted at install) and again once the rider has refused twice, which is what
 * the accompanying message is for. The result is not handled: the rider taps Track again.
 */
private fun AppCompatActivity.requestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        REQUEST_POST_NOTIFICATIONS
    )
}

private fun AppCompatActivity.toast(message: String, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, length).show()
}

/** Request code for the Track tap's notification prompt; only has to not collide with another. */
private const val REQUEST_POST_NOTIFICATIONS = 0x2166
