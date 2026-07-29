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
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import org.onebusaway.android.R
import org.onebusaway.android.notifications.NotificationChannels
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.feedback.FeedbackLauncher
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.RegionUtils

/**
 * Request codes for every reminder [PendingIntent]. The system matches a pending intent by
 * [Intent.filterEquals], which ignores extras — so the arrival alert's plain `HomeActivity` intent
 * and the feedback prompt's `HomeActivity` actions are indistinguishable to it, and `FLAG_UPDATE_CURRENT`
 * would rewrite whichever was registered first. The request code is the only discriminator, so every
 * pending intent in this file gets its own.
 */
private object ReminderRequestCodes {
    const val FEEDBACK_NO = 1
    const val FEEDBACK_YES = 2
    const val SILENCE = 10
    const val CANCEL = 11
    const val OPEN_APP = 12
}

internal interface ReminderNotificationPresenter {
    fun foregroundNotification(plan: ReminderPlan? = null): Notification
    fun present(effect: ReminderEffect)
    fun cancel()
}

@Singleton
internal class AndroidReminderNotificationPresenter @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderNotificationPresenter {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun foregroundNotification(plan: ReminderPlan?): Notification = builder()
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

    override fun present(effect: ReminderEffect) {
        when (effect) {
            is ReminderEffect.RideCompleted -> return
            // The ongoing progress notification: quiet by construction, re-posted on every fix.
            is ReminderEffect.Progress -> manager.notify(
                NavigationService.NOTIFICATION_ID,
                builder()
                    .setContentText(distanceText(effect.alightDistanceMeters))
                    .setOngoing(true)
                    .build()
            )
            // The one-shot "act now" alerts. These get their own ids and the high-importance
            // channel so they actually surface (#985); posting them into the progress
            // notification would both silence them and let the next fix overwrite the text.
            is ReminderEffect.GetReady -> manager.notify(
                GET_READY_NOTIFICATION_ID,
                arrivalAlert(alertText(effect.stop, effect.isTransfer, requestStop = false))
            )
            is ReminderEffect.AlightNow -> {
                manager.cancel(GET_READY_NOTIFICATION_ID)
                manager.notify(
                    ALIGHT_NOW_NOTIFICATION_ID,
                    arrivalAlert(alertText(effect.stop, effect.isTransfer, effect.usesRequestStopWording))
                )
            }
            ReminderEffect.SessionCompleted -> manager.notify(
                NavigationService.NOTIFICATION_ID,
                builder()
                    .setContentText(context.getString(R.string.destination_reminder_arrived))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    override fun cancel() {
        manager.cancel(NavigationService.NOTIFICATION_ID)
        manager.cancel(GET_READY_NOTIFICATION_ID)
        manager.cancel(ALIGHT_NOW_NOTIFICATION_ID)
    }

    /**
     * Builds one of the one-shot arrival alerts on the high-importance
     * [NotificationChannels.DESTINATION_ARRIVAL_ID] channel. `setPriority`/`setDefaults`/
     * `setVibrate` are the pre-26 equivalents of the channel's importance and vibration —
     * ignored on 26+, still required on the API 23-25 floor.
     */
    private fun arrivalAlert(text: String): Notification = NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ARRIVAL_ID)
        .setSmallIcon(R.drawable.ic_content_flag)
        .setContentTitle(context.getString(R.string.destination_reminder_title))
        .setContentText(text)
        .setContentIntent(contentIntent())
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_SOUND)
        .setVibrate(NotificationChannels.DESTINATION_VIBRATION_PATTERN)
        .setAutoCancel(true)
        .build()

    /**
     * Formats the remaining distance in the rider's preferred units, matching the region picker's
     * near/far split: feet below a tenth of a mile and meters below a kilometer, else miles/km.
     */
    private fun distanceText(distanceMeters: Double): String {
        val meters = distanceMeters.coerceAtLeast(0.0)
        val format = NumberFormat.getInstance().apply { maximumFractionDigits = 1 }
        if (PreferenceUtils.getUnitsAreMetricFromPreferences(context)) {
            val kilometers = meters / 1000.0
            return if (kilometers < 1) {
                // Round to 50 m so the text does not churn on every fix.
                val rounded = (meters / 50.0).roundToInt() * 50
                context.resources.getQuantityString(R.plurals.distance_meters, rounded, rounded)
            } else {
                context.resources.getQuantityString(
                    R.plurals.distance_kilometers,
                    kilometers.toInt(),
                    format.format(kilometers)
                )
            }
        }
        val miles = meters * RegionUtils.METERS_TO_MILES
        return if (miles < 0.1) {
            val rounded = (meters * RegionUtils.METERS_TO_FEET / 50.0).roundToInt() * 50
            context.resources.getQuantityString(R.plurals.distance_feet, rounded, rounded)
        } else {
            context.resources.getQuantityString(
                R.plurals.distance_miles,
                miles.toInt(),
                format.format(miles)
            )
        }
    }

    /** Opens the app; the reminder notifications carry no per-plan destination of their own. */
    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            ReminderRequestCodes.OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun builder(): NotificationCompat.Builder = NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ALERT_ID)
        .setSmallIcon(R.drawable.ic_content_flag)
        .setContentTitle(context.getString(R.string.destination_reminder_title))
        .setContentIntent(contentIntent())
        .addAction(
            commandAction(
                NavigationReceiver.ACTION_SILENCE,
                R.string.destination_reminder_silence,
                ReminderRequestCodes.SILENCE
            )
        )
        .addAction(
            commandAction(
                NavigationReceiver.ACTION_CANCEL,
                R.string.destination_reminder_cancel_trip,
                ReminderRequestCodes.CANCEL
            )
        )

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

