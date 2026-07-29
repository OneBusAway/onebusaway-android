/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.ui.tripresults

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.location.isLocationEnabled
import org.onebusaway.android.nav.NavigationService
import org.onebusaway.android.nav.ReminderPlan
import org.onebusaway.android.nav.ReminderPlanBuilder
import org.onebusaway.android.nav.ReminderPlanError
import org.onebusaway.android.nav.ReminderPlanResult
import org.onebusaway.android.nav.ReminderSessionStore
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.runCatchingCancellable

private const val TAG = "ReminderControl"

/** Full-width start/stop action for the currently selected itinerary. */
@Composable
internal fun ItineraryReminderControl(
    itinerary: TripItinerary?,
    modifier: Modifier = Modifier,
    viewModel: ReminderControlViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val active by viewModel.hasActiveSession.collectAsStateWithLifecycle()
    val confirmationPlan by viewModel.confirmationPlan.collectAsStateWithLifecycle()

    // Bound to the application context rather than this composable's: the ViewModel runs the
    // store-then-start sequence on its own scope, which outlives a screen the rider navigates away
    // from mid-sequence.
    val serviceStarter = remember(context) {
        val appContext = context.applicationContext
        ReminderServiceStarter { sessionId ->
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, NavigationService::class.java)
                    .putExtra(NavigationService.SESSION_ID, sessionId)
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReminderControlEvent.Started -> Toast.makeText(
                    context,
                    resources.getQuantityString(
                        R.plurals.destination_reminder_monitoring_rides,
                        event.rideCount,
                        event.rideCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
                is ReminderControlEvent.StoreFailed -> {
                    Log.e(TAG, "Could not store reminder session ${event.sessionId}", event.error)
                    Toast.makeText(context, R.string.destination_reminder_invalid_plan, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun start(plan: ReminderPlan) = viewModel.startSession(plan, serviceStarter)

    // Notifications carry the ongoing session and its Silence/Cancel actions, so a denial is worth
    // telling the rider about — but reminders still work by voice, and this screen can still stop
    // them, so the session starts either way.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val plan = viewModel.takePendingPlan() ?: return@rememberLauncherForActivityResult
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
        viewModel.awaitPermission(plan)
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val plan = viewModel.takePendingPlan() ?: return@rememberLauncherForActivityResult
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
                    is ReminderPlanResult.Success -> viewModel.confirm(result.plan)
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
            onDismissRequest = { viewModel.dismissConfirmation() },
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
                        viewModel.dismissConfirmation()
                        if (!isLocationEnabled(context)) {
                            Toast.makeText(context, R.string.destination_reminder_enable_location, Toast.LENGTH_LONG).show()
                        } else if (PermissionUtils.hasGrantedPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        ) {
                            startAfterNotificationPermission(plan)
                        } else {
                            viewModel.awaitPermission(plan)
                            locationPermission.launch(PermissionUtils.LOCATION_PERMISSIONS)
                        }
                    }
                ) { Text(stringResource(R.string.destination_reminder_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmation() }) {
                    Text(stringResource(R.string.destination_reminder_cancel))
                }
            }
        )
    }
}

/**
 * Starts the reminder foreground service for a session already in the store. Supplied per call
 * rather than held by the ViewModel so the ViewModel never owns a Context, and so the caller
 * decides which one the service is started from.
 */
internal fun interface ReminderServiceStarter {
    fun start(sessionId: String)
}

/**
 * One-shot feedback from a start attempt. The sequence runs on the ViewModel's scope, so it can
 * finish after the screen is gone; these are dropped when nothing is collecting rather than queued
 * for a screen that may never come back.
 */
internal sealed interface ReminderControlEvent {
    data class Started(val rideCount: Int) : ReminderControlEvent

    data class StoreFailed(val sessionId: String, val error: Throwable) : ReminderControlEvent
}

@HiltViewModel
internal class ReminderControlViewModel @Inject constructor(
    private val store: ReminderSessionStore
) : ViewModel() {
    val hasActiveSession = store.hasActiveSession.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    private val _confirmationPlan = MutableStateFlow<ReminderPlan?>(null)

    /** The plan the rider is being asked to confirm, held here so a rotation keeps the dialog up. */
    val confirmationPlan: StateFlow<ReminderPlan?> = _confirmationPlan.asStateFlow()

    private val _events = MutableSharedFlow<ReminderControlEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<ReminderControlEvent> = _events.asSharedFlow()

    /**
     * The plan waiting on a system permission dialog. It lives here rather than in composition
     * because the dialog can take the activity through a configuration change: a plan remembered in
     * the composable would be gone when the result arrived, and the rider would grant location only
     * for nothing to happen.
     */
    private var pendingPlan: ReminderPlan? = null

    fun confirm(plan: ReminderPlan) {
        _confirmationPlan.value = plan
    }

    fun dismissConfirmation() {
        _confirmationPlan.value = null
    }

    fun awaitPermission(plan: ReminderPlan) {
        pendingPlan = plan
    }

    /** The plan a permission result belongs to, consumed so a later result cannot restart it. */
    fun takePendingPlan(): ReminderPlan? = pendingPlan.also { pendingPlan = null }

    /**
     * Stores [plan] and then starts the service for it. Store first: the intent carries only the
     * session id, so the row has to be there before the service goes looking for it — and the pair
     * runs on [viewModelScope] so a rider who navigates away in between cannot leave a stored
     * session with nothing monitoring it, which would strand the screen on its Stop action.
     */
    fun startSession(plan: ReminderPlan, service: ReminderServiceStarter) {
        viewModelScope.launch {
            storeSession(plan)
                .onSuccess {
                    service.start(plan.sessionId)
                    _events.tryEmit(ReminderControlEvent.Started(plan.rides.size))
                }
                .onFailure { _events.tryEmit(ReminderControlEvent.StoreFailed(plan.sessionId, it)) }
        }
    }

    /**
     * Makes [plan] the stored session, replacing any other. The caller must not start the service
     * on a failure: it is started by session id and would find nothing to monitor. Reporting is
     * left to the caller so this stays free of Android dependencies and unit-testable.
     */
    suspend fun storeSession(plan: ReminderPlan): Result<Unit> = runCatchingCancellable {
        store.start(plan, WallTime.now())
    }
}

private fun ReminderPlanResult.Error.userMessage(context: Context): String = when (reason) {
    ReminderPlanError.MISSING_TRIP -> context.getString(R.string.destination_reminder_missing_trip)
    ReminderPlanError.INCOMPLETE_STOP_INFORMATION -> legNumber?.let {
        context.getString(R.string.destination_reminder_incomplete_stop_information, it)
    } ?: context.getString(R.string.destination_reminder_invalid_plan)
    ReminderPlanError.NO_TRANSIT_RIDES -> context.getString(R.string.destination_reminder_no_transit_rides)
    ReminderPlanError.INVALID_PLAN -> context.getString(R.string.destination_reminder_invalid_plan)
}
