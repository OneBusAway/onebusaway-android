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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.R
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.etaMinutes
import org.onebusaway.android.ui.arrivals.StopLauncher
import org.onebusaway.android.util.ScheduleDeviation

/**
 * `AppWidgetProvider` for the Stop Times widget. Each widget instance is identified by an
 * `appWidgetId` assigned by the system; config and arrival data for each instance are stored in
 * [WidgetPrefs].
 *
 * Renders with classic `RemoteViews`, not Jetpack Glance — Glance still compiles to `RemoteViews`
 * under the hood, so this is a style choice, not a capability one, and it avoids a first-time
 * dependency + pattern integration for this feature.
 */
class StopTimesWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        when (action) {
            ACTION_REFRESH_WIDGET -> {
                // Triggered every 5 min or by the refresh button.
                scheduleNextRefresh(context, widgetId)
                refreshWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }
            ACTION_UPDATE_RELATIVE_TIMES -> {
                // Triggered every minute; always reschedule (even with the screen off) so the chain
                // continues when the screen comes back on.
                scheduleNextRelativeTimesUpdate(context, widgetId)
                updateArrivalsFromCache(context, widgetId)
            }
            ACTION_APPLY_PENDING_CONFIG -> {
                // Callback after requestPinAppWidget succeeds.
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val stopId = intent.getStringExtra(EXTRA_STOP_ID)
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME)
                val widgetName = intent.getStringExtra(EXTRA_WIDGET_NAME)
                if (stopId == null || stopName == null || widgetName == null) return
                val routeIds = intent.getStringArrayListExtra(EXTRA_ROUTE_IDS).orEmpty()
                val routeNames = intent.getStringArrayListExtra(EXTRA_ROUTE_NAMES).orEmpty()
                val routeShortNames = routeIds.mapIndexed { i, id -> id to (routeNames.getOrNull(i) ?: id) }.toMap()

