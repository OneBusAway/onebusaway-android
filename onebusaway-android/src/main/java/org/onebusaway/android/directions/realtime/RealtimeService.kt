/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.realtime

import android.app.Activity
import android.app.AlarmManager
import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.legacy.content.WakefulBroadcastReceiver
import org.onebusaway.android.R
import org.onebusaway.android.app.di.TripPlanRepositoryEntryPoint
import org.onebusaway.android.directions.model.ItineraryDescription
import org.onebusaway.android.directions.util.OTPConstants
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.PreferenceUtils
import org.opentripplanner.api.model.Itinerary
import java.time.Instant

/**
 * This service is started after a trip is planned by the user so they can be notified if the
 * trip results for their request change in the near future. For example, if a user plans a trip,
 * and then the top result for that trip gets delayed by 20 minutes, the user will be notified
 * that new trip results are available.
 */
open class RealtimeService : IntentService("RealtimeService") {

    public override fun onHandleIntent(intent: Intent?) {
        val bundle = intent?.extras ?: Bundle()

        when (intent?.action) {
            OTPConstants.INTENT_START_CHECKS -> {
                disableListenForTripUpdates()
                if (!rescheduleRealtimeUpdates(bundle)) {
                    val itinerary = getItinerary(bundle)
                    if (itinerary != null) {
                        startRealtimeUpdates(bundle, itinerary)
                    } else {
                        Log.w(TAG, "Cannot start realtime updates - no itinerary in bundle")
                    }
                }
            }

            OTPConstants.INTENT_CHECK_TRIP_TIME -> checkForItineraryChange(bundle)
        }

        intent?.let { WakefulBroadcastReceiver.completeWakefulIntent(it) }
    }

    // Depending on preferences / whether there is realtime info, start updates.
    private fun startRealtimeUpdates(params: Bundle, itinerary: Itinerary) {
        Log.d(TAG, "Checking whether to start realtime updates.")

        val realtimeLegsOnItineraries = itinerary.legs.any { it.realTime }

        if (realtimeLegsOnItineraries) {
            Log.d(TAG, "Starting realtime updates for itinerary")

            val alarmIntent = getAlarmIntent(params)
            if (alarmIntent == null) {
                Log.e(TAG, "Not scheduling realtime updates - unable to build alarm PendingIntent")
                return
            }

            // init alarm mgr
            getAlarmManager().setInexactRepeating(
                AlarmManager.RTC, System.currentTimeMillis(),
                OTPConstants.DEFAULT_UPDATE_INTERVAL_TRIP_TIME, alarmIntent
            )
        } else {
            Log.d(TAG, "No realtime legs on itinerary")
        }
    }

    /**
     * Check to see if the start of real-time trip updates should be rescheduled, and if necessary
     * reschedule it
     *
     * @param bundle trip details to be passed to TripRequestBuilder constructor
     * @return true if the start of trip real-time updates has been rescheduled, false if updates
     * should begin immediately
     */
    fun rescheduleRealtimeUpdates(bundle: Bundle): Boolean {
        // Delay if this trip doesn't start for at least an hour
        val start = TripRequestBuilder(this, bundle).dateTime

        if (start != null) {
            val queryStart = start.minusMillis(OTPConstants.REALTIME_SERVICE_QUERY_WINDOW)
            if (Instant.now().isBefore(queryStart)) {
                Log.d(TAG, "Start service at $queryStart")
                val future = Intent(applicationContext, RealtimeWakefulReceiver::class.java)
                future.action = OTPConstants.INTENT_START_CHECKS
                future.putExtras(bundle)

                val flags = immutableFlags(PendingIntent.FLAG_CANCEL_CURRENT)
                val pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, future, flags)
                getAlarmManager().set(AlarmManager.RTC_WAKEUP, queryStart.toEpochMilli(), pendingIntent)
                return true
            }
        } else {
            Log.w(
                TAG, "Trip start time is null - bundle may be incomplete. See #790 and #791. " +
                    "Bundle keys: " + bundle.keySet()
            )
        }

