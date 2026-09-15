/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.ui.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.random.Random
import org.onebusaway.android.R
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.ui.arrivals.DefaultArrivalsRepository
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.icons.AppIcons

/** A route the config screen offers as a chip: [id] is the route id, [shortName] the display label. */
private data class RouteOption(val id: String, val shortName: String)

private const val MAX_SELECTED_ROUTES = 3

/**
 * Configures a single Stop Times widget instance: pick a stop (via [StopPickerActivity]), pick up to
 * [MAX_SELECTED_ROUTES] of its routes, and name the widget. Saving either updates an existing
 * instance's [WidgetConfig] (reconfigure flow) or, when launched pre-placement with no `appWidgetId`
 * yet, requests the system pin a new one ([pinWidgetWithConfig]).
 */
@AndroidEntryPoint
class StopTimesWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var stopArrivalsDataSource: StopArrivalsDataSource

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED) // The widget is not added unless the user hits Save.

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        var stopId = intent.getStringExtra(EXTRA_STOP_ID)
        var stopName = intent.getStringExtra(EXTRA_STOP_NAME)
        var existingWidgetName: String? = null
        var existingRoutes: Map<String, String> = emptyMap()

        // If editing an existing widget (opened via its "reconfigure" affordance, with no stop extras
        // of its own), load the saved config to pre-populate the fields.
        if (stopId == null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetPrefs.loadConfig(this, appWidgetId)?.let { existing ->
                stopId = existing.stopId
                stopName = existing.stopName
                existingWidgetName = existing.widgetName
                existingRoutes = existing.routeShortNames
            }
        }

        setContent {
            ObaTheme {
                StopTimesWidgetConfigScreen(
                    initialStopId = stopId,
                    initialStopName = stopName,
                    initialWidgetName = existingWidgetName ?: stopName.orEmpty(),
                    initialSelectedRoutes = existingRoutes,
                    loadRoutes = ::loadRoutesForStop,
                    onSave = ::onSave,
                    onCancel = ::finish
                )
            }
        }
    }

    // Built from the stop's own route metadata + the response's reference pool, not the arrivals list
    // itself — a stop with no arrival in the fetch window (off-hours for a weekend/holiday-only route,
    // or a stale stop whose service was later restructured) would otherwise offer no routes to pick.
    private suspend fun loadRoutesForStop(stopId: String): List<RouteOption> {
        val response = stopArrivalsDataSource.arrivals(stopId, DefaultArrivalsRepository.MINUTES_AFTER_MAX).getOrNull()
        val routeIds = response?.stop?.routeIds.orEmpty()
        return routeIds
            .mapNotNull { id -> response?.route(id)?.let { RouteOption(id, it.shortName ?: id) } }
            .sortedWith { a, b -> compareRouteNames(a.shortName, b.shortName) }
    }

    private fun onSave(stopId: String, stopName: String, widgetName: String, selectedRoutes: Map<String, String>) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pinWidgetWithConfig(stopId, stopName, widgetName, selectedRoutes)
            return
        }

        WidgetPrefs.saveConfig(this, appWidgetId, WidgetConfig(stopId, stopName, widgetName, selectedRoutes))
        StopTimesWidget.refreshWidget(this, AppWidgetManager.getInstance(this), appWidgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun pinWidgetWithConfig(stopId: String, stopName: String, widgetName: String, selectedRoutes: Map<String, String>) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !appWidgetManager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, R.string.widget_config_add_manually, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Save the config before requesting the pin. On some Android versions the system launches the
        // configure activity after placement anyway, which we don't want; StopTimesWidget.onUpdate
        // detects and silently applies this instead of showing the config screen again.
        WidgetPrefs.savePendingPinConfig(this, WidgetConfig(stopId, stopName, widgetName, selectedRoutes))

        val callbackIntent = Intent(this, StopTimesWidget::class.java).apply {
            action = StopTimesWidget.ACTION_APPLY_PENDING_CONFIG
            putExtra(StopTimesWidget.EXTRA_STOP_ID, stopId)
            putExtra(StopTimesWidget.EXTRA_STOP_NAME, stopName)
            putExtra(StopTimesWidget.EXTRA_WIDGET_NAME, widgetName)
            putStringArrayListExtra(StopTimesWidget.EXTRA_ROUTE_IDS, ArrayList(selectedRoutes.keys))
            putStringArrayListExtra(StopTimesWidget.EXTRA_ROUTE_NAMES, ArrayList(selectedRoutes.values))
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE // required so the system can inject EXTRA_APPWIDGET_ID
        }
        // A random (not fixed) request code: two concurrent pin flows (e.g. the user backs out of one and
        // starts another before the first is placed) must not resolve to the same underlying
        // PendingIntent, which — since FLAG_UPDATE_CURRENT keeps the object but replaces its extras —
        // would let the second flow's config silently overwrite the first's callback before it fires.
        val requestCode = Random.nextInt()
        val callbackPendingIntent = PendingIntent.getBroadcast(this, requestCode, callbackIntent, flags)

        val previewExtras = Bundle().apply {
            val preview = RemoteViews(packageName, R.layout.stop_times_widget_preview)
            preview.setTextViewText(R.id.preview_stop_name, widgetName)
            selectedRoutes.values.firstOrNull()?.let { preview.setTextViewText(R.id.widget_route_1_title, it) }
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview)
        }

        appWidgetManager.requestPinAppWidget(ComponentName(this, StopTimesWidget::class.java), previewExtras, callbackPendingIntent)
        finish()
    }

    companion object {
        const val EXTRA_STOP_ID = "stop_id"
        const val EXTRA_STOP_NAME = "stop_name"
    }
}

