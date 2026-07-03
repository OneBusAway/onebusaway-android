/*
 * Copyright (C) 2005-2019 University of South Florida
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
package org.onebusaway.android.nav

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.onebusaway.android.R
import org.onebusaway.android.analytics.AnalyticsProvider
import org.onebusaway.android.app.Application
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.database.oba.ImportGate
import org.onebusaway.android.database.oba.NavStopDao
import org.onebusaway.android.database.oba.NavStopRecord
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopLocationRow
import org.onebusaway.android.nav.model.Path
import org.onebusaway.android.nav.model.PathLink
import org.onebusaway.android.ui.feedback.FeedbackLauncher
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.PreferenceUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Implements the "destination reminders" feature in the app that notifies the user as they
 * are approaching their destination stop on-board the transit vehicle.
 *
 * The NavigationService is started when the user begins a trip; it collects location fixes from the
 * shared [org.onebusaway.android.location.LocationRepository] feed (1 s cadence) and passes each one
 * to its [NavigationServiceProvider], which computes trip statuses and issues notifications/TTS. Once
 * the NavigationServiceProvider reports finished, the service stops itself.
 */
@AndroidEntryPoint
class NavigationService : Service() {

    @Inject lateinit var navStopDao: NavStopDao
    @Inject lateinit var stopDao: StopDao
    @Inject lateinit var importGate: ImportGate
    @Inject lateinit var analyticsProvider: AnalyticsProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var navJob: Job? = null

    private var lastLocation: Location? = null

    private var destinationStopId: String? = null   // Destination Stop ID
    private var beforeStopId: String? = null         // Before Destination Stop ID
    private var tripId: String? = null               // Trip ID

    private var coordId = 0

    private var navProvider: NavigationServiceProvider? = null
    private var logFile: File? = null