    private companion object {
        // Distinct from the ongoing progress notification so an alert is never overwritten by the
        // next distance update, and so both can be cancelled independently.
        const val GET_READY_NOTIFICATION_ID = NavigationService.NOTIFICATION_ID + 1
        const val ALIGHT_NOW_NOTIFICATION_ID = NavigationService.NOTIFICATION_ID + 2
    }
}

internal interface ReminderSpeechController {
    fun speak(effect: ReminderEffect)
    fun silence()
    fun close()
}

/**
 * Speaks reminder alerts, queueing rather than replacing so an alert emitted in the same transition
 * as another (the engine's sparse-fix recovery emits "get ready" and "exit now" together) is not
 * truncated, and draining the queue before shutting the engine down so the final "arriving at your
 * destination" is actually heard.
 */
internal class AndroidReminderSpeechController @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReminderSpeechController,
    TextToSpeech.OnInitListener {
    private val lock = Any()
    private val pending = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var outstanding = 0
    private var closeWhenIdle = false
    private var utteranceCounter = 0L

    override fun onInit(status: Int) {
        synchronized(lock) {
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                pending.clear()
                outstanding = 0
                shutdownLocked()
                return
            }
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(SPEECH_RATE)
            tts?.setOnUtteranceProgressListener(progressListener)
        }
        drainPending()
    }

    override fun speak(effect: ReminderEffect) {
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
        // Everything goes through the queue, so text is never held anywhere the shutdown check
        // cannot see it.
        val startEngine = synchronized(lock) {
            if (closeWhenIdle) return
            pending += text
            tts == null
        }
        if (startEngine) {
            // Constructing TextToSpeech triggers onInit, which drains `pending`.
            val engine = TextToSpeech(context, this)
            synchronized(lock) { tts = engine }
        } else {
            drainPending()
        }
    }

    override fun silence() {
        synchronized(lock) {
            pending.clear()
            outstanding = 0
            tts?.stop()
        }
    }

    /**
     * Stops accepting new speech and shuts the engine down once whatever is already speaking
     * finishes. [SHUTDOWN_GRACE_MS] bounds the wait so a TTS engine that never reports completion
     * cannot leak the instance.
     */
    override fun close() {
        val shutdownNow = synchronized(lock) {
            closeWhenIdle = true
            // Queued text has not reached the engine yet, so it is outstanding work too: on a cold
            // engine (a sticky restart, or "get ready" and "exit now" emitted together) the arrival
            // announcement is still in `pending` when the session completes, and clearing it here is
            // what used to swallow it.
            outstanding == 0 && pending.isEmpty()
        }
        if (shutdownNow) {
            synchronized(lock) { shutdownLocked() }
        } else {
            handler.postDelayed(shutdownRunnable, SHUTDOWN_GRACE_MS)
        }
    }

    /**
     * Hands queued text to the engine, doing nothing while it is still initializing (onInit calls
     * this again once it is ready). Each utterance is counted as outstanding in the same locked step
     * that removes it from [pending], so a shutdown check racing the drain never sees it in neither
     * place and tears the engine down mid-queue.
     */
    private fun drainPending() {
        while (true) {
            val (text, id) = synchronized(lock) {
                if (tts == null || !ready) return
                val next = pending.removeFirstOrNull() ?: return
                outstanding++
                next to "destination-reminder-${utteranceCounter++}"
            }
            // QUEUE_ADD, not QUEUE_FLUSH: a second alert in the same transition must follow the first
            // rather than cut it off.
            val result = synchronized(lock) { tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id) }
            if (result != TextToSpeech.SUCCESS) onUtteranceSettled()
        }
    }

    private fun onUtteranceSettled() {
        val shutdownNow = synchronized(lock) {
            if (outstanding > 0) outstanding--
            closeWhenIdle && outstanding == 0 && pending.isEmpty()
        }
        if (shutdownNow) {
            handler.removeCallbacks(shutdownRunnable)
            synchronized(lock) { shutdownLocked() }
        }
    }

    private fun shutdownLocked() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private val shutdownRunnable = Runnable {
        synchronized(lock) {
            pending.clear()
            outstanding = 0
            shutdownLocked()
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = onUtteranceSettled()

        @Deprecated("Required override; the int-code overload is called on API 21+")
        override fun onError(utteranceId: String?) = onUtteranceSettled()

        override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceSettled()

        override fun onStop(utteranceId: String?, interrupted: Boolean) = onUtteranceSettled()
    }

    private companion object {
        const val SPEECH_RATE = 0.75f

        /** Upper bound on how long a shutdown waits for in-flight speech to drain. */
        const val SHUTDOWN_GRACE_MS = 10_000L
    }
}

