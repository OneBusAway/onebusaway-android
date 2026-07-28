/*
 * Copyright (C) 2019-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.util.PreferenceUtils

class FeedbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NOTIFICATION_ID, NavigationService.NOTIFICATION_ID + 1)
        when (intent.action) {
            ACTION_DISMISS_FEEDBACK -> Log.d(TAG, "Feedback notification dismissed")
            ACTION_REPLY -> captureFeedback(context, intent, notificationId)
        }
    }

    private fun captureFeedback(context: Context, intent: Intent, notificationId: Int) {
        val feedback = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NavigationService.KEY_TEXT_REPLY)
            ?.toString()
            ?: return
        val response = intent.getIntExtra(RESPONSE, 0)
        val responseLabel = context.getString(
            if (response == FEEDBACK_YES) {
                R.string.analytics_label_destination_reminder_yes
            } else {
                R.string.analytics_label_destination_reminder_no
            }
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ALERT_ID)
                .setSmallIcon(R.drawable.ic_bus)
                .setContentTitle(context.getString(R.string.feedback_notify_title))
                .setContentText(context.getString(R.string.feedback_notify_confirmation))
                .setOngoing(false)
                .build()
        )
        manager.cancel(notificationId)

        val logPaths = intent.getStringArrayListExtra(LOG_FILES)
            ?.filter(String::isNotBlank)
            ?.ifEmpty { null }
            ?: listOfNotNull(intent.getStringExtra(LOG_FILE))
        val share = PreferenceUtils.getBoolean(
            context.getString(R.string.preferences_key_user_share_destination_logs),
            true
        )
        if (share && logPaths.isNotEmpty()) {
            moveLogs(context, feedback, responseLabel, logPaths)
        } else {
            logPaths.forEach { File(it).delete() }
            AnalyticsEntryPoint.get(context).reportDestinationReminderFeedback(
                response == FEEDBACK_YES,
                feedback.ifEmpty { null },
                null
            )
        }
    }

    private fun moveLogs(context: Context, feedback: String, response: String, paths: List<String>) {
        try {
            val destinationDirectory = File(
                File(context.filesDir, NavigationService.LOG_DIRECTORY),
                response
            ).apply { mkdirs() }
            paths.forEach { path ->
                val source = File(path)
                source.appendText(System.lineSeparator() + "User Feedback - " + feedback)
                val destination = File(destinationDirectory, source.name)
                if (!source.renameTo(destination)) {
                    throw IOException("Failed to move $source to $destination")
                }
            }
            val uploadWork = PeriodicWorkRequest.Builder(
                NavigationUploadWorker::class.java,
                24,
                TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NavigationUploadWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                uploadWork
            )
        } catch (error: IOException) {
            Log.e(TAG, "File write failed", error)
        }
    }

    companion object {
        const val TAG = "FeedbackReceiver"
        const val ACTION_REPLY = BuildConfig.APPLICATION_ID + ".action.REPLY"
        const val ACTION_DISMISS_FEEDBACK = BuildConfig.APPLICATION_ID + ".action.DISMISS_FEEDBACK"
        const val TRIP_ID = ".TRIP_ID"
        const val NOTIFICATION_ID = ".NOTIFICATION_ID"
        const val RESPONSE = ".RESPONSE"
        const val LOG_FILE = ".LOG_FILE"
        const val LOG_FILES = ".LOG_FILES"
        const val FEEDBACK_NO = 1
        const val FEEDBACK_YES = 2
    }
}
