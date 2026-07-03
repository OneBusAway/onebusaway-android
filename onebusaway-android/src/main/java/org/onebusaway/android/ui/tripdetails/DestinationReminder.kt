/*
 * Copyright (C) 2012-2026 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Open Transit Software Foundation
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
package org.onebusaway.android.ui.tripdetails

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.analytics.FirebaseAnalytics
import org.onebusaway.android.R
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.analytics.ObaAnalytics
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.nav.NavigationService
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.ui.compose.components.OptOutInfoDialog
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.util.LocationUtils
import org.onebusaway.android.util.PermissionUtils.NOTIFICATION_PERMISSION_REQUEST

/**
 * The destination-reminder flow (set a reminder to alight at a chosen stop), as a reusable Compose
 * action shared by [TripDetailsLauncher] and the trip-details NavHost destination. Ported faithfully
 * from the legacy `TripDetailsActivity` methods, but using `ActivityResultContracts` for the
 * location-settings resolution (instead of `startActivityForResult`/`onActivityResult`) and a
 * [DisposableEffect] for the trip-end receiver, so it works in a NavHost destination that has no
 * Activity result/lifecycle callbacks of its own.
 *
 * Returns an `(stopIndex) -> Unit` to wire to `TripDetailsRoute.onSetDestinationReminder`.
 */
