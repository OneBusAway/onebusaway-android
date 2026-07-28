/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.feedback.FeedbackLauncher
import org.onebusaway.android.util.PreferenceUtils

internal interface ReminderNotificationPresenter {
    fun foregroundNotification(plan: ReminderPlan? = null): Notification
    fun present(plan: ReminderPlan, effect: ReminderEffect)
    fun cancel()
}

@Singleton
internal class AndroidReminderNotificationPresenter @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderNotificationPresenter {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun foregroundNotification(plan: ReminderPlan?): Notification = builder(plan)
        .setContentText(
            plan?.let { context.getString(R.string.destination_reminder_monitoring_rides, it.rides.size) }
                ?: context.getString(R.string.destination_reminder_starting)
        )
        .setOngoing(true)
        .build()

    override fun present(plan: ReminderPlan, effect: ReminderEffect) {
        val text = when (effect) {
            is ReminderEffect.Progress -> context.getString(
                R.string.destination_reminder_distance,
                effect.alightDistanceMeters.toInt().coerceAtLeast(0)
            )
            is ReminderEffect.GetReady -> alertText(effect.stop, effect.isTransfer, requestStop = false)
            is ReminderEffect.AlightNow -> alertText(effect.stop, effect.isTransfer, effect.usesRequestStopWording)
            is ReminderEffect.RideCompleted -> return
            ReminderEffect.SessionCompleted -> context.getString(R.string.destination_reminder_arrived)
        }
        manager.notify(
            NavigationService.NOTIFICATION_ID,
            builder(plan).setContentText(text).setOngoing(effect !is ReminderEffect.SessionCompleted).build()
        )
    }

    override fun cancel() = manager.cancel(NavigationService.NOTIFICATION_ID)

    private fun builder(plan: ReminderPlan?): NotificationCompat.Builder {
        val contentIntent = plan?.rides?.getOrNull(0)?.let {
            val intent = Intent(context, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ALERT_ID)
            .setSmallIcon(R.drawable.ic_content_flag)
            .setContentTitle(context.getString(R.string.destination_reminder_title))
            .setContentIntent(contentIntent)
            .addAction(commandAction(NavigationReceiver.ACTION_SILENCE, R.string.destination_reminder_silence, 10))
            .addAction(commandAction(NavigationReceiver.ACTION_CANCEL, R.string.destination_reminder_cancel_trip, 11))
    }

    private fun commandAction(action: String, label: Int, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(context, NavigationReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pending).build()
    }

    private fun alertText(stop: ReminderStop, transfer: Boolean, requestStop: Boolean): String = when {
        transfer -> context.getString(R.string.destination_reminder_transfer_at, stop.name)
        requestStop -> context.getString(R.string.destination_reminder_request_stop_for, stop.name)
        else -> context.getString(R.string.destination_reminder_prepare_to_exit, stop.name)
    }
}

internal interface ReminderSpeechController {
    fun speak(plan: ReminderPlan, effect: ReminderEffect)
    fun silence()
    fun close()
}

@Singleton
internal class AndroidReminderSpeechController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderSpeechController,
    TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            tts.setSpeechRate(0.75f)
        }
    }

    override fun speak(plan: ReminderPlan, effect: ReminderEffect) {
        if (!ready) return
        val text = when (effect) {
            is ReminderEffect.GetReady -> context.getString(R.string.destination_voice_get_ready_for, effect.stop.name)
            is ReminderEffect.AlightNow -> when {
                effect.isTransfer -> context.getString(R.string.destination_voice_transfer_at, effect.stop.name)
                effect.usesRequestStopWording -> context.getString(R.string.destination_voice_request_stop_for, effect.stop.name)
                else -> context.getString(R.string.destination_voice_prepare_to_exit, effect.stop.name)
            }
            ReminderEffect.SessionCompleted -> context.getString(R.string.destination_voice_arriving_destination)
            else -> return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "destination-reminder")
    }

    override fun silence() {
        tts.stop()
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }
}

internal interface NavigationLogRecorder {
    fun start(plan: ReminderPlan, state: ReminderEngineState, existingPath: String?): String?
    fun record(plan: ReminderPlan, state: ReminderEngineState, sample: ReminderLocationSample, distanceMeters: Double)
    fun currentPath(state: ReminderEngineState): String?
    fun completedFiles(): List<File>
    fun cancel()
}

@Singleton
internal class CsvNavigationLogRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NavigationLogRecorder {
    private val files = mutableMapOf<Int, File>()
    private var activeSessionId: String? = null
    private var coordinateId = 0

