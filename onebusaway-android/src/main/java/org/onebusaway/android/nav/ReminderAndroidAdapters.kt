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
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.feedback.FeedbackLauncher

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
            plan?.let {
                context.resources.getQuantityString(
                    R.plurals.destination_reminder_monitoring_rides,
                    it.rides.size,
                    it.rides.size
                )
            }
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

internal class AndroidReminderSpeechController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderSpeechController,
    TextToSpeech.OnInitListener {
    @Volatile private var tts: TextToSpeech? = null

    @Volatile private var ready = false

    @Volatile private var pendingText: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(0.75f)
            pendingText?.let(::speakText)
            pendingText = null
        } else {
            tts?.shutdown()
            tts = null
        }
    }

    override fun speak(plan: ReminderPlan, effect: ReminderEffect) {
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
        val engine = tts
        if (engine == null) {
            pendingText = text
            tts = TextToSpeech(context, this)
        } else if (ready) {
            speakText(text)
        } else {
            pendingText = text
        }
    }

    override fun silence() {
        pendingText = null
        tts?.stop()
    }

    override fun close() {
        pendingText = null
        ready = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "destination-reminder")
    }
}

internal interface NavigationFeedbackRepository {
    fun requestFeedback()
}

@Singleton
internal class AndroidNavigationFeedbackRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NavigationFeedbackRepository {
    override fun requestFeedback() {
        val notificationId = NavigationService.NOTIFICATION_ID + 1
        val no = PendingIntent.getActivity(
            context,
            1,
            FeedbackLauncher.makeIntent(context, FeedbackLauncher.FEEDBACK_NO),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val yes = PendingIntent.getActivity(
            context,
            2,
            FeedbackLauncher.makeIntent(context, FeedbackLauncher.FEEDBACK_YES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ALERT_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(context.getString(R.string.feedback_notify_title))
            .setContentText(context.getString(R.string.feedback_notify_dialog_msg))
            .addAction(0, context.getString(R.string.feedback_action_reply_no), no)
            .addAction(0, context.getString(R.string.feedback_action_reply_yes), yes)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