@Composable
internal fun rememberDestinationReminderAction(
    viewModel: TripDetailsViewModel,
    prefsRepository: PreferencesRepository,
    tripId: String,
    stopId: String?,
): (stopIndex: Int) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = context.findActivity()

    // The two informational dialogs are Compose-hosted; these flags drive them (set by the
    // dialogFor*/destinationReminderBetaDialog actions below, rendered near the end of this composable).
    var showLocationModeDialog by remember { mutableStateOf(false) }
    var showBetaDialog by remember { mutableStateOf(false) }

    // Saved when we must wait for the user to enable location settings; started on the OK result.
    var pendingServiceIntent by remember { mutableStateOf<Intent?>(null) }

    fun startNavigationService(serviceIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.applicationContext.startForegroundService(serviceIntent)
        } else {
            context.applicationContext.startService(serviceIntent)
        }
    }

    // Replaces startResolutionForResult(...) + onActivityResult(REQUEST_ENABLE_LOCATION).
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingServiceIntent?.let { startNavigationService(it) }
        }
    }

    fun askUserToTurnLocationOn() {
        @Suppress("DEPRECATION")
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val request = LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build()
        LocationServices.getSettingsClient(context).checkLocationSettings(request)
            .addOnCompleteListener { task ->
                try {
                    task.getResult(ApiException::class.java)
                } catch (e: ApiException) {
                    if (e.statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                        try {
                            val resolution = (e as ResolvableApiException).resolution
                            locationSettingsLauncher.launch(
                                IntentSenderRequest.Builder(resolution.intentSender).build()
                            )
                        } catch (ignored: IntentSender.SendIntentException) {
                        } catch (ignored: ClassCastException) {
                        }
                    }
                }
            }
    }

    /** Builds the NavigationService intent for the destination at [position]; flags the stop. */
    fun setUpNavigationService(position: Int): Intent? {
        val stops = viewModel.destinationStops(position) ?: return null
        val serviceIntent = Intent(context, NavigationService::class.java).apply {
            putExtra(NavigationService.DESTINATION_ID, stops.destinationStopId)
            putExtra(NavigationService.BEFORE_STOP_ID, stops.beforeStopId)
            putExtra(NavigationService.TRIP_ID, tripId)
        }
        viewModel.setDestinationId(stops.destinationStopId)
        pendingServiceIntent = serviceIntent
        return serviceIntent
    }

    fun dialogForLocationModeChanges() {
        showLocationModeDialog = true
    }

    fun destinationReminderBetaDialog() {
        showBetaDialog = true
    }

    fun onDestinationReminderConfirmed(position: Int) {
        if (!LocationUtils.isLocationEnabled(context)) {
            // Still build the pending service intent so the location-settings result can start it.
            if (setUpNavigationService(position) == null) return
            askUserToTurnLocationOn()
            return
        }
        if (!prefsRepository.getBoolean(R.string.preference_key_never_show_change_location_mode_dialog, false) &&
            LocationUtils.getLocationMode(context) != Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
        ) {
            dialogForLocationModeChanges()
        }
        if (!prefsRepository.getBoolean(R.string.preference_key_never_show_destination_reminder_beta_dialog, false)) {
            destinationReminderBetaDialog()
        }
        ObaAnalytics.reportUiEvent(
            FirebaseAnalytics.getInstance(context), AnalyticsEntryPoint.get(context).plausible,
            PlausibleAnalytics.REPORT_DESTINATION_REMINDER_EVENT_URL,
            resources.getString(R.string.analytics_label_destination_reminder),
            resources.getString(R.string.analytics_label_destination_reminder_variant_started)
        )
        ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST
        )
        val serviceIntent = setUpNavigationService(position) ?: return
        startNavigationService(serviceIntent)
        Toast.makeText(
            context, resources.getString(R.string.destination_reminder_title), Toast.LENGTH_LONG
        ).show()
    }

    fun confirmDestinationReminder(position: Int) {
        MaterialAlertDialogBuilder(context)
            .setMessage(R.string.destination_reminder_dialog_msg)
            .setTitle(R.string.destination_reminder_dialog_title)
            .setPositiveButton(R.string.destination_reminder_confirm) { dialog, _ ->
                onDestinationReminderConfirmed(position)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.destination_reminder_cancel) { _, _ -> }
            .show()
    }

    if (showLocationModeDialog) {
        OptOutInfoDialog(
            title = stringResource(R.string.main_changelocationmode_title),
            icon = painterResource(android.R.drawable.ic_dialog_map),
            iconTint = colorResource(R.color.theme_primary),
            body = stringResource(R.string.main_changelocationmode),
            optOutLabel = stringResource(R.string.main_never_ask_again),
            onOptOut = {
                prefsRepository.setBoolean(
                    R.string.preference_key_never_show_change_location_mode_dialog, it
                )
            },
            confirmText = stringResource(R.string.rt_yes),
            onConfirm = {
                showLocationModeDialog = false
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            dismissText = stringResource(R.string.rt_no),
            onDismiss = { showLocationModeDialog = false },
            onDismissRequest = { showLocationModeDialog = false },
        )
    }
    if (showBetaDialog) {
        OptOutInfoDialog(
            title = stringResource(R.string.destination_reminder_beta_title),
            icon = painterResource(android.R.drawable.ic_dialog_alert),
            iconTint = colorResource(R.color.theme_primary),
            body = stringResource(R.string.destination_reminder_beta_summary),
            optOutLabel = stringResource(R.string.main_never_show_again),
            onOptOut = {
                prefsRepository.setBoolean(
                    R.string.preference_key_never_show_destination_reminder_beta_dialog, it
                )
            },
            confirmText = stringResource(R.string.ok),
            onConfirm = { showBetaDialog = false },
            onDismissRequest = { showBetaDialog = false },
        )
    }

    // Clears the destination flag when the NavigationService is destroyed (trip cancelled/ended).
    // Registered for the lifetime of the hosting composable (replaces the Activity's lazy register +
    // onDestroy unregister).
    DisposableEffect(activity, viewModel) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == TripDetailsLauncher.ACTION_SERVICE_DESTROYED) {
                    viewModel.setDestinationId(null)
                }
            }
        }
        val filter = IntentFilter(TripDetailsLauncher.ACTION_SERVICE_DESTROYED)
        ContextCompat.registerReceiver(
            activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { activity.unregisterReceiver(receiver) } }
    }

    return { stopIndex -> confirmDestinationReminder(stopIndex) }
}