    override fun start(plan: ReminderPlan, state: ReminderEngineState, existingPath: String?): String? {
        if (activeSessionId == plan.sessionId) return currentPath(state)
        files.clear()
        coordinateId = 0
        activeSessionId = plan.sessionId
        existingPath?.let(::File)?.takeIf(File::exists)?.let { files[state.activeRideIndex] = it }
        val directory = File(context.filesDir, NavigationService.LOG_DIRECTORY).apply { mkdirs() }
        val counterKey = context.getString(R.string.preference_key_nav_test_id)
        var counter = PreferenceUtils.getInt(counterKey, 0)
        val readableDate = SimpleDateFormat("EEE, MMM d yyyy, hh:mm aaa", Locale.US)
            .format(Calendar.getInstance().time)
        plan.rides.forEachIndexed { index, ride ->
            if (index < state.activeRideIndex || files.containsKey(index)) return@forEachIndexed
            counter += 1
            val suffix = if (plan.rides.size == 1) "" else "-ride-${index + 1}"
            val file = File(directory, "$counter-$readableDate$suffix.csv")
            val header = String.format(
                Locale.US,
                "%s,%s,%f,%f,%s,%f,%f\n",
                ride.tripId,
                ride.alight.id,
                ride.alight.point.latitude,
                ride.alight.point.longitude,
                ride.penultimate.id,
                ride.penultimate.point.latitude,
                ride.penultimate.point.longitude
            )
            file.writeText(header)
            files[index] = file
        }
        PreferenceUtils.saveInt(counterKey, counter)
        return currentPath(state)
    }

    override fun record(
        plan: ReminderPlan,
        state: ReminderEngineState,
        sample: ReminderLocationSample,
        distanceMeters: Double
    ) {
        if (distanceMeters > RECORDING_THRESHOLD_METERS) return
        val file = files[state.activeRideIndex] ?: return
        val line = String.format(
            Locale.US,
            "%d,%s,%s,%d,%d,%f,%f,%f,%f,%f,%f,%d,%s\n",
            coordinateId++,
            state.getReadyEmitted,
            state.completed,
            0L,
            sample.timestampMs,
            sample.point.latitude,
            sample.point.longitude,
            0.0,
            sample.speedMetersPerSecond ?: 0f,
            0.0,
            sample.accuracyMeters,
            0,
            "gps"
        )
        file.appendText(line)
    }

    override fun currentPath(state: ReminderEngineState): String? = files[state.activeRideIndex]?.absolutePath

    override fun completedFiles(): List<File> = files.toSortedMap().values.toList()

    override fun cancel() {
        files.values.forEach(File::delete)
        files.clear()
        activeSessionId = null
    }

    private companion object {
        const val RECORDING_THRESHOLD_METERS = 1_600.0
    }
}

internal interface NavigationFeedbackRepository {
    fun requestFeedback(logFiles: List<File>, tripId: String)
}

@Singleton
internal class AndroidNavigationFeedbackRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NavigationFeedbackRepository {
    override fun requestFeedback(logFiles: List<File>, tripId: String) {
        val firstLog = logFiles.firstOrNull() ?: return
        val notificationId = NavigationService.NOTIFICATION_ID + 1
        val paths = ArrayList(logFiles.map(File::getAbsolutePath))
        val no = FeedbackLauncher.makeIntent(
            context,
            FeedbackLauncher.FEEDBACK_NO,
            firstLog.absolutePath,
            tripId,
            notificationId
        ).putStringArrayListExtra(FeedbackReceiver.LOG_FILES, paths)
        val yes = FeedbackLauncher.makeIntent(
            context,
            FeedbackLauncher.FEEDBACK_YES,
            firstLog.absolutePath,
            tripId,
            notificationId
        ).putStringArrayListExtra(FeedbackReceiver.LOG_FILES, paths)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val deleteIntent = Intent(context, FeedbackReceiver::class.java).apply {
            action = FeedbackReceiver.ACTION_DISMISS_FEEDBACK
            putExtra(FeedbackReceiver.NOTIFICATION_ID, notificationId)
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ALERT_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(context.getString(R.string.feedback_notify_title))
            .setContentText(context.getString(R.string.feedback_notify_dialog_msg))
            .addAction(0, context.getString(R.string.feedback_action_reply_no), PendingIntent.getActivity(context, 1, no, flags))
            .addAction(0, context.getString(R.string.feedback_action_reply_yes), PendingIntent.getActivity(context, 2, yes, flags))
            .setDeleteIntent(PendingIntentCompat.getBroadcast(context, 0, deleteIntent, 0, true))
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationId, notification)
    }
}