internal interface NavigationFeedbackRepository {
    fun requestFeedback()

    /**
     * Dismisses the post-trip prompt once the rider has answered it. The prompt has no content
     * intent, only actions, so `setAutoCancel` never fires and the notification has to be taken
     * down explicitly.
     */
    fun dismissFeedbackPrompt()
}

@Singleton
internal class AndroidNavigationFeedbackRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NavigationFeedbackRepository {
    override fun requestFeedback() {
        val no = PendingIntent.getActivity(
            context,
            ReminderRequestCodes.FEEDBACK_NO,
            FeedbackLauncher.makeIntent(context, FeedbackLauncher.FEEDBACK_NO),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val yes = PendingIntent.getActivity(
            context,
            ReminderRequestCodes.FEEDBACK_YES,
            FeedbackLauncher.makeIntent(context, FeedbackLauncher.FEEDBACK_YES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.DESTINATION_ALERT_ID)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(context.getString(R.string.feedback_notify_title))
            .setContentText(context.getString(R.string.feedback_notify_dialog_msg))
            .addAction(0, context.getString(R.string.feedback_action_reply_no), no)
            .addAction(0, context.getString(R.string.feedback_action_reply_yes), yes)
            .build()
        manager.notify(FEEDBACK_NOTIFICATION_ID, notification)
    }

    override fun dismissFeedbackPrompt() = manager.cancel(FEEDBACK_NOTIFICATION_ID)

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        // Distinct from both arrival alerts: the next ride's "get ready" must not replace a prompt
        // the rider has yet to answer, and the reminder presenter must not cancel a prompt it does
        // not own.
        const val FEEDBACK_NOTIFICATION_ID = NavigationService.NOTIFICATION_ID + 3
    }
}
