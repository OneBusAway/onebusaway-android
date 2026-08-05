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
package org.onebusaway.android.tracking

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * The one way in and out of trip tracking, for every surface that offers it.
 *
 * Writing the tracked set and running the service are deliberately one call: the store persists the
 * intention and the service renders it, and a surface that did only the first would leave a rider
 * with a tracked bus and no card. Refusals come back as a value rather than a thrown exception —
 * the reasons a Track tap cannot take (notifications switched off, the platform declining a
 * foreground start) are all things the rider can act on, so each surface reports them in its own
 * idiom.
 */
@Singleton
class TripTrackingController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: TrackedTripStore
) {

    /** Why a Track tap did not take. */
    enum class Refusal {
        /** The rider (or the OS) has notifications off for the app — there is nowhere to show a card. */
        NOTIFICATIONS_DISABLED,

        /** The platform refused the foreground start. Rare, and not the rider's doing. */
        SERVICE_REFUSED
    }

    /** True when [instanceId] is the exact trip instance currently being tracked for its session. */
    fun isTracking(instanceId: String): Boolean = store.isTracking(instanceId)

    /** The tracked trip instances, live — for surfaces that label a row by whether it is tracked. */
    val trackedInstances: Flow<Set<String>> get() = store.trackedInstances

    /**
     * Starts (or, for a session that is already live, repoints and promotes) tracking of [trip].
     * Returns null on success, else why it did not take.
     */
    fun track(trip: TrackedTrip): Refusal? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Refusal.NOTIFICATIONS_DISABLED
        }
        store.track(trip)
        return try {
            ContextCompat.startForegroundService(context, Intent(context, TripTrackingService::class.java))
            null
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException. Roll the
            // store back rather than leave a tracked trip nothing is rendering.
            Log.w(TAG, "Foreground start refused; not tracking ${trip.key}", e)
            store.untrack(trip.key)
            Refusal.SERVICE_REFUSED
        }
    }

    /** Stops tracking the session identified by [key]; the service retires itself once empty. */
    fun untrack(key: TrackedTripKey) {
        store.untrack(key)
    }

    private companion object {
        const val TAG = "TripTrackingController"
    }
}