    private var finishedTime: Long = 0

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting Service")
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        val currentTime = System.currentTimeMillis()
        // The nav-stop read/write is now Room-backed (suspend), so the setup runs on the service scope
        // after the one-time import gate. Dispatchers.Main.immediate keeps startForeground on the main
        // thread; only the fast Room reads suspend, so it is still called promptly.
        serviceScope.launch {
            importGate.awaitReady()
            if (intent != null) {
                destinationStopId = intent.getStringExtra(DESTINATION_ID)
                beforeStopId = intent.getStringExtra(BEFORE_STOP_ID)
                tripId = intent.getStringExtra(TRIP_ID)

                navStopDao.replaceActive(
                    NavStopRecord(
                        navId = "1",
                        startTime = currentTime,
                        tripId = tripId.orEmpty(),
                        destinationId = destinationStopId.orEmpty(),
                        beforeId = beforeStopId.orEmpty(),
                        sequence = 1,
                        active = 1,
                    )
                )

                navProvider = NavigationServiceProvider(tripId, destinationStopId)
            } else {
                val args = navStopDao.getDetails("1")
                if (args != null) {
                    tripId = args.tripId
                    destinationStopId = args.destinationId
                    beforeStopId = args.beforeId
                    navProvider = NavigationServiceProvider(tripId, destinationStopId, 1)
                }
            }

            // No intent and no persisted nav to resume (a system restart of the sticky service after
            // the trip ended): there's nothing to navigate, so stop cleanly instead of hitting the
            // navProvider!! below with an NPE.
            if (navProvider == null) {
                Log.w(TAG, "No navigation data to resume; stopping service")
                stopSelf()
                return@launch
            }

            // Log in anonymously via Firebase
            initAnonFirebaseLogin()

            val dest = destinationStopId?.let { stopLocation(stopDao.location(it)) }
            val last = beforeStopId?.let { stopLocation(stopDao.location(it)) }

            // Setup file for logging.
            if (logFile == null) {
                setupLog(dest, last)
            }

            val pathLink = PathLink(currentTime, null, last, dest, tripId)

            navProvider?.let {
                // TODO Support more than one path link
                val links = ArrayList<PathLink>(1)
                links.add(pathLink)
                it.navigate(Path(links))
            }

            // Collect the shared location feed (1 s cadence) instead of owning a private LocationHelper.
            // Start it AFTER navigate() initializes the proximity calculator: the repository's StateFlow
            // replays its seeded value immediately on collect, so handleLocation() -> locationUpdated()
            // must not run before the provider is set up (the legacy LocationHelper delivered its first fix
            // asynchronously, after navigate()).
            if (navJob?.isActive != true) {
                Log.d(TAG, "Requesting Location Updates")
                navJob = serviceScope.launch {
                    LocationEntryPoint.get(this@NavigationService)
                        .locationUpdates(NAV_UPDATE_INTERVAL_SECONDS)
                        .collect { handleLocation(it) }
                }
            }

            val notification = navProvider!!.getForegroundStartingNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NavigationServiceProvider.NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NavigationServiceProvider.NOTIFICATION_ID, notification)
            }
        }
        return Service.START_STICKY
    }

    /** Converts a stop's stored coordinates to a [Location] (the legacy Stops.getLocation shape). */
    private fun stopLocation(row: StopLocationRow?): Location? = row?.let {
        Location(LocationManager.GPS_PROVIDER).apply {
            latitude = it.latitude
            longitude = it.longitude
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onUnbind(intent: Intent?): Boolean = false

    override fun onRebind(intent: Intent?) {}

    override fun onDestroy() {
        Log.d(TAG, "Destroying Service.")
        serviceScope.cancel() // cancels the feed collection -> releases the shared-feed demand
        super.onDestroy()

        // Send Broadcast
        broadcastServiceDestroyed()
    }

    /** Sends broadcast so that flag of destination alert is removed from trip detail screen. */
    private fun broadcastServiceDestroyed() {
        // Scope the broadcast to this app: the receiver is app-internal + non-exported, and an implicit
        // (package-less) broadcast to a non-exported receiver is rejected on modern Android.
        sendBroadcast(Intent(TripDetailsLauncher.ACTION_SERVICE_DESTROYED).setPackage(packageName))
    }

    private fun handleLocation(location: Location) {
        Log.d(TAG, "Location Updated")
        val provider = navProvider ?: return
        val last = lastLocation
        if (last == null) {
            provider.locationUpdated(location)
        } else if (!LocationUtils.isDuplicate(last, location)) {
            provider.locationUpdated(location)
        }

        if (provider.mSectoCurDistance <= RECORDING_THRESHOLD) {
            writeToLog(location)
        }
        lastLocation = location

        // Is trip is finished? If so end service.
        if (provider.getFinished()) {
            if (finishedTime == 0L) {
                finishedTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - finishedTime >= 30000) {
                ObaAnalytics.reportUiEvent(
                    firebaseAnalytics, analyticsProvider.plausible,
                    PlausibleAnalytics.REPORT_DESTINATION_REMINDER_EVENT_URL,
                    getString(R.string.analytics_label_destination_reminder),
                    getString(R.string.analytics_label_destination_reminder_variant_ended)
                )
                getUserFeedback()
                stopSelf()
                setupLogCleanupTask()
            }
        }
    }

    private fun initAnonFirebaseLogin() {
        val auth = FirebaseAuth.getInstance()
        val numCores = Runtime.getRuntime().availableProcessors()
        val executor = ThreadPoolExecutor(
            numCores * 2, numCores * 2,
            60L, TimeUnit.SECONDS, LinkedBlockingQueue()
        )
        auth.signInAnonymously()
            .addOnCompleteListener(executor) { task ->
                if (task.isSuccessful) {
                    // Sign in success
                    Log.d(TAG, "signInAnonymously:success")
                } else {
                    // Sign in failed
                    Log.w(TAG, "signInAnonymously:failure", task.exception)
                }
            }
    }

    /**
     * Creates the log file that GPS data and navigation performance is written to - see
     * DESTINATION_ALERTS.md
     */
    private fun setupLog(dest: Location?, last: Location?) {
        try {
            // Get the counter that's incremented for each test
            val navTestId = getString(R.string.preference_key_nav_test_id)
            var counter = PreferenceUtils.getInt(navTestId, 0)
            counter++
            PreferenceUtils.saveInt(navTestId, counter)

            val sdf = SimpleDateFormat("EEE, MMM d yyyy, hh:mm aaa", Locale.US)
            val readableDate = sdf.format(Calendar.getInstance().time)

            val subFolder = File(
                Application.get().applicationContext
                    .filesDir.absolutePath + File.separator + LOG_DIRECTORY
            )

            if (!subFolder.exists()) {
                subFolder.mkdirs()
            }

            val file = File(subFolder, "$counter-$readableDate.csv")
            logFile = file

            Log.d(TAG, ":" + file.absolutePath)

            val header = String.format(
                Locale.US, "%s,%s,%f,%f,%s,%f,%f\n", tripId, destinationStopId,
                dest?.latitude ?: 0.0, dest?.longitude ?: 0.0, beforeStopId,
                last?.latitude ?: 0.0, last?.longitude ?: 0.0
            )

            FileUtils.write(file, header, false)
        } catch (e: IOException) {
            Log.e(TAG, "File write failed: $e")
        }
    }

    private fun writeToLog(l: Location) {
        try {
            val nanoTime = l.elapsedRealtimeNanos.toString()

            var satellites = 0
            val extras = l.extras
            if (extras != null) {
                satellites = extras.getInt("satellites", 0)
            }

            val provider = navProvider!!
            val log = String.format(
                Locale.US, "%d,%s,%s,%s,%d,%f,%f,%f,%f,%f,%f,%d,%s\n",
                coordId, provider.getGetReady(), provider.getFinished(), nanoTime, l.time,
                l.latitude, l.longitude, l.altitude, l.speed,
                l.bearing, l.accuracy, satellites, l.provider
            )

            // Increments the id for each coordinate
            coordId++

            val file = logFile
            if (file != null && file.canWrite()) {
                FileUtils.write(file, log, true)
            } else {
                Log.e(TAG, "Failed to write to file")
            }
        } catch (e: IOException) {
            Log.e(TAG, "File write failed: $e")
        }
    }

    fun getUserFeedback() {
        val app = Application.get()
        val builder: NotificationCompat.Builder

        val message = Application.get().getString(R.string.feedback_notify_dialog_msg)
        mFirstFeedback = PreferenceUtils.getBoolean(FIRST_FEEDBACK, true)

        // Create delete intent to set flag for snackbar creation next time the app is opened.
        val delIntent = Intent(app.applicationContext, FeedbackReceiver::class.java)
        delIntent.putExtra(FeedbackReceiver.NOTIFICATION_ID, NavigationServiceProvider.NOTIFICATION_ID + 1)

        if (mFirstFeedback || Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            // Feedback is a HomeActivity NavHost destination; makeIntent builds the
            // explicit HomeActivity intent carrying the feedback route (with these args as nav-args).
            var fdIntent = FeedbackLauncher.makeIntent(
                app.applicationContext, FeedbackLauncher.FEEDBACK_NO, logFile!!.absolutePath, tripId,
                NavigationServiceProvider.NOTIFICATION_ID + 1
            )
            fdIntent.action = FeedbackReceiver.ACTION_REPLY

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // Pending intent used to handle feedback when user taps on 'No'
            val fdPendingIntentNo = PendingIntent.getActivity(app.applicationContext, 1, fdIntent, flags)

            fdIntent = FeedbackLauncher.makeIntent(
                app.applicationContext, FeedbackLauncher.FEEDBACK_YES, logFile!!.absolutePath, tripId,
                NavigationServiceProvider.NOTIFICATION_ID + 1
            )
            fdIntent.action = FeedbackReceiver.ACTION_REPLY

            // Pending intent used to handle feedback when user taps on 'Yes'
            val fdPendingIntentYes = PendingIntent.getActivity(app.applicationContext, 2, fdIntent, flags)

            delIntent.action = FeedbackReceiver.ACTION_DISMISS_FEEDBACK
            val delFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pDelIntent = PendingIntent.getBroadcast(app.applicationContext, 0, delIntent, delFlags)

            builder = NotificationCompat.Builder(
                Application.get().applicationContext, Application.CHANNEL_DESTINATION_ALERT_ID
            )
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(Application.get().resources.getString(R.string.feedback_notify_title))
                .setContentText(message)
                .addAction(
                    0, Application.get().resources.getString(R.string.feedback_action_reply_no),
                    fdPendingIntentNo
                )
                .addAction(
                    0, Application.get().resources.getString(R.string.feedback_action_reply_yes),
                    fdPendingIntentYes
                )
                .setDeleteIntent(pDelIntent)
                .setAutoCancel(true)
        } else {
            // Intent to handle user feedback when a user taps on 'No'
            val intentNo = Intent(Application.get().applicationContext, FeedbackReceiver::class.java)
            intentNo.action = FeedbackReceiver.ACTION_REPLY
            intentNo.putExtra(FeedbackReceiver.NOTIFICATION_ID, NavigationServiceProvider.NOTIFICATION_ID + 1)
            intentNo.putExtra(FeedbackReceiver.TRIP_ID, tripId)
            intentNo.putExtra(FeedbackReceiver.RESPONSE, FeedbackReceiver.FEEDBACK_NO)
            intentNo.putExtra(FeedbackReceiver.LOG_FILE, logFile!!.absolutePath)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

            // PendingIntent to handle user feedback when a user taps on 'No'
            val fdPendingIntentNo = PendingIntent.getBroadcast(
                Application.get().applicationContext, 100, intentNo, flags
            )

            val replyLabelNo = Application.get().resources.getString(R.string.feedback_action_reply_no)

            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabelNo).build()

            val replyActionNo = NotificationCompat.Action.Builder(0, replyLabelNo, fdPendingIntentNo)
                .addRemoteInput(remoteInput)
                .build()

            // Intent to handle user feedback when a user taps on 'Yes'
            val intentYes = Intent(Application.get().applicationContext, FeedbackReceiver::class.java)
            intentYes.action = FeedbackReceiver.ACTION_REPLY
            intentYes.putExtra(FeedbackReceiver.NOTIFICATION_ID, NavigationServiceProvider.NOTIFICATION_ID + 1)
            intentYes.putExtra(FeedbackReceiver.TRIP_ID, tripId)
            intentYes.putExtra(FeedbackReceiver.RESPONSE, FeedbackReceiver.FEEDBACK_YES)
            intentYes.putExtra(FeedbackReceiver.LOG_FILE, logFile!!.absolutePath)

            // PendingIntent to handle user feedback when a user taps on 'No'
            val fdPendingIntentYes = PendingIntent.getBroadcast(
                Application.get().applicationContext, 101, intentYes, flags
            )

            val replyLabelYes = Application.get().resources.getString(R.string.feedback_action_reply_yes)

            val remoteInput1 = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabelYes).build()

            val replyActionYes = NotificationCompat.Action.Builder(0, replyLabelYes, fdPendingIntentYes)
                .addRemoteInput(remoteInput1)
                .build()

            delIntent.action = FeedbackReceiver.ACTION_DISMISS_FEEDBACK
            val pDelIntent = PendingIntent.getBroadcast(app.applicationContext, 0, delIntent, flags)

            builder = NotificationCompat.Builder(
                Application.get().applicationContext, Application.CHANNEL_DESTINATION_ALERT_ID
            )
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(Application.get().resources.getString(R.string.feedback_notify_title))
                .setContentText(message)
                .addAction(replyActionNo)
                .addAction(replyActionYes)
                .setDeleteIntent(pDelIntent)
                .setAutoCancel(true)
        }

        builder.setOngoing(false)

        val notificationManager =
            Application.get().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NavigationServiceProvider.NOTIFICATION_ID + 1, builder.build())
    }

    private fun setupLogCleanupTask() {
        val cleanupLogsBuilder = PeriodicWorkRequest.Builder(
            NavigationCleanupWorker::class.java, 24, TimeUnit.HOURS
        )

        // Create the actual work object:
        val cleanUpCheckWork = cleanupLogsBuilder.build()

        // Then enqueue the recurring task:
        WorkManager.getInstance().enqueue(cleanUpCheckWork)
    }

    companion object {
        const val TAG = "NavigationService"

        const val DESTINATION_ID = ".DestinationId"
        const val BEFORE_STOP_ID = ".BeforeId"
        const val TRIP_ID = ".TripId"
        const val FIRST_FEEDBACK = "firstFeedback"
        const val KEY_TEXT_REPLY = "trip_feedback"

        const val LOG_DIRECTORY = "ObaNavLog"

        @JvmField
        var mFirstFeedback = true

        /** Nav-feed cadence (seconds) requested from the shared LocationRepository feed. */
        private const val NAV_UPDATE_INTERVAL_SECONDS = 1

        private val RECORDING_THRESHOLD = NavigationServiceProvider.DISTANCE_THRESHOLD + 100
    }
}
