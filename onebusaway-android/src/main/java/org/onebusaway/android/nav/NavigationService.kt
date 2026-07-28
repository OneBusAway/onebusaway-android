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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavStopRecord
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher

/** Thin foreground-service orchestrator for the pure [ReminderEngine]. */
@AndroidEntryPoint
class NavigationService : Service() {
    @Inject internal lateinit var sessionStore: ReminderSessionStore

    @Inject internal lateinit var notificationPresenter: ReminderNotificationPresenter

    @Inject internal lateinit var speechController: ReminderSpeechController

    @Inject internal lateinit var feedbackRepository: NavigationFeedbackRepository

    @Inject lateinit var navStopDao: NavStopDao

    @Inject lateinit var stopDao: StopDao

    @Inject lateinit var importGate: ImportGate

    @Inject lateinit var obaAnalytics: ObaAnalytics

    @Inject lateinit var locationRepository: LocationRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var navigationJob: Job? = null
    private var plan: ReminderPlan? = null
    private var engineState = ReminderEngineState()
    private var explicitCancellation = false
    private var mutedRequested = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires foreground promotion immediately. Plan restoration and validation can safely
        // suspend only after this process-visible notification exists.
        promoteToForeground(notificationPresenter.foregroundNotification())

        when (intent?.action) {
            ACTION_SILENCE -> {
                mutedRequested = true
                engineState = engineState.copy(speechMuted = true)
                speechController.silence()
                plan?.let { activePlan ->
                    serviceScope.launch {
                        sessionStore.persist(
                            activePlan,
                            engineState,
                            wallClock()
                        )
                    }
                }
                if (plan == null && navigationJob?.isActive != true) {
                    notificationPresenter.cancel()
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
            ACTION_CANCEL -> {
                explicitCancellation = true
                serviceScope.launch {
                    sessionStore.clear()
                    speechController.silence()
                    speechController.close()
                    notificationPresenter.cancel()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        val incomingPlan = intent?.getStringExtra(PLAN_JSON)?.let(ReminderPlanJson::decode)
        val supersedes = incomingPlan != null && incomingPlan.sessionId != plan?.sessionId
        if (navigationJob?.isActive != true || supersedes) {
            navigationJob?.cancel()
            navigationJob = serviceScope.launch { initialize(intent, incomingPlan) }
        }
        return START_STICKY
    }

    private suspend fun initialize(intent: Intent?, decodedIncomingPlan: ReminderPlan?) {
        importGate.awaitReady()
        val incomingPlan = decodedIncomingPlan
            ?: intent?.legacyPlan()
        val restored = if (incomingPlan == null) sessionStore.restore(wallClock()) else null
        val activePlan = incomingPlan ?: restored?.plan
        if (activePlan == null) {
            Log.w(TAG, "No valid reminder plan to start or restore")
            notificationPresenter.cancel()
            stopSelf()
            return
        }

        plan = activePlan
        engineState = (restored?.state ?: ReminderEngineState()).let {
            if (mutedRequested) it.copy(speechMuted = true) else it
        }
        if (incomingPlan != null) {
            sessionStore.start(activePlan, wallClock())
            persistLegacyCompatibility(activePlan)
        }
        sessionStore.persist(activePlan, engineState, wallClock())
        promoteToForeground(notificationPresenter.foregroundNotification(activePlan))

        locationRepository.locationUpdates(NAV_UPDATE_INTERVAL_SECONDS).collect(::handleLocation)
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
                board = beforeStop,
                penultimate = beforeStop,
                alight = destinationStop,
                scheduledStart = ServerTime(now.epochMs),
                scheduledEnd = ServerTime(now.epochMs)
            ) as? ReminderPlanResult.Success
            )?.plan
    }

    private suspend fun persistLegacyCompatibility(plan: ReminderPlan) {
        val ride = plan.rides.first()
        navStopDao.replaceActive(
            NavStopRecord(
                navId = "1",
                startTime = plan.rides.first().scheduledStart.epochMs,
                tripId = ride.tripId,
                destinationId = ride.alight.id,
                beforeId = ride.penultimate.id,
                sequence = 1,
                active = 1
            )
        )
    }

    private suspend fun handleLocation(location: Location) {
        val activePlan = plan ?: return
        val sample = ReminderLocationSample(
            point = ReminderPoint(location.latitude, location.longitude),
            accuracyMeters = location.accuracy,
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() },
            timestamp = WallTime(location.time)
        )
        val previous = engineState
        val transition = ReminderEngine.reduce(activePlan, previous, sample)
        if (transition.state == previous && transition.effects.isEmpty()) return
        engineState = transition.state
        sessionStore.persist(activePlan, engineState, wallClock())
        transition.effects.forEach { dispatch(activePlan, it) }
    }

    private suspend fun dispatch(activePlan: ReminderPlan, effect: ReminderEffect) {
        notificationPresenter.present(activePlan, effect)
        if (!engineState.speechMuted) speechController.speak(activePlan, effect)
        when (effect) {
            is ReminderEffect.GetReady -> report(R.string.analytics_label_destination_reminder_variant_get_ready)
            is ReminderEffect.AlightNow -> report(R.string.analytics_label_destination_reminder_variant_exit_at_next_stop)
            ReminderEffect.SessionCompleted -> completeSession()
            else -> Unit
        }
    }

    private suspend fun completeSession() {
        report(R.string.analytics_label_destination_reminder_variant_ended)
        sessionStore.clear()
        speechController.close()
        feedbackRepository.requestFeedback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        stopSelf()
    }

    private fun report(label: Int) {
        obaAnalytics.reportUiEvent(
            PlausibleAnalytics.REPORT_DESTINATION_REMINDER_EVENT_URL,
            getString(R.string.analytics_label_destination_reminder),
            getString(label)
        )
    }

    private fun promoteToForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        const val PLAN_JSON = ".ReminderPlanJson"
        const val ACTION_SILENCE = "org.onebusaway.android.nav.SILENCE"
        const val ACTION_CANCEL = "org.onebusaway.android.nav.CANCEL"
        private const val NAV_UPDATE_INTERVAL_SECONDS = 1
    }
}
