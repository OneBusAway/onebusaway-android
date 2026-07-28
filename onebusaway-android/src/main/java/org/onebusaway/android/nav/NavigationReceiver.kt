/*
 * Copyright (C) 2005-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Handles explicit notification commands without reaching into static service or TTS state. */
@AndroidEntryPoint
class NavigationReceiver : BroadcastReceiver() {
    @Inject internal lateinit var speechController: ReminderSpeechController

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.action ?: when (intent.getIntExtra(ACTION_NUM, 0)) {
            DISMISS_NOTIFICATION -> ACTION_SILENCE
            CANCEL_TRIP -> ACTION_CANCEL
            else -> null
        }
        when (command) {
            ACTION_SILENCE -> speechController.silence()
            ACTION_CANCEL -> ContextCompat.startForegroundService(
                context,
                Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_CANCEL)
            )
        }
    }

    companion object {
        const val TAG = "NavigationReceiver"
        const val NAV_ID = ".NAV_ID"
        const val ACTION_NUM = ".ACTION_NUM"
        const val NOTIFICATION_ID = ".NOTIFICATION_ID"
        const val DISMISS_NOTIFICATION = 1
        const val CANCEL_TRIP = 2
        const val ACTION_SILENCE = NavigationService.ACTION_SILENCE
        const val ACTION_CANCEL = NavigationService.ACTION_CANCEL
    }
}