/** Sorts route names numerically if both are integers, otherwise alphabetically (case-insensitive). */
private fun compareRouteNames(a: String, b: String): Int {
    val aInt = a.toIntOrNull()
    val bInt = b.toIntOrNull()
    return if (aInt != null && bInt != null) aInt.compareTo(bInt) else a.compareTo(b, ignoreCase = true)
}

@Composable
private fun StopTimesWidgetConfigScreen(
    initialStopId: String?,
    initialStopName: String?,
    initialWidgetName: String,
    initialSelectedRoutes: Map<String, String>,
    loadRoutes: suspend (String) -> List<RouteOption>,
    onSave: (stopId: String, stopName: String, widgetName: String, selectedRoutes: Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var stopId by remember { mutableStateOf(initialStopId) }
    var stopName by remember { mutableStateOf(initialStopName) }
    var widgetName by remember { mutableStateOf(initialWidgetName) }
    var routes by remember { mutableStateOf<List<RouteOption>>(emptyList()) }
    var isLoadingRoutes by remember { mutableStateOf(false) }
    var selectedRouteIds by remember { mutableStateOf(initialSelectedRoutes.keys) }
    // The previously-saved id -> short name map for the stop being reconfigured. A route that has no
    // upcoming arrival in the fetch below (last bus of the night, weekend-only route, ...) won't appear
    // in `routes` at all, so it needs this fallback to survive an unrelated save instead of silently
    // dropping out of the widget's config. Cleared whenever a *different* stop is picked, since these
    // ids belong to the old stop's routes.
    var persistedRoutes by remember { mutableStateOf(initialSelectedRoutes) }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val pickedId = data.getStringExtra(StopPickerActivity.EXTRA_STOP_ID) ?: return@rememberLauncherForActivityResult
        val pickedName = data.getStringExtra(StopPickerActivity.EXTRA_STOP_NAME).orEmpty()
        stopId = pickedId
        stopName = pickedName
        widgetName = pickedName
        selectedRouteIds = emptySet()
        persistedRoutes = emptyMap()
    }

    LaunchedEffect(stopId) {
        val id = stopId ?: return@LaunchedEffect
        isLoadingRoutes = true
        routes = loadRoutes(id)
        isLoadingRoutes = false
    }

    Scaffold(topBar = { ObaTopAppBar(title = stringResource(R.string.widget_config_title), onBack = onCancel) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.widget_config_selected_stop), style = MaterialTheme.typography.titleSmall)

            OutlinedButton(
                onClick = { pickerLauncher.launch(Intent(context, StopPickerActivity::class.java)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stopName ?: stringResource(R.string.widget_config_select_a_stop))
            }

            OutlinedTextField(
                value = widgetName,
                onValueChange = { widgetName = it },
                label = { Text(stringResource(R.string.widget_config_widget_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.widget_config_selected_routes), style = MaterialTheme.typography.titleSmall)

            if (isLoadingRoutes) {
                CircularProgressIndicator()
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routes.forEach { route ->
                        val checked = route.id in selectedRouteIds
                        FilterChip(
                            selected = checked,
                            onClick = {
                                selectedRouteIds = when {
                                    checked -> selectedRouteIds - route.id
                                    selectedRouteIds.size >= MAX_SELECTED_ROUTES -> {
                                        Toast.makeText(context, R.string.widget_config_select_no_more_than_three_routes, Toast.LENGTH_SHORT).show()
                                        selectedRouteIds
                                    }
                                    else -> selectedRouteIds + route.id
                                }
                            },
                            label = { Text(route.shortName) },
                            leadingIcon = if (checked) {
                                { Icon(AppIcons.Check, contentDescription = null) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val finalStopId = stopId
                    val finalStopName = stopName
                    if (finalStopId == null || finalStopName == null) return@Button
                    if (selectedRouteIds.isEmpty()) {
                        Toast.makeText(context, R.string.widget_config_select_at_least_one_route, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val finalWidgetName = widgetName.trim().ifEmpty { finalStopName }
                    // Prefer the freshly-fetched short name (keeps it current if it changed); fall back
                    // to the persisted one for a selected route this fetch didn't happen to return.
                    val fetchedById = routes.associateBy { it.id }
                    val selectedRoutes = selectedRouteIds.associateWith { id ->
                        fetchedById[id]?.shortName ?: persistedRoutes[id] ?: id
                    }
                    onSave(finalStopId, finalStopName, finalWidgetName, selectedRoutes)
                },
                enabled = stopId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.widget_config_save_widget))
            }
        }
    }
}
