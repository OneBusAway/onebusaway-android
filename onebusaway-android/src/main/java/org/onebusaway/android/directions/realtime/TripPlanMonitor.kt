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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.toRequestBuilder
import org.onebusaway.android.util.PreferenceUtils
import org.opentripplanner.api.model.Itinerary

/**
 * Entry point + scheduler for the trip-plan-change monitor. Replaces the WorkManager/AlarmManager poll
 * (`RealtimeChecker` + `RealtimeCheckWorker` + `RealtimeReceiver`): the repeating ~60s poll becomes an
 * in-process coroutine loop inside [TripPlanMonitorService] (a foreground service), and this object only
 * decides *when* that service should run.
 *
 * - Within the pre-departure query window: start the foreground service immediately.
 * - Further out: schedule a **single** one-shot alarm to start the service at `departure − window`, so
 *   no ongoing notification is shown until monitoring actually begins ([TripPlanMonitorAlarmReceiver]).
 *
 * The monitored state (the simplified trip request + the watched itinerary's trip ids / start+end times
 * + the notification target) is packed into a [Bundle] carried by the service intent / alarm
 * PendingIntent — the same simplified-bundle format the notification re-entry reader already speaks
 * (`TripRequestBuilder.initFromBundleSimple`).
 */
object TripPlanMonitor {

    private const val TAG = "TripPlanMonitor"

    /** Trip ids of the transit legs the user is watching (String[]). */
    const val EXTRA_ITINERARY_DESC = "org.onebusaway.android.tripmonitor.ITINERARY_DESC"

    /** The watched itinerary's departure time, epoch millis (long; 0 if unknown). */
    const val EXTRA_ITINERARY_START_DATE = "org.onebusaway.android.tripmonitor.ITINERARY_START_DATE"

    /** The watched itinerary's end time, epoch millis (long). */
    const val EXTRA_ITINERARY_END_DATE = "org.onebusaway.android.tripmonitor.ITINERARY_END_DATE"

    internal const val ACTION_START_MONITORING =
        "org.onebusaway.android.tripmonitor.action.START_MONITORING"

    /** Fixed request code for the deferral alarm's PendingIntent (one monitored trip at a time). */
    private const val ALARM_REQUEST_CODE = 0

    /**
     * Arm monitoring for the [itinerary] the user selected, re-planning [params] to detect delays or a
     * superseded plan. No-op when live updates are disabled, or the itinerary has no realtime legs, or
     * its description is incomplete (mirrors the legacy `RealtimeChecker` guards).
     *
     * @param notificationTarget the activity to reopen from a change notification (HomeActivity).
     */
    @JvmStatic
    fun start(
        context: Context,
        params: TripPlanParams,
        itinerary: Itinerary,
        notificationTarget: Class<*>,
    ) {
        val app = context.applicationContext

        if (!PreferenceUtils.getBoolean(OTPConstants.PREFERENCE_KEY_LIVE_UPDATES, true)) {
            return
        }

        val desc = try {
            ItineraryDescription(itinerary)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot monitor - unable to describe itinerary", e)
            return
        }

        // Only monitor itineraries with realtime legs — a schedule-only itinerary can't be "delayed"
        // relative to realtime, matching the legacy realtime-legs gate.
        if (itinerary.legs.none { it.realTime }) {
            Log.d(TAG, "No realtime legs on itinerary - not monitoring")
            return
        }

        val tripIds = desc.tripIds
        val endDate = desc.endDate
        if (tripIds.isEmpty() || endDate == null) {
            Log.w(TAG, "Itinerary description incomplete - not monitoring")
            return
        }

        // The itinerary's departure (origin start), used to stop monitoring once travel begins — a
        // warning is moot after that. 0 if unparseable; the service then falls back to the end-time guard.
        val itineraryDepartureMillis = ConversionUtils.parseOtpDate(itinerary.startTime)?.toEpochMilli() ?: 0L

        val builder = params.toRequestBuilder(app)
        val bundle = Bundle().apply {
            builder.copyIntoBundleSimple(this)
            putStringArray(EXTRA_ITINERARY_DESC, tripIds.toTypedArray())
            putLong(EXTRA_ITINERARY_START_DATE, itineraryDepartureMillis)
            putLong(EXTRA_ITINERARY_END_DATE, endDate.toEpochMilli())
            putString(OTPConstants.NOTIFICATION_TARGET, notificationTarget.name)
        }

        // Supersede any previously-scheduled start (the user re-selected an option / re-planned).
        cancelScheduledStart(app)

        // The deferral is based on the request's date/time (the legacy rescheduleRealtimeUpdates basis),
        // which is the departure for a "depart at" trip; the stop-at-departure guard uses the itinerary's
        // actual start time.
        val requestTimeMillis = builder.dateTime?.toEpochMilli()
        if (requestTimeMillis != null && !TripMonitorWindow.shouldStartNow(
                requestTimeMillis, System.currentTimeMillis(), OTPConstants.REALTIME_SERVICE_QUERY_WINDOW
            )
        ) {
            scheduleStart(app, bundle, requestTimeMillis - OTPConstants.REALTIME_SERVICE_QUERY_WINDOW)
        } else {
            startServiceNow(app, bundle)
        }
    }

    private fun startServiceNow(context: Context, bundle: Bundle) {
        Log.d(TAG, "Starting trip-plan monitor now")
        val intent = Intent(context, TripPlanMonitorService::class.java).putExtras(bundle)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun scheduleStart(context: Context, bundle: Bundle, startAtMillis: Long) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Scheduling trip-plan monitor to start at $startAtMillis")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent(context, bundle)

        // A single one-shot alarm (not a repeating poll). An exact alarm-fired broadcast is granted the
        // temporary background foreground-service-start exemption the receiver relies on; fall back to an
        // inexact allow-while-idle alarm if the user has revoked exact-alarm access (a few minutes of
        // slop into the query window is harmless).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAtMillis, pendingIntent)
        }
    }

    /** Cancels a pending deferral alarm, if any. Safe to call when nothing is scheduled. */
    @JvmStatic
    fun cancelScheduledStart(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context.applicationContext, Bundle()))
    }

    /**
     * The deferral alarm PendingIntent. Extras don't participate in PendingIntent identity (only the
     * action/component do), so the [Bundle] here is only meaningful when scheduling; [cancelScheduledStart]
     * can pass an empty one and still match.
     */
    private fun alarmPendingIntent(context: Context, bundle: Bundle): PendingIntent {
        val intent = Intent(context, TripPlanMonitorAlarmReceiver::class.java)
            .setAction(ACTION_START_MONITORING)
            .putExtras(bundle)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
    }
}
