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

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.DefaultArrivalsRepository

/**
 * Keeps the rider's tracked buses on the Lock Screen as live, self-updating countdowns.
 *
 * **Why a poll and not a push.** The obvious vehicle would be the push registration the app already
 * has (#1957): OBACloud knows the device's token and the region already fans arrival reminders out
 * over it. It cannot serve this. The sidecar's only trip-shaped endpoint is `alarms`, which
 * registers a *one-shot* "notify me N seconds before this departure" — a single fire, no stream, no
 * per-trip countdown channel to subscribe to. There is nothing on the server to receive, so this
 * polls, and pays for it by being short-lived: the session lasts exactly as long as the trips the
 * rider named are still upcoming, and the service stops itself the moment the last one retires.
 *
 * **Which clock.** Every countdown is measured against the server clock — the response's own
 * `currentTime`, projected forward between polls by *monotonic* elapsed device time, exactly as the
 * arrivals repository projects a stale snapshot. A notification outlives a device sleep by design,
 * so it is the single place in the app where clock skew would be most visible and least
 * explicable; the projection never subtracts a device wall-clock reading from a server one.
 *
 * The tracked set itself lives in [TrackedTripStore], not here. This service renders it: it collects
 * the store, so a "stop tracking" tap from the notification takes effect immediately, and a sticky
 * restart after a process death picks the session back up from the store rather than from an intent.
 */
@AndroidEntryPoint
class TripTrackingService : Service() {

    @Inject lateinit var store: TrackedTripStore

    @Inject lateinit var stopArrivals: StopArrivalsDataSource

    @Inject lateinit var notifications: TripTrackingNotifications

    @Inject lateinit var elapsedClock: ElapsedClock

    // Held explicitly so onDestroy cancels via the Job member rather than the CoroutineScope.cancel
    // extension, which would trip lint's MemberExtensionConflict (see TripPlanMonitorService).
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var renderJob: Job? = null

    /** The last good arrivals response per stop, with the monotonic reading it was received at. */
    private val snapshots = mutableMapOf<String, StopSnapshot>()

    /** The last card posted per notification id, so an unchanged tick costs no re-post. */
    private val rendered = mutableMapOf<Int, TrackedTripCard>()

    /**
     * The freshest remaining wait per tracked instance, seeded from the wait at Track time. Only use:
     * sizing the arrivals window wide enough that the tracked trip is provably inside it, so
     * "absent from the response" can be read as "gone" rather than "outside the window we asked for".
     */
    private val expectedWait = mutableMapOf<String, Duration>()

    /**
     * When each trip's card first went data-less, so a stop whose fetches keep failing does not leave
     * an unresolvable card — and a foreground service behind it — up indefinitely. Cleared the moment
     * the trip resolves against a response.
     */
    private val pendingSince = mutableMapOf<String, ElapsedTime>()

    /** The notification the platform currently treats as this foreground service's own. */
    private var anchorId: Int? = null

    /** Cadence for the next fetch, from the soonest countdown across the tracked set. */
    private var pollInterval: Duration = TRACKING_POLL_INTERVAL

    // The platform manager, not NotificationManagerCompat: the compat notify() carries a
    // @RequiresPermission(POST_NOTIFICATIONS) that lint cannot see satisfied here (the gate is in
    // TripTrackingController, at the Track tap). Same choice as TripPlanMonitorService.
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val primary = store.trips.value.firstOrNull()
        if (primary == null) {
            // Nothing to show — but the platform still expects a startForeground for the
            // startForegroundService that got us here, so promote and immediately retire.
            Log.d(TAG, "Started with nothing tracked - stopping")
            stopTracking()
            return START_NOT_STICKY
        }

        // Must promote promptly (the platform allows seconds, not a network round trip), so the very
        // first card is a placeholder. A later start while already running re-promotes the card that
        // is already on screen rather than flashing the placeholder back over a live countdown.
        val anchorCard = rendered[trackingNotificationId(primary.instanceId)]
        val notification = anchorCard?.let(notifications::build) ?: notifications.pending(primary)
        if (!promoteToForeground(trackingNotificationId(primary.instanceId), notification)) {
            stopSelf()
            return START_NOT_STICKY
        }
        anchorId = trackingNotificationId(primary.instanceId)

