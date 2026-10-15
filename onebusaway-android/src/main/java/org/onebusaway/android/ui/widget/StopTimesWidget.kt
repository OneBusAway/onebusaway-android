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
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StrikethroughSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.EntryPointAccessors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import org.onebusaway.android.R
import org.onebusaway.android.app.di.WidgetEntryPoint
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.StopLauncher
import org.onebusaway.android.util.DisplayFormat
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
            ACTION_REDRAW_WIDGET -> {
                // Every minute, from the cache only: drops departed trips between fetches. Fetching is the
                // refresh alarm's job; a stale-triggered fetch here would bypass a failing worker's backoff.
                scheduleNextRedraw(context, widgetId)
                updateArrivalsFromCache(context, widgetId, allowStaleRefresh = false)
            }
            ACTION_APPLY_PENDING_CONFIG -> {
                // Callback after requestPinAppWidget succeeds.
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val config = WidgetPrefs.decodeConfig(intent.getStringExtra(EXTRA_CONFIG)) ?: return

                WidgetPrefs.saveConfig(context, widgetId, config)
                scheduleAlarms(context, widgetId)
                refreshWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate widgetIds=${appWidgetIds.toList()}")
        for (appWidgetId in appWidgetIds) {
            scheduleAlarms(context, appWidgetId)
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

            // Paused: nothing to fetch. Enqueuing anyway would loop, since WorkManager toggling its own
            // receivers when the queue empties counts as a package change, which re-sends APPWIDGET_UPDATE.
            if (isPaused(context, config)) continue

            val snapshot = WidgetPrefs.loadSnapshot(context, appWidgetId, config)
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
            cancelAlarm(context, alarmManager, appWidgetId, ACTION_REFRESH_WIDGET, REQUEST_SLOT_REFRESH_ALARM)
            cancelAlarm(context, alarmManager, appWidgetId, ACTION_REDRAW_WIDGET, REQUEST_SLOT_REDRAW_ALARM)
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

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, widgetId: Int, action: String, slot: Int) {
        val intent = Intent(context, StopTimesWidget::class.java).setAction(action)
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                widgetId * REQUEST_CODE_SLOTS + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {

        const val ACTION_REFRESH_WIDGET = "org.onebusaway.android.ui.ACTION_REFRESH_WIDGET"
        const val ACTION_REDRAW_WIDGET = "org.onebusaway.android.ui.ACTION_REDRAW_WIDGET"
        const val ACTION_APPLY_PENDING_CONFIG = "org.onebusaway.android.ui.ACTION_APPLY_PENDING_WIDGET_CONFIG"

        /** The pin callback's whole [WidgetConfig], as [WidgetPrefs.encodeConfig] wrote it. */
        const val EXTRA_CONFIG = "widget_config"

        private const val TAG = "StopTimesWidget"

        private const val WIDGET_CELL_SIZE_2 = 110

        // Floors, not promises: both alarms are inexact and non-waking (see scheduleAlarm).
        private val REFRESH_INTERVAL = 5.minutes
        private val REDRAW_INTERVAL = 1.minutes

        // A widget just placed with no snapshot yet, or one whose snapshot is older than this, gets an
        // immediate fetch instead of waiting for the next 5-minute alarm.
        private val STALE_ON_PLACEMENT_WINDOW = 5.minutes

        // A render that finds a snapshot older than this refetches instead.
        private val STALE_SNAPSHOT_MAX_AGE = 10.minutes

        // Per-widget request-code slots, so no two broadcast PendingIntents collide.
        private const val REQUEST_CODE_SLOTS = 3
        private const val REQUEST_SLOT_REFRESH_ALARM = 0
        private const val REQUEST_SLOT_REFRESH_BUTTON = 1
        private const val REQUEST_SLOT_REDRAW_ALARM = 2

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
            val config = WidgetPrefs.loadConfig(context, appWidgetId)
            if (config != null && isPaused(context, config)) {
                // Nothing to fetch; just redraw the paused state.
                updateArrivalsFromCache(context, appWidgetId)
                return
            }
            applyLoadingState(context, appWidgetManager, appWidgetId)
            if (config != null) {
                WidgetArrivalWorker.enqueue(context, appWidgetId)
            }
        }

        /**
         * Populates the widget's arrival rows from the last cached [WidgetArrivalSnapshot]. Called by
         * [WidgetArrivalWorker] after a fetch, on placement and resize, and each minute to drop departed trips.
         *
         * Arrivals show clock times ("3:42pm"), not a countdown: widget text is static between redraws,
         * and on Android 12+ nothing guarantees a redraw on time (no exact alarms by default). A clock
         * time stays correct however late the next redraw is.
         *
         * @param allowStaleRefresh whether an unusable snapshot should trigger [refreshWidget] (which
         * enqueues a fresh [WidgetArrivalWorker] run). Must be `false` from the worker's own failure path
         * (`Result.retry()`) and the per-minute redraw: enqueuing a replacement run there would bypass
         * WorkManager's retry backoff and could hammer the API on every failure instead of backing off.
         */
        fun updateArrivalsFromCache(context: Context, widgetId: Int, allowStaleRefresh: Boolean = true) {
            val views = baseViews(context)
            val config = WidgetPrefs.loadConfig(context, widgetId)
            val appWidgetManager = AppWidgetManager.getInstance(context)

            if (config == null) {
                views.setTextViewText(R.id.stop_times_widget_title, context.getString(R.string.widget_not_configured))
                appWidgetManager.updateAppWidget(widgetId, views)
                return
            }
            views.setTextViewText(R.id.stop_times_widget_title, config.widgetName)

            if (isPaused(context, config)) {
                // The app is on another server than this widget's stop: show why and fetch nothing, since
                // that server would reject the id or answer for a different stop. Tapping opens the app,
                // not the stop's board, which would hit the wrong server too.
                views.setTextViewText(R.id.widget_updated_at, context.getString(R.string.widget_paused_other_region, config.deployment.displayName))
                bindAppIntent(context, views, widgetId)
                bindRefreshIntent(context, views, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
                return
            }

            // Renders with no route data — the widget just shows its title, no ETA rows. Used both when
            // there's nothing cached yet and when a cached snapshot exists but can't be trusted.
            fun renderWithoutRouteData() {
                bindStopIntent(context, views, widgetId, config)
                bindRefreshIntent(context, views, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }

            val snapshot = WidgetPrefs.loadSnapshot(context, widgetId, config)
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

            val rows = snapshot.routes.map { route -> route.shortName to route.arrivals.filter { it.isShownAt(nowServer) } }
            val pillTexts = rows.map { (_, arrivals) -> arrivals.map { pillText(context, it) } }
            bindRouteRows(context, views, rows, pillTexts)
            views.setTextViewText(
                R.id.widget_updated_at,
                context.getString(R.string.widget_updated_at_time, DisplayFormat.formatTime(context, snapshot.serverTimeAtFetchMs))
            )

            applySizeVisibility(context, views, minWidthDp, minHeightDp, pillTexts)
            bindStopIntent(context, views, widgetId, config)
            bindRefreshIntent(context, views, widgetId)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        // Switches the widget UI to the "loading" state for a single widget.
        private fun applyLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = baseViews(context)
            val config = WidgetPrefs.loadConfig(context, appWidgetId)

            if (config == null) {
                views.setTextViewText(R.id.stop_times_widget_title, context.getString(R.string.widget_not_configured))
            } else {
                views.setTextViewText(R.id.stop_times_widget_title, config.widgetName)
                bindStopIntent(context, views, appWidgetId, config)
                views.setViewVisibility(R.id.widget_loading_spinner, View.VISIBLE)
            }
            bindRefreshIntent(context, views, appWidgetId)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * A fresh [RemoteViews] reset to a known baseline: no rows, no spinner, no footer. A launcher
         * *reapplies* an update onto the views already showing when the layout is the same, so nothing
         * a render doesn't set falls back to the layout's defaults; it keeps whatever the last render set.
         */
        private fun baseViews(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.stop_times_widget).apply {
            for (rowId in WIDGET_ROW_IDS) setViewVisibility(rowId, View.GONE)
            setViewVisibility(R.id.widget_loading_spinner, View.GONE)
            setTextViewText(R.id.widget_updated_at, "")
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

        // A paused widget's tap opens the app (see updateArrivalsFromCache).
        private fun bindAppIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }

        /**
         * Whether the app is on a different server than [config]'s (see [WidgetDeployment]). Not while the
         * region is still loading: the cached snapshot came from the widget's own server, so keep showing it.
         */
        private fun isPaused(context: Context, config: WidgetConfig): Boolean {
            val current = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .widgetDeployments()
                .current()
            return current != CurrentServer.Loading && !config.isServedBy(current)
        }

        // Tapping the refresh button triggers an immediate API fetch.
        private fun bindRefreshIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
            val refreshIntent = Intent(context, StopTimesWidget::class.java).apply {
                action = ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * REQUEST_CODE_SLOTS + REQUEST_SLOT_REFRESH_BUTTON,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refresh_header, refreshPendingIntent)
        }

        // Shows or hides route rows and ETA columns based on the current widget dimensions.
        private fun applySizeVisibility(
            context: Context,
            views: RemoteViews,
            minWidthDp: Int,
            minHeightDp: Int,
            pillTexts: List<List<CharSequence>>
        ) {
            // Measured, since a clock time's width varies by locale and 12/24-hour setting. Min width is
            // the portrait width, the narrower layout.
            val res = context.resources
            val columns = pillColumnsThatFit(
                rowWidthPx = minWidthDp *
                    res.displayMetrics.density -
                    2 *
                    res.getDimension(R.dimen.widget_row_padding_horizontal) -
                    res.getDimension(R.dimen.widget_route_title_width),
                rows = pillTexts,
                measurePx = TextPaint(Paint.ANTI_ALIAS_FLAG).let { paint ->
                    // The widget's pinned font, not the launcher's (which may be wider and clip the last
                    // pill). getDesiredWidth, not measureText, so the AM/PM size span is measured too.
                    paint.typeface = Typeface.create(res.getString(R.string.widget_font_family), Typeface.NORMAL)
                    paint.textSize = res.getDimension(R.dimen.widget_eta_text_size)
                    ({ text: CharSequence -> Layout.getDesiredWidth(text, paint) })
                },
                pillPaddingPx = 2 * res.getDimension(R.dimen.widget_eta_padding_horizontal),
                pillMinWidthPx = res.getDimension(R.dimen.widget_eta_min_width),
                gapPx = res.getDimension(R.dimen.widget_eta_gap)
            )
            for (rowEtas in WIDGET_ETA_IDS) {
                for (column in columns until rowEtas.size) views.setViewVisibility(rowEtas[column], View.GONE)
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

        private fun scheduleAlarms(context: Context, appWidgetId: Int) {
            scheduleNextRefresh(context, appWidgetId)
            scheduleNextRedraw(context, appWidgetId)
        }

        private fun scheduleNextRefresh(context: Context, appWidgetId: Int) = scheduleAlarm(context, appWidgetId, ACTION_REFRESH_WIDGET, REQUEST_SLOT_REFRESH_ALARM, REFRESH_INTERVAL)

        private fun scheduleNextRedraw(context: Context, appWidgetId: Int) = scheduleAlarm(context, appWidgetId, ACTION_REDRAW_WIDGET, REQUEST_SLOT_REDRAW_ALARM, REDRAW_INTERVAL)

        /**
         * Schedules [action] for this widget [delay] from now. Inexact and non-waking on purpose: a
         * sleeping phone isn't woken for a widget nobody can see, and an alarm that came due during sleep
         * fires as the phone wakes. Being late only delays a departed trip's removal or the next fetch.
         */
        private fun scheduleAlarm(context: Context, appWidgetId: Int, action: String, slot: Int, delay: Duration) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, StopTimesWidget::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId * REQUEST_CODE_SLOTS + slot,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, (ElapsedTime.now() + delay).ms, pendingIntent)
        }

        // One row per route: its name, then a pill per arrival labelled from [pillTexts].
        private fun bindRouteRows(
            context: Context,
            views: RemoteViews,
            rows: List<Pair<String, List<WidgetArrivalSnapshot.Arrival>>>,
            pillTexts: List<List<CharSequence>>
        ) {
            for (rowIndex in WIDGET_ROUTE_TITLE_IDS.indices) {
                if (rowIndex >= rows.size) {
                    views.setViewVisibility(WIDGET_ROW_IDS[rowIndex], View.GONE)
                    continue
                }
                views.setViewVisibility(WIDGET_ROW_IDS[rowIndex], View.VISIBLE)

                val (shortName, arrivals) = rows[rowIndex]
                views.setTextViewText(WIDGET_ROUTE_TITLE_IDS[rowIndex], shortName)
                bindEtaPills(context, views, WIDGET_ETA_IDS[rowIndex], arrivals, pillTexts[rowIndex])
            }
        }

        private fun bindEtaPills(
            context: Context,
            views: RemoteViews,
            etaViewIds: IntArray,
            arrivals: List<WidgetArrivalSnapshot.Arrival>,
            texts: List<CharSequence>
        ) {
            if (arrivals.isEmpty()) {
                // No upcoming arrivals — show N/A in the first pill and hide the rest.
                views.setTextViewText(etaViewIds[0], context.getString(R.string.widget_no_times))
                views.setInt(etaViewIds[0], "setBackgroundResource", R.drawable.widget_eta_bg_scheduled)
                views.setContentDescription(etaViewIds[0], null)
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
                val eta = texts[i]
                if (arrival.isCanceled) {
                    // Struck through, as on the arrivals board. Screen readers don't announce the
                    // strike, hence the description.
                    views.setTextViewText(
                        etaViewIds[i],
                        SpannableString(eta).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                    )
                    views.setContentDescription(etaViewIds[i], context.getString(R.string.widget_eta_canceled_description, eta.toString()))
                } else {
                    views.setTextViewText(etaViewIds[i], eta)
                    views.setContentDescription(etaViewIds[i], null) // clear a previous canceled trip's
                }
                views.setInt(etaViewIds[i], "setBackgroundResource", etaBackgroundResource(arrival))
                views.setViewVisibility(etaViewIds[i], View.VISIBLE)
            }
        }

        /** A pill's clock time with its AM/PM marker shrunk, as the board shrinks a countdown's units. */
        private fun pillText(context: Context, arrival: WidgetArrivalSnapshot.Arrival): CharSequence {
            val markerSizePx = context.resources.getDimensionPixelSize(R.dimen.widget_eta_marker_text_size)
            val text = SpannableStringBuilder()
            for (part in DisplayFormat.formatTimeParts(context, arrival.displayTimeMs)) {
                val start = text.length
                text.append(part.text)
                if (!part.emphasized) text.setSpan(AbsoluteSizeSpan(markerSizePx), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return text
        }

        // The pill color is the app's single source of truth for schedule-deviation color (#2043) —
        // the same states/colors the arrivals board's own ETA pills use — rather than a widget-only
        // palette. A canceled trip is always gray, per the board's legend.
        private fun etaBackgroundResource(arrival: WidgetArrivalSnapshot.Arrival): Int {
            if (arrival.isCanceled || !arrival.isPredicted) return R.drawable.widget_eta_bg_scheduled
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

/**
 * How many ETA columns fit every row in [rowWidthPx] (after padding and the route name); at least 1.
 * Pill sizing mirrors layout/stop_times_widget.xml via the same dimens. Rows with fewer pills don't
 * limit the others.
 */
internal fun pillColumnsThatFit(
    rowWidthPx: Float,
    rows: List<List<CharSequence>>,
    measurePx: (CharSequence) -> Float,
    pillPaddingPx: Float,
    pillMinWidthPx: Float,
    gapPx: Float
): Int {
    val maxColumns = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    val fitting = rows.minOfOrNull { texts ->
        var used = 0f
        var fit = 0
        for ((i, text) in texts.withIndex()) {
            used += (if (i > 0) gapPx else 0f) + maxOf(measurePx(text) + pillPaddingPx, pillMinWidthPx)
            if (used > rowWidthPx) break
            fit++
        }
        if (fit == texts.size) Int.MAX_VALUE else fit
    } ?: Int.MAX_VALUE
    return fitting.coerceIn(1, maxColumns)
}