                WidgetPrefs.saveConfig(context, widgetId, WidgetConfig(stopId, stopName, widgetName, routeShortNames))
                scheduleRepeatingRefreshBroadcasts(context, widgetId)
                refreshWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate widgetIds=${appWidgetIds.toList()}")
        for (appWidgetId in appWidgetIds) {
            scheduleRepeatingRefreshBroadcasts(context, appWidgetId)
            updateArrivalsFromCache(context, appWidgetId)

            val config = WidgetPrefs.loadConfig(context, appWidgetId)
            if (config == null) {
                // Widget was placed with no config: on some devices, requestPinAppWidget places the
                // widget without re-launching the configure activity or firing the callback, leaving
                // the pending config unclaimed. Apply it here so the widget is immediately usable
                // without requiring the user to configure it again.
                val pending = WidgetPrefs.loadAndClearPendingPinConfig(context)
                if (pending != null) {
                    WidgetPrefs.saveConfig(context, appWidgetId, pending)
                    refreshWidget(context, appWidgetManager, appWidgetId)
                }
                continue
            }

            val snapshot = WidgetPrefs.loadSnapshot(context, appWidgetId)
            val isStale = snapshot == null ||
                ElapsedTime.now() - ElapsedTime(snapshot.receivedAtElapsedMs) > STALE_ON_PLACEMENT_WINDOW
            if (isStale) {
                WidgetArrivalWorker.enqueue(context, appWidgetId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted widgetIds=${appWidgetIds.toList()}")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (appWidgetId in appWidgetIds) {
            cancelAlarm(context, alarmManager, appWidgetId, ACTION_UPDATE_RELATIVE_TIMES)
            cancelAlarm(context, alarmManager, appWidgetId, ACTION_REFRESH_WIDGET)
            WidgetPrefs.delete(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateArrivalsFromCache(context, appWidgetId)
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, widgetId: Int, action: String) {
        val intent = Intent(context, StopTimesWidget::class.java).setAction(action)
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                requestCode(widgetId, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {

        const val ACTION_REFRESH_WIDGET = "org.onebusaway.android.ui.ACTION_REFRESH_WIDGET"
        const val ACTION_UPDATE_RELATIVE_TIMES = "org.onebusaway.android.ui.ACTION_UPDATE_WIDGET_RELATIVE_TIMES"
        const val ACTION_APPLY_PENDING_CONFIG = "org.onebusaway.android.ui.ACTION_APPLY_PENDING_WIDGET_CONFIG"

        const val EXTRA_STOP_ID = "stop_id"
        const val EXTRA_STOP_NAME = "stop_name"
        const val EXTRA_WIDGET_NAME = "widget_name"
        const val EXTRA_ROUTE_IDS = "route_ids"
        const val EXTRA_ROUTE_NAMES = "route_names"

        private const val TAG = "StopTimesWidget"

        private const val WIDGET_CELL_SIZE_2 = 110
        private const val WIDGET_CELL_SIZE_3 = 250
        private const val WIDGET_CELL_SIZE_4 = 300

        // Alarm-scheduling cadence.
        private val RELATIVE_TIMES_INTERVAL = 1.minutes
        private val REFRESH_INTERVAL = 5.minutes

        // A widget just placed with no snapshot yet, or one whose snapshot is older than this, gets an
        // immediate fetch instead of waiting for the next 5-minute alarm.
        private val STALE_ON_PLACEMENT_WINDOW = 5.minutes

        // A snapshot older than this is too old to keep ticking down (would show garbage countdowns);
        // trigger a full refresh instead of a cache-only relabel.
        private val STALE_SNAPSHOT_MAX_AGE = 10.minutes

        // An arrival more than this far in the past is dropped rather than shown as a negative ETA.
        private const val STALE_ARRIVAL_GRACE_MINUTES = 2L

        private const val REQUEST_CODE_SLOTS = 3

        private val WIDGET_ROW_IDS = intArrayOf(
            R.id.widget_route_1_row,
            R.id.widget_route_2_row,
            R.id.widget_route_3_row
        )

        private val WIDGET_ROUTE_TITLE_IDS = intArrayOf(
            R.id.widget_route_1_title,
            R.id.widget_route_2_title,
            R.id.widget_route_3_title
        )

        private val WIDGET_ETA_IDS = arrayOf(
            intArrayOf(R.id.widget_route_1_eta_1, R.id.widget_route_1_eta_2, R.id.widget_route_1_eta_3),
            intArrayOf(R.id.widget_route_2_eta_1, R.id.widget_route_2_eta_2, R.id.widget_route_2_eta_3),
            intArrayOf(R.id.widget_route_3_eta_1, R.id.widget_route_3_eta_2, R.id.widget_route_3_eta_3)
        )

        /**
         * Triggers a full widget refresh for a single instance. Shows the loading state and enqueues
         * [WidgetArrivalWorker] to fetch arrivals from the OBA API in the background. Once the worker
         * finishes, it calls [updateArrivalsFromCache] to populate the rows.
         */
        fun refreshWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            applyLoadingState(context, appWidgetManager, appWidgetId)
            if (WidgetPrefs.loadConfig(context, appWidgetId) != null) {
                WidgetArrivalWorker.enqueue(context, appWidgetId)
            }
        }

        /**
         * Populates the widget's arrival rows from the last cached [WidgetArrivalSnapshot]. Called by
         * [WidgetArrivalWorker] after a successful API fetch, and by the per-minute alarm to reformat
         * ETA labels without hitting the network.
         *
         * @param allowStaleRefresh whether an unusable snapshot should trigger [refreshWidget] (which
         * enqueues a fresh [WidgetArrivalWorker] run). Must be `false` when called from the worker's own
         * failure path (`Result.retry()`): enqueuing a replacement run there would bypass WorkManager's
         * retry backoff and could hammer the API on every failure instead of backing off.
         */
        fun updateArrivalsFromCache(context: Context, widgetId: Int, allowStaleRefresh: Boolean = true) {
            val views = RemoteViews(context.packageName, R.layout.stop_times_widget)
            val config = WidgetPrefs.loadConfig(context, widgetId)
            val appWidgetManager = AppWidgetManager.getInstance(context)

            if (config == null) {
                views.setTextViewText(R.id.stop_times_widget_title, context.getString(R.string.widget_not_configured))
                appWidgetManager.updateAppWidget(widgetId, views)
                return
            }
            views.setTextViewText(R.id.stop_times_widget_title, config.widgetName)

            // Renders with no route data — the widget just shows its title, no ETA rows. Used both when
            // there's nothing cached yet and when a cached snapshot exists but can't be trusted.
            fun renderWithoutRouteData() {
                views.setViewVisibility(R.id.widget_loading_spinner, View.GONE)
                bindStopIntent(context, views, widgetId, config)
                bindRefreshIntent(context, views, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }

            val snapshot = WidgetPrefs.loadSnapshot(context, widgetId)
            if (snapshot == null) {
                renderWithoutRouteData()
                return
            }

            // Project the server clock forward by elapsed device time since the fetch, instead of
            // comparing a stored fetch time against a fresh System.currentTimeMillis() — the same
            // stale-fallback projection DefaultArrivalsRepository uses, so device clock skew can't leak
            // into these ETAs (#1612).
            val elapsedSinceFetch = ElapsedTime.now() - ElapsedTime(snapshot.receivedAtElapsedMs)

            // Negative when the device rebooted since the fetch: SystemClock.elapsedRealtime() resets at
            // boot, so the persisted reading and "now" are on different epochs and aren't meaningfully
            // comparable at all — not just "old". There's nothing to project forward from, unlike the
            // plain-stale case below, so this always needs a real refetch rather than a stale render.
            if (elapsedSinceFetch < Duration.ZERO) {
                if (allowStaleRefresh) {
                    refreshWidget(context, appWidgetManager, widgetId)
                } else {
                    renderWithoutRouteData()
                }
                return
            }

            if (elapsedSinceFetch > STALE_SNAPSHOT_MAX_AGE) {
                if (allowStaleRefresh) {
                    refreshWidget(context, appWidgetManager, widgetId)
                    return
                }
                // A fetch attempt just failed (WidgetArrivalWorker's Result.retry() path calls this
                // before returning) — don't enqueue another one on top of it, or every failure would
                // bypass WorkManager's retry backoff. Fall through and render the stale data as-is,
                // matching DefaultArrivalsRepository's own stale-fallback behavior on a failed refresh.
            }
            val nowServer = ServerTime(snapshot.serverTimeAtFetchMs) + elapsedSinceFetch

            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            bindRouteRows(context, views, snapshot.routes, nowServer)
            bindUpdatedAtLabel(context, views, elapsedSinceFetch)

            views.setViewVisibility(R.id.widget_loading_spinner, View.GONE)
            applySizeVisibility(context, views, minWidthDp, minHeightDp)
            bindStopIntent(context, views, widgetId, config)
            bindRefreshIntent(context, views, widgetId)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        // Switches the widget UI to the "loading" state for a single widget.
        private fun applyLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.stop_times_widget)
            val config = WidgetPrefs.loadConfig(context, appWidgetId)

            if (config == null) {
                views.setTextViewText(R.id.stop_times_widget_title, context.getString(R.string.widget_not_configured))
            } else {
                views.setTextViewText(R.id.stop_times_widget_title, config.widgetName)
                bindStopIntent(context, views, appWidgetId, config)
                views.setViewVisibility(R.id.widget_loading_spinner, View.VISIBLE)
                for (rowId in WIDGET_ROW_IDS) views.setViewVisibility(rowId, View.GONE)
            }
            bindRefreshIntent(context, views, appWidgetId)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Tapping the widget body opens the arrivals board for the configured stop.
        private fun bindStopIntent(context: Context, views: RemoteViews, appWidgetId: Int, config: WidgetConfig) {
            val intent = StopLauncher.Builder(context, config.stopId)
                .setStopName(config.stopName)
                .intent
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }

        // Tapping the refresh button triggers an immediate API fetch.
        private fun bindRefreshIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
            val refreshIntent = Intent(context, StopTimesWidget::class.java).apply {
                action = ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                (appWidgetId * REQUEST_CODE_SLOTS) + 2,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refresh_header, refreshPendingIntent)
        }

        // Shows or hides route rows and ETA columns based on the current widget dimensions.
        private fun applySizeVisibility(context: Context, views: RemoteViews, minWidthDp: Int, minHeightDp: Int) {
            // Hide ETA columns as the widget gets narrower.
            if (minWidthDp <= WIDGET_CELL_SIZE_4) {
                for (rowEtas in WIDGET_ETA_IDS) views.setViewVisibility(rowEtas[2], View.GONE)
            }
            if (minWidthDp <= WIDGET_CELL_SIZE_3) {
                for (rowEtas in WIDGET_ETA_IDS) views.setViewVisibility(rowEtas[1], View.GONE)
            }

            val headerHorizontalPaddingPx = dpToPx(context, 18)

            // At minimum height, hide bottom rows, remove the spacer, and tighten padding.
            if (minHeightDp <= WIDGET_CELL_SIZE_2) {
                val headerVerticalPaddingPx = dpToPx(context, 6)
                views.setViewVisibility(R.id.widget_route_2_row, View.GONE)
                views.setViewVisibility(R.id.widget_route_3_row, View.GONE)
                views.setViewVisibility(R.id.header_spacer, View.GONE)
                views.setViewPadding(
                    R.id.stop_times_widget_header,
                    headerHorizontalPaddingPx,
                    headerVerticalPaddingPx,
                    headerHorizontalPaddingPx,
                    headerVerticalPaddingPx
                )
                views.setViewPadding(R.id.widget_updated_at, 0, 0, 0, dpToPx(context, 5))
            } else {
                val headerVerticalPaddingPx = dpToPx(context, 8)
                views.setViewVisibility(R.id.header_spacer, View.VISIBLE)
                views.setViewPadding(
                    R.id.stop_times_widget_header,
                    headerHorizontalPaddingPx,
                    headerVerticalPaddingPx,
                    headerHorizontalPaddingPx,
                    headerVerticalPaddingPx
                )
                views.setViewPadding(R.id.widget_updated_at, 0, 0, 0, dpToPx(context, 10))
            }
        }

        private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

        // Schedules both alarms for a widget. Called once on placement/reboot via onUpdate. Both
        // alarms reschedule themselves on each fire via onReceive.
        private fun scheduleRepeatingRefreshBroadcasts(context: Context, appWidgetId: Int) {
            scheduleNextRelativeTimesUpdate(context, appWidgetId)
            scheduleNextRefresh(context, appWidgetId)
        }

        private fun scheduleNextRelativeTimesUpdate(context: Context, appWidgetId: Int) {
            scheduleExactAlarm(context, appWidgetId, ACTION_UPDATE_RELATIVE_TIMES, RELATIVE_TIMES_INTERVAL)
        }

        private fun scheduleNextRefresh(context: Context, appWidgetId: Int) {
            scheduleExactAlarm(context, appWidgetId, ACTION_REFRESH_WIDGET, REFRESH_INTERVAL)
        }

        // Schedules an exact alarm for the given action. The data refresh uses ELAPSED_REALTIME_WAKEUP
        // so it runs even with the screen off; the relative-times update uses ELAPSED_REALTIME since
        // the labels are only visible when the screen is on. Falls back to setWindow on API 31+ if the
        // SCHEDULE_EXACT_ALARM permission has not been granted by the user.
        private fun scheduleExactAlarm(context: Context, appWidgetId: Int, action: String, delay: Duration) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, StopTimesWidget::class.java).apply {
                setAction(action)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(appWidgetId, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtElapsed = ElapsedTime.now() + delay
            val alarmType = if (action == ACTION_UPDATE_RELATIVE_TIMES) {
                AlarmManager.ELAPSED_REALTIME
            } else {
                AlarmManager.ELAPSED_REALTIME_WAKEUP
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setWindow(alarmType, triggerAtElapsed.ms, 30.seconds.inWholeMilliseconds, pendingIntent)
            } else {
                alarmManager.setExact(alarmType, triggerAtElapsed.ms, pendingIntent)
            }
        }

        // A unique PendingIntent request code for the given widget ID and action. Slot 0 = REFRESH
        // alarm, 1 = RELATIVE_TIMES alarm, 2 = refresh button (see bindRefreshIntent).
        private fun requestCode(widgetId: Int, action: String): Int {
            val actionIndex = when (action) {
                ACTION_REFRESH_WIDGET -> 0
                ACTION_UPDATE_RELATIVE_TIMES -> 1
                else -> throw IllegalArgumentException("Unknown action: $action")
            }
            return (widgetId * REQUEST_CODE_SLOTS) + actionIndex
        }

        private fun bindRouteRows(
            context: Context,
            views: RemoteViews,
            routes: List<WidgetArrivalSnapshot.Route>,
            nowServer: ServerTime
        ) {
            for (rowIndex in WIDGET_ROUTE_TITLE_IDS.indices) {
                if (rowIndex >= routes.size) {
                    views.setViewVisibility(WIDGET_ROW_IDS[rowIndex], View.GONE)
                    continue
                }
                views.setViewVisibility(WIDGET_ROW_IDS[rowIndex], View.VISIBLE)

                val route = routes[rowIndex]
                views.setTextViewText(WIDGET_ROUTE_TITLE_IDS[rowIndex], route.shortName)

                val validArrivals = route.arrivals.filter {
                    etaMinutes(ServerTime(it.displayTimeMs), nowServer) >= -STALE_ARRIVAL_GRACE_MINUTES
                }
                bindEtaPills(context, views, WIDGET_ETA_IDS[rowIndex], validArrivals, nowServer)
            }
        }

        private fun bindEtaPills(
            context: Context,
            views: RemoteViews,
            etaViewIds: IntArray,
            arrivals: List<WidgetArrivalSnapshot.Arrival>,
            nowServer: ServerTime
        ) {
            if (arrivals.isEmpty()) {
                // No upcoming arrivals — show N/A in the first pill and hide the rest.
                views.setTextViewText(etaViewIds[0], context.getString(R.string.widget_no_times))
                views.setInt(etaViewIds[0], "setBackgroundResource", R.drawable.widget_eta_bg_scheduled)
                views.setViewVisibility(etaViewIds[0], View.VISIBLE)
                for (i in 1 until etaViewIds.size) views.setViewVisibility(etaViewIds[i], View.GONE)
                return
            }
            for (i in etaViewIds.indices) {
                if (i >= arrivals.size) {
                    views.setViewVisibility(etaViewIds[i], View.GONE)
                    continue
                }
                val arrival = arrivals[i]
                views.setTextViewText(etaViewIds[i], formatMinutesAway(context, arrival, nowServer))
                views.setInt(etaViewIds[i], "setBackgroundResource", etaBackgroundResource(arrival))
                views.setViewVisibility(etaViewIds[i], View.VISIBLE)
            }
        }

        private fun bindUpdatedAtLabel(context: Context, views: RemoteViews, elapsedSinceFetch: Duration) {
            // Round to the nearest minute (adding 30s before integer division) so the label
            // transitions at the halfway point rather than truncating to the lower minute.
            val minutes = (elapsedSinceFetch.inWholeSeconds + 30) / 60
            val label = if (minutes == 0L) {
                context.getString(R.string.widget_updated_just_now)
            } else {
                context.getString(R.string.widget_updated_minutes_ago, minutes)
            }
            views.setTextViewText(R.id.widget_updated_at, label)
        }

        private fun formatMinutesAway(context: Context, arrival: WidgetArrivalSnapshot.Arrival, nowServer: ServerTime): String {
            val minutes = etaMinutes(ServerTime(arrival.displayTimeMs), nowServer)
            return if (minutes == 0L) {
                context.getString(R.string.stop_info_eta_now)
            } else {
                "$minutes ${context.getString(R.string.minutes_abbreviation)}"
            }
        }

        // The pill color is the app's single source of truth for schedule-deviation color (#2043) —
        // the same states/colors the arrivals board's own ETA pills use — rather than a widget-only
        // palette.
        private fun etaBackgroundResource(arrival: WidgetArrivalSnapshot.Arrival): Int {
            if (!arrival.isPredicted) return R.drawable.widget_eta_bg_scheduled
            val deviation = (arrival.displayTimeMs - arrival.scheduledTimeMs).milliseconds
            return when (ScheduleDeviation.status(isRealtime = true, deviation = deviation)) {
                ScheduleDeviation.Status.EARLY -> R.drawable.widget_eta_bg_early
                ScheduleDeviation.Status.DELAYED -> R.drawable.widget_eta_bg_late
                ScheduleDeviation.Status.ON_TIME -> R.drawable.widget_eta_bg_on_time
                ScheduleDeviation.Status.SCHEDULED -> R.drawable.widget_eta_bg_scheduled
            }
        }
    }
}
