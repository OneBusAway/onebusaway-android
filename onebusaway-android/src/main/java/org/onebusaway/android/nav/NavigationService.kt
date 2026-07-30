/*
 * Copyright (C) 2005-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.FeatureFlags
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher

/** Thin foreground-service orchestrator for the pure [ReminderEngine]. */
@AndroidEntryPoint
class NavigationService : Service() {
    @Inject internal lateinit var sessionStore: ReminderSessionStore

    @Inject internal lateinit var notificationPresenter: ReminderNotificationPresenter

    @Inject internal lateinit var speechController: ReminderSpeechController

    @Inject internal lateinit var feedbackRepository: NavigationFeedbackRepository

    @Inject lateinit var stopDao: StopDao

    @Inject lateinit var importGate: ImportGate

    @Inject lateinit var obaAnalytics: ObaAnalytics

    @Inject lateinit var locationRepository: LocationRepository

    @Inject internal lateinit var shapeSource: ReminderShapeSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var navigationJob: Job? = null
    private var plan: ReminderPlan? = null
    private var engineState = ReminderEngineState()
    private var explicitCancellation = false
    private var mutedRequested = false

    /** The newest start command, so a teardown that lost the race to one can stand down. */
    private var latestStartId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (!FeatureFlags.DESTINATION_REMINDERS) {
            // Nothing can legitimately start this service while the feature is off, so anything that
            // reaches here is left over from a build where it was on: a stored session plus the
            // sticky restart the platform owes it. Clear the row so it cannot be resumed later, and
            // stop. Deliberately no foreground promotion, for the same reason the teardown commands
            // below skip it — this is a service that is going away, not one that is starting.
            explicitCancellation = true
            serviceScope.launch {
                sessionStore.clear()
                notificationPresenter.cancel()
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        // The teardown commands are handled before any foreground promotion: promoting here would
        // post a fresh ongoing notification only to cancel it a moment later, and on API 34+
        // startForeground with a location type throws if the permission has since been revoked —
        // which is exactly when a rider reaches for "cancel".
        when (intent?.action) {
            ACTION_CANCEL -> {
                explicitCancellation = true
                stopLocationCollection()
                serviceScope.launch {
                    // A rider who taps "cancel" and immediately starts another trip: by the time
                    // this runs, the newer start has already written its session and re-promoted the
                    // service, so tearing down here would delete its row and cancel its
                    // notification. Nothing about this cancellation applies to it.
                    if (startId != latestStartId) return@launch
                    sessionStore.clear()
                    speechController.silence()
                    speechController.close()
                    notificationPresenter.cancel()
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_SILENCE -> {
                mutedRequested = true
                engineState = engineState.copy(speechMuted = true)
                speechController.silence()
                val activePlan = plan
                if (activePlan == null && navigationJob?.isActive != true) {
                    notificationPresenter.cancel()
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                // Reached via startForegroundService, so a refused promotion has to end the service
                // rather than leave it un-promoted for the platform to kill.
                if (!promoteToForeground(notificationPresenter.foregroundNotification(activePlan))) {
                    serviceScope.launch { sessionStore.clear() }
                    notificationPresenter.cancel()
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                activePlan?.let {
                    serviceScope.launch { sessionStore.persist(it, engineState, wallClock()) }
                }
                return START_STICKY
            }
        }

        // Android requires foreground promotion immediately. Plan restoration and validation can safely
        // suspend only after this process-visible notification exists.
        if (!promoteToForeground(notificationPresenter.foregroundNotification())) {
            // Without the location permission there is nothing this service can do; a sticky
            // restart after the rider revoked it lands here.
            serviceScope.launch { sessionStore.clear() }
            notificationPresenter.cancel()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Identifiers only — the session itself lives in the store. Any fresh launch supersedes what
        // is running and inherits nothing from it, including the previous rider's "silence" choice.
        // A sticky restart arrives with no intent at all and simply resumes.
        val requestedSessionId = intent?.getStringExtra(SESSION_ID)
        val supersedes = (requestedSessionId != null && requestedSessionId != plan?.sessionId) ||
            intent?.hasExtra(TRIP_ID) == true
        if (supersedes) {
            mutedRequested = false
            // Including a cancellation this launch raced: it applied to the session being replaced,
            // and leaving it set would cancel this session's "you have arrived" summary on destroy.
            explicitCancellation = false
        }
        if (navigationJob?.isActive != true || supersedes) {
            navigationJob?.cancel()
            navigationJob = serviceScope.launch { initialize(intent, requestedSessionId) }
        }
        return START_STICKY
    }

    /**
     * Loads the session to monitor. There is exactly one stored at a time, so [requestedSessionId]
     * is not a lookup key — it says which session the caller believes it just wrote, and a mismatch
     * means another has superseded it in between. The stored session wins either way; it is the
     * source of truth that a sticky restart (which arrives with no intent at all) has to rely on.
     */
    private suspend fun initialize(intent: Intent?, requestedSessionId: String?) {
        importGate.awaitReady()
        // The legacy trip-details launch carries stop and trip ids rather than a session, so the
        // service is the one that builds its plan — and therefore the one that stores it.
        val legacyPlan = if (requestedSessionId == null) intent?.legacyPlan() else null
        if (legacyPlan != null) {
            sessionStore.start(legacyPlan.withLegacyShapesResolved(), wallClock())
        }

        val restored = sessionStore.restore(wallClock())
        if (restored == null) {
            Log.w(TAG, "No reminder session to monitor")
            notificationPresenter.cancel()
            stopSelf()
            return
        }
        if (requestedSessionId != null && restored.plan.sessionId != requestedSessionId) {
            Log.i(TAG, "Session $requestedSessionId was superseded by ${restored.plan.sessionId}")
        }

        val activePlan = restored.plan
        plan = activePlan
        engineState = restored.state.let { if (mutedRequested) it.copy(speechMuted = true) else it }
        if (!promoteToForeground(notificationPresenter.foregroundNotification(activePlan))) {
            sessionStore.clear()
            notificationPresenter.cancel()
            stopSelf()
            return
        }

        locationRepository.locationUpdates(NAV_UPDATE_INTERVAL_SECONDS).collect(::handleLocation)
    }

    /**
     * Looks up the route shape for a legacy ride, which arrives with a trip id and two stop ids and
     * no geometry. Identified by the absent boarding stop: a plan built from a directions itinerary
     * carries its own geometry and is left alone, which also avoids a pointless lookup of an OTP
     * trip id that would not resolve against the OBA API.
     *
     * Best-effort in every direction. The lookup is bounded by [SHAPE_LOOKUP_TIMEOUT] so a slow
     * network cannot hold up the start of monitoring, and any failure simply leaves the ride on
     * straight-line distances — the behaviour it had before shapes existed.
     */
    private suspend fun ReminderPlan.withLegacyShapesResolved(): ReminderPlan {
        val ride = rides.singleOrNull() ?: return this
        if (ride.shape != null || ride.board != null) return this
        val shape = withTimeoutOrNull(SHAPE_LOOKUP_TIMEOUT) {
            shapeSource.shapeFor(ride.tripId, ride.penultimate.id, ride.alight.id)
        }
        if (shape == null) {
            Log.i(TAG, "No shape for trip ${ride.tripId}; monitoring by straight-line distance")
            return this
        }
        return copy(rides = listOf(ride.copy(shape = shape)))
    }

    private suspend fun Intent.legacyPlan(): ReminderPlan? {
        val destinationId = getStringExtra(DESTINATION_ID) ?: return null
        val beforeId = getStringExtra(BEFORE_STOP_ID) ?: return null
        val tripId = getStringExtra(TRIP_ID) ?: return null
        val destination = stopDao.getStop(destinationId) ?: return null
        val before = stopDao.getStop(beforeId) ?: return null
        val beforeStop = ReminderStop(before.id, before.name, ReminderPoint(before.latitude, before.longitude))
        val destinationStop = ReminderStop(
            destination.id,
            destination.name,
            ReminderPoint(destination.latitude, destination.longitude)
        )
        val now = wallClock()
        return (
            ReminderPlanBuilder.buildSingleRide(
                sessionId = "legacy-${now.epochMs}",
                tripId = tripId,
                // The legacy trip-details launch contract carries no boarding stop.
                board = null,
                penultimate = beforeStop,
                alight = destinationStop,
                scheduledStart = null,
                scheduledEnd = null
            ) as? ReminderPlanResult.Success
            )?.plan
    }

    private suspend fun handleLocation(location: Location) {
        val activePlan = plan ?: return
        val sample = ReminderLocationSample(
            point = ReminderPoint(location.latitude, location.longitude),
            // A fix that reports no accuracy reads as 0f, which the engine's accuracy gate would
            // accept as a perfect one. NaN fails that range test, so the sample is rejected instead
            // of driving alerts off an unknown-quality position.
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.NaN,
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
            timestamp = WallTime(location.time)
        )
        val previous = engineState
        val transition = ReminderEngine.reduce(activePlan, previous, sample)
        if (transition.state == previous && transition.effects.isEmpty()) return
        engineState = transition.state
        if (previous.restorationKey() != engineState.restorationKey()) {
            sessionStore.persist(activePlan, engineState, wallClock())
        }
        transition.effects.forEach { dispatch(it) }
    }

    private suspend fun dispatch(effect: ReminderEffect) {
        notificationPresenter.present(effect)
        if (!engineState.speechMuted) speechController.speak(effect)
        when (effect) {
            is ReminderEffect.GetReady -> report(R.string.analytics_label_destination_reminder_variant_get_ready)
            is ReminderEffect.AlightNow -> report(R.string.analytics_label_destination_reminder_variant_exit_at_next_stop)
            ReminderEffect.SessionCompleted -> completeSession()
            else -> Unit
        }
    }

    private suspend fun completeSession() {
        report(R.string.analytics_label_destination_reminder_variant_ended)
        stopLocationCollection()
        withContext(NonCancellable) {
            sessionStore.clear()
            speechController.close()
            feedbackRepository.requestFeedback()
            // Detach rather than remove, so the "you have arrived" summary outlives the service on
            // every supported API level (the platform overload is API 24+; this compat call is not).
            ServiceCompat.stopForeground(this@NavigationService, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun stopLocationCollection() {
        navigationJob?.cancel()
        navigationJob = null
        plan = null
    }

    private fun report(label: Int) {
        obaAnalytics.reportUiEvent(
            PlausibleAnalytics.REPORT_DESTINATION_REMINDER_EVENT_URL,
            getString(R.string.analytics_label_destination_reminder),
            getString(label)
        )
    }

    /**
     * Promotes to the foreground, returning false when the platform refused. On API 34+ a
     * location-typed foreground service throws if the app no longer holds a location permission,
     * which the rider can revoke at any time — including while the service is stopped and awaiting
     * a sticky restart.
     */
    private fun promoteToForeground(notification: android.app.Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot run destination reminders in the foreground; stopping", e)
        false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        speechController.close()
        if (explicitCancellation) notificationPresenter.cancel()
        sendBroadcast(Intent(TripDetailsLauncher.ACTION_SERVICE_DESTROYED).setPackage(packageName))
        super.onDestroy()
    }

    private fun wallClock(): WallTime = WallTime.now()

    companion object {
        const val TAG = "NavigationService"
        const val NOTIFICATION_ID = 33620
        const val DESTINATION_ID = ".DestinationId"
        const val BEFORE_STOP_ID = ".BeforeId"
        const val TRIP_ID = ".TripId"

        /** The session the caller has just written to the store. State never travels in the intent. */
        const val SESSION_ID = ".ReminderSessionId"
        const val ACTION_SILENCE = "org.onebusaway.android.nav.SILENCE"
        const val ACTION_CANCEL = "org.onebusaway.android.nav.CANCEL"
        private const val NAV_UPDATE_INTERVAL_SECONDS = 1

        /** Upper bound on the legacy shape lookup, so a slow network cannot delay monitoring. */
        private val SHAPE_LOOKUP_TIMEOUT = 10.seconds
    }
}
