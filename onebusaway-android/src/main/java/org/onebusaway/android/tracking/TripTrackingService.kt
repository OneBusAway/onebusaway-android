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
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.Status
import org.onebusaway.android.time.ElapsedClock
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.time.liveServerTime
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.DefaultArrivalsRepository

/**
 * Keeps the rider's tracked route rows on the Lock Screen as live, self-updating countdowns.
 *
 * **Why a poll and not a push.** The obvious vehicle would be the push registration the app already
 * has (#1957): OBACloud knows the device's token and the region already fans arrival reminders out
 * over it. It cannot serve this. The sidecar's only trip-shaped endpoint is `alarms`, which
 * registers a *one-shot* "notify me N seconds before this departure" — a single fire, no stream, no
 * per-row countdown channel to subscribe to. There is nothing on the server to receive, so this
 * polls, and pays for it by being bounded: a row retires when it runs out of departures, and the
 * whole session retires after [MAX_TRACKING_DURATION] regardless.
 *
 * **Which clock.** Every countdown is measured against the server clock — the response's own
 * `currentTime`, projected forward between polls by *monotonic* elapsed device time, exactly as the
 * arrivals repository projects a stale snapshot. A notification outlives a device sleep by design,
 * so it is the single place in the app where clock skew would be most visible and least
 * explicable; the projection never subtracts a device wall-clock reading from a server one.
 *
 * The tracked set itself lives in [TrackedRouteStore], not here. This service renders it: it
 * collects the store, so a "stop tracking" tap from the notification takes effect immediately, and a
 * sticky restart after a process death picks the session back up from the store, not from an intent.
 */
@AndroidEntryPoint
class TripTrackingService : Service() {

    @Inject lateinit var store: TrackedRouteStore

    @Inject lateinit var stopArrivals: StopArrivalsDataSource

    @Inject lateinit var notifications: TripTrackingNotifications

    @Inject lateinit var elapsedClock: ElapsedClock

    // Held explicitly so onDestroy cancels via the Job member rather than the CoroutineScope.cancel
    // extension, which would trip lint's MemberExtensionConflict (see TripPlanMonitorService).
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var renderJob: Job? = null

    /** Where each tracked stop stands: when it was last asked, and its last good answer. */
    private val polls = mutableMapOf<String, StopPoll>()

    /** The last card posted per notification id, so an unchanged tick costs no re-post. */
    private val rendered = mutableMapOf<Int, TrackedRouteCard>()

    /**
     * When each row's card first went data-less, so a stop whose fetches keep failing does not leave
     * an unresolvable card — and a foreground service behind it — up indefinitely. Cleared the
     * moment the row resolves against a response.
     */
    private val pendingSince = mutableMapOf<TrackedRouteKey, ElapsedTime>()

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
        val primary = store.routes.value.firstOrNull()
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
        val anchorCard = rendered[trackingNotificationId(primary.id)]
        val notification = anchorCard?.let(notifications::build) ?: notifications.pending(primary)
        if (!promoteToForeground(trackingNotificationId(primary.id), notification)) {
            stopSelf()
            return START_NOT_STICKY
        }
        anchorId = trackingNotificationId(primary.id)

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
     * Re-renders when the cards' numbers are next due to change *and* on every change to the tracked
     * set. `collectLatest` is what makes "stop tracking" instant: a store emission cancels the sleep
     * rather than leaving the card up until the next wake-up.
     */
    private suspend fun runRenderLoop() {
        store.routes.collectLatest { routes ->
            while (true) {
                delay(refresh(routes) ?: break)
            }
        }
    }

    /**
     * Draws the cards, and answers how long they stay right for — see [nextRenderDelay]. Null when
     * this pass handed off rather than drew: nothing is left to reschedule, and whatever comes next
     * (a store change, the service stopping) arrives on its own.
     */
    private suspend fun refresh(routes: List<TrackedRoute>): Duration? {
        if (routes.isEmpty()) {
            stopTracking()
            return null
        }
        forgetUntracked(routes)
        val stopIds = routes.map { it.key.stopId }.distinct()
        fetchStaleStops(stopIds)

        // One reading for the whole pass. The countdowns, the cards and the wake-up that times them
        // all have to be measured against the same instant, or the sleep is timing a number other
        // than the one on screen — which is the class of bug this scheduling replaced.
        val elapsed = elapsedClock.now()
        val resolved = routes.map { resolve(it, elapsed) }

        val retiring = resolved.filter { it.outcome is TrackingOutcome.Retire }
        if (retiring.isNotEmpty()) {
            // Dropping them from the store re-emits the tracked list, which cancels this sleep and
            // brings us straight back here with the survivors; take their cards down now so nothing
            // lingers in between.
            retiring.forEach { done ->
                notificationManager.cancel(trackingNotificationId(done.route.id))
                rendered.remove(trackingNotificationId(done.route.id))
            }
            store.untrackAll(retiring.map { it.route.key })
            return null
        }

        val cards = resolved.mapIndexed { rank, it -> notifications.card(it.route, it.outcome, rank) }
        if (!post(cards)) return null

        pollInterval = trackingPollInterval(
            resolved.mapNotNull { (it.outcome as? TrackingOutcome.Live)?.departures?.first()?.eta }.minOrNull()
        )
        // Read after the poll and after the new cadence is set: a fetch that just landed re-dates its
        // stop, and its response is the anchor the countdowns are now projected from.
        return nextRenderDelay(resolved.mapNotNull { it.now }, stopIds.minOf { untilDue(it, elapsed) })
    }

