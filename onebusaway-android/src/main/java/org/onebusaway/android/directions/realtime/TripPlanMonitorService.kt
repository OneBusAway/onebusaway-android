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
package org.onebusaway.android.directions.realtime

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.toJson
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripplan.TripPlanRepository

/**
 * Foreground service that watches a planned trip for changes while the user is within the
 * pre-departure query window, and notifies them if the recommended itinerary is delayed, early, or
 * superseded. This is the coroutine-loop replacement for the WorkManager/AlarmManager poll
 * (`RealtimeChecker` + `RealtimeCheckWorker` driven by `RealtimeReceiver` on a 60s repeating alarm).
 *
 * The recurring check is a plain `while { check(); delay(60s) }` loop on [serviceScope] (mirroring
 * [org.onebusaway.android.nav.NavigationService]); the service stops itself as soon as it notifies,
 * the plan returns nothing, or the monitored trip departs, so the foreground notification is short-lived.
 */
@AndroidEntryPoint
class TripPlanMonitorService : Service() {

    @Inject lateinit var tripPlanRepository: TripPlanRepository

    // Hold the Job explicitly so onDestroy cancels it via Job.cancel() (a member) rather than the
    // CoroutineScope.cancel() extension — importing that extension trips lint's MemberExtensionConflict
    // against the Job.cancel() member call in onStartCommand.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val extras = intent?.extras
        val target = extras?.let { readNotificationTarget(it) }
        val parse = extras?.let { readMonitorState(it) }
        if (extras == null || target == null || parse !is MonitorStateParse.Valid) {
            // Stop without notifying on any unusable state. Incompatible = a pending alarm / redelivered
            // intent written by a newer monitor format that survived an app update; we can't interpret it,
            // so we never risk a spurious "your trip changed" alert on it.
            when (parse) {
                MonitorStateParse.Incompatible ->
                    Log.w(TAG, "Monitor bundle is a newer, incompatible format - stopping")
                else -> Log.w(TAG, "Missing monitoring state - stopping")
            }
            stopSelf()
            return Service.START_NOT_STICKY
        }

        // Must promote to the foreground promptly after startForegroundService(); do it synchronously.
        startForegroundMonitoring(target)