        return false
    }

    private fun checkForItineraryChange(bundle: Bundle) {
        val builder = TripRequestBuilder.initFromBundleSimple(this, bundle)
        val desc = getItineraryDescription(bundle)
        val target = getNotificationTarget(bundle)
        // A CHECK broadcast missing its itinerary/target extras is malformed (the receiver is
        // exported, so this can arrive from outside): stop listening rather than crash.
        if (desc == null || target == null) {
            disableListenForTripUpdates()
            return
        }
        checkForItineraryChange(target, builder, desc)
    }

    private fun checkForItineraryChange(
        source: Class<*>,
        builder: TripRequestBuilder,
        itineraryDescription: ItineraryDescription,
    ) {
        Log.d(TAG, "Check for change")

        // onHandleIntent already runs on the IntentService worker thread, so we can plan
        // synchronously here. This replaces the legacy TripRequest AsyncTask + callback.
        val itineraries: List<Itinerary> = try {
            TripPlanRepositoryEntryPoint.get(this).planBlocking(builder)
        } catch (e: Exception) {
            Log.e(TAG, "checkForItineraryChange: error planning trip", e)
            disableListenForTripUpdates()
            return
        }

        if (itineraries.isEmpty()) {
            Log.e(TAG, "Failure checking itineraries - no results returned.")
            disableListenForTripUpdates()
            return
        }

        // Check each itinerary. Notify user if our *current* itinerary doesn't exist
        // or has a lower rank.
        var skippedMalformed = false
        for (i in itineraries.indices) {
            // Skip a malformed itinerary rather than crash the worker thread (mirrors the guard in
            // getSimplifiedBundle).
            val other = try {
                ItineraryDescription(itineraries[i])
            } catch (e: NullPointerException) {
                Log.e(TAG, "checkForItineraryChange: error creating ItineraryDescription", e)
                skippedMalformed = true
                continue
            } catch (e: IndexOutOfBoundsException) {
                Log.e(TAG, "checkForItineraryChange: error creating ItineraryDescription", e)
                skippedMalformed = true
                continue
            }

            if (itineraryDescription.itineraryMatches(other)) {

                // A null delay means an end time we couldn't parse: the itinerary still matches, so
                // treat it as "exists, no actionable change" rather than notifying.
                val delay = itineraryDescription.getDelay(other)
                Log.d(TAG, "Schedule deviation on itinerary: $delay")

                if (delay != null && Math.abs(delay) > OTPConstants.REALTIME_SERVICE_DELAY_THRESHOLD) {
                    Log.d(TAG, "Notify due to large early/late schedule deviation.")
                    showNotification(
                        itineraryDescription,
                        if (delay > 0) R.string.trip_plan_delay else R.string.trip_plan_early,
                        R.string.trip_plan_notification_new_plan_text,
                        source, builder.getBundle(), itineraries
                    )
                    disableListenForTripUpdates()
                    return
                }

                // Otherwise, we are still good.
                Log.d(TAG, "Itinerary exists and no large schedule deviation.")
                checkDisableDueToTimeout(itineraryDescription)

                return
            }
        }
        if (skippedMalformed) {
            // A matching itinerary may have been among the ones we couldn't parse, so we can't
            // conclude the trip changed — don't fire a spurious "trip changed" notification.
            Log.d(TAG, "Skipped unparseable itineraries; not notifying of a change.")
            disableListenForTripUpdates()
            return
        }
        Log.d(
            TAG,
            "Did not find a matching itinerary in new call - notify user that something has changed."
        )
        showNotification(
            itineraryDescription,
            R.string.trip_plan_notification_new_plan_title,
            R.string.trip_plan_notification_new_plan_text, source,
            builder.getBundle(), itineraries
        )
        disableListenForTripUpdates()
    }

    private fun showNotification(
        description: ItineraryDescription,
        title: Int,
        message: Int,
        notificationTarget: Class<*>,
        params: Bundle,
        itineraries: List<Itinerary>,
    ) {
        val titleText = resources.getString(title)
        val messageText = resources.getString(message)

        val openIntent = Intent(applicationContext, notificationTarget)
        openIntent.putExtras(params)
        openIntent.putExtra(OTPConstants.INTENT_SOURCE, OTPConstants.Source.NOTIFICATION)
        @Suppress("UNCHECKED_CAST")
        openIntent.putExtra(OTPConstants.ITINERARIES, itineraries as ArrayList<Itinerary>)
        // The trip-plan screen is now a HomeActivity NavHost destination; route the re-entry there.
        // (notificationTarget resolves to HomeActivity because TripResultsFragment — which starts
        // this service — is hosted by HomeActivity's fragment manager.)
        openIntent.putExtra(NavRoutes.EXTRA_NAV_ROUTE, NavRoutes.TRIP_PLAN)
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = immutableFlags(PendingIntent.FLAG_CANCEL_CURRENT)
        val openPendingIntent = PendingIntent.getActivity(applicationContext, 0, openIntent, flags)

        val mBuilder = NotificationCompat.Builder(
            applicationContext, NotificationChannels.TRIP_PLAN_UPDATES_ID
        )
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(titleText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openPendingIntent)

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = mBuilder.build()
        notification.defaults = Notification.DEFAULT_ALL
        notification.flags =
            notification.flags or Notification.FLAG_AUTO_CANCEL or Notification.FLAG_SHOW_LIGHTS

        val notificationId = description.id
        notificationManager.notify(notificationId, notification)
    }

    // If the end time for this itinerary has passed, disable trip updates.
    private fun checkDisableDueToTimeout(itineraryDescription: ItineraryDescription) {
        if (itineraryDescription.isExpired(Instant.now())) {
            Log.d(TAG, "End of trip has passed.")
            disableListenForTripUpdates()
        }
    }

    fun disableListenForTripUpdates() {
        Log.d(TAG, "Disable trip updates.")
        getAlarmIntent(null)?.let { getAlarmManager().cancel(it) }
    }

    private fun getAlarmManager(): AlarmManager =
        applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Android S+ requires every PendingIntent to declare mutability; ours are all immutable.
    private fun immutableFlags(base: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base or PendingIntent.FLAG_IMMUTABLE
        } else {
            base
        }

    private fun getAlarmIntent(bundle: Bundle?): PendingIntent? {
        // Use an explicit Intent for Android U+ restrictions on implicit PendingIntents
        val intent = Intent(applicationContext, RealtimeWakefulReceiver::class.java)
        intent.action = OTPConstants.INTENT_CHECK_TRIP_TIME
        if (bundle != null) {
            val extras = getSimplifiedBundle(bundle)
            if (extras == null) {
                Log.e(TAG, "getAlarmIntent: simplified bundle is null, returning null PendingIntent")
                return null
            }
            intent.putExtras(extras)
        }
        val flags = immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        return PendingIntent.getBroadcast(applicationContext, 0, intent, flags)
    }

    fun getItinerary(bundle: Bundle): Itinerary? {
        @Suppress("UNCHECKED_CAST")
        val itineraries =
            bundle.getSerializable(OTPConstants.ITINERARIES) as? ArrayList<Itinerary>
        if (itineraries.isNullOrEmpty()) {
            return null
        }
        val i = bundle.getInt(OTPConstants.SELECTED_ITINERARY)
        if (i < 0 || i >= itineraries.size) {
            return null
        }
        return itineraries[i]
    }

    private fun getItineraryDescription(bundle: Bundle): ItineraryDescription? {
        val ids = bundle.getStringArray(ITINERARY_DESC) ?: return null
        val date = bundle.getLong(ITINERARY_END_DATE)
        return ItineraryDescription(ids.toList(), Instant.ofEpochMilli(date))
    }

    private fun getNotificationTarget(bundle: Bundle): Class<*>? {
        // Class.forName(null) throws NPE; return null so the caller takes its no-target path.
        val name = bundle.getString(OTPConstants.NOTIFICATION_TARGET) ?: return null
        return try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "unable to find class for name $name")
            null
        }
    }

    fun getSimplifiedBundle(params: Bundle): Bundle? {
        val itinerary = getItinerary(params)
        if (itinerary == null) {
            Log.e(
                TAG, "getSimplifiedBundle: itinerary is null, bundle may be incomplete. " +
                    "Bundle keys: " + params.keySet()
            )
            return null
        }

        val desc: ItineraryDescription = try {
            ItineraryDescription(itinerary)
        } catch (e: NullPointerException) {
            Log.e(TAG, "getSimplifiedBundle: error creating ItineraryDescription", e)
            return null
        } catch (e: IndexOutOfBoundsException) {
            Log.e(TAG, "getSimplifiedBundle: error creating ItineraryDescription", e)
            return null
        }

        val extras = Bundle()
        try {
            TripRequestBuilder(this, params).copyIntoBundleSimple(extras)
        } catch (e: NullPointerException) {
            Log.e(TAG, "getSimplifiedBundle: error copying trip params into bundle", e)
            return null
        }

        val idList = desc.tripIds
        if (idList.isEmpty() || desc.endDate == null) {
            Log.e(
                TAG, "getSimplifiedBundle: itinerary description is incomplete, " +
                    "not scheduling realtime updates."
            )
            return null
        }

        val ids = idList.toTypedArray()
        extras.putStringArray(ITINERARY_DESC, ids)
        extras.putLong(ITINERARY_END_DATE, desc.endDate!!.toEpochMilli())

        val source = params.getSerializable(OTPConstants.NOTIFICATION_TARGET) as? Class<*>
        if (source == null) {
            Log.e(
                TAG, "getSimplifiedBundle: NOTIFICATION_TARGET is missing from params, " +
                    "not scheduling realtime updates."
            )
            return null
        }

        extras.putString(OTPConstants.NOTIFICATION_TARGET, source.name)

        return extras
    }

    companion object {

        private const val TAG = "RealtimeService"

        private const val ITINERARY_DESC = ".ItineraryDesc"
        private const val ITINERARY_END_DATE = ".ItineraryEndDate"

        /**
         * Start realtime updates.
         *
         * @param source Activity from which updates are started
         * @param bundle Bundle with selected itinerary/parameters
         */
        @JvmStatic
        fun start(source: Activity, bundle: Bundle) {
            if (!PreferenceUtils.getBoolean(OTPConstants.PREFERENCE_KEY_LIVE_UPDATES, true)) {
                return
            }

            bundle.putSerializable(OTPConstants.NOTIFICATION_TARGET, source.javaClass)
            val intent = Intent(OTPConstants.INTENT_START_CHECKS)
            intent.putExtras(bundle)
            source.sendBroadcast(intent)
        }
    }
}