    /**
     * How long until [stopId] is due another fetch, zero or negative when it is due now — including
     * a stop that has never been asked.
     *
     * The single statement of the cadence, read both by the fetch filter and by the next wake-up, so
     * the two cannot drift. It measures from when the stop was *asked* rather than from
     * [StopSnapshot.receivedAt]: a stop whose fetches keep failing has no receipt to age, so a
     * receipt-based cadence would call it due forever and retry it as fast as the loop can turn.
     */
    private fun untilDue(stopId: String, elapsed: ElapsedTime): Duration = polls[stopId]?.let { pollInterval - (elapsed - it.askedAt) } ?: Duration.ZERO

    /** Posts the cards, keeping the foreground anchor on the most-recently-tracked one. */
    private fun post(cards: List<TrackedRouteCard>): Boolean {
        val primary = cards.first()
        val anchorMoved = anchorId != primary.notificationId
        if (anchorMoved || rendered[primary.notificationId] != primary) {
            if (!promoteToForeground(primary.notificationId, notifications.build(primary))) {
                stopTracking()
                return false
            }
            if (anchorMoved) {
                // Re-anchoring takes the previous anchor's notification down with it (the platform
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

    /**
     * One tracked row resolved against the freshest snapshot for its stop, and [now] — the
     * server-clock instant its card was measured against, which the next wake-up is timed from.
     * Null for a row with no response yet: its card is a placeholder, which no minute turnover
     * changes.
     */
    private data class Resolved(val route: TrackedRoute, val outcome: TrackingOutcome, val now: ServerTime?)

    private fun resolve(route: TrackedRoute, elapsed: ElapsedTime): Resolved {
        // Wall clock, and the session's own persisted start: the bound has to hold across the service
        // being killed and restored, which is exactly when a forgotten session would otherwise reset.
        if (trackingSessionExpired(route.startedAt, WallTime.now())) {
            Log.d(TAG, "Tracking session for ${route.key} exceeded $MAX_TRACKING_DURATION - retiring")
            return Resolved(route, TrackingOutcome.Retire, now = null)
        }

        val snapshot = polls[route.key.stopId]?.snapshot
            ?: return Resolved(route, pendingOutcomeFor(route.key, elapsed), now = null)
        pendingSince.remove(route.key)

        // Only the outcome depends on "now": every field of a TrackedMatch is fixed by the response
        // that produced it, so the matches were built once when it landed (see [StopSnapshot]).
        val now = snapshot.serverNow(elapsed)
        return Resolved(route, trackingOutcome(snapshot.matches[route.key].orEmpty(), now), now)
    }

    /** Holds a data-less card until the fetches have had long enough to be called hopeless. */
    private fun pendingOutcomeFor(key: TrackedRouteKey, elapsed: ElapsedTime): TrackingOutcome {
        val since = pendingSince.getOrPut(key) { elapsed }
        return pendingOutcome(elapsed - since)
    }

    /**
     * Every row in a response, with its arrivals already lifted to [TrackedMatch] — grouped by the
     * same (stop, route, headsign) key a tracked row is, so resolving one is a map lookup.
     *
     * Done once here rather than per row per tick because none of a match's fields move with the
     * clock: the display instant, whether it is predicted, its cancellation, and its deviation are
     * all settled by the response. Only [trackingOutcome] needs a live "now". Building them per tick
     * meant constructing an [ArrivalInfo] every 15s to read four fixed values — and its `init`
     * eagerly formats three status strings this never reads.
     *
     * Built through [ArrivalInfo] rather than read off [ArrivalData] directly so the choice of
     * instant — departure at the first stop, arrival elsewhere; prediction only when the server
     * actually supplied one (#1687) — and the deviation bucket are the arrivals list's, not a second
     * implementation of the same rules that could drift from it.
     */
    private fun matchesByRow(
        arrivals: List<ArrivalData>,
        now: ServerTime
    ): Map<TrackedRouteKey, List<TrackedMatch>> = arrivals
        .groupBy { TrackedRouteKey(it.stopId, it.routeId, it.headsign.orEmpty()) }
        .mapValues { (_, rowArrivals) -> rowArrivals.map { toMatch(it, now) } }

    private fun toMatch(arrival: ArrivalData, now: ServerTime): TrackedMatch {
        val info = ArrivalInfo(this, arrival, now, includeArrivalDepartureInStatusLabel = false)
        return TrackedMatch(
            displayTime = info.displayTime,
            predicted = info.predicted,
            canceled = info.status == Status.CANCELED
        )
    }

    /**
     * Fetches any stop whose snapshot is missing or older than the current [pollInterval], the stale
     * ones concurrently — a tick's re-render waits on the slowest request rather than their sum (the
     * same fan-out the starred-stops badge poll uses). The snapshot writes stay on this scope's Main
     * dispatcher, so the map needs no synchronization.
     */
    private suspend fun fetchStaleStops(stopIds: List<String>) = coroutineScope {
        val elapsed = elapsedClock.now()
        stopIds.filter { untilDue(it, elapsed) <= Duration.ZERO }
            .map { stopId -> async { fetch(stopId) } }
            .awaitAll()
    }

    private suspend fun fetch(stopId: String) {
        // Stamped before the request, so the cadence measures from when the stop was asked whether or
        // not it answers.
        val askedAt = elapsedClock.now()
        val before = polls[stopId]
        polls[stopId] = StopPoll(askedAt, before?.snapshot)
        // The arrivals screen's own default window. Tracking a row needs only the next few
        // departures, so there is nothing here to size the window around — unlike the earlier design,
        // which pinned one trip that might sit far out and had to be kept provably inside the window.
        // The adaptation and the per-row lift both happen here, off the main thread, so the tick that
        // consumes them does no work beyond a map lookup.
        val result = try {
            withContext(Dispatchers.IO) {
                stopArrivals.arrivals(stopId, DefaultArrivalsRepository.MINUTES_AFTER_DEFAULT)
                    .map { response ->
                        val serverTime = ServerTime(response.currentTime)
                        serverTime to matchesByRow(response.arrivals, serverTime)
                    }
            }
        } catch (e: CancellationException) {
            // A Track or Untrack restarts the render loop, which cancels this request mid-flight. The
            // stop was neither answered nor refused, so put its cadence back: leaving it stamped as
            // asked would sit it out a whole interval over a request that never happened.
            if (before == null) polls.remove(stopId) else polls[stopId] = before
            throw e
        }
        // Stamp the receipt as close to the response as possible: it is the baseline the server clock
        // is projected forward from between polls.
        val receivedAt = elapsedClock.now()
        result.onSuccess { (serverTime, matches) ->
            polls[stopId] = StopPoll(
                askedAt = askedAt,
                snapshot = StopSnapshot(
                    matches = matches,
                    serverTime = serverTime,
                    receivedAt = receivedAt
                )
            )
        }.onFailure {
            // Keep the previous snapshot and let its countdowns keep projecting; a dropped poll is not
            // a reason to take a card down. Retirement is decided by the row's own times, not by ours.
            Log.w(TAG, "Tracking poll for stop $stopId failed - keeping the last good arrivals", it)
        }
    }

    /** Drops per-row bookkeeping for sessions that are no longer tracked. */
    private fun forgetUntracked(routes: List<TrackedRoute>) {
        val live = routes.mapTo(mutableSetOf()) { it.key }
        pendingSince.keys.retainAll(live)
        val liveIds = routes.mapTo(mutableSetOf()) { trackingNotificationId(it.id) }
        rendered.keys.filterNot { it in liveIds }.forEach { id ->
            notificationManager.cancel(id)
            rendered.remove(id)
        }
        polls.keys.retainAll(routes.mapTo(mutableSetOf()) { it.key.stopId })
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

    /**
     * Where one tracked stop stands: when it was last [asked][askedAt], and the last answer it gave
     * — null while it has never answered, which is a state worth being able to hold rather than
     * infer. Keeping both on one record is what lets the cadence measure from the ask while the
     * countdowns project from the answer, with a single key space and a single prune.
     */
    private data class StopPoll(val askedAt: ElapsedTime, val snapshot: StopSnapshot?)

    /**
     * A stop's last good arrivals response — already lifted to per-row [TrackedMatch]es (see
     * [matchesByRow]) — and the monotonic reading it landed at.
     */
    private data class StopSnapshot(
        val matches: Map<TrackedRouteKey, List<TrackedMatch>>,
        val serverTime: ServerTime,
        val receivedAt: ElapsedTime
    ) {
        /**
         * The server clock now: this response's own `currentTime` advanced by the monotonic time
         * since it arrived — the #1612 extrapolation, shared with the arrivals strip's live ticker
         * rather than restated here, so the zero clamp guarding a fresh anchor comes with it.
         */
        fun serverNow(elapsed: ElapsedTime): ServerTime = liveServerTime(serverTime, receivedAt, elapsed)
    }

    private companion object {
        const val TAG = "TripTrackingService"
    }
}