        // A fresh start supersedes any in-flight loop (re-selected option / redelivered intent).
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            runMonitorLoop(extras, parse.description, target, parse.departure)
        }

        // Redeliver the monitoring state if the service is killed and restarted mid-window.
        return Service.START_REDELIVER_INTENT
    }

    private suspend fun runMonitorLoop(
        extras: Bundle,
        desc: ItineraryDescription,
        target: Class<*>,
        departure: ServerTime?
    ) {
        try {
            while (coroutineContext.isActive) {
                // Stop once the trip departs — a delay/change warning is moot once travel has begun.
                // (isExpired, the trip-end guard, is the fallback when the departure time is unknown.)
                if (TripMonitorWindow.hasDeparted(departure, WallTime.now())) {
                    Log.d(TAG, "Monitored trip has departed - stopping")
                    break
                }
                if (desc.isExpired(Instant.now())) {
                    Log.d(TAG, "Monitored trip has ended - stopping")
                    break
                }

                // A single cycle's work is wrapped so a transient failure (a malformed plan response
                // that trips initFromBundleSimple/decide, say) logs and retries next tick rather than
                // escaping the Main-dispatched coroutine and crashing the app. planBlocking itself never
                // throws (it maps failures to an empty list, which decide() turns into a graceful Stop).
                // CancellationException must propagate so job cancellation / service teardown still works.
                try {
                    val builder = TripRequestBuilder.initFromBundleSimple(this@TripPlanMonitorService, extras)
                    val itineraries = withContext(Dispatchers.IO) {
                        tripPlanRepository.planBlocking(builder)
                    }

                    val result = TripMonitorDecider.decide(
                        desc,
                        itineraries,
                        OTPConstants.REALTIME_SERVICE_DELAY_THRESHOLD
                    )
                    if (BuildConfig.DEBUG) Log.d(TAG, "Monitor check result: $result")
                    when (result) {
                        is MonitorResult.Deviation -> {
                            notifyChange(
                                target,
                                builder,
                                itineraries,
                                titleRes = if (result.delaySeconds > 0) {
                                    R.string.trip_plan_delay
                                } else {
                                    R.string.trip_plan_early
                                },
                                messageRes = R.string.trip_plan_notification_deviation_text
                            )
                            break
                        }

                        MonitorResult.ItineraryChanged -> {
                            // The monitored itinerary is no longer among the recommended results — the
                            // plan "changed", which is not the same as "a better plan was found".
                            notifyChange(
                                target,
                                builder,
                                itineraries,
                                titleRes = R.string.trip_plan_notification_changed_title,
                                messageRes = R.string.trip_plan_notification_changed_text
                            )
                            break
                        }

                        MonitorResult.Stop -> break
                        MonitorResult.KeepMonitoring -> Unit // fall through to the delay
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Monitor check failed - will retry next cycle", e)
                }

                delay(OTPConstants.DEFAULT_UPDATE_INTERVAL_TRIP_TIME)
            }
        } finally {
            // Only tear down if we're still the active loop. A newer onStartCommand (the user re-selected
            // an option, restarting the service) cancels this job and takes over the foreground lifecycle;
            // stopping here would kill that fresh monitor. All of this runs on the Main dispatcher, so the
            // monitorJob field read here can't race the write in onStartCommand.
            if (monitorJob === coroutineContext[Job]) {
                stopMonitoring()
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    // -- Foreground / notifications -------------------------------------------------------------

    private fun startForegroundMonitoring(target: Class<*>) {
        val notification = buildOngoingNotification(target)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    /** The low-key ongoing notification required while the foreground service runs. */
    private fun buildOngoingNotification(target: Class<*>): Notification {
        // Opens HOME (the trip planner is now an on-map directions focus, not a separate destination).
        // Restoring the watched trip into directions focus is a deferred follow-up.
        val openIntent = Intent(applicationContext, target)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return NotificationCompat.Builder(this, NotificationChannels.TRIP_PLAN_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(getString(R.string.trip_plan_monitoring_notification_title))
            .setContentText(getString(R.string.trip_plan_monitoring_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(activityPendingIntent(openIntent))
            .build()
    }

    /** Fires the user-facing "your trip changed" alert (a distinct, dismissable notification). */
    private fun notifyChange(
        target: Class<*>,
        builder: TripRequestBuilder,
        itineraries: List<TripItinerary>,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int
    ) {
        val messageText = getString(messageRes)

        // Reopen HOME with enough state to rehydrate the directions form + these fresh results. The
        // simplified request bundle + JSON itineraries are consumed by HomeActivity.maybeRestoreDirections-
        // FromIntent (#1939), which re-enters the on-map directions focus. The itinerary list is
        // JSON-encoded (kotlinx.serialization), not Intent.putExtra(String, Serializable) — TripItinerary
        // has no reason to implement java.io.Serializable, unlike the OTP1 POJOs this model replaced.
        val requestExtras = Bundle().also { builder.copyIntoBundleSimple(it) }
        val openIntent = Intent(applicationContext, target).apply {
            putExtras(requestExtras)
            putExtra(OTPConstants.ITINERARIES, itineraries.toJson())
            putExtra(OTPConstants.INTENT_SOURCE, OTPConstants.Source.NOTIFICATION)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // Sound / lights / vibration come from the notification channel (O+); PRIORITY_MAX drives the
        // pre-O heads-up. (The legacy checker set the now-deprecated Notification.defaults / FLAG_SHOW_LIGHTS.)
        val notification = NotificationCompat.Builder(this, NotificationChannels.TRIP_PLAN_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(getString(titleRes))
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(activityPendingIntent(openIntent))
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TRIP_CHANGE_NOTIFICATION_ID, notification)
    }

    private fun activityPendingIntent(intent: Intent): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(applicationContext, 0, intent, flags)
    }

    private fun stopMonitoring() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // -- Monitoring state (read back from the intent extras) ------------------------------------

    /** Pull the persisted primitives out of the [Bundle] and validate them (see [parseMonitorState]). */
    private fun readMonitorState(extras: Bundle): MonitorStateParse = parseMonitorState(
        version = if (extras.containsKey(TripPlanMonitor.EXTRA_MONITOR_VERSION)) {
            extras.getInt(TripPlanMonitor.EXTRA_MONITOR_VERSION)
        } else {
            null
        },
        tripIds = extras.getStringArray(TripPlanMonitor.EXTRA_ITINERARY_DESC)?.toList(),
        startDateMillis = extras.getLong(TripPlanMonitor.EXTRA_ITINERARY_START_DATE),
        endDateMillis = extras.getLong(TripPlanMonitor.EXTRA_ITINERARY_END_DATE)
    )

    private fun readNotificationTarget(extras: Bundle): Class<*>? {
        val name = extras.getString(OTPConstants.NOTIFICATION_TARGET) ?: return null
        return try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Notification target class not found: $name", e)
            null
        }
    }

    private companion object {
        const val TAG = "TripPlanMonitorSvc"

        /** Stable id for the ongoing foreground notification (distinct from the change-alert id). */
        const val ONGOING_NOTIFICATION_ID = 0x7C1D

        /**
         * Stable id for the "your trip changed / is delayed" alert. Fixed (not per-trip) because only one
         * trip is monitored at a time, so a later alert correctly replaces an earlier one.
         */
        const val TRIP_CHANGE_NOTIFICATION_ID = 0x7C1E
    }
}
