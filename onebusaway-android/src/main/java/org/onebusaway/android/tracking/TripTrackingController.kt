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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.onebusaway.android.time.WallTime

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
    private val store: TrackedRouteStore
) {

    /** Why a Track tap did not take. */
    enum class Refusal {
        /** The rider (or the OS) has notifications off for the app — there is nowhere to show a card. */
        NOTIFICATIONS_DISABLED,

        /** The platform refused the foreground start. Rare, and not the rider's doing. */
        SERVICE_REFUSED
    }

    /** True when [key]'s route row is currently tracked. */
    fun isTracking(key: TrackedRouteKey): Boolean = store.isTracking(key)

    /** The tracked row keys, live — for surfaces that label a row by whether it is tracked. */
    val trackedKeys: Flow<Set<TrackedRouteKey>> get() = store.trackedKeys

    /**
     * Starts tracking [route], or — when its row is already tracked — promotes that session.
     * Returns null on success, else why it did not take.
     */
    fun track(route: TrackedRoute): Refusal? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Refusal.NOTIFICATIONS_DISABLED
        }
        store.track(route, WallTime.now())
        return try {
            ContextCompat.startForegroundService(context, Intent(context, TripTrackingService::class.java))
            null
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException. Roll the
            // store back rather than leave a tracked trip nothing is rendering.
            Log.w(TAG, "Foreground start refused; not tracking ${route.key}", e)
            store.untrack(route.key)
            Refusal.SERVICE_REFUSED
        }
    }

    /**
     * Starts watching the app's foreground transitions, so a tracked set that outlived its service
     * gets one back. Called once from `Application.onCreate` (mirrors `PushRegistrationManager`).
     *
     * The store is the source of truth for what is tracked and the service merely renders it, but a
     * force-stop kills the service while leaving the store intact — so on the next launch the arrivals
     * row still showed its tracking eye with no card behind it. Nothing brings the service back on its
     * own: it is only ever started by a Track tap, and a force-stopped app is not restarted by the
     * system. Reconciling on foreground is what makes "the store is the truth" true again.
     */
    fun start() {
        if (started.getAndSet(true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = resume()
        })
    }

    /**
     * Brings the service back for a tracked set that has one persisted but nothing rendering it. A
     * no-op when nothing is tracked, and when the service is already running (the start command lands
     * on the live instance, which re-promotes the card it already has).
     *
     * If notifications have been switched off since, the sessions are dropped rather than left
     * standing: they cannot be honoured, and leaving them would keep the arrivals row claiming a
     * countdown that can never appear — the exact mismatch this method exists to close.
     */
    fun resume() {
        if (store.routes.value.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.i(TAG, "Notifications are off; dropping ${store.routes.value.size} tracked row(s)")
            store.clear()
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, TripTrackingService::class.java))
        } catch (e: IllegalStateException) {
            // Not fatal here, unlike a Track tap: nothing was just asked for, so the tracked set stands
            // and the next foreground tries again.
            Log.w(TAG, "Could not resume trip tracking", e)
        }
    }

    /** Stops tracking the row identified by [key]; the service retires itself once empty. */
    fun untrack(key: TrackedRouteKey) {
        store.untrack(key)
    }

    private val started = AtomicBoolean(false)

    private companion object {
        const val TAG = "TripTrackingController"
    }
}
