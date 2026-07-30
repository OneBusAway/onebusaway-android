/*
 * Copyright (C) 2005-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Handles explicit notification commands without reaching into static service or TTS state. */
class NavigationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Commands normally arrive as an action. The extra-based form is the shape used by
        // PendingIntents a previous app version created, which the system still holds and can
        // deliver here after an upgrade.
        val command = intent.action ?: when (intent.getIntExtra(ACTION_NUM, 0)) {
            DISMISS_NOTIFICATION -> ACTION_SILENCE
            CANCEL_TRIP -> ACTION_CANCEL
            else -> null
        }
        when (command) {
            ACTION_SILENCE -> ContextCompat.startForegroundService(
                context,
                Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_SILENCE)
            )
            ACTION_CANCEL -> ContextCompat.startForegroundService(
                context,
                Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_CANCEL)
            )
        }
    }

    companion object {
        // Extras and codes of the pre-modernization notification intents, kept only so upgraded
        // installs can still honour a PendingIntent the system created before the upgrade.
        const val ACTION_NUM = ".ACTION_NUM"
        const val DISMISS_NOTIFICATION = 1
        const val CANCEL_TRIP = 2

        const val ACTION_SILENCE = NavigationService.ACTION_SILENCE
        const val ACTION_CANCEL = NavigationService.ACTION_CANCEL
    }
}
