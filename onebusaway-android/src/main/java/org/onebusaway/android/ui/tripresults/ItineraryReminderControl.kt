/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.ui.tripresults

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.isLocationEnabled
import org.onebusaway.android.nav.NavigationService
import org.onebusaway.android.nav.ReminderPlan
import org.onebusaway.android.nav.ReminderPlanBuilder
import org.onebusaway.android.nav.ReminderPlanError
import org.onebusaway.android.nav.ReminderPlanJson
import org.onebusaway.android.nav.ReminderPlanResult
import org.onebusaway.android.nav.ReminderSessionStore
import org.onebusaway.android.util.PermissionUtils

/** Full-width start/stop action for the currently selected itinerary. */
@Composable
internal fun ItineraryReminderControl(
    itinerary: TripItinerary?,
    modifier: Modifier = Modifier,
    viewModel: ReminderControlViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var pendingPlan by remember { mutableStateOf<ReminderPlan?>(null) }
    var confirmationPlan by remember { mutableStateOf<ReminderPlan?>(null) }
    val active by viewModel.hasActiveSession.collectAsStateWithLifecycle()

    fun start(plan: ReminderPlan) {
        val intent = Intent(context, NavigationService::class.java).apply {
            putExtra(NavigationService.PLAN_JSON, ReminderPlanJson.encode(plan))
        }
        ContextCompat.startForegroundService(context, intent)
        pendingPlan = null
        Toast.makeText(
            context,
            resources.getQuantityString(
                R.plurals.destination_reminder_monitoring_rides,
                plan.rides.size,
                plan.rides.size
            ),
            Toast.LENGTH_LONG
        ).show()
    }

    // Notifications carry the ongoing session and its Silence/Cancel actions, so a denial is worth
    // telling the rider about — but reminders still work by voice, and this screen can still stop
    // them, so the session starts either way.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val plan = pendingPlan ?: return@rememberLauncherForActivityResult
        if (!granted) {
            Toast.makeText(context, R.string.destination_reminder_notifications_denied, Toast.LENGTH_LONG).show()
        }
        start(plan)
    }

    fun startAfterNotificationPermission(plan: ReminderPlan) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            PermissionUtils.hasGrantedPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            start(plan)
            return
        }
        pendingPlan = plan
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val plan = pendingPlan
        pendingPlan = null
        if (plan == null) return@rememberLauncherForActivityResult
        // Coarse location is roughly kilometre-scale; the engine rejects fixes worse than
        // DestinationReminderPolicy.MAX_ACCEPTED_ACCURACY_METERS, so a coarse-only grant would
        // silently never alert. Say so rather than starting a session that cannot work.
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startAfterNotificationPermission(plan)
        } else {
            val message = if (grants.values.any { it }) {
                R.string.destination_reminder_precise_location_required
            } else {
                R.string.destination_reminder_location_required
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Button(
        onClick = {
            if (active) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, NavigationService::class.java).setAction(NavigationService.ACTION_CANCEL)
                )
                return@Button
            }
            itinerary?.let { selectedItinerary ->
                when (val result = ReminderPlanBuilder.build(selectedItinerary)) {
                    is ReminderPlanResult.Error -> Toast.makeText(context, result.userMessage(context), Toast.LENGTH_LONG).show()
                    is ReminderPlanResult.Success -> confirmationPlan = result.plan
                }
            }
        },
        enabled = active || itinerary != null,
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
                        } else if (PermissionUtils.hasGrantedPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        ) {
                            startAfterNotificationPermission(plan)
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

@HiltViewModel
internal class ReminderControlViewModel @Inject constructor(store: ReminderSessionStore) : ViewModel() {
    val hasActiveSession = store.hasActiveSession.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )
}

private fun ReminderPlanResult.Error.userMessage(context: Context): String = when (reason) {
    ReminderPlanError.MISSING_TRIP -> context.getString(R.string.destination_reminder_missing_trip)
    ReminderPlanError.INCOMPLETE_STOP_INFORMATION -> legNumber?.let {
        context.getString(R.string.destination_reminder_incomplete_stop_information, it)
    } ?: context.getString(R.string.destination_reminder_invalid_plan)
    ReminderPlanError.NO_TRANSIT_RIDES -> context.getString(R.string.destination_reminder_no_transit_rides)
    ReminderPlanError.INVALID_PLAN -> context.getString(R.string.destination_reminder_invalid_plan)
}
