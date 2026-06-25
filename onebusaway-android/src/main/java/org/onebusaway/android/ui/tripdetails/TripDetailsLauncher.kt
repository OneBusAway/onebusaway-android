/*
 * Copyright (C) 2012-2026 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com), Open Transit Software Foundation
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
package org.onebusaway.android.ui.tripdetails

import org.onebusaway.android.ui.HomeActivity
import android.content.Context
import android.content.Intent
import org.onebusaway.android.ui.nav.NavRoutes

/**
 * Launches the trip-details screen (a trip's stops + live vehicle position).
 *
 * Trip details is a NavHost destination hosted by [HomeActivity] (with the shared
 * destination-reminder controller); this is no longer an Activity but a launcher facade that builds
 * an explicit [HomeActivity] intent carrying the trip args as extras (trip details has no data-URI
 * contract). HomeActivity's translator reads [NavRoutes.ARG_TRIP_ID] and navigates. An
 * `<activity-alias>` for the frozen `org.onebusaway.android.ui.tripdetails.TripDetailsActivity` name keeps any
 * in-flight NavigationService ongoing-notification PendingIntent resolving to HomeActivity.
 */
object TripDetailsLauncher {

    // Scroll-mode *values* (read by TripDetailsRepository + the launch callers) and the broadcast
    // action the NavigationService sends when destroyed (observed by the reminder controller).
    const val SCROLL_MODE_VEHICLE = "vehicle"
    const val SCROLL_MODE_STOP = "stop"
    const val ACTION_SERVICE_DESTROYED = "NavigationServiceDestroyed"

    /** Fluent launcher for the trip details screen. */
    class Builder(private val context: Context, tripId: String) {

        private val intent = Intent(context, HomeActivity::class.java)
            .putExtra(NavRoutes.ARG_TRIP_ID, tripId)

        fun setStopId(stopId: String?): Builder = apply { intent.putExtra(NavRoutes.ARG_STOP_ID, stopId) }

        fun setScrollMode(mode: String?): Builder =
            apply { intent.putExtra(NavRoutes.ARG_SCROLL_MODE, mode) }

        fun setDestinationId(stopId: String?): Builder =
            apply { intent.putExtra(NavRoutes.ARG_DEST_ID, stopId) }

        fun getIntent(): Intent = intent

        fun start() {
            context.startActivity(intent)
        }
    }

    @JvmStatic
    fun start(context: Context, tripId: String) {
        Builder(context, tripId).start()
    }

    @JvmStatic
    fun start(context: Context, tripId: String, mode: String) {
        Builder(context, tripId).setScrollMode(mode).start()
    }

    @JvmStatic
    fun start(context: Context, tripId: String, stopId: String, mode: String) {
        Builder(context, tripId).setStopId(stopId).setScrollMode(mode).start()
    }
}
