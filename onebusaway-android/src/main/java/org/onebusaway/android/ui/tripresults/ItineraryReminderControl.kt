/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.ui.tripresults

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.isLocationEnabled
import org.onebusaway.android.nav.NavigationService
import org.onebusaway.android.nav.ReminderPlan
import org.onebusaway.android.nav.ReminderPlanBuilder
import org.onebusaway.android.nav.ReminderPlanJson
import org.onebusaway.android.nav.ReminderPlanResult
import org.onebusaway.android.ui.compose.rememberNotificationPermissionRequest
import org.onebusaway.android.ui.tripdetails.TripDetailsLauncher
import org.onebusaway.android.util.PermissionUtils

/** Full-width start/stop action for the currently selected itinerary. */
@Composable
internal fun ItineraryReminderControl(itinerary: TripItinerary?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pendingPlan by remember { mutableStateOf<ReminderPlan?>(null) }
    var confirmationPlan by remember { mutableStateOf<ReminderPlan?>(null) }
    var active by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == TripDetailsLauncher.ACTION_SERVICE_DESTROYED) active = false
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TripDetailsLauncher.ACTION_SERVICE_DESTROYED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun start(plan: ReminderPlan) {
        requestNotifications()
        val intent = Intent(context, NavigationService::class.java).apply {
            putExtra(NavigationService.PLAN_JSON, ReminderPlanJson.encode(plan))
        }
        ContextCompat.startForegroundService(context, intent)
        active = true
        pendingPlan = null
        Toast.makeText(
            context,
            context.resources.getQuantityString(
                R.plurals.destination_reminder_started_rides,
                plan.rides.size,
                plan.rides.size
            ),
            Toast.LENGTH_LONG
        ).show()
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val plan = pendingPlan
        if (plan != null && grants.values.any { it }) {
            start(plan)
        } else if (plan != null) {
            Toast.makeText(context, R.string.destination_reminder_location_required, Toast.LENGTH_LONG).show()
        }
        pendingPlan = null
    }

    Button(
        onClick = {
            if (active) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_CANCEL)
                )
                active = false
                return@Button
            }
            val result = itinerary?.let(ReminderPlanBuilder::build)
                ?: ReminderPlanResult.Error(context.getString(R.string.destination_reminder_no_itinerary))
            when (result) {
                is ReminderPlanResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                is ReminderPlanResult.Success -> confirmationPlan = result.plan
            }
        },
        enabled = itinerary != null,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(stringResource(if (active) R.string.destination_reminder_stop else R.string.destination_reminder_start))
    }

    confirmationPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { confirmationPlan = null },
            title = { Text(stringResource(R.string.destination_reminder_dialog_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.destination_reminder_confirm_rides,
                        plan.rides.size,
                        plan.rides.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmationPlan = null
                        if (!isLocationEnabled(context)) {
                            Toast.makeText(context, R.string.destination_reminder_enable_location, Toast.LENGTH_LONG).show()
                        } else if (PermissionUtils.hasGrantedAtLeastOnePermission(
                                context,
                                PermissionUtils.LOCATION_PERMISSIONS
                            )
                        ) {
                            start(plan)
                        } else {
                            pendingPlan = plan
                            locationPermission.launch(PermissionUtils.LOCATION_PERMISSIONS)
                        }
                    }
                ) { Text(stringResource(R.string.destination_reminder_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmationPlan = null }) {
                    Text(stringResource(R.string.destination_reminder_cancel))
                }
            }
        )
    }
}