        if (renderJob == null) {
            renderJob = serviceScope.launch { runRenderLoop() }
        }
        // Sticky, with no intent state to redeliver: everything this service needs is in the store,
        // so a restart with a null intent resumes the same session.
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    /**
     * Re-renders on every tick *and* on every change to the tracked set. Combining the two sources
     * is what makes "stop tracking" instant: the store emission wakes the loop rather than the card
     * sitting there until the next tick.
     */
    private suspend fun runRenderLoop() {
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(TRACKING_TICK)
            }
        }
        combine(store.trips, ticker) { trips, _ -> trips }.collect { trips -> refresh(trips) }
    }

    private suspend fun refresh(trips: List<TrackedTrip>) {
        if (trips.isEmpty()) {
            stopTracking()
            return
        }
        forgetUntracked(trips)
        fetchStaleStops(trips)

        val resolved = trips.map { trip -> resolve(trip) }

        val retiring = resolved.filter { it.outcome is TrackingOutcome.Retire }
        if (retiring.isNotEmpty()) {
            // Dropping them from the store re-emits the tracked list, which brings us straight back
            // here with the survivors; take their cards down now so nothing lingers in between.
            retiring.forEach { take ->
                notificationManager.cancel(trackingNotificationId(take.trip.instanceId))
                rendered.remove(trackingNotificationId(take.trip.instanceId))
                store.untrack(take.trip.key)
            }
            return
        }

        val cards = resolved.mapIndexed { rank, it ->
            notifications.card(it.trip, it.outcome, it.match, rank)
        }
        if (!post(cards)) return

        pollInterval = trackingPollInterval(
            resolved.mapNotNull { (it.outcome as? TrackingOutcome.Waiting)?.eta }.minOrNull()
        )
    }

    /** Posts the cards, keeping the foreground anchor on the most-recently-tracked one. */
    private fun post(cards: List<TrackedTripCard>): Boolean {
        val primary = cards.first()
        val anchorMoved = anchorId != primary.notificationId
        if (anchorMoved || rendered[primary.notificationId] != primary) {
            if (!promoteToForeground(primary.notificationId, notifications.build(primary))) {
                stopTracking()
                return false
            }
            if (anchorMoved) {
                // Re-anchoring can take the previous anchor's notification down with it (the platform
                // allows one foreground notification per service). Forget every other render so the
                // loop below re-posts them all instead of suppressing them as unchanged.
                rendered.keys.retainAll(setOf(primary.notificationId))
                anchorId = primary.notificationId
            }
            rendered[primary.notificationId] = primary
        }
        cards.drop(1).forEach { card ->
            if (rendered[card.notificationId] != card) {
                notificationManager.notify(card.notificationId, notifications.build(card))
                rendered[card.notificationId] = card
            }
        }
        return true
    }

    /** One tracked trip resolved against the freshest snapshot for its stop. */
    private data class Resolved(
        val trip: TrackedTrip,
        val match: TrackedMatch?,
        val outcome: TrackingOutcome
    )

    private fun resolve(trip: TrackedTrip): Resolved {
        val snapshot = snapshots[trip.key.stopId]
            ?: return Resolved(trip, match = null, outcome = pendingOutcome(trip.instanceId))
        pendingSince.remove(trip.instanceId)

        val now = snapshot.serverNow(elapsedClock.now())
        val arrival = snapshot.arrivals.firstOrNull {
            trackedInstanceId(it.stopId, it.tripId, it.serviceDate) == trip.instanceId
        }
        val match = arrival?.let { toMatch(it, now) }
        val outcome = trackingOutcome(match, now)
        (outcome as? TrackingOutcome.Waiting)?.let { expectedWait[trip.instanceId] = it.eta }
        return Resolved(trip, match, outcome)
    }

    /** Holds a data-less card until the fetches have had long enough to be called hopeless. */
    private fun pendingOutcome(instanceId: String): TrackingOutcome {
        val since = pendingSince.getOrPut(instanceId) { elapsedClock.now() }
        return pendingOutcome(elapsedClock.now() - since)
    }

    /**
     * The arrival's tracked facts. Built through [ArrivalInfo] rather than read off [ArrivalData]
     * directly so the choice of instant — departure at the first stop, arrival elsewhere; prediction
     * only when the server actually supplied one (#1687) — and the lateness colour are the arrivals
     * list's, not a second implementation of the same rules that could drift from it.
     */
    private fun toMatch(arrival: ArrivalData, now: ServerTime): TrackedMatch {
        val info = ArrivalInfo(this, arrival, now, includeArrivalDepartureInStatusLabel = false)
        return TrackedMatch(
            displayTime = info.displayTime,
            predicted = info.predicted,
            canceled = info.status == Status.CANCELED,
            fillColorRes = info.fillColor
        )
    }

    /** Fetches any stop whose snapshot is missing or older than the current [pollInterval]. */
    private suspend fun fetchStaleStops(trips: List<TrackedTrip>) {
        trips.map { it.key.stopId }.distinct().forEach { stopId ->
            val existing = snapshots[stopId]
            if (existing != null && elapsedClock.now() - existing.receivedAt < pollInterval) return@forEach
            fetch(stopId, windowMinutes(trips, stopId))
        }
    }

    private suspend fun fetch(stopId: String, minutesAfter: Int) {
        // Stamp the receipt as close to the response as possible: it is the baseline the server clock
        // is projected forward from between polls.
        val result = withContext(Dispatchers.IO) { stopArrivals.arrivals(stopId, minutesAfter) }
        val receivedAt = elapsedClock.now()
        result.onSuccess { response ->
            snapshots[stopId] = StopSnapshot(
                arrivals = response.arrivals,
                serverTime = ServerTime(response.currentTime),
                receivedAt = receivedAt
            )
        }.onFailure {
            // Keep the previous snapshot and let its countdown keep projecting; a dropped poll is not
            // a reason to take a card down. Retirement is decided by the trip's own time, not by ours.
            Log.w(TAG, "Tracking poll for stop $stopId failed - keeping the last good arrivals", it)
        }
    }

    /**
     * How far ahead to ask for arrivals at [stopId]: far enough to provably contain every trip
     * tracked there, so [trackingOutcome] can read "absent from the response" as "this trip is gone"
     * rather than "we asked for too narrow a window". Sized off the trips' own remaining waits (the
     * wait at Track time until a live one replaces it) plus slack for a delay that pushes one out.
     */
    private fun windowMinutes(trips: List<TrackedTrip>, stopId: String): Int {
        val longest = trips.filter { it.key.stopId == stopId }.maxOf { trip ->
            expectedWait[trip.instanceId]?.inWholeSeconds ?: trip.plannedWaitSeconds.toLong()
        }
        val needed = ceil(longest / 60.0).toInt() + TRACKING_WINDOW_SLACK_MINUTES
        return maxOf(DefaultArrivalsRepository.MINUTES_AFTER_DEFAULT, needed)
    }

    /** Drops per-trip bookkeeping for sessions that are no longer tracked. */
    private fun forgetUntracked(trips: List<TrackedTrip>) {
        val live = trips.mapTo(mutableSetOf()) { it.instanceId }
        expectedWait.keys.retainAll(live)
        pendingSince.keys.retainAll(live)
        val liveIds = trips.mapTo(mutableSetOf()) { trackingNotificationId(it.instanceId) }
        rendered.keys.filterNot { it in liveIds }.forEach { id ->
            notificationManager.cancel(id)
            rendered.remove(id)
        }
        val liveStops = trips.mapTo(mutableSetOf()) { it.key.stopId }
        snapshots.keys.retainAll(liveStops)
    }

    /**
     * Promotes to the foreground, returning false when the platform refuses. A foreground service
     * start can be rejected outright (the app fell into the background between the rider's tap and
     * this call), and a crash there would take the app down over a countdown.
     */
    private fun promoteToForeground(id: Int, notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException subtype.
        Log.w(TAG, "Cannot run trip tracking in the foreground; stopping", e)
        false
    }

    private fun stopTracking() {
        // End the loop first, so a tick already queued behind this one cannot re-post a card onto a
        // shade we are in the middle of clearing. Safe to call from inside the loop itself: the
        // cancellation lands at its next suspension point, after this method has finished.
        renderJob?.cancel()
        renderJob = null
        // Remove the anchor next — a plain cancel() on the foreground notification is ignored while
        // it is still the service's own — then take down the rest.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        rendered.keys.forEach(notificationManager::cancel)
        rendered.clear()
        anchorId = null
        stopSelf()
    }

    /** A stop's last good arrivals response and the monotonic reading it landed at. */
    private data class StopSnapshot(
        val arrivals: List<ArrivalData>,
        val serverTime: ServerTime,
        val receivedAt: ElapsedTime
    ) {
        /**
         * The server clock now: this response's own `currentTime` advanced by the monotonic time
         * since it arrived. Two same-domain operations — an [ElapsedTime] difference and a
         * [ServerTime] shift — so no device wall clock enters the countdown (#1612).
         */
        fun serverNow(elapsed: ElapsedTime): ServerTime = serverTime + (elapsed - receivedAt)
    }

    private companion object {
        const val TAG = "TripTrackingService"

        /**
         * Extra arrivals window past the longest tracked wait. Absorbs a delay that pushes a tracked
         * trip out past the time it was tracked at, so a late bus keeps its card instead of falling
         * out of the requested window and being read as gone.
         */
        const val TRACKING_WINDOW_SLACK_MINUTES = 20
    }
}
